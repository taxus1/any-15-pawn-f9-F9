package com.somepro.interfaces.rest.ledger;

import com.somepro.application.ledger.MonthlyLedgerAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.common.vo.PageVO;
import com.somepro.interfaces.rest.ledger.converter.MonthlyLedgerVoConverter;
import com.somepro.interfaces.rest.ledger.vo.MonthlyLedgerRowVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 月度经营台账用户接口层：一行 = 一个自然月 × 一个类别当月的开票 / 续当 / 赎当 / 绝当汇总。
 *
 * 只做协议适配（参数解析、VO 转换、Result 包装），业务编排在 {@link MonthlyLedgerAppService}。
 * 接口只读：翻多少遍台账都不动票、续当、赎当、绝当任何一笔。
 */
@RestController
@RequestMapping("/api/ledger/monthly")
public class MonthlyLedgerController {

    private final MonthlyLedgerAppService monthlyLedgerAppService;

    public MonthlyLedgerController(MonthlyLedgerAppService monthlyLedgerAppService) {
        this.monthlyLedgerAppService = monthlyLedgerAppService;
    }

    /**
     * 翻月度台账：
     * - month 按 yyyy-MM 精确到自然月（办理时刻落在该月内），不传翻所有有业务的月份；
     * - category 只认 JEWELRY / WATCH / ELECTRONICS / VEHICLE / OTHER，不传五类全摆；
     * - 指定了月份时，该月没有业务的类别也各占一行零数；无数据时返回空页不报错；
     * - 已打删除标记的票 / 续当 / 赎当 / 绝当不计入；
     * - 排序为月份升序、同月按类别次序；pageNum/pageSize 一页页走。
     */
    @GetMapping("/list")
    public Mono<Result<PageVO<MonthlyLedgerRowVO>>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String category) {
        return monthlyLedgerAppService.page(pageNum, pageSize, month, category)
                .map(MonthlyLedgerVoConverter::toPageVo)
                .map(Result::ok);
    }
}
