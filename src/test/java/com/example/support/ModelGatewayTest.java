package com.example.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.support.ModelGateway.ParsedCommand;
import org.junit.jupiter.api.Test;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import reactor.core.publisher.Mono;

class ModelGatewayTest {

    @Test
    void resolveModel_mapsAliases() {
        assertEquals(DeepSeekApi.ChatModel.DEEPSEEK_CHAT, ModelGateway.resolveModel("chat"));
        assertEquals(DeepSeekApi.ChatModel.DEEPSEEK_CHAT, ModelGateway.resolveModel("deepseek-chat"));
        assertEquals(DeepSeekApi.ChatModel.DEEPSEEK_REASONER, ModelGateway.resolveModel("reasoner"));
        assertEquals(DeepSeekApi.ChatModel.DEEPSEEK_V4_FLASH, ModelGateway.resolveModel("flash"));
        assertEquals(DeepSeekApi.ChatModel.DEEPSEEK_V4_PRO, ModelGateway.resolveModel("pro"));
    }

    @Test
    void resolveModel_unknownOrNullFallsBackToDefault() {
        assertEquals(ModelGateway.DEFAULT_MODEL, ModelGateway.resolveModel(null));
        assertEquals(ModelGateway.DEFAULT_MODEL, ModelGateway.resolveModel("不存在的模型"));
    }

    @Test
    void parseCommand_extractsModelAliasAndMessage() {
        ParsedCommand parsed = ModelGateway.parseCommand("/model reasoner 帮我查一下订单 10002");
        assertEquals("reasoner", parsed.modelAlias());
        assertEquals("帮我查一下订单 10002", parsed.message());
    }

    @Test
    void parseCommand_withoutCommandKeepsMessage() {
        ParsedCommand parsed = ModelGateway.parseCommand("你好");
        assertNull(parsed.modelAlias());
        assertEquals("你好", parsed.message());
    }

    @Test
    void call_fallsBackToDefaultModelWhenPrimaryFails() {
        ModelGateway gateway = new ModelGateway(null) {
            @Override
            protected Mono<String> callWith(DeepSeekApi.ChatModel model, String message, String conversationId) {
                if (model == DeepSeekApi.ChatModel.DEEPSEEK_REASONER) {
                    return Mono.error(new RuntimeException("模拟主模型 401 失败"));
                }
                return Mono.just("降级后的回答");
            }
        };

        ModelGateway.GatewayResult result = gateway.call("你好", "c1", "reasoner").block();

        assertNotNull(result);
        assertEquals("deepseek-chat（降级）", result.modelUsed());
        assertEquals("降级后的回答", result.reply());
    }
}
