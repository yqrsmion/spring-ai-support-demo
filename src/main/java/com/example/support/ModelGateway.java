package com.example.support;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 模型网关：模型注册表（别名 → DeepSeek 模型）、按请求路由、失败自动降级到默认模型。
 * 所有真实调用走同一个 ChatClient，通过 per-prompt options 切换模型。
 */
@Component
public class ModelGateway {

    private static final Logger log = LoggerFactory.getLogger(ModelGateway.class);

    public static final DeepSeekApi.ChatModel DEFAULT_MODEL = DeepSeekApi.ChatModel.DEEPSEEK_CHAT;

    private final ChatClient chatClient;

    public ModelGateway(ChatClient supportChatClient) {
        this.chatClient = supportChatClient;
    }

    public record GatewayResult(String reply, String modelUsed) {
    }

    public record ParsedCommand(String modelAlias, String message) {
    }

    /** 解析 "/model xxx 消息内容" 形式的命令；没有命令则原样返回。 */
    public static ParsedCommand parseCommand(String message) {
        if (message != null) {
            String trimmed = message.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("/model")) {
                String rest = trimmed.substring("/model".length()).trim();
                int idx = rest.indexOf(' ');
                if (idx > 0) {
                    return new ParsedCommand(rest.substring(0, idx).trim(), rest.substring(idx + 1).trim());
                }
                return new ParsedCommand(rest.trim(), "");
            }
        }
        return new ParsedCommand(null, message);
    }

    /** 模型别名/名称 → DeepSeek 模型枚举；未知一律回落到默认模型。 */
    public static DeepSeekApi.ChatModel resolveModel(String alias) {
        if (alias == null) {
            return DEFAULT_MODEL;
        }
        String a = alias.trim().toLowerCase(Locale.ROOT);
        return switch (a) {
            case "chat", "deepseek-chat" -> DeepSeekApi.ChatModel.DEEPSEEK_CHAT;
            case "reasoner", "deepseek-reasoner" -> DeepSeekApi.ChatModel.DEEPSEEK_REASONER;
            case "flash", "v4-flash", "deepseek-v4-flash" -> DeepSeekApi.ChatModel.DEEPSEEK_V4_FLASH;
            case "pro", "v4-pro", "deepseek-v4-pro" -> DeepSeekApi.ChatModel.DEEPSEEK_V4_PRO;
            default -> DEFAULT_MODEL;
        };
    }

    public String modelName(String alias) {
        return resolveModel(alias).getValue();
    }

    public Mono<GatewayResult> call(String message, String conversationId, String modelAlias) {
        DeepSeekApi.ChatModel primary = resolveModel(modelAlias);
        return callWith(primary, message, conversationId)
                .map(reply -> new GatewayResult(reply, primary.getValue()))
                .onErrorResume(error -> {
                    log.warn("模型 {} 调用失败，降级到 {}：{}", primary, DEFAULT_MODEL, error.getMessage());
                    return callWith(DEFAULT_MODEL, message, conversationId)
                            .map(reply -> new GatewayResult(reply, DEFAULT_MODEL.getValue() + "（降级）"));
                });
    }

    public Flux<String> stream(String message, String conversationId, String modelAlias) {
        DeepSeekApi.ChatModel primary = resolveModel(modelAlias);
        return streamWith(primary, message, conversationId)
                .onErrorResume(error -> {
                    log.warn("模型 {} 流式调用失败，降级到 {}：{}", primary, DEFAULT_MODEL, error.getMessage());
                    return streamWith(DEFAULT_MODEL, message, conversationId);
                });
    }

    protected Mono<String> callWith(DeepSeekApi.ChatModel model, String message, String conversationId) {
        return Mono.fromCallable(() ->
                        chatClient.prompt()
                                .options(DeepSeekChatOptions.builder().model(model))
                                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                                .user(message)
                                .call()
                                .content())
                .subscribeOn(Schedulers.boundedElastic());
    }

    protected Flux<String> streamWith(DeepSeekApi.ChatModel model, String message, String conversationId) {
        return chatClient.prompt()
                .options(DeepSeekChatOptions.builder().model(model))
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(message)
                .stream()
                .content();
    }
}
