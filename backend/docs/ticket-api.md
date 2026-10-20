# 基础工单管理与最小登录

沿用 `Controller → Service → Mapper`。Service 处理业务、数据权限和事务，Mapper 执行 SQL。
本阶段没有注册、客服分配、状态流转、回复消息、AI 调用和前端。

## 启动准备

1. 沿用前一阶段 MySQL 8 的数据库和六张表，不重新建表或清空数据。
2. 手工执行 [V2__ticket_update_operation.sql](sql/V2__ticket_update_operation.sql)，只给历史表 CHECK 增加 `UPDATE` 类型。MySQL DDL 会隐式提交，请在没有未完成事务的独立控制台执行。
3. 按 [README](../README.md) 配置环境变量或外置 `backend/config/application-local.yml` 数据库连接。
4. 生成并设置 `JWT_SECRET`，从 backend 目录启动：

```powershell
cd E:\smart-ticket-system\backend
$jwtKeyBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $jwtRng.GetBytes($jwtKeyBytes) } finally { $jwtRng.Dispose() }
$env:JWT_SECRET = [Convert]::ToBase64String($jwtKeyBytes)
mvn spring-boot:run '-Dspring-boot.run.profiles=local'
```

JWT 使用 HS256，有效期 3600 秒。运行密钥无默认值，至少 32 字符；不要提交真实密钥。
也可以把本机随机生成的密钥配置在被 Git 忽略的 `config/application-local.yml` 的 `security.jwt.secret` 下。
重启后更换密钥会使旧 Token 失效；如需保留 Token，需要在本机保存并注入同一个密钥。

此前模拟数据的密码摘要未验证。若无法登录，可选择手工执行 [dev-reset-demo-password.sql](sql/dev-reset-demo-password.sql)，
将 `alice`、`agent01`、`admin` 三个模拟账号密码设为 `DemoPass123!`。此摘要已经过 BCrypt 校验。
脚本仅用于本地测试，不会自动执行，不创建账号或更改角色。

## 权限和数据规则

| 操作 | USER | AGENT | ADMIN |
| --- | --- | --- | --- |
| 创建 | 可以，创建人为当前用户 | 无 USER 角色时不允许 | 无 USER 角色时不允许 |
| 详情和通用分页 | 自己创建的 | 分配给自己的 | 全部 |
| 我的工单 | 自己创建的 | 无 USER 角色时不允许 | 无 USER 角色时不允许 |
| 修改标题、描述 | 自己创建、NEW 且未分配时 | 无 USER 角色时不允许 | 无 USER 角色时不允许 |

- 多角色账号的通用查询范围取并集；`/my` 额外限制当前创建人。
- Service 再次校验身份和角色，身份只来自 Spring Security 的认证信息。JWT 每次使用时重新读取账号状态和角色。
- 创建仅接收标题、描述。默认 `MEDIUM / NEW / PENDING`、无分类、无处理人、version=0。
- 工单编号为 32 位不带横线的 UUID，有数据库唯一索引。
- 修改仅接收标题、描述、version。修改内容后清空旧分类、理由和 AI 错误，重置为 PENDING，不发起 AI 调用。
- 未定义 JSON 字段返回 400；不能通过 creatorId、assigneeId、status 等注入身份或修改保护字段。
- 查询参数不开放创建人、处理人的筛选；附带 userId、creatorId 不会改变数据权限。
- 数据权限过滤、COUNT、LIMIT 均在数据库执行，按 `created_at DESC, id DESC` 稳定排序。
- 不存在和不可见的工单统一返回 404，避免泄露他人工单是否存在。
- 修改 SQL 同时校验创建人、版本、NEW 状态和处理人为空，防止覆盖更新及检查后状态变化。
- 创建/修改与对应历史记录同事务，历史记录失败则整个操作回滚。

## 响应结构

所有响应包含 `code`、`message`、`data`、`timestamp`，HTTP 状态码保留真实语义。
下文 ID 和时间是示例，实际以服务响应为准。工单时间是数据库 DATETIME 的本地时间，无时区后缀；外层 timestamp 为 UTC。

工单对象完整结构如下，创建、详情和修改接口均返回此结构：

```json
{
  "id": 2004,
  "ticketNo": "e084b6b34d2f4e99aba7268892e6b3e1",
  "title": "支付后订单未更新",
  "description": "已经扣款，但页面仍显示待支付。",
  "category": null,
  "categoryReason": null,
  "aiStatus": "PENDING",
  "priority": "MEDIUM",
  "status": "NEW",
  "creatorId": 1001,
  "assigneeId": null,
  "createdAt": "2026-10-20T20:00:00",
  "updatedAt": "2026-10-20T20:00:00",
  "version": 0
}
```

## 1. 登录

`POST /api/auth/login`，无需 Token，`Content-Type: application/json`。

请求：

```json
{ "username": "alice", "password": "DemoPass123!" }
```

