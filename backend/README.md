# 后端基础工程

已提供基础工程、MySQL、MyBatis-Plus、统一响应、全局异常处理和健康检查，
并新增最小登录/JWT、基础工单管理、管理员分配、工单状态流转和 AI 分类。没有 Redis 或消息队列代码。

**新阶段启动前**：必须设置 `JWT_SECRET`，确认已执行 V2 历史表 CHECK 迁移，
并在停服后执行 V3 状态迁移（NEW → PENDING、IN_PROGRESS → PROCESSING）。
生成密钥、接口示例、文件清单和测试步骤见 [基础工单接口文档](docs/ticket-api.md)。
状态规则、分配/状态接口、测试用例和迁移步骤见 [工单状态流转](docs/ticket-workflow.md)。
AI 分类配置、重试、失败模拟和结果验证见 [AI 工单分类](docs/ticket-ai.md)。
工单公开回复、内部备注和消息权限见 [工单消息接口](docs/ticket-messages.md)。

## 环境与版本

- JDK 17。
- Maven 3.6.3 或以上（当前工程使用本机 Maven，无 Maven Wrapper）。
- MySQL 8.0.16 或以上：沿用前一阶段的 `smart_ticket` 数据库；此前设计的 CHECK 约束需要此版本起才会实际执行。
- Spring Boot 3.5.7、MyBatis-Plus 3.5.14，MySQL 驱动由 Spring Boot 管理版本。
- H2 只存在于测试依赖中，不会打包到运行时应用中。

## 目录和文件职责

```text
backend/
├── pom.xml                                Maven 依赖、Java 版本和打包插件
├── README.md                              本说明
├── config/
│   └── application-local.example.yml       外置本地凭据配置模板
└── src/
    ├── main/
    │   ├── java/com/example/ticket/
    │   │   ├── TicketApplication.java        Spring Boot 启动入口
    │   │   ├── config/
    │   │   │   └── MybatisPlusConfig.java    扫描健康检查 Mapper
    │   │   ├── common/
    │   │   │   ├── response/
    │   │   │   │   └── ApiResponse.java      统一 JSON 响应结构
    │   │   │   └── exception/
    │   │   │       └── GlobalExceptionHandler.java
    │   │   │                                请求异常转换为 HTTP 状态和统一响应
    │   │   └── health/
    │   │       ├── controller/HealthController.java
    │   │       │                            存活和数据库健康检查接口
    │   │       ├── service/HealthService.java
    │   │       │                            数据库检查逻辑
    │   │       └── mapper/HealthMapper.java   通过 MyBatis 执行 SELECT 1
    │   └── resources/
    │       └── application.yml              端口、数据源、连接池和 MyBatis-Plus 配置
    └── test/
        ├── java/com/example/ticket/health/
        │   ├── HealthIntegrationTest.java   HTTP、真实 Mapper 查询和常见请求异常测试
        │   └── DatabaseFailureTest.java     数据库故障响应和存活接口测试
        └── resources/application-test.yml   隔离的 H2 测试数据源
```

仓库根目录 `.gitignore` 忽略编译产物、IDE 文件、环境文件和 `application-local.yml`。
上面列出基础阶段目录；新增 auth、security、ticket 模块见接口文档中的文件清单。
不自动建表、更新表结构或插入数据。

## 启动方式一：环境变量

在 PowerShell 中执行。将用户名和密码替换为你本地 MySQL 的真实值；不要将这两项写入版本控制。

```powershell
Set-Location E:\smart-ticket-system\backend

$env:DB_USERNAME = '你的数据库用户名'
$env:DB_PASSWORD = '你的数据库密码'

# 还需生成并设置 JWT_SECRET，具体命令见 docs/ticket-api.md 的启动准备。

# 可选；默认连接 localhost:3306/smart_ticket
$env:DB_URL = 'jdbc:mysql://localhost:3306/smart_ticket?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC&connectTimeout=5000&socketTimeout=5000'

# 可选；默认 8080
$env:SERVER_PORT = '8080'

mvn spring-boot:run
```

环境变量只影响当前 PowerShell 及其启动的子进程。若在 IDEA 启动，请在 Run Configuration 的 Environment variables 中设置相同变量。
工程不自动加载 `.env` 文件。

## 启动方式二：本地 YAML

```powershell
Set-Location E:\smart-ticket-system\backend
Copy-Item config/application-local.example.yml config/application-local.yml
```

编辑生成的 `config/application-local.yml`，填写本地连接、用户名和密码，然后执行：

```powershell
mvn spring-boot:run '-Dspring-boot.run.profiles=local'
```

实际的 `config/application-local.yml` 被 Git 忽略，且位于资源目录之外，不会打包进入 JAR。
此方式也需要配置 `JWT_SECRET` 环境变量，或在本地 YAML 增加 `security.jwt.secret`。
使用此方式时，从 `backend` 目录启动；IDE 请将工作目录设置为 `backend` 并激活 `local`。

## 编译、测试、打包

```powershell
Set-Location E:\smart-ticket-system\backend
mvn clean verify
```

