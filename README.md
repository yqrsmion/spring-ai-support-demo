# Spring AI 智能客服助手演示工程

一个不依赖数据库和部署环境的本地 Spring Boot 工程，用"智能客服/订单助手"场景演示：

- `ChatClient` 的模型调用入口；
- `MessageChatMemoryAdvisor` + `conversationId` 的会话隔离；
- `@Tool` 注册本地工具（查 FAQ、查订单、查客服指标）以及框架控制的 Tool Calling Loop；
- WebFlux + SSE 的流式输出（流式 chunk + 末尾 full 完整内容对比）；
- 模型重试配置。

## 运行前准备

1. 安装 JDK 17 与 Maven。
2. 在当前 PowerShell 会话设置 API Key（不要写入 Git）：

```powershell
$env:DEEPSEEK_API_KEY = "你的 DeepSeek API Key"
```

3. 启动：

```powershell
mvn spring-boot:run
```

4. 用 IDEA 打开工程根目录的 `requests.http` 逐条运行，或用 curl 测试：

```powershell
curl.exe -N "http://localhost:8080/api/chat/stream?conversationId=support-1&message=帮我查一下订单%2010002"
```

## 调用链

`HTTP 请求 -> ChatController -> ChatClient -> Advisor 链（会话记忆） -> 模型 -> Tool Calling Loop（按需） -> SSE Chunk -> full 完整内容`

## 可演示的要点

- FAQ 查询：问"退换货政策是什么？"触发 `searchFaq`；
- 订单查询：问"订单 10002 什么状态？"触发 `getOrderStatus`（模拟数据）；
- 客服指标：问"满意度怎么样？"触发 `getSupportMetrics`（模拟数据）；
- 会话记忆：同一 `conversationId` 连续对话会记住上下文。

## 第一版的边界

- 订单与 FAQ 是硬编码模拟数据，目的是聚焦 Tool Calling；后续可替换为真实服务/RAG。
- 记忆是内存实现，重启即丢失；生产上可换成 JDBC、Redis 等仓储。
- 当前未处理客户端断连、全局限流、鉴权和完整链路观测；这些是下一步的工程化练习重点。
