# Spring AI 智能客服助手演示工程

一个不依赖数据库和部署环境的本地 Spring Boot 工程，用"智能客服/订单助手"场景完整演示
Spring AI 从基础链路到企业级组件的能力。

## 功能总览

- 流式对话：WebFlux + SSE（chunk 逐段推送 + 末尾 full 完整内容对比）；
- 非流式对话：`/api/chat` 一次返回完整 JSON；
- 工具调用：查 FAQ、查订单、查客服指标（`@Tool` + Tool Calling Loop）；
- 会话记忆：同一 `conversationId` 连续对话记住上下文，本地 JSON 文件持久化（重启不丢）；
- 模型网关：`chat / reasoner / flash / pro` 多模型路由与失败自动降级；
- 简化版 RAG：本地知识库检索并注入上下文（无向量库、零依赖）；
- 企业级硬化：令牌桶限流、超时与异常降级、traceId 链路日志、全局异常兜底；
- 内置网页聊天界面：`http://localhost:8080`；
- 单元测试：17 个用例，`mvn test` 全绿。

## 运行

1. 安装 JDK 17 与 Maven；
2. 设置 API Key（不要写入 Git）：

```powershell
$env:DEEPSEEK_API_KEY = "你的 DeepSeek API Key"
```

3. 启动：

```powershell
mvn spring-boot:run
```

4. 浏览器打开 `http://localhost:8080` 直接聊天，或运行工程根目录 `requests.http`
   （1~11 条请求覆盖全部功能）。

## 组件结构

| 组件 | 职责 |
|---|---|
| SupportApplication | Spring Boot 启动类 |
| ChatController | 对话接口：`/api/chat/stream`（SSE）与 `/api/chat`（JSON），含 RAG 注入 |
| AiClientConfiguration | ChatClient、会话记忆 Bean（FileChatMemory） |
| SupportTools | 本地工具：searchFaq / getOrderStatus / getSupportMetrics |
| ModelGateway | 模型注册表 + 路由 + 降级（`/model` 命令） |
| FileChatMemory | 本地 JSON 文件会话记忆 |
| SimpleRetriever | 简化版 RAG 检索器（文档切块 + 关键词打分） |
| TokenBucketRateLimiter | 令牌桶限流 |
| TraceIdFilter | 请求 traceId + 耗时日志（X-Trace-Id 响应头） |
| ApiExceptionHandler | 全局异常兜底 |

## 调用链

`HTTP → ChatController →（RAG 检索命中则注入上下文）→ ModelGateway（按模型路由）→ ChatClient → Advisor（会话记忆）→ 模型 → Tool Calling Loop（按需）→ SSE chunk → full 完整内容`

## 验证清单（requests.http）

1~3：基础对话与工具（FAQ / 订单 / 指标）；4~5：会话记忆；
6：非流式 JSON；7~8：模型切换；9~10：记忆持久化（先执行 9，重启服务后再执行 10）；
11：简化版 RAG。

## 边界

- 订单、指标为模拟数据，知识库为本地文档，聚焦链路与原理；
- 记忆为单机文件存储，生产可换 Redis / JDBC；
- 未做鉴权、分布式限流、真实向量库 RAG，可作为后续扩展方向。
