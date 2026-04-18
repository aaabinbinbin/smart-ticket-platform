# 企业智能工单协同平台

本项目是一个 Java 17 + Spring Boot 3.x 的模块化单体项目，目标是构建面向企业服务台场景的智能工单协同平台。

项目定位：

- 后端主系统负责工单生命周期、权限、状态流转、审计、缓存和一致性。
- Agent 增强层负责自然语言入口、意图识别、Tool 调用编排、历史案例检索和结果摘要。
- 第一版不采用微服务，不引入 Spring Cloud，不引入注册发现或复杂配置中心。

## 模块说明

```text
smart-ticket-platform
├─ smart-ticket-app
├─ smart-ticket-common
├─ smart-ticket-auth
├─ smart-ticket-domain
├─ smart-ticket-biz
├─ smart-ticket-agent
├─ smart-ticket-rag
├─ smart-ticket-infra
└─ smart-ticket-api
```

### smart-ticket-app

应用启动模块，包含 `SmartTicketApplication`。负责组合所有内部模块并启动 Spring Boot 应用。

### smart-ticket-common

公共基础模块。后续用于放置通用异常、错误码、统一返回结构、基础常量和少量无业务含义的工具类。

### smart-ticket-auth

认证与基础授权模块。后续用于登录认证、Spring Security、JWT、当前用户上下文和基础 RBAC。

边界：`auth` 负责“你是谁”和“你有哪些角色”，不负责判断“你能不能操作某张工单”。

### smart-ticket-domain

领域数据模块。后续用于放置核心数据对象、枚举、Mapper / Repository 接口等。

边界：`domain` 定义对象和数据结构，不编排业务流程，不做权限判断。

### smart-ticket-biz

核心业务模块。后续用于工单创建、查询、分配、转派、评论、状态流转、关闭、幂等、操作日志和业务权限判断。

边界：所有改变工单事实状态的操作都应经过 `biz`。

### smart-ticket-agent

智能入口模块。后续用于自然语言入口、意图识别、澄清追问、参数抽取、Tool 调用编排、会话上下文和结果摘要。

边界：`agent` 不直接写数据库，不绕过 `biz` 执行业务操作。

### smart-ticket-rag

知识检索模块。后续用于已关闭工单的知识文本处理、切片、Embedding、查询改写、向量检索和相似历史案例召回。

边界：`rag` 提供历史经验参考，不参与工单主事务写入。

### smart-ticket-infra

基础设施模块。后续用于 Redis、MySQL、pgvector、文件存储、LLM Client、Embedding Client 和第三方 SDK 适配。

边界：`infra` 提供技术访问能力，不反向依赖业务模块。

### smart-ticket-api

HTTP 接口模块。后续用于 Controller、DTO / VO、参数校验、Assembler / Converter 和 OpenAPI 暴露。

边界：`api` 负责协议适配，不承载核心业务规则。

## 依赖方向

推荐依赖方向：

```text
api -> biz
api -> agent

agent -> biz
agent -> rag

biz -> domain
biz -> infra

rag -> domain
rag -> infra

auth -> domain
auth -> common

infra -> common
domain -> common

app -> api / auth / biz / agent / rag / infra / domain / common
```

需要避免：

- `api` 直接操作持久化对象。
- `agent` 绕过 `biz` 写业务数据。
- `rag` 参与工单主事务。
- `infra` 反向依赖业务模块。
- `common` 变成业务规则堆放处。

## 本地构建

```bash
mvn clean package
```

## 启动应用

```bash
mvn -pl smart-ticket-app spring-boot:run
```

当前仅完成工程初始化和基础包结构，暂未实现具体业务逻辑。

## 数据库说明

当前阶段使用 MySQL 作为主业务库，初始化脚本位于：

- `docs/sql/schema.sql`
- `docs/sql/seed.sql`

默认本地连接配置位于 `smart-ticket-app/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/smart_ticket_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: 123456
```

初始化顺序：

```bash
mysql -h127.0.0.1 -uroot -p123456 < docs/sql/schema.sql
mysql -h127.0.0.1 -uroot -p123456 < docs/sql/seed.sql
```

已建表：

- `sys_user`
- `sys_role`
- `sys_user_role`
- `ticket`
- `ticket_comment`
- `ticket_operation_log`
- `ticket_attachment`
- `ticket_knowledge`
- `ticket_knowledge_embedding`

初始角色：

- `USER`
- `STAFF`
- `ADMIN`

数据访问层当前采用 MyBatis mapper 接口，位于 `smart-ticket-domain` 模块。实体类与表结构保持一致，暂未实现业务 Service。

## 认证与基础权限

当前版本基于 Spring Security + JWT 实现基础登录认证和 RBAC 控制，详细流程见：

- 流程说明：`docs/flow/auth-flow.md`
- 学习理解：`docs/learning/stage-3-auth-rbac-explained.md`

登录接口：

```http
POST /api/auth/login
```

本地演示账号：

```text
user1 / 123456   -> USER
staff1 / 123456  -> USER + STAFF
admin1 / 123456  -> USER + STAFF + ADMIN
```

受保护接口示例：

```http
GET /api/examples/security/me
GET /api/examples/security/user
GET /api/examples/security/staff
GET /api/examples/security/admin
```

访问受保护接口时需要携带：

```http
Authorization: Bearer <accessToken>
```

当前 `auth` 模块只负责“你是谁”和“你有哪些系统角色”。某张工单能否关闭、转派、评论等业务权限，后续由 `biz` 模块结合工单关系和状态判断。

## 工单核心业务 MVP

当前已实现工单核心闭环：

- 创建工单
- 查询工单详情
- 分页查询工单列表
- 管理员分配工单
- 当前负责人或管理员转派工单
- 更新工单状态
- 添加工单评论
- 关闭工单
- 自动记录操作日志

接口说明见：

- `docs/ticket-api.md`

状态流转约束：

```text
PENDING_ASSIGN -> PROCESSING -> RESOLVED -> CLOSED
```

工单业务权限由 `smart-ticket-biz` 模块判断。`auth` 只提供当前用户身份和 USER / STAFF / ADMIN 基础角色。
