# Codex Bootstrap Notes

## 1. 当前仓库状态

已阅读 `docs/1.enterprise_smart_ticket_platform_dev_guide.md`。`README.md` 当前不存在。

当前根目录只有：

```text
smart-ticket-platform
├─ .idea
└─ docs
```

结论：

- 当前仓库还没有 Maven 根 `pom.xml`。
- 当前仓库还没有任何 Java 模块目录。
- 当前结构可以作为项目根目录继续使用，但尚未完成多模块 Maven 项目的初始化。
- 不建议在当前阶段直接补大量模板代码，应先确定父工程、模块边界、依赖方向和后续开发顺序。

项目目标应保持为：以 Java 后端业务系统为主体，以 Agent 和 RAG 能力作为增强层的模块化单体系统。第一优先级是跑通工单业务闭环，其次才是自然语言入口和历史知识复用。

## 2. 推荐根目录结构

建议后续初始化为 Maven 多模块单体：

```text
smart-ticket-platform
├─ pom.xml
├─ README.md
├─ docs
│  ├─ 1.enterprise_smart_ticket_platform_dev_guide.md
│  ├─ 2.codex_task_plan_smart_ticket.md
│  ├─ 3.smart_ticket_business_understanding.md
│  └─ codex-bootstrap-notes.md
├─ smart-ticket-common
├─ smart-ticket-domain
├─ smart-ticket-infra
├─ smart-ticket-auth
├─ smart-ticket-biz
├─ smart-ticket-rag
├─ smart-ticket-agent
├─ smart-ticket-api
└─ smart-ticket-app
```

说明：

- `pom.xml` 作为父工程，只管理模块、版本、插件和公共依赖，不承载业务代码。
- 各子模块先建立最小 Maven 模块即可，不需要一次性生成 Controller、Service、Entity 等模板。
- `smart-ticket-app` 是最终可启动应用，其他模块作为内部依赖参与装配。
- 这是模块化单体，不是微服务拆分。模块用于约束边界和依赖方向，而不是提前引入分布式复杂度。

## 3. 模块职责划分

### `smart-ticket-common`

职责：

- 通用异常、错误码、统一返回结构。
- 基础常量、通用枚举接口、少量无业务含义工具类。
- 不放工单业务规则，不依赖任何业务模块。

### `smart-ticket-domain`

职责：

- 核心数据对象定义，例如用户、角色、工单、评论、操作日志、附件、知识记录、知识切片。
- 领域枚举，例如工单状态、优先级、分类、操作类型。
- Mapper / Repository 接口或持久化对象定义，具体取决于后续 MyBatis / JPA 选择。

边界：

- `domain` 表达“业务对象是什么”和“数据结构是什么”。
- `domain` 不编排业务流程，不做权限决策，不调用 Redis、LLM、向量库。

### `smart-ticket-infra`

职责：

- MySQL、Redis、pgvector、文件存储、LLM Client、Embedding Client 等基础设施适配。
- 第三方 SDK 封装。
- 技术配置和外部系统访问能力。

边界：

- `infra` 提供技术能力，不反向理解业务流程。
- `infra` 不应该依赖 `biz`、`agent`、`api`。

### `smart-ticket-auth`

职责：

- 登录认证、JWT、Spring Security 配置。
- 当前用户上下文。
- 基础 RBAC，即用户有哪些系统角色。

边界：

- `auth` 回答“你是谁”和“你有哪些基础角色”。
- `auth` 不回答“你能不能处理这张工单”。后者必须由 `biz` 结合工单关系、角色、状态判断。

### `smart-ticket-biz`

职责：

- 工单核心业务规则。
- 工单创建、查询、分配、转派、评论、状态流转、关闭。
- 幂等、操作日志、业务权限判断。
- 触发知识构建，但不承担向量检索实现细节。

边界：

- `biz` 是业务事实和业务规则的权威入口。
- 所有会改变工单事实状态的操作都必须经过 `biz`。

### `smart-ticket-rag`

职责：

- 已关闭工单的知识文本处理。
- 文本切片、Embedding、查询改写、向量检索、相似历史案例召回。
- 为 Agent 或业务链路提供历史经验检索结果。

边界：

- `rag` 处理知识数据，不处理主业务事务。
- `rag` 不直接决定工单是否创建、转派、关闭。
- `rag` 的结果是参考信息，不是当前事实真相。

### `smart-ticket-agent`

职责：

