package com.somepro.infrastructure.persistence.ledger;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 月度经营台账只读 Mapper（基础设施层）。
 *
 * 一条 SQL 把四档流水点齐：开票（t_pawn_ticket）/ 续当（t_pawn_renew）/ 赎当（t_pawn_redeem）/
 * 绝当（t_pawn_forfeit）各自按「自然月 × 票面类别」先聚合，UNION ALL 拼成稀疏长表后，
 * 外层再按「月 × 类别」合并成一格。本 Mapper 不做任何写入。
 *
 * 归月口径（看办理时刻落在哪个自然月，月底最后一天算本月、次月一号算次月）：
 * - 开票：当票落账时刻 t.create_time（起当日期 start_date 是业务日期，可与开票时刻不同月，不能拿它归月）；
 * - 续当：r.renewed_at；
 * - 赎当：rd.redeemed_at；
 * - 绝当：f.forfeited_at。
 * 月份用 DATE_FORMAT(...,'%Y-%m') 现算（库连接时区固定 Asia/Shanghai，与行里时区一致）。
 *
 * 删除口径：四段都只点 del_flag = 0 的行 —— 已销掉的票 / 续当 / 赎当 / 绝当一概不进台账；
 * 续当 / 赎当 / 绝当本身不存类别，INNER JOIN 未删除的当票取票面类别快照，
 * 票若被销掉，挂在它名下的续 / 赎 / 绝当也随票一起不算。
 *
 * 赎当这格的两个钱：
 * - 收回本金合计 = SUM(t.pawn_amount) —— 赎当表不存本金，本金一律照票面当金取，
 *   与「一笔赎当对应一张票、一张票只赎一回」的口径一致；
 * - 费用合计 = SUM(rd.fee_amount) —— 逐笔赎当办理当下结出来定格的钱累加，不重算。
 *
 * 月份过滤走半开区间 [monthStart, monthEnd)（monthStart=1 号 00:00:00、
 * monthEnd=次月 1 号 00:00:00），边界时刻归月不会错，也能吃上时间列上的索引。
 * 类别过滤直接压到每一段上，类别取自票面快照。
 */
@Mapper
public interface MonthlyLedgerMapper {

