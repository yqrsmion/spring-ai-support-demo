package com.example.support;

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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient supportChatClient) {
        this.chatClient = supportChatClient;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @RequestParam String message,
            @RequestParam(defaultValue = "demo-session") String conversationId) {
        if (!StringUtils.hasText(message)) {
            return Flux.just(ServerSentEvent.builder("message 不能为空").event("error").build());
        }

        // 对比演示：一边实时推送 chunk，一边累积完整内容
        StringBuilder full = new StringBuilder();
        return chatClient.prompt()
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(message)
                .stream()
                .content()
                .map(content -> {
                    full.append(content);
                    return ServerSentEvent.builder(content).event("chunk").build();
                })
                // 流结束后，把累积的完整文本一次性吐出来
                .concatWith(Mono.fromCallable(() ->
                        ServerSentEvent.builder(full.toString()).event("full").build()))
                .concatWithValues(ServerSentEvent.builder("[DONE]").event("done").build());
    }

    /**
     * 非流式接口：等模型完整回答后一次返回，适合查看最终结果（不展示中间分块）。
     * 同样携带 conversationId 会话记忆。
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, String>> chat(
            @RequestParam String message,
            @RequestParam(defaultValue = "demo-session") String conversationId) {
        if (!StringUtils.hasText(message)) {
            return Mono.just(Map.of("error", "message 不能为空"));
        }
        return Mono.fromCallable(() ->
                        chatClient.prompt()
                                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                                .user(message)
                                .call()
                                .content())
                .subscribeOn(Schedulers.boundedElastic())
                .map(reply -> Map.of("conversationId", conversationId, "reply", reply));
    }
}
