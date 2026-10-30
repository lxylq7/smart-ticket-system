# AI 工单分类

本阶段只使用 Spring AI 的 ChatClient 调用一个聊天模型，根据工单标题和问题描述返回分类。
没有 RAG、向量数据库、Agent、工具调用或工作流编排。模型不能决定创建人、处理人、权限或工单状态。

## 配置

默认 `AI_ENABLED=false`，因此没有配置密钥时应用也能启动，创建工单不会发起外部调用。
配置来自环境变量或本地未提交的 `config/application-local.yml`，代码仓库不包含真实密钥。

PowerShell 示例（从 `backend` 目录启动）：

```powershell
$env:AI_ENABLED = 'true'
$env:AI_CHAT_MODEL = 'openai'
$env:AI_API_KEY = '替换为实际密钥'
$env:AI_BASE_URL = 'https://api.openai.com'       # OpenAI 兼容供应商可替换
$env:AI_MODEL = 'gpt-4o-mini'
$env:AI_TIMEOUT = '15s'
$env:AI_TEMPERATURE = '0.0'
$env:AI_MAX_COMPLETION_TOKENS = '300'

mvn spring-boot:run '-Dspring-boot.run.profiles=local'
```

也可以写入被 Git 忽略的 `config/application-local.yml`：

```yaml
spring:
  ai:
    model:
      chat: openai
    openai:
      api-key: ${AI_API_KEY}
      base-url: ${AI_BASE_URL:https://api.openai.com}
      chat:
        options:
          model: ${AI_MODEL:gpt-4o-mini}
          temperature: 0.0
          max-completion-tokens: 300
app:
  ai:
    enabled: true
    timeout: 15s
```

`AI_API_KEY` 是唯一的敏感配置，不能提交到 Git、写进 Java、写进 Postman 集合或日志。
`spring.ai.openai.api-key` 的默认值 `disabled` 只是关闭模式下使用的占位值，
只有 `app.ai.enabled=true` 且 `spring.ai.model.chat=openai` 时才允许实际分类调用。
供应商必须提供 OpenAI Chat Completions 兼容接口；`base-url` 用于切换兼容服务。

真实供应商联调前先确认：模型供应商支持 Chat Completions 和结构化 JSON 输出，
API 地址可以从当前机器访问，密钥只通过环境变量或被 Git 忽略的本地配置提供。
本仓库不包含真实密钥，因此自动测试只验证分类校验、失败回退、超时和重试逻辑；
配置真实供应商后，再按“成功结果验证”执行一次人工 HTTP 验证。

`app.ai.timeout` 是一次分类的应用层截止时间，默认 15 秒。超时、连接失败、供应商错误、空响应、
结构化输出解析失败和不合法字段都会进入失败处理，不会回滚已创建的工单。

## 分类结构和校验

模型被要求只返回以下结构：

```json
{
  "category": "ORDER",
  "reason": "标题和描述都在说明订单支付结果异常"
}
```

`category` 只能是 `ORDER`、`ACCOUNT`、`TECHNICAL`、`OTHER`；`reason` 必须非空且不超过 500 个字符。
Spring AI 的 `entity(TicketClassificationResult.class)`负责结构化输出转换，应用随后再次执行枚举、非空和长度校验。
任何无效结果都标记为 `FAILED`，并保存经过截断的错误类型和原因；不会把模型原文直接写入工单权限或状态字段。

## 创建、重试和并发

`POST /api/tickets` 先在本地事务中创建工单和 CREATE 历史，返回时分类通常仍是 `PENDING`。
事务提交后才异步调用模型；调用失败只把 `aiStatus` 置为 `FAILED`，不影响创建成功。

失败工单可由有权访问该工单的创建人、当前客服或管理员调用：

```http
POST /api/tickets/{id}/classification/retry
Authorization: Bearer <accessToken>
```

请求无 body。只有 `aiStatus=FAILED` 且没有已有分类的工单可以重试；成功将状态重置为 `PENDING` 并在事务提交后再次调用模型。
重试会增加工单 version，响应返回最新工单对象。AI 未启用时仍可以执行重置，便于稍后启用服务后处理。

