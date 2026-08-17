package com.example.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 基于本地 JSON 文件的会话记忆：每个 conversationId 一个文件，进程重启后记忆仍在。
 * 零外部依赖（仅用 Jackson，Spring 自带）。工具消息不持久化（保持简单）。
 */
public class FileChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(FileChatMemory.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Path dir;
    private final int maxMessages;

    public FileChatMemory(Path dir, int maxMessages) {
        this.dir = dir;
        this.maxMessages = maxMessages;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建记忆目录: " + dir, e);
        }
    }

    @Override
    public synchronized void add(String conversationId, List<Message> messages) {
        if (conversationId == null || messages == null || messages.isEmpty()) {
            return;
        }
        List<StoredMessage> stored = readAll(conversationId);
        for (Message message : messages) {
            if (message.getMessageType() == MessageType.TOOL) {
                continue; // 工具消息不持久化
            }
            stored.add(new StoredMessage(message.getMessageType().name(), message.getText()));
        }
        if (stored.size() > maxMessages) {
            stored = new ArrayList<>(stored.subList(stored.size() - maxMessages, stored.size()));
        }
        writeAll(conversationId, stored);
    }

    @Override
    public synchronized List<Message> get(String conversationId) {
        return readAll(conversationId).stream()
                .map(FileChatMemory::toMessage)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public synchronized void clear(String conversationId) {
        try {
            Files.deleteIfExists(fileOf(conversationId));
        } catch (IOException e) {
            log.warn("清除记忆失败: {}", e.getMessage());
        }
    }

    private Path fileOf(String conversationId) {
        String safe = conversationId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return dir.resolve(safe + ".json");
    }

    private List<StoredMessage> readAll(String conversationId) {
        Path file = fileOf(conversationId);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            return OBJECT_MAPPER.readValue(file.toFile(), new TypeReference<List<StoredMessage>>() {
            });
        } catch (Exception e) {
            log.warn("读取记忆失败，按空会话处理: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void writeAll(String conversationId, List<StoredMessage> stored) {
        Path file = fileOf(conversationId);
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            OBJECT_MAPPER.writeValue(tmp.toFile(), stored);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new IllegalStateException("写入记忆失败: " + file, e);
        }
    }

    private static Message toMessage(StoredMessage stored) {
        return switch (stored.type()) {
            case "USER" -> new UserMessage(stored.text());
            case "ASSISTANT" -> new AssistantMessage(stored.text());
            case "SYSTEM" -> new SystemMessage(stored.text());
            default -> null;
        };
    }

    private record StoredMessage(String type, String text) {
    }
}