- 自然语言入口。
- 意图识别、澄清追问、参数抽取、Tool 调用编排。
- 会话上下文、Prompt 模板、结果摘要。
- 按意图调用 `biz` 或 `rag`。

边界：

- `agent` 负责理解和编排，不负责最终业务规则。
- `agent` 不能绕过 `biz` 直接写数据库。
- `agent` 不能把模型输出当作权限判断或事务结果。

### `smart-ticket-api`

职责：

- HTTP Controller。
- DTO / VO。
- 参数校验。
- Assembler / Converter。
- OpenAPI 暴露层。

边界：

- `api` 负责协议适配，不承载业务规则。
- `api` 不直接操作 `domain` 持久化对象。
- `api` 调用 `biz` 完成工单业务，调用 `agent` 完成自然语言入口。

### `smart-ticket-app`

职责：

- Spring Boot 启动类。
- 全局装配入口。
- 全局异常处理、Swagger 配置、运行时配置汇总。
- 打包可运行应用。

边界：

- `app` 可以依赖所有模块用于启动装配。
- `app` 不写具体业务流程。

## 4. 重点边界解释

### domain 和 biz 的边界

`domain` 是业务对象和数据模型层，回答“系统里有哪些对象、字段、枚举和持久化接口”。

`biz` 是业务行为和规则层，回答“什么条件下可以创建、分配、转派、解决、关闭工单”。

建议原则：

- 工单状态枚举可以放在 `domain`。
- 工单状态能否从 `PROCESSING` 变为 `CLOSED` 的判断应放在 `biz`。
- `Ticket`、`TicketComment`、`TicketOperationLog` 等对象定义放在 `domain`。
- `TicketCommandService`、`TicketAssignService`、`TicketPermissionService` 放在 `biz`。
- `domain` 不调用 `biz`。

### api 和 app 的边界

`api` 是 HTTP 入口层，负责把外部请求转换成内部命令或查询，并把内部结果转换成响应。

`app` 是启动装配层，负责把所有模块组合成一个可运行的 Spring Boot 应用。

建议原则：

- Controller 放在 `api`。
- 启动类放在 `app`。
- Swagger / OpenAPI 的接口注解主要跟随 `api`，全局配置可以放在 `app`。
- 全局异常处理可以放在 `app` 或 `api`，但建议由 `app` 统一装配，避免散落。
- `app` 不应该成为业务逻辑中转站。

### agent 和 biz 的边界

`agent` 是智能增强层，负责自然语言理解、澄清、参数整理、Tool 编排和表达。

`biz` 是业务规则层，负责权限校验、状态流转、数据写入、操作留痕和一致性。

建议原则：

- 用户说“帮我把 INC001 转给张三”，`agent` 负责识别意图和提取工单号、目标处理人。
- 是否允许当前用户转派、目标处理人是否有效、工单状态是否允许转派，由 `biz` 判断。
- `agent` 可以调用 `biz` 暴露的命令服务。
- `agent` 不直接调用 Mapper 更新工单负责人。
- `agent` 的输出不能作为最终事实，最终事实以 `biz` 返回结果和数据库状态为准。

### biz / rag / infra 在知识构建链路中的边界

知识构建链路建议拆成三段：

1. `biz` 触发知识构建。
2. `rag` 负责知识处理和检索逻辑。
3. `infra` 负责外部技术能力适配。

具体边界：

- `biz` 在工单关闭后判断是否具备知识沉淀价值，并生成或组织业务侧知识原文，例如问题、处理过程、解决方案摘要。
- `biz` 可以发布领域事件或调用知识构建接口，但不应直接写向量库。
- `rag` 接收知识文本，负责切片、Embedding 调用编排、向量记录组织、相似案例检索、查询改写。
- `rag` 不修改工单主状态，不参与工单关闭事务。
- `infra` 提供 pgvector、Embedding Client、LLM Client、Redis 等具体技术访问。
- `infra` 不知道“工单关闭后要沉淀知识”这个业务规则。

推荐链路：

```text
工单关闭
  -> biz 校验关闭权限并写入 CLOSED 状态
  -> biz 写操作日志
  -> biz 触发知识构建任务或事件
  -> rag 生成知识切片并调用 embedding
  -> infra 写入 pgvector / 调用 embedding provider
```

重要原则：

- 工单关闭事务不应强依赖向量化成功。
- 知识构建可以异步、可重试、可补偿。
- RAG 结果用于“历史经验参考”，不能替代 MySQL 中的当前工单事实。

## 5. 推荐依赖方向

建议依赖方向如下：

