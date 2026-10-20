# 工单分配与状态流转

本阶段沿用现有 Controller、Service、Mapper、JWT 及统一异常结构。
没有增加消息、AI、优先级修改、用户管理或自动任务。

## 状态与权限

| 原状态 | 目标状态 | 操作 | ADMIN | 当前处理人且有 AGENT 角色 | USER |
| --- | --- | --- | --- | --- | --- |
| PENDING | ASSIGNED | 首次分配 | 允许 | 禁止 | 禁止 |
| ASSIGNED / PROCESSING / WAITING_USER | ASSIGNED | 转派到另一客服 | 允许 | 禁止 | 禁止 |
| ASSIGNED | PROCESSING | 开始处理 | 允许 | 允许 | 禁止 |
| PROCESSING | WAITING_USER | 等待反馈 | 允许 | 允许 | 禁止 |
| WAITING_USER | PROCESSING | 继续处理 | 允许 | 允许 | 禁止 |
| PROCESSING | RESOLVED | 标记解决 | 允许 | 允许 | 禁止 |
| RESOLVED | PROCESSING | 重新处理 | 允许 | 允许 | 禁止 |
| RESOLVED | CLOSED | 关闭 | 允许 | 禁止 | 禁止 |

表外转换全部禁止。CLOSED 为终态；不支持撤销分配、直接关闭未解决工单或跳过开始处理。
转派不能指向同一客服，RESOLVED 和 CLOSED 不允许转派；已解决工单需要先重新处理才能转派。
分配目标必须是 ACTIVE 账号，并具有 ACTIVE 的 AGENT 角色。
多角色账号执行处理操作仍须具备 AGENT 且是当前处理人，ADMIN 可操作全部工单。
仅有 USER 角色时，自己创建的工单也不能直接修改状态。

WAITING_USER → PROCESSING 本阶段由客服或管理员手动操作，尚未接入用户回复自动流转。
工单状态 PENDING 与 AI 分类状态 aiStatus=PENDING 是独立字段。

## 接口

所有请求使用 `Content-Type: application/json`，并携带对应账号的
`Authorization: Bearer <accessToken>`。身份和操作人由后端认证信息确定。
version 必须取自最近一次工单 GET 或修改接口响应；每次成功写入后增加 1。
不要在多个操作中复用同一版本，冲突后先重新 GET，再由操作者确认是否重试。

### 管理员分配或转派

`PUT /api/tickets/{id}/assignment`

```json
{
  "assigneeId": 3,
  "version": 0,
  "remark": "分配给订单客服"
}
```

assigneeId 为真实客服账号 ID；首次分配和转派共用此接口。
remark 可不传或为 null，最长 500 字；空白归一为 null。
成功返回 200，data 为更新后的完整工单（与基础详情结构一致），包括
`status: "ASSIGNED"`、`assigneeId: 3`、`version: 1`。
resolvedAt 和 closedAt 为空。

### 修改工单状态

`PUT /api/tickets/{id}/status`

```json
{
  "status": "PROCESSING",
  "version": 1,
  "remark": "开始排查支付流水"
}
```

