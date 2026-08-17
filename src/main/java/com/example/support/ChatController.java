package com.example.support;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final Duration MODEL_TIMEOUT = Duration.ofSeconds(90);

    private final ChatClient chatClient;
    private final TokenBucketRateLimiter rateLimiter;
    private final ModelGateway modelGateway;
    private final SimpleRetriever retriever;

    public ChatController(ChatClient supportChatClient, TokenBucketRateLimiter rateLimiter,
                          ModelGateway modelGateway, SimpleRetriever retriever) {
        this.chatClient = supportChatClient;
        this.rateLimiter = rateLimiter;
        this.modelGateway = modelGateway;
        this.retriever = retriever;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @RequestParam String message,
            @RequestParam(defaultValue = "demo-session") String conversationId,
            @RequestParam(required = false) String model) {
        if (!StringUtils.hasText(message)) {
            return Flux.just(ServerSentEvent.builder("message 不能为空").event("error").build());
        }
        ModelGateway.ParsedCommand command = ModelGateway.parseCommand(message);
        String realMessage = command.message();
        String modelAlias = model != null ? model : command.modelAlias();
        if (!StringUtils.hasText(realMessage)) {
            return Flux.just(
                    ServerSentEvent.builder("命令格式：/model 模型别名 问题内容").event("error").build(),
                    ServerSentEvent.builder("[DONE]").event("done").build());
        }
        if (!rateLimiter.tryAcquire()) {
            log.warn("rate limited: /api/chat/stream");
            return Flux.just(
                    ServerSentEvent.builder("请求过于频繁，请稍后再试").event("error").build(),
                    ServerSentEvent.builder("[DONE]").event("done").build());
        }

        // 简化版 RAG：检索知识库命中则注入上下文
        List<String> hits = retriever.search(realMessage);
        String prompt = hits.isEmpty()
                ? realMessage
                : retriever.toContext(hits) + "\n\n用户问题：" + realMessage;
        int hitCount = hits.size();

        // 对比演示：一边实时推送 chunk，一边累积完整内容
        StringBuilder full = new StringBuilder();
        return Flux.concat(
                        Mono.just(ServerSentEvent.builder(modelGateway.modelName(modelAlias)).event("model").build()),
                        hitCount > 0
                                ? Mono.just(ServerSentEvent.builder("命中" + hitCount + "个知识块").event("rag").build())
                                : Flux.empty(),
                        modelGateway.stream(prompt, conversationId, modelAlias)
                                .map(content -> {
                                    full.append(content);
                                    return ServerSentEvent.builder(content).event("chunk").build();
                                }))
                // 流结束后，把累积的完整文本一次性吐出来
                .concatWith(Mono.fromCallable(() ->
                        ServerSentEvent.builder(full.toString()).event("full").build()))
                .concatWithValues(ServerSentEvent.builder("[DONE]").event("done").build())
                // 企业级硬化：超时 + 异常降级，避免把堆栈直接抛给客户端
                .timeout(MODEL_TIMEOUT)
                .onErrorResume(error -> {
                    log.warn("stream error: {}", error.getMessage());
                    return Flux.just(
                            ServerSentEvent.builder("服务繁忙，请稍后再试（" + friendlyMessage(error) + "）")
                                    .event("error").build(),
                            ServerSentEvent.builder("[DONE]").event("done").build());
                });
    }

    /**
     * 非流式接口：等模型完整回答后一次返回，适合查看最终结果（不展示中间分块）。
     * 同样携带 conversationId 会话记忆。
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, String>> chat(
            @RequestParam String message,
            @RequestParam(defaultValue = "demo-session") String conversationId,
            @RequestParam(required = false) String model) {
        if (!StringUtils.hasText(message)) {
            return Mono.just(Map.of("error", "message 不能为空"));
        }
        ModelGateway.ParsedCommand command = ModelGateway.parseCommand(message);
        String realMessage = command.message();
        String modelAlias = model != null ? model : command.modelAlias();
        if (!StringUtils.hasText(realMessage)) {
            return Mono.just(Map.of("conversationId", conversationId, "error", "命令格式：/model 模型别名 问题内容"));
        }
        if (!rateLimiter.tryAcquire()) {
            log.warn("rate limited: /api/chat");
            return Mono.just(Map.of("conversationId", conversationId, "error", "请求过于频繁，请稍后再试"));
        }
        // 简化版 RAG：检索知识库命中则注入上下文
        List<String> hits = retriever.search(realMessage);
        String prompt = hits.isEmpty()
                ? realMessage
                : retriever.toContext(hits) + "\n\n用户问题：" + realMessage;

        return modelGateway.call(prompt, conversationId, modelAlias)
                .timeout(MODEL_TIMEOUT)
                .map(result -> Map.of(
                        "conversationId", conversationId,
                        "model", result.modelUsed(),
                        "rag", hits.isEmpty() ? "off" : "命中" + hits.size() + "个知识块",
                        "reply", result.reply()))
                .onErrorResume(error -> {
                    log.warn("chat error: {}", error.getMessage());
                    return Mono.just(Map.of(
                            "conversationId", conversationId,
                            "error", "服务繁忙，请稍后再试",
                            "detail", friendlyMessage(error)));
                });
    }

    private static String friendlyMessage(Throwable error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        if (message.contains("401") || message.contains("Unauthorized")) {
            return "模型鉴权失败，请检查 DEEPSEEK_API_KEY";
        }
        if (message.contains("429") || message.contains("Too Many Requests")) {
            return "模型限流，请稍后再试";
        }
        if (message.contains("timeout") || message.contains("Timeout")) {
            return "模型响应超时";
        }
        return "模型调用异常";
    }
}
