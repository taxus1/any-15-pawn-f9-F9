package com.somepro.interfaces.rest.ledger.vo;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 月度经营台账一行对外对象（不可变 record）。
 *
 * 一行 = 一个自然月 × 一个类别当月的四档流水：
 * month 月份（yyyy-MM）、category 类别编码、categoryLabel 类别中文名，
 * 新开票数 / 新放当金合计、续当次数、赎当笔数 / 赎当收回本金合计 / 赎当费用合计、绝当笔数。
 * 没有业务的格也是一行、各数为 0。刻意不暴露任何审计 / 删除字段。
 */
public record MonthlyLedgerRowVO(String month,
                                 String category,
                                 String categoryLabel,
                                 Integer newTicketCount,
                                 BigDecimal newPawnAmount,
                                 Integer renewCount,
                                 Integer redeemCount,
                                 BigDecimal redeemedPrincipal,
                                 BigDecimal redeemedFee,
                                 Integer forfeitCount)
        implements Serializable {
}