响应 HTTP 200：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "accessToken": "实际签发的JWT",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": { "id": 1001, "username": "alice", "roles": ["USER"] }
  },
  "timestamp": "2026-10-20T12:00:00Z"
}
```

密码错误、用户禁用、无有效角色均返回 401、INVALID_CREDENTIALS，不返回密码摘要。
以下所有工单接口均需 `Authorization: Bearer <accessToken>`。

## 2. 创建

`POST /api/tickets`，JSON 请求：

```json
{ "title": "支付后订单未更新", "description": "已经扣款，但页面仍显示待支付。" }
```

响应 HTTP 201：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "id": 2004,
    "ticketNo": "e084b6b34d2f4e99aba7268892e6b3e1",
    "title": "支付后订单未更新",
    "description": "已经扣款，但页面仍显示待支付。",
    "category": null,
    "categoryReason": null,
    "aiStatus": "PENDING",
    "priority": "MEDIUM",
    "status": "NEW",
    "creatorId": 1001,
    "assigneeId": null,
    "createdAt": "2026-10-20T20:00:00",
    "updatedAt": "2026-10-20T20:00:00",
    "version": 0
  },
  "timestamp": "2026-10-20T12:00:00Z"
}
```

标题、描述不能空白，最长分别 200、20000 字符。

## 3. 按 ID 查询

请求 `GET /api/tickets/2004`，无请求体。

响应 HTTP 200：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "id": 2004,
    "ticketNo": "e084b6b34d2f4e99aba7268892e6b3e1",
    "title": "支付后订单未更新",
    "description": "已经扣款，但页面仍显示待支付。",
    "category": null,
    "categoryReason": null,
    "aiStatus": "PENDING",
    "priority": "MEDIUM",
    "status": "NEW",
    "creatorId": 1001,
    "assigneeId": null,
    "createdAt": "2026-10-20T20:00:00",
    "updatedAt": "2026-10-20T20:00:00",
    "version": 0
  },
  "timestamp": "2026-10-20T12:00:01Z"
}
```

ID 必须是正整数；不可见或不存在返回 HTTP 404：

```json
{
  "code": "TICKET_NOT_FOUND",
  "message": "工单不存在或无权访问",
  "data": null,
  "timestamp": "2026-10-20T12:00:01Z"
}
```

## 4. 分页查询

请求 `GET /api/tickets?page=1&size=20&status=NEW&aiStatus=PENDING`。

| 参数 | 规则 |
| --- | --- |
| page | 默认 1，范围 1～1000000 |
| size | 默认 20，范围 1～100 |
| keyword | 可选，最长 100 字符，标题 LIKE 搜索或工单编号精确匹配 |
| category | 可选：ORDER、ACCOUNT、TECHNICAL、OTHER |
| aiStatus | 可选：PENDING、SUCCESS、FAILED |
| priority | 可选：LOW、MEDIUM、HIGH、URGENT |
| status | 可选：NEW、ASSIGNED、IN_PROGRESS、WAITING_USER、RESOLVED、CLOSED |

响应 HTTP 200，以下为空结果示例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": { "page": 1, "size": 20, "total": 0, "pages": 0, "records": [] },
  "timestamp": "2026-10-20T12:00:02Z"
}
```

有数据时 records 包含完整工单对象数组，total 只统计可见且满足筛选条件的数据。
页码超过最后一页返回空 records，保留实际 total/pages，不跳回第一页。

## 5. 修改

`PUT /api/tickets/2004`，JSON 请求：

```json
{ "title": "支付异常补充", "description": "已经等待十分钟，仍显示待支付。", "version": 0 }
```

HTTP 200 响应仍为完整工单对象，title/description 为本次提交内容，version=1，updatedAt 更新。
例如：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "id": 2004,
    "ticketNo": "e084b6b34d2f4e99aba7268892e6b3e1",
    "title": "支付异常补充",
    "description": "已经等待十分钟，仍显示待支付。",
    "category": null,
    "categoryReason": null,
    "aiStatus": "PENDING",
    "priority": "MEDIUM",
    "status": "NEW",
    "creatorId": 1001,
    "assigneeId": null,
    "createdAt": "2026-10-20T20:00:00",
    "updatedAt": "2026-10-20T20:01:00",
    "version": 1
  },
  "timestamp": "2026-10-20T12:01:00Z"
}
```

version 必填、不能为负，必须来自最近一次查询。必须同时提供标题和描述。
版本冲突返回 409；已分配或非 NEW 返回 409。本阶段没有状态、优先级和分配修改接口。

## 6. 我的工单

请求 `GET /api/tickets/my?page=1&size=10&status=NEW`，仅 USER 角色可调用。
参数与通用分页相同，始终限制 creatorId 为当前认证用户。

HTTP 200 响应示例：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": { "page": 1, "size": 10, "total": 0, "pages": 0, "records": [] },
  "timestamp": "2026-10-20T12:00:03Z"
}
```

## 错误码

| HTTP | code | 情况 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | 参数、JSON、未知 JSON 字段或枚举不合法 |
| 401 | INVALID_CREDENTIALS | 登录失败 |
| 401 | UNAUTHORIZED | 未登录、无效/过期 Token、账号已禁用或无有效角色 |
| 403 | FORBIDDEN | 角色无权执行操作 |
| 404 | TICKET_NOT_FOUND | 工单不存在或不可见 |
| 409 | TICKET_NOT_EDITABLE | 已分配或非 NEW |
| 409 | TICKET_CONFLICT | 版本冲突或更新时可编辑状态已改变 |
| 503 | DATABASE_UNAVAILABLE | 数据库访问失败，包含历史写入失败 |

