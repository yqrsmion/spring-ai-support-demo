package com.example.support;

import java.nio.file.Path;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiClientConfiguration {

    @Bean
    ChatMemory chatMemory(
            @Value("${app.memory.dir:./data/memory}") String memoryDir,
            @Value("${app.memory.max-messages:20}") int maxMessages) {
        // 本地 JSON 文件持久化：重启后同一 conversationId 的记忆仍在
        return new FileChatMemory(Path.of(memoryDir), maxMessages);
    }

    @Bean
    ChatClient supportChatClient(ChatModel chatModel, ChatMemory chatMemory, SupportTools tools) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        你是智能客服助手，负责订单查询、售后政策咨询和基础问题解答。
                        回答要求：简洁、友好、结论先行；需要查订单时调用 getOrderStatus，
                        需要查 FAQ 时调用 searchFaq，需要运行指标时调用 getSupportMetrics；
                        不要编造订单状态或政策内容。
                        """)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(tools)
                .build();
    }
}
