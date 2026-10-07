package com.somepro.infrastructure.persistence.ledger;

import com.somepro.domain.collateral.model.Category;
import com.somepro.domain.ledger.model.LedgerQuery;
import com.somepro.domain.ledger.model.MonthlyLedgerRow;
import com.somepro.domain.ledger.repository.MonthlyLedgerRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.config.ReactiveOperatorContext;
import com.somepro.infrastructure.persistence.audit.AuditContextHolder;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 月度经营台账仓储适配器（基础设施层）：四档流水聚合的只读查询，经 blocking(...) 桥接进响应式链路。
 *
 * 台账补零格子与分页都在这里收口：
 * 1. 一条 UNION ALL 聚合 SQL 取回「有业务」的稀疏格（格数 = 月数 × 类别数，天然很小，不分页 SQL）；
 * 2. 交给领域模型 {@link MonthlyLedgerRow#assembleGrid} 按查询条件把「月 × 类别」空格补零、排序；
 * 3. 再在内存里切当前页 —— 补零本身会改变行数，没法让 PageHelper 直接分页，
 *    而月度汇总行整年也就几十行量级，全量取稀疏格再切页没有性能问题，total 也能算准。
 *
 * 月份过滤在 SQL 侧按半开区间压下去；查询不指定月份时区间两端为 null，不拼该条件。
 */
@Repository
public class MonthlyLedgerRepositoryImpl implements MonthlyLedgerRepository {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final MonthlyLedgerMapper monthlyLedgerMapper;

    public MonthlyLedgerRepositoryImpl(MonthlyLedgerMapper monthlyLedgerMapper) {
        this.monthlyLedgerMapper = monthlyLedgerMapper;
    }

    @Override
    public Mono<PageResult<MonthlyLedgerRow>> page(int pageNum, int pageSize, LedgerQuery query) {
        // 自然月半开区间：[1 号 00:00, 次月 1 号 00:00)，月底最后一天的办理归本月、次月一号归次月
        LocalDateTime monthStart = query.month() == null ? null
                : query.month().atDay(1).atStartOfDay();
        LocalDateTime monthEnd = query.month() == null ? null
                : query.month().plusMonths(1).atDay(1).atStartOfDay();
        String category = query.category() == null ? null : query.category().code();

        return this.<PageResult<MonthlyLedgerRow>>blocking(() -> {
            List<LedgerAggregateRow> aggregates =
                    monthlyLedgerMapper.selectMonthlyAggregates(monthStart, monthEnd, category);
            List<MonthlyLedgerRow> facts = aggregates.stream()
                    .map(MonthlyLedgerRepositoryImpl::toDomain)
                    .collect(Collectors.toList());
            // 补零格子 + 排序：指定月份时全类别零行也会补出来；全库无数据且未指定月份时为空表
            List<MonthlyLedgerRow> grid =
                    MonthlyLedgerRow.assembleGrid(facts, query.month(), query.category());

            long total = grid.size();
            int fromIndex = (pageNum - 1) * pageSize;
            List<MonthlyLedgerRow> pageContent = fromIndex >= grid.size()
                    ? List.of()
                    : grid.subList(fromIndex, Math.min(fromIndex + pageSize, grid.size()));
            // 库里一条都没有时 content 为空、total=0：交回空页，不报错
            return new PageResult<>(pageContent, total, pageNum, pageSize);
        });
    }

    /** 投影行 → 领域台账行：月份字符串解析回 YearMonth，金额统一两位、计数缺省补 0。 */
    private static MonthlyLedgerRow toDomain(LedgerAggregateRow row) {
        return new MonthlyLedgerRow(
                YearMonth.parse(row.getMonth(), MONTH_FORMATTER),
                Category.valueOf(row.getCategory()),
                row.getNewTicketCount() == null ? 0 : row.getNewTicketCount().intValue(),
                scale(row.getNewPawnAmount()),
                row.getRenewCount() == null ? 0 : row.getRenewCount().intValue(),
                row.getRedeemCount() == null ? 0 : row.getRedeemCount().intValue(),
                scale(row.getRedeemedPrincipal()),
                scale(row.getRedeemedFee()),
                row.getForfeitCount() == null ? 0 : row.getForfeitCount().intValue());
    }

    private static BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2);
    }

    /**
     * 阻塞 DB 调用 → 响应式链路桥接器：先从 Reactor Context 取操作人，再切到 boundedElastic
     * （纯读不写审计，取操作人仅为与其它只读端口保持同一套桥接约定）。
     */
    private <T> Mono<T> blocking(Supplier<T> supplier) {
        return Mono.deferContextual(ctx -> {
            String operator = ReactiveOperatorContext.getOperator(ctx);
            return Mono.fromCallable(() -> {
                AuditContextHolder.setOperator(operator);
                try {
                    return supplier.get();
                } finally {
                    AuditContextHolder.clear();
                }
            }).subscribeOn(Schedulers.boundedElastic());
        });
    }
}