无 Token 只开放登录、既有健康检查及错误转发；受保护路径优先返回 401。
合法认证后未知路径返回统一 404。错误 data 为 null，仍有 code、message 和 timestamp。

## 手工验证

另开 PowerShell。示例密码要求先执行可选模拟密码重置脚本，否则按实际密码修改。

```powershell
$baseUrl = 'http://localhost:8080'
$body = @{ username = 'alice'; password = 'DemoPass123!' } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/auth/login" `
    -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
$headers = @{ Authorization = "Bearer $($login.data.accessToken)" }

$body = @{ title = '支付异常'; description = '已扣款但订单未更新' } | ConvertTo-Json
$created = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/tickets" -Headers $headers `
    -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
$ticketId = $created.data.id

Invoke-RestMethod -Uri "$baseUrl/api/tickets/$ticketId" -Headers $headers
Invoke-RestMethod -Uri "$baseUrl/api/tickets?page=1&size=10" -Headers $headers
Invoke-RestMethod -Uri "$baseUrl/api/tickets/my?page=1&size=10" -Headers $headers

$body = @{ title = '支付异常补充'; description = '已等待十分钟'; version = $created.data.version } | ConvertTo-Json
Invoke-RestMethod -Method Put -Uri "$baseUrl/api/tickets/$ticketId" -Headers $headers `
    -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
```

预期更新后 version=1，数据库有 CREATE、UPDATE 历史。重复旧 version 应返回 409。
使用其他用户或未分配客服的 Token 读取该工单应返回 404。

## 自动化测试

```powershell
cd E:\smart-ticket-system\backend
mvn clean verify
```

测试只使用隔离的 H2 MySQL 模式和测试签名密钥，不改你的 MySQL 数据。
覆盖登录、JWT、账号禁用、角色实时生效、服务端身份、字段注入、数据权限、多角色范围、真实分页 LIMIT、
筛选边界、并发更新、事务回滚、分类重置和原有健康检查。
H2 不替代真实 MySQL 验收；需手工执行迁移并运行上述完整主流程。

## 文件变更清单

新增业务文件（相对于 src/main/java/com/example/ticket）：

| 目录 | 文件 |
| --- | --- |
| auth/controller | AuthController.java |
| auth/dto | LoginRequest.java |
| auth/mapper | UserAccountMapper.java |
| auth/model | UserAccount.java |
| auth/service | AuthService.java |
| auth/vo | LoginResponse.java |
| security | UserRole.java、AuthenticatedUser.java、CurrentUser.java、JwtProperties.java、AccountIdentityService.java、SecurityResponseWriter.java、DatabaseJwtAuthenticationConverter.java |
| config | SecurityConfig.java |
| common/exception | ApiException.java |
| common/response | PageResponse.java |
| ticket/controller | TicketController.java |
| ticket/dto | CreateTicketRequest.java、UpdateTicketRequest.java、TicketQuery.java |
| ticket/entity | Ticket.java |
| ticket/enums | TicketCategory.java、ClassificationStatus.java、TicketPriority.java、TicketStatus.java |
| ticket/mapper | TicketMapper.java、TicketHistoryMapper.java |
| ticket/service | TicketService.java |
| ticket/vo | TicketView.java |

其他新增：本文件、docs/sql 下两份 SQL、测试 TicketApiIntegrationTest.java、SqlCapture.java、测试 db/schema.sql 和 db/data.sql。
修改：pom.xml、MybatisPlusConfig.java、GlobalExceptionHandler.java、application.yml、application-test.yml、
HealthIntegrationTest.java、根目录与后端 README。没有重构已完成模块，没有修改实际本地凭据文件。

## 已知限制

- 没有注册、账号管理、修改密码、JWT 刷新和服务端主动登出；密码必须为 BCrypt 摘要。
- 没有 AI、分配、状态流转、消息、附件接口；新工单保持 NEW/PENDING，留给后续阶段处理。
- 没有管理员代用户修改内容的能力。
- 当前无请求幂等，重复提交会创建不同工单；标题搜索使用 LIKE。
- MySQL 迁移和真实数据库主流程需要额外手工验收，应用不会自动运行 SQL 脚本。
- 功能在独立分支开发并提交，真实数据库验收前不自动合并 main。

## 本次验证记录

- `mvn clean verify`：31 项测试通过，0 失败，0 错误，成功生成可执行 JAR。
- 使用打包 JAR 和本地配置启动：存活检查 200、真实 MySQL 连接检查 200、无 Token 查询工单 401。
- 只读确认真实数据库为 MySQL 8.0.44，所需五张表可以读取。
- 当前真实数据库的历史操作 CHECK 尚不包含 UPDATE，需要手工执行上方 V2 脚本；未执行时修改工单会失败并整体回滚。
- 未在真实数据库执行迁移、重置密码或写入测试工单，未宣称真实 MySQL 的业务主流程已验收。
