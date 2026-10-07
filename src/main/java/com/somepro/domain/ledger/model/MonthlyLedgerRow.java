package com.somepro.domain.ledger.model;

import com.somepro.domain.collateral.model.Category;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 月度经营台账的一行（领域只读模型，不可变 record）。
 *
 * 一行 = 「一个自然月 × 一个类别」这一格当月的四档流水：
 * <ol>
 *   <li>新开票数 / 新放当金合计 —— 按开票办理时刻（当票落账时刻）归月；</li>
 *   <li>续当次数 —— 按续当办理时刻（renewed_at）归月；</li>
 *   <li>赎当笔数 / 赎当收回本金合计 / 赎当费用合计 —— 按赎当办理时刻（redeemed_at）归月，
 *       本金合计是每笔赎当对应票面当金的累加，费用合计是每笔赎当结出来的费用（fee_amount）累加；</li>
 *   <li>绝当笔数 —— 按绝当处置时刻（forfeited_at）归月。</li>
 * </ol>
 * 四档数各算各的格，互不串月、互不串类别。已打删除标记的票 / 续当 / 赎当 / 绝当在 SQL 里已被剔掉，
 * 不会进到这里把数字撑大。
 *
 * @param month              所属自然月
 * @param category           类别
 * @param newTicketCount     本月本类别新开票数
 * @param newPawnAmount      本月本类别新放当金合计（元）
 * @param renewCount         本月本类别续当次数
 * @param redeemCount        本月本类别赎当笔数
 * @param redeemedPrincipal  本月本类别赎当收回本金合计 = 每笔赎当对应票面当金累加（元）
 * @param redeemedFee        本月本类别赎当费用合计 = 每笔赎当 fee_amount 累加（元）
 * @param forfeitCount       本月本类别绝当笔数
 */
public record MonthlyLedgerRow(YearMonth month,
                               Category category,
                               int newTicketCount,
                               BigDecimal newPawnAmount,
                               int renewCount,
                               int redeemCount,
                               BigDecimal redeemedPrincipal,
                               BigDecimal redeemedFee,
                               int forfeitCount) {

    /** 金额统一两位小数，空格也是 0.00 而不是 null，前端直接求和不会 NPE。 */
    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }

    /** 一格数全是 0 的占位行：某个「月 × 类别」四档流水一条都没有时也给它占一行。 */
    public static MonthlyLedgerRow empty(YearMonth month, Category category) {
        return new MonthlyLedgerRow(month, category, 0, zero(), 0, 0, zero(), zero(), 0);
    }

    /**
     * 把四档流水的稀疏汇总（只有真有业务的格才在表里有行）补成整张台账并排序。
     *
     * 补零格子的月份 / 类别宇宙：
     * - 月份：查询指定了月份就只摆这个月；否则摆稀疏数据里出现过的每个月；
     * - 类别：查询指定了类别就只摆这个类别；否则五个类别（{@link Category#values()}）全摆。
     * 两者做笛卡尔积，稀疏数据里缺的格补全零行 —— 指定月份时，哪怕这个月所有类别都没业务，
     * 也会回满五类零行，不会整页空掉报错；月份都不限且全库无数据时没有月份宇宙，交回空表。
     *
     * 排序固定月份升序、同月类别按枚举声明次序升序，翻页稳定、对账对得上。
     *
     * @param facts        SQL 汇总出来的稀疏格（同一「月 × 类别」至多一行）
     * @param queryMonth   查询指定月份，null 表示没指定
     * @param queryCategory 查询指定类别，null 表示没指定
     */
    public static List<MonthlyLedgerRow> assembleGrid(List<MonthlyLedgerRow> facts,
                                                      YearMonth queryMonth,
                                                      Category queryCategory) {
        Set<YearMonth> presentMonths = new LinkedHashSet<>();
        Map<GridKey, MonthlyLedgerRow> byKey = new LinkedHashMap<>();
        for (MonthlyLedgerRow fact : facts) {
            byKey.merge(new GridKey(fact.month(), fact.category()), fact, MonthlyLedgerRow::mergeSameGrid);
            presentMonths.add(fact.month());
        }

        List<YearMonth> monthUniverse;
        if (queryMonth != null) {
            monthUniverse = List.of(queryMonth);
        } else {
            monthUniverse = presentMonths.stream()
                    .sorted()
                    .toList();
        }

        List<MonthlyLedgerRow> grid = new ArrayList<>(monthUniverse.size() * Category.values().length);
        for (YearMonth month : monthUniverse) {
            for (Category category : Category.values()) {
                if (queryCategory != null && category != queryCategory) {
                    continue;
                }
                MonthlyLedgerRow fact = byKey.get(new GridKey(month, category));
                grid.add(fact != null ? fact : empty(month, category));
            }
        }
        grid.sort(Comparator
                .comparing(MonthlyLedgerRow::month)
                .thenComparingInt(row -> row.category().ordinal()));
        return grid;
    }

    /** 同一「月 × 类别」若因 SQL 分段返回了多行，这里做防御性相加，保证一格只出一个数。 */
    private MonthlyLedgerRow mergeSameGrid(MonthlyLedgerRow other) {
        return new MonthlyLedgerRow(
                this.month,
                this.category,
                this.newTicketCount + other.newTicketCount,
                this.newPawnAmount.add(other.newPawnAmount).setScale(2),
                this.renewCount + other.renewCount,
                this.redeemCount + other.redeemCount,
                this.redeemedPrincipal.add(other.redeemedPrincipal).setScale(2),
                this.redeemedFee.add(other.redeemedFee).setScale(2),
                this.forfeitCount + other.forfeitCount);
    }

    private record GridKey(YearMonth month, Category category) {
    }
}