模型调用前通过 `id + version + category IS NULL + aiStatus=PENDING` 原子认领任务，`aiAttempts` 增加一次。
一个工单的重复事件最多一个调用获胜。成功或失败保存时再次检查认领版本；用户修改标题/描述、状态流转或其他写操作后，旧模型结果不会覆盖新数据。
分类成功或失败的写入与 `AI_CLASSIFY` / `AI_CLASSIFY_FAILED` 操作历史在同一事务中提交。

## 防止覆盖人工分类

当前阶段没有人工分类 HTTP 接口，但数据库和服务已经按保护规则实现：

- 成功写入 SQL 必须满足 `category IS NULL`；任何已有分类都不会被模型覆盖。
- 重试接口发现已有分类时返回 `409 CLASSIFICATION_ALREADY_SET`，不重置状态、不发布模型任务。
- 如果管理员或后续人工分类模块先写入 category，正在运行的旧模型结果会因 `category IS NULL` 条件失败而丢弃。
- 模型结果还必须匹配认领时的 version；标题、描述、状态或人工分类发生变化时不会覆盖当前记录。

## 测试

不访问外部模型的单元测试：

```powershell
cd E:\smart-ticket-system\backend
mvn '-Dtest=TicketClassificationServiceTest' test
mvn clean verify
```

测试覆盖：成功结构化结果、缺失分类、模型异常、已有分类保护、乐观认领防重复、失败重试权限和事件发布。
测试 profile 设置 `spring.ai.model.chat=none`、`app.ai.enabled=false`，不会读取真实密钥或调用供应商。

### 模拟模型调用失败

最简单的方式是运行单元测试；`TicketClassificationServiceTest.modelExceptionBecomesFailedWithoutThrowing` 使用 mock 抛出供应商异常。
手工 HTTP 验证时，可以把 `AI_BASE_URL` 指向一个会返回 500/超时的 OpenAI 兼容测试服务，或暂时将模型名配置为不存在的模型：

```powershell
$env:AI_ENABLED = 'true'
$env:AI_CHAT_MODEL = 'openai'
$env:AI_API_KEY = 'test-key'
$env:AI_BASE_URL = 'http://127.0.0.1:9'
mvn spring-boot:run '-Dspring-boot.run.profiles=local'
```

创建工单后查询详情，应看到 `aiStatus=FAILED`，工单仍存在；随后调用 retry，确认状态先回到 `PENDING`，再次失败后回到 `FAILED`。
不要在生产环境使用无效地址反复重试。

### 成功结果验证

配置有效模型并创建工单：

1. 登录 USER，调用 `POST /api/tickets`，记录 id。
2. 轮询 `GET /api/tickets/{id}`，等待 `aiStatus=SUCCESS`。
3. 确认 `category` 是四个枚举之一，`categoryReason` 非空；创建人、处理人和 status 没有因模型改变。
4. 查询数据库确认分类和尝试次数：

```sql
SELECT id, category, category_reason, ai_status, ai_attempts, ai_error_message, version
FROM ticket WHERE id = <工单ID>;

SELECT operation_type, remark, created_at
FROM ticket_operation_history WHERE ticket_id = <工单ID>
ORDER BY id;
```

成功应有 `AI_CLASSIFY` 历史；失败应有 `AI_CLASSIFY_FAILED` 历史。失败原因可能包含供应商异常类型，应用不会返回 API Key 或 Token。

## 文件职责

| 文件 | 作用 |
| --- | --- |
| `ai/SpringAiTicketClassifier.java` | Spring AI ChatClient、系统提示词和结构化输出 |
| `ai/TicketClassificationResult.java` | 模型输出 DTO 及分类/理由校验 |
| `ai/TicketClassificationService.java` | 异步调用、超时、认领、失败重试和访问权限 |
| `ai/TicketClassificationPersistence.java` | 分类结果与 AI 操作历史的事务写入 |
| `ai/TicketClassificationEventListener.java` | 创建/重试提交后触发异步任务 |
| `config/AiConfiguration.java` | 默认关闭、Spring AI classifier 条件装配及线程池 |
| `ticket/mapper/TicketMapper.java` | 分类认领、成功、失败和重置的条件更新 |
| `ticket/mapper/TicketHistoryMapper.java` | AI 分类成功/失败历史 |
| `docs/ticket-ai.md` | 配置、验证、失败模拟和人工分类保护说明 |
| `src/test/java/com/example/ticket/ai/TicketClassificationServiceTest.java` | 分类单元测试 |

AI 只负责分类，不决定任何认证、权限、创建人、处理人或工单状态。