```text
smart-ticket-api
  -> smart-ticket-biz
  -> smart-ticket-agent
  -> smart-ticket-common

smart-ticket-agent
  -> smart-ticket-biz
  -> smart-ticket-rag
  -> smart-ticket-common

smart-ticket-biz
  -> smart-ticket-domain
  -> smart-ticket-infra
  -> smart-ticket-common
  -> smart-ticket-rag（仅用于受控的知识构建触发，优先考虑接口或事件隔离）

smart-ticket-rag
  -> smart-ticket-domain
  -> smart-ticket-infra
  -> smart-ticket-common

smart-ticket-auth
  -> smart-ticket-domain
  -> smart-ticket-common

smart-ticket-infra
  -> smart-ticket-common

smart-ticket-domain
  -> smart-ticket-common

smart-ticket-app
  -> smart-ticket-api
  -> smart-ticket-auth
  -> smart-ticket-biz
  -> smart-ticket-agent
  -> smart-ticket-rag
  -> smart-ticket-infra
  -> smart-ticket-domain
  -> smart-ticket-common
```

需要避免：

- `api -> domain` 后直接查库或改库。
- `agent -> domain / infra` 后绕过 `biz` 写业务数据。
- `infra -> biz` 形成反向依赖。
- `rag` 参与工单主事务写入。
- `common` 变成万能模块，放入业务规则。

## 6. 后续推荐开发顺序

### 阶段 0：工程骨架

目标：只建立 Maven 多模块和基础工程约束。

建议任务：

- 创建父 `pom.xml`。
- 创建 9 个子模块。
- 统一 Java 17、Spring Boot 3.x、依赖版本管理。
- 配置基础构建、测试插件。
- 补充 `README.md`，说明项目定位、运行方式和模块说明。

### 阶段 1：领域模型和数据库设计

目标：先让业务对象稳定下来。

建议任务：

- 定义用户、角色、工单、评论、操作日志、附件、知识记录等核心模型。
- 明确工单状态、优先级、分类、操作类型枚举。
- 准备 MySQL 建表脚本或迁移脚本。
- 暂不引入复杂 Agent 逻辑。

### 阶段 2：认证和基础权限

目标：解决“你是谁”和基础角色能力。

建议任务：

- 登录认证。
- JWT。
- 当前用户上下文。
- 基础角色控制。
- 注意不要把工单关系权限写死在 `auth`。

### 阶段 3：工单核心闭环

目标：完成后端主业务闭环。

建议任务：

- 创建工单。
- 查询工单列表和详情。
- 分配 / 转派。
- 添加评论。
- 状态更新。
- 解决和关闭。
- 操作日志。
- 工单关系权限。

这是项目的主线，应优先于 Agent。

### 阶段 4：工程化能力

目标：补足后端项目证据。

建议任务：

- Redis 工单详情缓存。
- 创建工单幂等防重。
- Swagger / OpenAPI。
- Docker Compose。
- 单元测试和必要的集成测试。

### 阶段 5：RAG 知识构建

目标：让已关闭工单可复用。

建议任务：

- 关闭工单后生成知识文本。
- 文本切片。
- Embedding。
- pgvector 存储。
- 相似历史工单检索。
- 建议先做异步或手动触发，避免影响主事务。

### 阶段 6：Agent 自然语言入口

目标：在稳定业务能力之上增加智能入口。

建议任务：

- 意图识别：查工单、建工单、转派工单、查历史案例。
- 澄清追问：信息不足时不强行写入。
- Tool 调用：通过 `biz` 执行业务操作，通过 `rag` 检索历史案例。
- 会话上下文：支持“它”“这个工单”等指代。
- 结果摘要：把结构化业务结果转成自然语言响应。

### 阶段 7：验收和简历表达

目标：形成可展示的完整项目。

建议任务：

- 准备接口文档和演示数据。
- 准备核心链路测试。
- 准备架构说明图。
- 准备 README 中的项目亮点、模块边界、核心链路和运行方式。

## 7. 初始化阶段不建议做的事

- 不建议现在生成大量 Controller / Service / Entity 空模板。
- 不建议现在直接接入 LLM 或 pgvector。
- 不建议把 Agent 作为第一条开发主线。
- 不建议引入微服务、消息中间件、复杂工作流引擎。
- 不建议在 `common` 中提前堆放业务工具类。

当前最合理的下一步是：建立 Maven 多模块父子工程，保持模块边界清晰，然后从领域模型和工单核心闭环开始开发。
