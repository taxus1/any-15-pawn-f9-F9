package com.somepro.domain.ledger.model;

import com.somepro.domain.collateral.model.Category;

import java.time.YearMonth;

/**
 * 月度经营台账翻账条件（不可变值对象）。
 *
 * 月份、类别都可空：任一项为 null 即不参与过滤，全不填翻整份台账。
 * 月份按自然月精确筛（办理时刻落在该月 1 号到下个自然月 1 号之前，左闭右开），
 * 类别在进入本对象前已由 {@link Category#ofCode} 解析，非法写法在解析阶段挡回。
 *
 * @param month    指定月份；null 表示不按月份筛
 * @param category 指定类别；null 表示不按类别筛
 */
public record LedgerQuery(YearMonth month, Category category) {

    public static LedgerQuery of(YearMonth month, Category category) {
        return new LedgerQuery(month, category);
    }
}
