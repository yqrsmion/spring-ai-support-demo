package com.example.support;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiClientConfiguration {

    @Bean
    ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(12)
                .build();
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
