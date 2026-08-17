package com.example.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

class FileChatMemoryTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsAcrossInstances() {
        FileChatMemory first = new FileChatMemory(tempDir, 20);
        first.add("c1", List.of(new UserMessage("我叫yqr"), new AssistantMessage("记住了")));

        // 模拟服务重启：用同一个目录新建实例
        FileChatMemory second = new FileChatMemory(tempDir, 20);
        List<Message> history = second.get("c1");

        assertEquals(2, history.size());
        assertEquals("我叫yqr", history.get(0).getText());
        assertEquals("记住了", history.get(1).getText());
    }

    @Test
    void clearRemovesHistory() {
        FileChatMemory memory = new FileChatMemory(tempDir, 20);
        memory.add("c1", List.of(new UserMessage("你好")));
        memory.clear("c1");
        assertTrue(memory.get("c1").isEmpty());
    }

    @Test
    void trimsToMaxMessages() {
        FileChatMemory memory = new FileChatMemory(tempDir, 2);
        memory.add("c1", List.of(new UserMessage("1"), new UserMessage("2"), new UserMessage("3")));
        List<Message> history = memory.get("c1");
        assertEquals(2, history.size());
        assertEquals("2", history.get(0).getText());
        assertEquals("3", history.get(1).getText());
    }
}