    @Select("""
            <script>
            SELECT agg.month                                AS month,
                   agg.category                             AS category,
                   SUM(agg.new_ticket_count)                AS new_ticket_count,
                   SUM(agg.new_pawn_amount)                 AS new_pawn_amount,
                   SUM(agg.renew_count)                     AS renew_count,
                   SUM(agg.redeem_count)                    AS redeem_count,
                   SUM(agg.redeemed_principal)              AS redeemed_principal,
                   SUM(agg.redeemed_fee)                    AS redeemed_fee,
                   SUM(agg.forfeit_count)                   AS forfeit_count
            FROM (
                /* 档一：开票 —— 按当票落账时刻归月 */
                SELECT DATE_FORMAT(t.create_time, '%Y-%m') AS month,
                       t.category                          AS category,
                       COUNT(1)                            AS new_ticket_count,
                       SUM(t.pawn_amount)                  AS new_pawn_amount,
                       CAST(NULL AS UNSIGNED)              AS renew_count,
                       CAST(NULL AS UNSIGNED)              AS redeem_count,
                       CAST(NULL AS DECIMAL(14,2))         AS redeemed_principal,
                       CAST(NULL AS DECIMAL(14,2))         AS redeemed_fee,
                       CAST(NULL AS UNSIGNED)              AS forfeit_count
                FROM t_pawn_ticket t
                WHERE t.del_flag = 0
                  AND t.create_time IS NOT NULL
                <if test="monthStart != null">AND t.create_time &gt;= #{monthStart}</if>
                <if test="monthEnd != null">AND t.create_time &lt; #{monthEnd}</if>
                <if test="category != null">AND t.category = #{category}</if>
                GROUP BY DATE_FORMAT(t.create_time, '%Y-%m'), t.category

                UNION ALL

                /* 档二：续当 —— 按续当办理时刻归月，类别回票面快照取 */
                SELECT DATE_FORMAT(r.renewed_at, '%Y-%m') AS month,
                       t.category                         AS category,
                       CAST(NULL AS UNSIGNED)             AS new_ticket_count,
                       CAST(NULL AS DECIMAL(14,2))        AS new_pawn_amount,
                       COUNT(1)                           AS renew_count,
                       CAST(NULL AS UNSIGNED)             AS redeem_count,
                       CAST(NULL AS DECIMAL(14,2))        AS redeemed_principal,
                       CAST(NULL AS DECIMAL(14,2))        AS redeemed_fee,
                       CAST(NULL AS UNSIGNED)             AS forfeit_count
                FROM t_pawn_renew r
                INNER JOIN t_pawn_ticket t ON t.id = r.ticket_id AND t.del_flag = 0
                WHERE r.del_flag = 0
                  AND r.renewed_at IS NOT NULL
                <if test="monthStart != null">AND r.renewed_at &gt;= #{monthStart}</if>
                <if test="monthEnd != null">AND r.renewed_at &lt; #{monthEnd}</if>
                <if test="category != null">AND t.category = #{category}</if>
                GROUP BY DATE_FORMAT(r.renewed_at, '%Y-%m'), t.category

                UNION ALL

                /* 档三：赎当 —— 按赎当办理时刻归月；本金照票面当金，费用逐笔 fee_amount */
                SELECT DATE_FORMAT(rd.redeemed_at, '%Y-%m') AS month,
                       t.category                          AS category,
                       CAST(NULL AS UNSIGNED)              AS new_ticket_count,
                       CAST(NULL AS DECIMAL(14,2))         AS new_pawn_amount,
                       CAST(NULL AS UNSIGNED)              AS renew_count,
                       COUNT(1)                            AS redeem_count,
                       SUM(t.pawn_amount)                  AS redeemed_principal,
                       SUM(rd.fee_amount)                  AS redeemed_fee,
                       CAST(NULL AS UNSIGNED)              AS forfeit_count
                FROM t_pawn_redeem rd
                INNER JOIN t_pawn_ticket t ON t.id = rd.ticket_id AND t.del_flag = 0
                WHERE rd.del_flag = 0
                  AND rd.redeemed_at IS NOT NULL
                <if test="monthStart != null">AND rd.redeemed_at &gt;= #{monthStart}</if>
                <if test="monthEnd != null">AND rd.redeemed_at &lt; #{monthEnd}</if>
                <if test="category != null">AND t.category = #{category}</if>
                GROUP BY DATE_FORMAT(rd.redeemed_at, '%Y-%m'), t.category

                UNION ALL

                /* 档四：绝当 —— 按处置时刻归月，类别回票面快照取 */
                SELECT DATE_FORMAT(f.forfeited_at, '%Y-%m') AS month,
                       t.category                         AS category,
                       CAST(NULL AS UNSIGNED)             AS new_ticket_count,
                       CAST(NULL AS DECIMAL(14,2))        AS new_pawn_amount,
                       CAST(NULL AS UNSIGNED)             AS renew_count,
                       CAST(NULL AS UNSIGNED)             AS redeem_count,
                       CAST(NULL AS DECIMAL(14,2))        AS redeemed_principal,
                       CAST(NULL AS DECIMAL(14,2))        AS redeemed_fee,
                       COUNT(1)                           AS forfeit_count
                FROM t_pawn_forfeit f
                INNER JOIN t_pawn_ticket t ON t.id = f.ticket_id AND t.del_flag = 0
                WHERE f.del_flag = 0
                  AND f.forfeited_at IS NOT NULL
                <if test="monthStart != null">AND f.forfeited_at &gt;= #{monthStart}</if>
                <if test="monthEnd != null">AND f.forfeited_at &lt; #{monthEnd}</if>
                <if test="category != null">AND t.category = #{category}</if>
                GROUP BY DATE_FORMAT(f.forfeited_at, '%Y-%m'), t.category
            ) agg
            GROUP BY agg.month, agg.category
            </script>
            """)
    List<LedgerAggregateRow> selectMonthlyAggregates(@Param("monthStart") LocalDateTime monthStart,
                                                     @Param("monthEnd") LocalDateTime monthEnd,
                                                     @Param("category") String category);
}
