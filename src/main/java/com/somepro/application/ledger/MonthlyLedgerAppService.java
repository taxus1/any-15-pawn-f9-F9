package com.somepro.application.ledger;

import com.somepro.common.exception.BizException;
import com.somepro.domain.collateral.model.Category;
import com.somepro.domain.ledger.model.LedgerQuery;
import com.somepro.domain.ledger.model.MonthlyLedgerRow;
import com.somepro.domain.ledger.repository.MonthlyLedgerRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 月度经营台账应用服务：编排「月底老板看这个月生意做得怎么样」这一个只读用例 ——
 * 把开票 / 续当 / 赎当 / 绝当四档流水按「自然月 × 类别」铺成一张汇总表，一页页翻。
 *
 * 出入参用领域对象/基础类型，不认识 PO 与 VO。全程只读，不触碰任何办理落库链。
 */
@Service
public class MonthlyLedgerAppService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final MonthlyLedgerRepository monthlyLedgerRepository;

    public MonthlyLedgerAppService(MonthlyLedgerRepository monthlyLedgerRepository) {
        this.monthlyLedgerRepository = monthlyLedgerRepository;
    }

    /**
     * 翻月度台账。
     *
     * @param pageNum  页码，从 1 起
     * @param pageSize 每页条数
     * @param month    月份筛选，yyyy-MM；空串 / null 表示不按月份筛
     * @param category 类别筛选，空串 / null 表示不按类别筛
     */
    public Mono<PageResult<MonthlyLedgerRow>> page(int pageNum, int pageSize, String month, String category) {
        if (pageNum < 1 || pageSize < 1) {
            return Mono.error(new BizException("页码与每页条数必须为正整数"));
        }
        LedgerQuery query = LedgerQuery.of(parseMonth(month), Category.ofCode(blankToNull(category)));
        return monthlyLedgerRepository.page(pageNum, pageSize, query);
    }

    /** 月份入参解析：只认 yyyy-MM（如 2026-09）；留空表示不按月份筛。 */
    private YearMonth parseMonth(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(raw.trim(), MONTH_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new BizException("月份格式应为 yyyy-MM：" + raw);
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
