package com.somepro.interfaces.rest.ledger.converter;

import com.somepro.domain.ledger.model.MonthlyLedgerRow;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.interfaces.rest.common.vo.PageVO;
import com.somepro.interfaces.rest.ledger.vo.MonthlyLedgerRowVO;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 月度台账领域行 → VO 转换器（用户接口层）。Controller 不直接把领域对象塞进 Result。
 */
public final class MonthlyLedgerVoConverter {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private MonthlyLedgerVoConverter() {
    }

    public static MonthlyLedgerRowVO toVo(MonthlyLedgerRow row) {
        return new MonthlyLedgerRowVO(
                row.month().format(MONTH_FORMATTER),
                row.category().code(),
                row.category().label(),
                row.newTicketCount(),
                row.newPawnAmount(),
                row.renewCount(),
                row.redeemCount(),
                row.redeemedPrincipal(),
                row.redeemedFee(),
                row.forfeitCount());
    }

    public static PageVO<MonthlyLedgerRowVO> toPageVo(PageResult<MonthlyLedgerRow> page) {
        List<MonthlyLedgerRowVO> content = page.content().stream()
                .map(MonthlyLedgerVoConverter::toVo)
                .collect(Collectors.toList());
        return new PageVO<>(content, page.total(), page.pageNum(), page.pageSize(), page.totalPages());
    }
}