status 必填，取值为六种工单状态之一，且须满足上述转换表。
PENDING/ASSIGNED 不能通过此接口直接设置，分配必须走管理员分配接口。
version 必填且不小于 0；remark 规则同分配接口。
成功响应示例（ID、工单编号和时间仅为示例）：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "id": 101,
    "ticketNo": "b9a1ba0687dc4258948861afed3b6297",
    "title": "订单支付异常",
    "description": "已扣款但订单未更新",
    "category": null,
    "categoryReason": null,
    "aiStatus": "PENDING",
    "priority": "MEDIUM",
    "status": "PROCESSING",
    "creatorId": 1,
    "assigneeId": 3,
    "createdAt": "2026-10-20T20:00:00",
    "updatedAt": "2026-10-20T20:01:00",
    "version": 2,
    "resolvedAt": null,
    "closedAt": null
  },
  "timestamp": "2026-10-20T12:01:00Z"
}
```

RESOLVED 时写入 resolvedAt；重新处理时清空 resolvedAt；CLOSED 时写入 closedAt 并保留解决时间。
旧的创建、详情、分页、修改内容接口同步返回这两个新增时间字段。
只有 PENDING 且未分配的工单仍允许原创建人修改标题和描述。

### 错误响应

| HTTP | code | 场景 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | 字段缺失、负版本号、非法枚举、额外 JSON 字段、备注过长、非正数 ID |
| 400 | INVALID_ASSIGNEE | 不存在、禁用或不具有有效客服角色的处理人 |
| 401 | UNAUTHORIZED | 未登录或 Token 无效 |
| 403 | FORBIDDEN | 非管理员分配、USER 改状态、客服关闭 |
| 404 | TICKET_NOT_FOUND | 工单不存在，或客服未被分配该工单 |
| 409 | INVALID_STATUS_TRANSITION | 跳转、原地重复设置、终态修改或对已解决/关闭工单分配 |
| 409 | TICKET_ALREADY_ASSIGNED | 分配给当前同一客服 |
| 409 | TICKET_CONFLICT | 旧版本或读后更新时工单已经改变 |
| 409 | TICKET_NOT_ASSIGNED | 遗留异常数据无处理人，无法开始流转 |
| 503 | DATABASE_UNAVAILABLE | 数据库失败，包括历史写入失败；本次更新整体回滚 |

错误 data 为 null。优先校验权限，避免泄露无权访问工单的状态。
转派和旧客服操作并发时，旧客服可能得到 409，也可能在读取时已失去权限而得到 404。

## 事务、并发和操作历史

- 分配与变更状态均在 Service 使用 `@Transactional`。工单更新及对应历史插入共同提交，任一失败共同回滚。
- 更新 SQL 同时检查 id、version、读取到的原状态和原处理人；客服状态更新另检查当前操作人仍是处理人。
- 内容修改也使用同一个 version。分配、转派、内容修改、状态流转竞争同一版本时只有一个成功，避免互相覆盖。
- ASSIGN 历史记录原/新状态、原/新处理人、当前管理员、备注和时间，单条记录完整表示分配造成的状态变化。
- STATUS_CHANGE 历史记录原/新状态、当前操作人、备注和时间。失败请求不会留下成功历史。
- 当前阶段不提供历史查询 HTTP 接口，可通过下面的 SQL 验证记录。

## 本地数据库升级及启动

已有 smart_ticket 仍可能采用 NEW/IN_PROGRESS，本次自动测试不修改这个原库。
旧代码无法识别新状态，所以升级时需要停止所有连接这个业务库的旧版应用实例，再执行迁移并启动新版。

1. 停止旧应用，备份 smart_ticket，确认没有未完成事务。
2. 确认 V2 已执行，操作历史 CHECK 已包含 UPDATE（此前真实 MySQL 验收已在本地执行过 V2，不要重复运行 V2）。
3. 在数据库客户端完整执行 [V3__ticket_status_workflow.sql](sql/V3__ticket_status_workflow.sql)。
4. 配置本地数据库凭据、JWT_SECRET，从 backend 目录启动新版：

```powershell
cd E:\smart-ticket-system\backend
mvn spring-boot:run '-Dspring-boot.run.profiles=local'
```

V3 先扩展三处状态 CHECK，再将 ticket、history 中的 NEW → PENDING、IN_PROGRESS → PROCESSING，
最后收紧约束并修改默认状态。已转换工单的 version 增加 1，以拒绝旧客户端的更新；
已使用新状态的行不会再次递增。历史只更名以保持原语义，不伪造业务操作历史。
MySQL DDL 隐式提交，整个文件不能靠一次 ROLLBACK 撤销；若执行中断，排除失败原因后可完整重跑 V3。
不提供新旧应用同时运行的兼容写入。

验证迁移结果：

```sql
USE smart_ticket;
SELECT COUNT(*) AS legacy_tickets FROM ticket WHERE status IN ('NEW', 'IN_PROGRESS');
SELECT COUNT(*) AS legacy_history FROM ticket_operation_history
WHERE from_status IN ('NEW', 'IN_PROGRESS') OR to_status IN ('NEW', 'IN_PROGRESS');
SHOW CREATE TABLE ticket;
SHOW CREATE TABLE ticket_operation_history;
```

两个 COUNT 应为 0；工单默认状态应为 PENDING，三处状态 CHECK 均只允许新六种状态。

## 自动化测试与验收用例

无需 MySQL 的集成测试：

```powershell
cd E:\smart-ticket-system\backend
mvn clean verify
# 仅运行本阶段测试
mvn '-Dtest=TicketWorkflowIntegrationTest' test
```

真实 MySQL、迁移和 HTTP 测试：

```powershell
.\tools\run-mysql-smoke.ps1
```

该脚本先完整构建测试，再读取被 Git 忽略的 config/application-local.yml。
需要数据库账号具备创建/删除临时库权限，源库有本项目六张表和 ACTIVE 的 USER、AGENT、ADMIN 角色。
它复制表结构及系统角色到随机临时数据库，在临时库中验证 V2/V3、生成账号和工单，
启动独立随机端口应用。结束后停止临时应用、清理数据并删除临时库。源库、原账号密码和 8080 服务不变。
脚本会在临时库故意阻止指定历史插入，验证真实 MySQL 事务回滚；这些临时 CHECK 随测试移除。
原始断言结果为 target/mysql-http-smoke-report.txt，应用日志为 target/mysql-http-smoke-app.log。

| 用例 | 操作 | 预期 |
| --- | --- | --- |
| 完整流程 | 管理员分配；客服开始、等待、继续、解决；管理员关闭 | 全部 200，版本逐次增加，时间及历史正确 |
| 全状态矩阵 | 六种原状态分别设置六种目标状态，共 36 种组合 | 只有约定的 6 条状态边成功，其余 409 |
| 转派 | 对四种可分配状态转派到另一启用客服 | 回到 ASSIGNED，处理人及 ASSIGN 历史正确 |
| 角色及范围 | USER 改状态/分配，客服分配、处理别人、关闭，转派后旧客服再操作 | 按场景返回 403/404，无额外历史 |
| 非法目标与参数 | 非客服/禁用账号、停用客服角色、同客服、缺字段、负版本、超长备注、身份注入、旧状态名 | 400 或 409，数据不改变 |
| 重开与终态 | RESOLVED 重新处理，然后再解决、关闭；CLOSED 再处理或转派 | 重开清空解决时间；关闭后拒绝变更 |
| 并发 | 两次分配、两次状态变更、分配与内容编辑、转派与客服操作竞争同版本 | 一个成功，另一个 409；失去访问权时可为 404；只增加一次版本及历史 |
| 事务回滚 | 强制分配历史/状态历史插入失败 | 503，工单处理人、状态、版本、时间均回滚 |
| MySQL 迁移 | 构造旧状态及历史、运行 V3、重复运行、尝试写入旧状态 | 状态和历史转换正确；版本只递增一次；旧状态被 CHECK 拒绝 |

本次测试执行结果见本文末尾。

## Postman 手工验证

1. 调用 `POST /api/auth/login`，分别获取现有 USER、ADMIN、AGENT 账号 Token。
2. USER 调用 `POST /api/tickets`，记录返回 id、version=0，确认 status=PENDING。
3. ADMIN 按分配接口示例传入实际客服 ID、version=0，记录 version=1。
4. 该 AGENT 按状态接口依次发送 PROCESSING、WAITING_USER、PROCESSING、RESOLVED；每次使用上次响应的 version。
5. ADMIN 将 RESOLVED 改为 CLOSED；GET 详情确认 resolvedAt、closedAt 均存在。
6. 用 USER 改状态应为 403；另一客服操作应为 404；重用旧版本应为 409；关闭后再流转应为 409。

查询分配目标 ID：

```sql
SELECT DISTINCT u.id, u.username, u.display_name
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.id
JOIN sys_role r ON r.id = ur.role_id
WHERE u.status = 'ACTIVE' AND r.status = 'ACTIVE' AND r.code = 'AGENT';
```

查询某张工单的结果（将 101 替换为新建工单的真实 ID）：

```sql
SELECT id, status, assignee_id, version, resolved_at, closed_at, updated_at
FROM ticket WHERE id = 101;

