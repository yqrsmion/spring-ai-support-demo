package com.example.support;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 本地确定性工具：模拟订单、FAQ 与客服指标，让工具调用链路可复现，
 * 不依赖数据库或外部业务系统。
 */
@Component
public class SupportTools {

    private static final Logger log = LoggerFactory.getLogger(SupportTools.class);

    private static final List<Faq> FAQS = List.of(
            new Faq("退换货", "签收后 7 天内支持无理由退换货，商品需保持完好且不影响二次销售。"),
            new Faq("物流", "默认 48 小时内发货，物流时效 3~7 天，偏远地区可能延长 1~2 天。"),
            new Faq("发票", "支持电子发票，订单完成后可在订单详情页申请，3 个工作日内开具。"),
            new Faq("售后", "售后问题可在订单详情页提交工单，客服会在 24 小时内响应。"),
            new Faq("会员", "会员下单享免运费与专属客服，具体权益以会员中心展示为准。")
    );

    private static final Map<String, String> ORDERS = Map.of(
            "10001", "已发货（顺丰，预计 2 天内送达）",
            "10002", "已签收（2026-08-16）",
            "10003", "退款处理中（预计 1~3 个工作日到账）"
    );

    @Tool(description = "查询 FAQ 政策库，适用于用户询问退换货、物流、发票、售后、会员等政策。")
    public String searchFaq(String keyword) {
        log.info("Tool invoked: searchFaq, keyword={}", keyword);
        if (keyword == null || keyword.isBlank()) {
            return "请提供明确的检索关键词。";
        }
        String normalized = keyword.toLowerCase(Locale.ROOT);
        return FAQS.stream()
                .filter(faq -> {
                    String question = faq.question().toLowerCase(Locale.ROOT);
                    String answer = faq.answer().toLowerCase(Locale.ROOT);
                    // 关键词命中条目名/内容，或关键词包含条目名（如"退换货政策"包含"退换货"）
                    return answer.contains(normalized)
                            || question.contains(normalized)
                            || normalized.contains(question);
                })
                .map(faq -> faq.question() + "：" + faq.answer())
                .findFirst()
                .orElse("FAQ 中没有匹配内容，请基于通用知识回答并说明信息来源不足。");
    }

    @Tool(description = "查询模拟订单状态。只在用户询问具体订单号时调用。")
    public String getOrderStatus(String orderId) {
        log.info("Tool invoked: getOrderStatus, orderId={}", orderId);
        if (orderId == null || orderId.isBlank()) {
            return "请提供订单号。";
        }
        return ORDERS.getOrDefault(orderId.trim(), "未找到订单 " + orderId + "，请核对订单号。");
    }

    @Tool(description = "获取客服运行指标。只在用户询问会话量、响应时长、满意度、排队等指标时调用。")
    public String getSupportMetrics() {
        log.info("Tool invoked: getSupportMetrics");
        return "演示数据：今日会话量 1280；平均首次响应时长 18 秒；满意度 4.6/5；当前排队 3 人。";
    }

    private record Faq(String question, String answer) {
    }
}
