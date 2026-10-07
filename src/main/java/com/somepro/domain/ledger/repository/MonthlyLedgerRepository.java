package com.somepro.domain.ledger.repository;

import com.somepro.domain.ledger.model.LedgerQuery;
import com.somepro.domain.ledger.model.MonthlyLedgerRow;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

/**
 * 月度经营台账只读仓储端口（领域层）。
 *
 * 台账是四档流水（开票 / 续当 / 赎当 / 绝当）按「自然月 × 类别」铺开的汇总表，
 * 端口负责按 {@link LedgerQuery} 翻补零后的整格台账并分页；实现落在基础设施层。
 */
public interface MonthlyLedgerRepository {

    /**
     * 翻月度台账：先汇总出有业务的稀疏格、补零格子、按月份与类别排序，再一页页切。
     *
     * @param pageNum  页码，从 1 起
     * @param pageSize 每页条数
     * @param query    月份 / 类别过滤条件
     */
    Mono<PageResult<MonthlyLedgerRow>> page(int pageNum, int pageSize, LedgerQuery query);
}
