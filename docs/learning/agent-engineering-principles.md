# Agent 工程设计原则

## 文档定位

本文用于指导后续 Agent 开发和工单业务扩展。这里参考的是 Claude Code 这类 coding agent 的工程思想，而不是照搬它的产品形态。

本项目的领域是企业工单系统，不是代码仓库助手。因此可以迁移的是 Agent 设计原则：Tool-first、受控执行、显式上下文、可观测轨迹、失败恢复和高风险操作确认。

## 核心原则

### LLM 不做最终事实裁决

LLM 可以参与理解、规划和总结，但不能直接修改业务事实。

LLM 负责：

- 理解用户意图
- 抽取结构化参数
- 生成澄清问题
- 选择候选 Tool
- 总结 Tool 执行结果

Tool 和 `biz` 层负责：

- 查询数据库
- 创建工单
- 转派工单
- 校验权限
- 校验状态机
- 写操作日志
- 返回真实业务结果

最终业务事实以 `biz` 返回结果和数据库状态为准，不能以模型输出为准。

### Tool-first 设计

后续 Agent 能力应优先设计为 Tool，而不是散落在编排服务中的条件分支。

每个 Tool 都应明确：

- `name`：工具名
- `description`：工具用途
- `input schema`：输入参数结构
- `output schema`：输出结果结构
- `riskLevel`：风险等级
- `readOnly`：是否只读
- `requireConfirmation`：是否需要用户确认
- `businessService`：最终调用的业务服务

建议第一批 Tool：

- `QueryTicketTool`
- `CreateTicketTool`
- `TransferTicketTool`
- `SearchHistoryTool`

后续可扩展：

- `AddCommentTool`
- `UpdateTicketStatusTool`
- `CloseTicketTool`
- `RetrieveSimilarCaseTool`
- `SuggestSolutionTool`

### Planning -> Action -> Observation

可以参考 Claude Code 的工作循环，但在本项目中要保持轻量和受控：

```text
用户输入
  -> 理解任务
  -> 制定轻量计划
  -> 选择 Tool
  -> 执行 Tool
  -> 观察 ToolResult
  -> 更新上下文
  -> 总结响应
```

第一版单 Agent 编排不建议无限循环。建议限制每轮最多 1 到 3 次 Tool 调用，避免模型陷入不可控循环。

### 显式上下文管理

不要只把所有历史消息拼给 LLM。应维护结构化会话上下文。

建议 `AgentSessionContext` 后续扩展：

- `activeTicketId`
- `activeAssigneeId`
- `lastIntent`
- `recentMessages`
- `pendingIntent`
- `pendingToolName`
- `pendingParameters`
- `awaitingFields`
- `lastToolResult`
- `conversationSummary`

这样可以支持：

```text
用户：帮我创建一个高优工单
Agent：请补充问题描述
用户：销售系统登录失败
Agent：继续使用 pendingParameters 创建工单
```

### 高风险操作需要确认

工单系统存在写操作和状态流转，必须区分风险等级。

建议风险等级：

```text
READ_ONLY
LOW_RISK_WRITE
HIGH_RISK_WRITE
```

示例：

- `QueryTicketTool`：`READ_ONLY`
- `SearchHistoryTool`：`READ_ONLY`
- `CreateTicketTool`：`LOW_RISK_WRITE`
- `AddCommentTool`：`LOW_RISK_WRITE`
- `TransferTicketTool`：`HIGH_RISK_WRITE`
- `UpdateTicketStatusTool`：`HIGH_RISK_WRITE`
- `CloseTicketTool`：`HIGH_RISK_WRITE`

执行策略建议：

- `READ_ONLY`：可自动执行
- `LOW_RISK_WRITE`：参数完整时可执行，或按配置确认
- `HIGH_RISK_WRITE`：执行前必须用户确认

例如用户说“把这个工单关了”，Agent 应先确认：

```text
将关闭工单 INC202604190001，关闭后不能继续评论。确认关闭吗？
```

### 可回放、可观测的执行轨迹

Agent 行为必须可解释、可调试、可回放。

建议记录：

- `sessionId`
- `messageId`
- `intent`
- `llmDecision`
- `selectedTool`
- `toolInput`
- `toolOutput`
- `status`
- `error`
- `duration`

第一版可以先打日志。后续可扩展为：

- `agent_trace`
- `agent_tool_call_log`

这对调试 LLM 决策、复盘错误调用、解释用户操作都很重要。

### 失败后可恢复

Tool 调用失败时，Agent 不应直接暴露异常堆栈，而应转成可理解、可继续的响应。

示例：

```text
用户：把工单 12 转给 99
Tool 返回：ASSIGNEE_NOT_FOUND
Agent 回复：处理人 99 不存在或已禁用，请提供有效 STAFF 用户 ID。
```

建议流程：

```text
BusinessException
  -> AgentToolResult(status = FAILED, errorCode, errorMessage)
  -> ResponseBuilder / LLM summary
  -> 用户可理解回复
```

### 保留规则 fallback

即使接入 LLM，也应保留当前 `IntentRouter`。

推荐策略：

```text
LLM 可用：
  LLM 识别意图和抽取参数
  代码校验
  Tool 执行

LLM 不可用：
  IntentRouter 规则路由
  简单参数抽取
  Tool 执行
```

这能保证 Agent 在模型不可用、输出不合法或超时时仍具备基本可用性。

## 不建议照搬的内容

Claude Code 面向代码仓库，本项目不应照搬以下内容：

- 不做复杂终端权限模型
- 不做仓库文件编辑工作流
- 不过早引入多 Agent
- 不让 Agent 自由循环执行大量步骤
- 不把业务流程完全交给模型自主决策

本项目优先级始终是：

```text
业务正确性 > 权限安全 > 可解释 > 智能化
```

## 对后续阶段的落地建议

阶段 7：Tool 工具落地

- 定义 `AgentTool`
- 定义 `AgentToolRegistry`
- 定义 `AgentToolRequest`
- 定义 `AgentToolResult`
- 定义 `AgentToolStatus`
- 定义 `ToolRiskLevel`
- 拆出 `QueryTicketTool`、`CreateTicketTool`、`TransferTicketTool`、`SearchHistoryTool`

阶段 8：LLM 接入与 Prompt 层

- LLM 只做意图识别、参数抽取、澄清问题生成和结果总结
- LLM 输出必须经过代码校验
- LLM 不直接执行写操作
- 规则路由作为 fallback

阶段 9：单 Agent 编排

- 实现 `TicketAgentOrchestrator`
- 使用 Planning -> Action -> Observation -> Response 流程
- 限制每轮 Tool 调用次数
- 工具执行必须可观测、可恢复、可审计

阶段 10：上下文会话增强

- 增加 pending action
- 支持缺参追问
- 支持指代消解
- 支持最近 Tool 结果引用

阶段 11 到 12：知识构建与 RAG

- RAG 只用于历史经验检索
- 不替代当前工单事实查询
- 不参与权限判断
- 不强制阻止工单创建