测试通过 `test` profile 自动覆盖数据源，不需要设置 MySQL 凭据，也不会修改 `smart_ticket`。
测试包含使用 H2 执行 `SELECT 1` 的完整 Mapper 链路；这是基础集成检查，不等于已验证本地 MySQL 连接或数据库权限。

生成的可执行 JAR：

```text
target/smart-ticket-backend-0.0.1-SNAPSHOT.jar
```

设置好数据库环境变量后可使用：

```powershell
java -jar target/smart-ticket-backend-0.0.1-SNAPSHOT.jar
```

如需查看依赖：`mvn dependency:tree`。

## 验证接口

启动后，在另一个 PowerShell 中执行：

```powershell
curl.exe -i http://localhost:8080/api/health
curl.exe -i http://localhost:8080/api/health/db
```

### 服务存活

`GET /api/health` 返回 HTTP 200：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": { "status": "UP" },
  "timestamp": "2026-10-20T00:00:00Z"
}
```

`timestamp` 由服务实时生成，使用 UTC。此接口不访问数据库。

### 数据库连接

`GET /api/health/db` 通过 HealthMapper → 数据源 → MySQL 执行 `SELECT 1`。
成功时 HTTP 200，`data` 为：

```json
{ "status": "UP", "database": "UP" }
```

失败时 HTTP 503：

```json
{
  "code": "DATABASE_UNAVAILABLE",
  "message": "数据库访问失败，请检查数据库服务与连接配置",
  "data": null,
  "timestamp": "2026-10-20T00:00:00Z"
}
```

连接池在首次访问数据库时建立连接，所以服务启动成功、存活接口成功都不代表 MySQL 已连接成功。
此检查不读取用户或工单表，也不验证表结构或业务写入权限。

### 全局异常处理

```powershell
# 不存在的资源：HTTP 404，code=NOT_FOUND
# 新阶段需先登录；未登录会优先返回 401
curl.exe -i -H 'Authorization: Bearer <登录返回的accessToken>' http://localhost:8080/api/not-exists

# 不支持的请求方法：HTTP 405，code=METHOD_NOT_ALLOWED
curl.exe -i -X POST http://localhost:8080/api/health
```

统一响应字段为 `code`、`message`、`data`、`timestamp`。HTTP 状态码保留真实语义，不把所有错误转为 HTTP 200。
已配置参数/JSON 错误 400、资源不存在 404、方法不支持 405、内容类型不支持 415、数据库异常 503、其他未处理异常 500。
API 不返回 SQL、数据库地址、账号或堆栈信息。

## 常见问题

| 现象 | 检查与处理 |
| --- | --- |
| `invalid target release: 17` 或类版本不兼容 | 用 `java -version` 和 `mvn -version` 确认 Maven 实际使用 JDK 17，检查 `JAVA_HOME` 和 IDE 的 Maven JDK。 |
| `Could not resolve placeholder 'DB_USERNAME'` / `DB_PASSWORD` | 当前进程没有凭据环境变量；设置后在同一终端启动，或使用 local profile。 |
| `JWT_SECRET` 缺失或密钥长度校验失败 | 按接口文档生成随机密钥，在同一终端设置，或放入被忽略的本地配置。 |
| 端口 8080 被占用 | 改为 `$env:SERVER_PORT = '8081'`，启动后访问对应端口。 |
| `Communications link failure`、连接拒绝或数据库接口 503 | 检查 MySQL 服务、DB_URL、3306 端口、网络和防火墙；使用数据库客户端验证同样的地址和凭据。 |
| `Access denied for user` | 检查用户名、密码以及 MySQL 用户允许连接的主机；不要把密码或完整连接凭据贴到日志/仓库。 |
| `Unknown database 'smart_ticket'` | 在数据库客户端确认前一阶段的数据库已在当前 MySQL 实例创建；本工程不会自动建库。 |
| `Public Key Retrieval is not allowed` | 优先正确配置 TLS 或服务器 RSA 公钥；仅在可信的本地开发环境，可在 DB_URL 末尾追加 `&allowPublicKeyRetrieval=true`。 |
| Maven 依赖下载失败 | 检查 Maven 仓库、网络、代理和 settings.xml 的镜像配置；恢复网络后重新执行 `mvn clean verify`。 |
| local 配置未生效 | 确认工作目录为 `backend`、文件为 `config/application-local.yml`，启动时显式激活 `local`。 |
| `/api/health` 正常，`/api/health/db` 返回 503 | 前者只检查进程；排查数据库连接。连接池等待、连接建立和读取均有限时，故障时可能等待数秒。 |

数据库错误只记录异常类型，避免把连接凭据写入日志。需要进一步诊断时，在本地使用数据库客户端验证或用调试器查看异常原因。

## 本阶段验收

1. `mvn clean verify` 成功并生成可执行 JAR。
2. 使用数据库环境变量或本地 YAML 启动成功。
3. `GET /api/health` 返回 HTTP 200 和统一响应。
4. 连接真实 MySQL 时 `GET /api/health/db` 返回 HTTP 200、`database=UP`。
5. 错误路径/请求方法返回统一 404/405。
6. 仓库中不包含实际数据库密码，不修改已有表结构或模拟数据。
