package com.somepro.infrastructure.persistence.ledger;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 月度台账四档流水汇总 SQL 的投影行（基础设施层）。
 *
 * 不是任何一张表的 PO、没有 @TableName —— 它是开票 / 续当 / 赎当 / 绝当四段聚合
 * UNION ALL 后再按「月 × 类别」合并出来的一格形状，由 MyBatis 按列别名（驼峰映射）填充。
 * 只在基础设施层内使用，不外泄到领域 / 接口层。
 *
 * 月份统一落成 yyyy-MM 字符串（SQL DATE_FORMAT 与 Java YearMonth 同一写法），
 * 到仓储适配器再解析成 {@link java.time.YearMonth}；类别是票面类别快照的枚举名。
 * 七个数里没有用到的档位一律为 0（SQL 里已 IFNULL），不会出现 null。
 */
@Getter
@Setter
public class LedgerAggregateRow {

    /** 所属自然月，yyyy-MM。 */
    private String month;

    /** 类别（t_pawn_ticket.category 枚举名）。 */
    private String category;

    /** 本月本类别新开票数。 */
    private Long newTicketCount;

    /** 本月本类别新放当金合计。 */
    private BigDecimal newPawnAmount;

    /** 本月本类别续当次数。 */
    private Long renewCount;

    /** 本月本类别赎当笔数。 */
    private Long redeemCount;

    /** 本月本类别赎当收回本金合计（每笔票面当金累加）。 */
    private BigDecimal redeemedPrincipal;

    /** 本月本类别赎当费用合计（每笔 fee_amount 累加）。 */
    private BigDecimal redeemedFee;

    /** 本月本类别绝当笔数。 */
    private Long forfeitCount;
}