SELECT id, operator_id, operation_type, from_status, to_status,
       from_assignee_id, to_assignee_id, remark, created_at
FROM ticket_operation_history WHERE ticket_id = 101 ORDER BY id;
```

## 文件职责

| 文件 | 用途 |
| --- | --- |
| ticket/controller/TicketWorkflowController.java | 分配及状态 HTTP 接口与角色限制 |
| ticket/service/TicketWorkflowService.java | 数据权限、合法性、事务及冲突处理 |
| ticket/dto/AssignTicketRequest.java、ChangeTicketStatusRequest.java | 独立请求结构和参数校验 |
| ticket/enums/TicketStatus.java | 六种状态和转换规则 |
| ticket/mapper/TicketMapper.java | 启用客服查询、带原状态/处理人/version 的条件更新 |
| ticket/mapper/TicketHistoryMapper.java | 分配和状态历史；创建历史采用新初始状态 |
| ticket/service/TicketService.java | 创建/编辑初始状态改为 PENDING |
| ticket/entity/Ticket.java、ticket/vo/TicketView.java | 返回解决及关闭时间 |
| docs/sql/V3__ticket_status_workflow.sql | 原数据库的状态、默认值及 CHECK 升级 |
| src/test/java/.../TicketWorkflowIntegrationTest.java | 56 项本阶段集成测试 |
| src/test/resources/db/schema.sql、data.sql、TicketApiIntegrationTest.java | 原有测试适配新状态与历史约束 |
| tools/MySqlHttpSmoke.java | 独立 MySQL 临时库的迁移、HTTP、并发和回滚验证 |

未修改登录实现、全局异常处理、依赖或数据库凭据。

## 本次验证结果（2026-10-20）

| 验证 | 结果 |
| --- | --- |
| `mvn -B -ntp clean verify` | 87 项测试，0 失败、0 错误、0 跳过；JAR 构建成功 |
| 其中 TicketWorkflowIntegrationTest | 本阶段新增 56 项，全部通过 |
| `tools/run-mysql-smoke.ps1` 全流程 | 成功；MySQL 8.0.44，打包 JAR + 随机端口真实 HTTP |
| HTTP / 数据库断言 | 160 项全部通过，包含迁移和清理断言，并非 160 个独立测试用例 |
| 历史写入失败的真实 MySQL 测试 | 分配和状态变更均返回 503，业务数据完整回滚 |
| 临时库和测试进程 | 已清理、停止；原数据库及 8080 服务未改动 |

V3 已在复制原表结构的独立临时库验证，包含旧数据转换及重跑。
实际 smart_ticket 尚未执行 V3，启用本阶段代码前须按上文停服升级。
最初一次复测曾因日志输出到 target 而阻止 Maven clean；更换日志目录后完整脚本通过。
