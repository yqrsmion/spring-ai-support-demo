package com.example.support;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SupportToolsTest {

    private final SupportTools tools = new SupportTools();

    @Test
    void searchFaq_returnsMatchingEntry() {
        String result = tools.searchFaq("退换货政策");
        assertTrue(result.contains("退换货"), result);
        assertTrue(result.contains("7 天"), result);
    }

    @Test
    void searchFaq_unknownKeywordReturnsHint() {
        String result = tools.searchFaq("量子计算");
        assertTrue(result.contains("没有匹配"), result);
    }

    @Test
    void getOrderStatus_knownAndUnknown() {
        String known = tools.getOrderStatus("10003");
        assertTrue(known.contains("退款"), known);
        String unknown = tools.getOrderStatus("99999");
        assertTrue(unknown.contains("未找到"), unknown);
    }

    @Test
    void getSupportMetrics_returnsDemoMetrics() {
        String result = tools.getSupportMetrics();
        assertTrue(result.contains("满意度"), result);
        assertTrue(result.contains("会话量"), result);
    }
}
