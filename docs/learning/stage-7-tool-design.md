# 阶段 7：Agent Tool 设计

## 阶段目标

阶段 7 将阶段 6 的简单能力服务整理为标准 Tool 层。当前仍不接入 LLM、不引入 MCP、不访问 RAG。Tool 是 Agent 调用业务能力的受控封装层，不是新的业务层。

当前调用链：

```text
/api/agent/chat
  -> AgentChatService
  -> IntentRouter
  -> AgentToolParameterExtractor
  -> AgentToolRegistry
  -> AgentTool
  -> TicketService / AgentSessionContext
```

`/api/agent/chat` 的对外请求和响应保持兼容，内部从 `TicketAgentCapabilityService` 切换为 `AgentToolRegistry + AgentTool`。

## 目录结构

阶段 7 后，`smart-ticket-agent` 的 Tool 相关代码按职责拆分：

```text
com.smartticket.agent
├─ model
│  ├─ AgentIntent.java
│  ├─ AgentSessionContext.java
│  └─ IntentRoute.java
└─ tool
   ├─ core
   │  ├─ AgentTool.java
   │  ├─ AgentToolMetadata.java
   │  ├─ AgentToolRegistry.java
   │  ├─ AgentToolRequest.java
   │  ├─ AgentToolResult.java
   │  ├─ AgentToolStatus.java
   │  └─ ToolRiskLevel.java
   ├─ parameter
   │  ├─ AgentToolParameterExtractor.java
   │  ├─ AgentToolParameterField.java
   │  ├─ AgentToolParameters.java
   │  ├─ AgentToolRequestValidator.java
   │  └─ AgentToolValidationResult.java
   ├─ support
   │  └─ AgentToolResults.java
   └─ ticket
      ├─ CreateTicketTool.java
      ├─ QueryTicketTool.java
      ├─ SearchHistoryTool.java
      └─ TransferTicketTool.java
```

拆分原则：

- `model` 只保留 Agent 通用模型，不放 Tool 专属协议对象。
- `tool.core` 放 Tool 抽象、注册表、请求响应协议和风险等级。
- `tool.parameter` 放参数抽取、参数字段和参数校验。
- `tool.support` 放 Tool 辅助构造器。
- `tool.ticket` 放具体工单业务 Tool。

## 统一模型

### AgentTool

统一 Tool 接口包含：

- `name()`：Tool 名称。
- `support(intent)`：是否支持某个意图。
- `metadata()`：Tool 元数据。
- `execute(toolRequest)`：执行 Tool。

### AgentToolMetadata

Tool 元数据包含：

- `name`：工具名。
- `description`：工具用途。
- `riskLevel`：风险等级。
- `readOnly`：是否只读。
- `requireConfirmation`：是否需要用户确认。
- `requiredFields`：执行前必须具备的结构化参数字段。

### AgentToolRequest

Tool 请求包含：

- `currentUser`：当前登录用户。
- `message`：用户原始消息。
- `context`：当前 Agent 会话上下文。
- `route`：意图路由结果。
- `parameters`：结构化参数。

### AgentToolParameters

第一版结构化参数包含：

- `ticketId`
- `assigneeId`
- `title`
- `description`
- `category`
- `priority`
- `numbers`

参数当前由规则抽取器 `AgentToolParameterExtractor` 生成。后续阶段接入 LLM 后，可以用 LLM 参数抽取结果补充或替换规则抽取结果，但仍必须经过代码校验。

### AgentToolResult

Tool 响应包含：

- `invoked`：是否实际调用了业务能力。
- `status`：`SUCCESS`、`NEED_MORE_INFO`、`FAILED`。
- `toolName`：执行的 Tool 名。
- `reply`：面向用户的简短回复。
- `data`：原始业务结果或缺参说明。
- `activeTicketId`：本次调用产生或确认的当前工单 ID。
- `activeAssigneeId`：本次调用产生或确认的当前处理人 ID。

### AgentToolStatus

- `SUCCESS`：Tool 调用成功。
- `NEED_MORE_INFO`：缺少必要参数，需要用户补充。
- `FAILED`：调用失败。第一版主要由异常处理链路承接，后续可在 Tool 层统一包装业务异常。

### AgentToolParameterField

用于声明 Tool 的必填参数字段：

- `TICKET_ID`
- `ASSIGNEE_ID`
- `TITLE`
- `DESCRIPTION`
- `CATEGORY`
- `PRIORITY`

### AgentToolRequestValidator

通用参数校验器会读取 `AgentToolMetadata.requiredFields`，并检查 `AgentToolRequest.parameters` 是否完整。

当前策略：

- 字符串字段为空或空白时视为缺失。
- 枚举字段为 `null` 时视为缺失。
- ID 字段为 `null` 时视为缺失。

Tool 可以在调用通用校验器前先做自己的特殊参数修正。例如 `TransferTicketTool` 会在只有一个数字且上下文存在 `activeTicketId` 时，把该数字解释为 `assigneeId`。

### AgentToolResults

统一结果构造器，负责生成：

- `SUCCESS`
- `NEED_MORE_INFO`
- `FAILED`

这样可以避免每个 Tool 重复手写 `AgentToolResult.builder()`，也能统一缺参提示格式。

### ToolRiskLevel

- `READ_ONLY`：只读查询。
- `LOW_RISK_WRITE`：低风险写操作。
- `HIGH_RISK_WRITE`：高风险写操作。

## Tool 清单

### QueryTicketTool

- Tool 名：`queryTicket`
- 支持意图：`QUERY_TICKET`
- 风险等级：`READ_ONLY`
- 是否只读：是
- 是否需要确认：否

输入：

- `ticketId`：可选。存在时查询工单详情。
- `currentUser`：必需，用于业务层权限判断。

输出：

- 有 `ticketId`：返回 `TicketDetailDTO`。
- 无 `ticketId`：返回当前用户可见工单第一页 `PageResult<Ticket>`。
- 成功时状态为 `SUCCESS`。

约束：

- 必须调用 `TicketService.getDetail` 或 `TicketService.pageTickets`。
- 必须查询当前事实数据，不允许调用 RAG。

### CreateTicketTool

- Tool 名：`createTicket`
- 支持意图：`CREATE_TICKET`
- 风险等级：`LOW_RISK_WRITE`
- 是否只读：否
- 是否需要确认：否

输入：

- `title`：必需，由规则从消息生成。
- `description`：必需，当前使用用户原始消息。
- `category`：必需，规则推断，默认 `OTHER`。
- `priority`：必需，规则推断，默认 `MEDIUM`。
- `currentUser`：必需。

必填字段：

- `TITLE`
- `DESCRIPTION`
- `CATEGORY`
- `PRIORITY`

输出：

- 返回创建后的 `Ticket`。
- 成功时状态为 `SUCCESS`。
- 更新 `activeTicketId`。

约束：

- 必须调用 `TicketService.createTicket`。
- 不直接写数据库。
- 当前阶段不做创建前相似案例检查，RAG 预留到后续阶段。

### TransferTicketTool

- Tool 名：`transferTicket`
- 支持意图：`TRANSFER_TICKET`
- 风险等级：`HIGH_RISK_WRITE`
- 是否只读：否
- 是否需要确认：是

输入：

- `ticketId`：必需。缺失时可从 `AgentSessionContext.activeTicketId` 复用。
- `assigneeId`：必需。
- `currentUser`：必需。

必填字段：

- `TICKET_ID`
- `ASSIGNEE_ID`

输出：

- 参数完整且业务成功时返回转派后的 `Ticket`。
- 缺少 `ticketId` 或 `assigneeId` 时返回 `NEED_MORE_INFO`。
- 成功时更新 `activeTicketId` 和 `activeAssigneeId`。

约束：

- 必须调用 `TicketService.transferTicket`。
- 目标处理人是否存在、是否为 STAFF、当前用户是否允许转派，仍由 `biz` 层判断。
- 当前阶段只声明 `requireConfirmation=true`，实际用户确认流程留到后续上下文和编排阶段完善。

### SearchHistoryTool

- Tool 名：`searchHistory`
- 支持意图：`SEARCH_HISTORY`
- 风险等级：`READ_ONLY`
- 是否只读：是
- 是否需要确认：否

输入：

- `context`：当前 Agent 会话上下文。

输出：

- 返回 `recentMessages`。
- 成功时状态为 `SUCCESS`。

约束：

- 当前阶段只查询会话历史。
- 不访问 RAG。
- 历史工单经验检索留到 RAG 阶段，由后续 RAG Tool 承接。

## 风险控制

当前阶段只做风险元数据声明，不做完整确认流程：

- `READ_ONLY` Tool 可以自动执行。
- `LOW_RISK_WRITE` Tool 可以在参数完整时执行。
- `HIGH_RISK_WRITE` Tool 声明 `requireConfirmation=true`，后续阶段应接入用户确认。

无论风险等级如何，所有写操作都必须通过 `biz` 层服务完成。

## 参数校验边界

当前阶段采用三层边界：

- 参数抽取器：尽量从用户消息和上下文中得到结构化参数。
- Tool 校验器：根据 `requiredFields` 做确定性缺参检查。
- `biz` 层：做权限、状态机、目标处理人合法性、并发状态等业务规则判断。

后续接入 LLM 后，LLM 可以辅助判断缺少哪些信息并生成澄清问题，但不能替代 Tool 校验器和 `biz` 层校验。

## 后续演进

阶段 8 接入 LLM 后，LLM 可以生成候选 Tool 调用计划，但必须经过以下检查：

- Tool 是否存在。
- Tool 是否支持当前意图。
- 参数是否完整。
- 是否需要用户确认。
- 是否允许自动执行。

阶段 9 单 Agent 编排时，`AgentToolRegistry` 将成为 Tool 选择和执行的统一入口。
