# Agent 模块按类到方法级别的改造清单

## 1. 文档目的

这份清单用于指导 `smart-ticket-agent` 模块重构，目标不是重写一套“更复杂的 Agent”，而是把当前实现收敛成一套：

- 可读
- 可控
- 可审计
- 可测试
- 可继续扩展

的后端 Agent 编排方案。

这份清单默认遵循两个前提：

- 现有 `Tool`、`Guard`、`Session`、`Memory`、`Trace` 分层保留，不推倒重来。
- ReAct 只保留在只读场景，不再作为所有意图的统一执行主链。

---

## 2. 重构原则

### 2.1 代码可读性约束

- `AgentFacade` 只保留薄入口，不再承载整条执行链。
- 单个类只负责一个主题：路由、策略、执行、状态、渲染、追踪分开。
- 单个公开方法优先控制在 40-60 行内，超过则继续拆分。
- 不允许在一个方法里同时做：
  - 路由判断
  - tool 执行
  - 状态落库
  - reply 拼装
  - trace 写入
- 最终回复只能由结构化结果渲染，不直接返回 LLM 原始输出。

### 2.2 行为约束

- `QUERY_TICKET`、`SEARCH_HISTORY`：允许只读 ReAct。
- `CREATE_TICKET`：允许 LLM 辅助填参，但执行必须走确定性链路。
- `TRANSFER_TICKET`：完全确定性，不允许 ReAct 自主执行。
- 任何分支只要出现 `NEED_MORE_INFO` 或 `NEED_CONFIRMATION`，都必须统一落 `pendingAction`。
- 同一轮对话只允许一次 session/memory commit。

---

## 3. 目标包结构

建议新增或调整为以下结构：

```text
com.smartticket.agent
├─ execution
│  ├─ AgentExecutionMode.java
│  ├─ AgentExecutionPolicy.java
│  ├─ AgentExecutionPolicyService.java
│  ├─ DeterministicToolExecutor.java
│  └─ PendingActionCoordinator.java
├─ orchestration
│  ├─ AgentOrchestrator.java
│  ├─ AgentTurnState.java
│  └─ AgentExecutionSummary.java
├─ react
│  ├─ AgentObservation.java
│  ├─ AgentReadOnlyReactExecutor.java
│  └─ AgentReactToolCatalog.java
├─ reply
│  └─ AgentReplyRenderer.java
└─ service
   └─ AgentFacade.java
```

说明：

- `service.AgentFacade` 只作为 API 调用入口保留。
- 真正的主流程迁移到 `orchestration.AgentOrchestrator`。
- `react` 包只处理只读 ReAct。
- `execution` 包负责模式判定、pending action、确定性执行。
- `reply` 包只负责最终回复生成。

---

## 4. 类到方法级别改造清单

## 4.1 `AgentFacade`

文件：

- `smart-ticket-agent/src/main/java/com/smartticket/agent/service/AgentFacade.java`

处理策略：

- 保留
- 大幅瘦身

目标职责：

- 对外保留 `chat(...)` 入口
- 加载基础上下文
- 委派给 `AgentOrchestrator`
- 返回 `AgentChatResult`

建议保留的方法：

```java
public AgentChatResult chat(CurrentUser currentUser, String sessionId, String message)
```

建议删除或迁移的方法：

- `agentReActLoop(...)` -> 迁移到 `AgentReadOnlyReactExecutor`
- `tryNonStreamingAgentCall(...)` -> 迁移到 `AgentReadOnlyReactExecutor`
- `executeDeterministicFallback(...)` -> 迁移到 `DeterministicToolExecutor`
- `continuePendingConfirmation(...)` -> 迁移到 `PendingActionCoordinator`
- `continuePendingCreate(...)` -> 迁移到 `PendingActionCoordinator`
- `buildConversationContext(...)` -> 拆分到 `AgentReadOnlyReactExecutor`
- `agentSystemPrompt(...)` -> 若仍保留 LLM，只保留只读场景 prompt 组装
- `syncCreatePendingAction(...)` -> 迁移到 `PendingActionCoordinator`
- `buildConfirmationResult(...)` -> 迁移到 `PendingActionCoordinator`
- `updateSessionAfterTool(...)` -> 改成统一 commit 逻辑，迁移到 `AgentOrchestrator`

验收标准：

- `AgentFacade` 总行数显著下降
- 私有方法数量大幅减少
- 除 `chat(...)` 外不再承载主流程细节

---

## 4.2 `AgentOrchestrator`

文件：

- 新增 `smart-ticket-agent/src/main/java/com/smartticket/agent/orchestration/AgentOrchestrator.java`

目标职责：

- 编排整轮对话
- 串起 route、policy、pending、execute、render、persist、trace

建议方法：

```java
public AgentChatResult handle(CurrentUser currentUser, String sessionId, String message)
```

```java
private AgentSessionContext loadContext(String sessionId)
```

```java
private IntentRoute route(String message, AgentSessionContext context)
```

```java
private AgentExecutionMode decideMode(CurrentUser currentUser, IntentRoute route)
```

```java
private AgentExecutionSummary handlePendingIfNecessary(
        CurrentUser currentUser,
        String sessionId,
        String message,
        AgentSessionContext context,
        AgentTraceContext trace
)
```

```java
private AgentExecutionSummary executeMainFlow(
        CurrentUser currentUser,
        String sessionId,
        String message,
        AgentSessionContext context,
        IntentRoute route,
        AgentPlan plan,
        AgentExecutionMode mode,
        AgentTraceContext trace
)
```

```java
private void commitTurn(
        CurrentUser currentUser,
        String sessionId,
        AgentSessionContext context,
        IntentRoute route,
        AgentExecutionSummary summary
)
```

```java
private AgentChatResult buildChatResult(
        String sessionId,
        IntentRoute route,
        AgentSessionContext context,
        AgentPlan plan,
        AgentExecutionSummary summary,
        AgentTraceContext trace
)
```

验收标准：

- 对话主链只在这一个类里可读地串起来
- 每一步都是显式命名的方法
- 不出现 100 行以上的单方法

---

## 4.3 `AgentExecutionMode`

文件：

- 新增 `smart-ticket-agent/src/main/java/com/smartticket/agent/execution/AgentExecutionMode.java`

目标职责：

- 明确当前意图采用哪种执行模式

建议枚举值：

```java
public enum AgentExecutionMode {
    READ_ONLY_REACT,
    WRITE_DETERMINISTIC,
    HIGH_RISK_CONFIRMATION
}
```

验收标准：

- 主链不再通过布尔分支隐式推断执行方式

---

## 4.4 `AgentExecutionPolicy` 与 `AgentExecutionPolicyService`

文件：

- 新增 `smart-ticket-agent/src/main/java/com/smartticket/agent/execution/AgentExecutionPolicy.java`
- 新增 `smart-ticket-agent/src/main/java/com/smartticket/agent/execution/AgentExecutionPolicyService.java`

目标职责：

- 根据 `intent`、用户权限、风险等级，决定执行模式和可用工具集

建议字段：

```java
public class AgentExecutionPolicy {
    private AgentExecutionMode mode;
    private List<AgentSkill> allowedSkills;
    private boolean allowReact;
    private boolean allowAutoExecute;
}
```

建议方法：

```java
public AgentExecutionPolicy resolve(CurrentUser currentUser, IntentRoute route)
```

```java
private AgentExecutionPolicy readOnlyPolicy(CurrentUser currentUser, IntentRoute route)
```

```java
private AgentExecutionPolicy createTicketPolicy(CurrentUser currentUser, IntentRoute route)
```

```java
private AgentExecutionPolicy transferTicketPolicy(CurrentUser currentUser, IntentRoute route)
```

```java
private List<AgentSkill> loadAllowedSkills(CurrentUser currentUser, AgentIntent intent, ToolRiskLevel maxRisk)
```

验收标准：

- 不再出现 `allTools()` 无差别暴露所有 tool
- `requiredPermissions` 和 `findAvailable(...)` 真正参与运行时决策

---

## 4.5 `AgentReadOnlyReactExecutor`

文件：

- 新增 `smart-ticket-agent/src/main/java/com/smartticket/agent/react/AgentReadOnlyReactExecutor.java`

目标职责：

- 只读意图的 ReAct 执行
- 收集 observation
- 不直接改 session
- 不直接返回最终用户回复

建议方法：

```java
public AgentExecutionSummary execute(
        CurrentUser currentUser,
        String message,
        AgentSessionContext context,
        IntentRoute route,
        AgentPlan plan,
        List<AgentSkill> allowedSkills,
        AgentTraceContext trace
)
```

```java
private Object[] allowedTools(List<AgentSkill> allowedSkills)
```

```java
private String buildSystemPrompt(IntentRoute route, AgentSessionContext context)
```

```java
private String buildUserPrompt(String message, AgentSessionContext context, IntentRoute route)
```

```java
private List<AgentObservation> collectObservations(SpringAiToolCallState callState)
```

```java
private AgentToolResult selectPrimaryResult(List<AgentObservation> observations)
```

```java
private String buildModelSummary(ChatClient chatClient, List<AgentObservation> observations, IntentRoute route)
```

重要约束：

- 不允许直接把 streaming 的 `llmOutput` 返回给用户
- `llmOutput` 若保留，只能作为 trace/debug 信息
- 最终面向用户的文本必须通过 `AgentReplyRenderer`

验收标准：

- ReAct 只接受只读工具白名单
- 输出只有 observation / primary result / optional summary

---

## 4.6 `AgentObservation`

文件：

- 新增 `smart-ticket-agent/src/main/java/com/smartticket/agent/react/AgentObservation.java`

目标职责：

- 描述一次 tool 调用结果

建议字段：

```java
private String toolName;
private AgentToolStatus status;
private String reply;
private Object data;
private Long activeTicketId;
private Long activeAssigneeId;
```

建议方法：

```java
public static AgentObservation from(String toolName, AgentToolResult result)
```

验收标准：

- ReAct 路径不再直接操作 `SpringAiToolCallState.AgentToolCallRecord`
- observation 成为统一中间模型

---

## 4.7 `AgentReactToolCatalog`

文件：

- 新增 `smart-ticket-agent/src/main/java/com/smartticket/agent/react/AgentReactToolCatalog.java`

目标职责：

- 根据 policy 构造当前轮次允许暴露给模型的 tool 集合

建议方法：

```java
public Object[] buildTools(List<AgentSkill> allowedSkills)
```

```java
public boolean canExpose(AgentSkill skill)
```

验收标准：

- `AgentFacade` 不再自己拼 tool 列表
- tool 暴露逻辑集中管理

---

## 4.8 `DeterministicToolExecutor`

文件：

- 新增 `smart-ticket-agent/src/main/java/com/smartticket/agent/execution/DeterministicToolExecutor.java`

目标职责：

- 确定性执行写操作
- 统一调用 `parameterExtractor`、`resolveReferences`、`executionGuard`

建议方法：

```java
public AgentExecutionSummary execute(
        CurrentUser currentUser,
        String message,
        AgentSessionContext context,
        IntentRoute route,
        AgentPlan plan,
        AgentTraceContext trace
)
```

```java
private AgentToolParameters extractParameters(String message, AgentSessionContext context)
```

```java
private ToolCallPlan buildToolCallPlan(IntentRoute route, AgentTool tool, AgentToolParameters parameters)
```

```java
private AgentToolResult executeAllowedTool(
        CurrentUser currentUser,
        String message,
        AgentSessionContext context,
        IntentRoute route,
        AgentTool tool,
        AgentToolParameters parameters
)
```

```java
private AgentExecutionSummary toSummary(
        IntentRoute route,
        AgentPlan plan,
        AgentToolParameters parameters,
        AgentToolResult result
)
```

验收标准：

- `CREATE_TICKET`、`TRANSFER_TICKET` 统一走这一层
- fallback 不再是“另一套主链”，而是正式写链路

---

## 4.9 `PendingActionCoordinator`

文件：

- 新增 `smart-ticket-agent/src/main/java/com/smartticket/agent/execution/PendingActionCoordinator.java`

目标职责：

- 统一维护 `pendingAction`
- 处理补参续接
- 处理确认续接

建议方法：

```java
public boolean hasPendingAction(AgentSessionContext context)
```

```java
public AgentExecutionSummary continuePendingAction(
        CurrentUser currentUser,
        String sessionId,
        String message,
        AgentSessionContext context,
        AgentTraceContext trace
)
```

```java
private AgentExecutionSummary continuePendingConfirmation(
        CurrentUser currentUser,
        String sessionId,
        String message,
        AgentSessionContext context,
        AgentPendingAction pendingAction,
        AgentTraceContext trace
)
```

```java
private AgentExecutionSummary continuePendingCreateDraft(
        CurrentUser currentUser,
        String sessionId,
        String message,
        AgentSessionContext context,
        AgentPendingAction pendingAction,
        AgentTraceContext trace
)
```

```java
public void syncPendingAction(
        AgentSessionContext context,
        IntentRoute route,
        AgentToolParameters parameters,
        AgentToolResult result,
        String message
)
```

```java
private AgentPendingAction buildConfirmationPendingAction(...)
```

```java
private AgentPendingAction buildCreateDraftPendingAction(...)
```

```java
private boolean isConfirmMessage(String message)
```

```java
private boolean isCancelMessage(String message)
```

验收标准：

- 不再分别在 fallback / ReAct 路径里散落 pending action 逻辑
- `pendingAction` 的创建、恢复、清空只有这一处负责

---

## 4.10 `AgentReplyRenderer`

文件：

- 新增 `smart-ticket-agent/src/main/java/com/smartticket/agent/reply/AgentReplyRenderer.java`

目标职责：

- 基于结构化执行结果生成最终回复

建议方法：

```java
public String render(
        IntentRoute route,
        AgentPlan plan,
        AgentSessionContext context,
        AgentExecutionSummary summary
)
```

```java
private String renderToolReply(AgentExecutionSummary summary)
```

```java
private String renderReadOnlySummary(IntentRoute route, AgentExecutionSummary summary)
```

```java
private String renderNeedMoreInfo(AgentExecutionSummary summary)
```

```java
private String renderNeedConfirmation(AgentExecutionSummary summary)
```

```java
private String renderFailure(AgentExecutionSummary summary)
```

设计约束：

- 主输入必须是 `AgentToolResult` / `AgentObservation`
- 不以 `llmOutput` 为最终返回值
- 可以在只读查询场景中拼接更友好的总结，但事实部分必须来自 tool result

验收标准：

- 最终回复可以解释清楚来源
- 不会把中间推理内容直接展示给用户

---

## 4.11 `AgentTurnState`

文件：

- 新增 `smart-ticket-agent/src/main/java/com/smartticket/agent/orchestration/AgentTurnState.java`

目标职责：

- 保存一轮对话运行中的临时状态

建议字段：

```java
private AgentSessionContext context;
private IntentRoute route;
private AgentPlan plan;
private AgentExecutionPolicy policy;
private AgentTraceContext trace;
```

作用：

- 避免主流程参数列表越来越长
- 让 orchestrator 方法签名保持可读

验收标准：

- 主链方法签名不再出现大量重复参数

---

## 4.12 `AgentExecutionSummary`

文件：

- 新增 `smart-ticket-agent/src/main/java/com/smartticket/agent/orchestration/AgentExecutionSummary.java`

目标职责：

- 统一描述本轮执行结果

建议字段：

```java
private AgentToolParameters parameters;
private AgentToolResult primaryResult;
private List<AgentObservation> observations;
private String renderedReply;
private boolean springAiUsed;
private boolean fallbackUsed;
```

建议方法：

```java
public boolean hasObservation()
```

```java
public boolean needPersistPendingAction()
```

```java
public Long activeTicketId()
```

```java
public Long activeAssigneeId()
```

验收标准：

- session update、memory update、reply render、trace finish 统一读取同一个 summary

---

## 4.13 `AgentPlanner`

文件：

- `smart-ticket-agent/src/main/java/com/smartticket/agent/planner/AgentPlanner.java`

处理策略：

- 保留
- 职责收缩

目标职责：

- 管理计划状态机
- 不再承担 ReAct 细节追踪

建议保留的方法：

```java
public AgentPlan buildOrLoadPlan(AgentSessionContext context, IntentRoute route)
```

```java
public void markClarify(AgentPlan plan, String summary)
```

```java
public void markNeedConfirmation(AgentPlan plan, String summary)
```

```java
public void afterTool(AgentPlan plan, AgentToolResult result)
```

建议新增的方法：

```java
public void markReadOnlyReact(AgentPlan plan)
```

```java
public void markWaitingForSlots(AgentPlan plan, List<AgentToolParameterField> missingFields)
```

```java
public void markCompleted(AgentPlan plan)
```

建议删除或弱化的方法：

- `recordToolCalls(...)`
  - 不建议继续在 planner 里记录 ReAct 调用链
  - ReAct 调用链应该由 trace/observation 负责

验收标准：

- planner 变成纯状态推进器
- planner 单测不再依赖 Spring AI 调用细节

---

## 4.14 `SpringAiToolSupport`

文件：

- `smart-ticket-agent/src/main/java/com/smartticket/agent/tool/support/SpringAiToolSupport.java`

处理策略：

- 保留
- 调整执行契约

目标职责：

- 仅做 Spring AI tool calling 适配
- 不负责执行策略决策

必须修改的方法：

```java
public AgentToolResult execute(
        AgentTool tool,
        ToolContext toolContext,
        AgentIntent intent,
        AgentToolParameters parameters
)
```

改造要求：

- `intent` 不能再由当前 tool 自己定义执行边界
- 必须从 `toolContext` 读取“原始 route intent”和“允许工具列表”
- 如果当前 tool 不在允许列表中，直接拒绝执行

建议新增的方法：

```java
private boolean isAllowedTool(ToolContext toolContext, AgentTool tool)
```

```java
private IntentRoute requireOriginalRoute(ToolContext toolContext)
```

```java
private List<String> allowedToolNames(ToolContext toolContext)
```

验收标准：

- ReAct 不可能越权调用当前轮次未授权的 tool

---

## 4.15 `AgentContextUpdater`

文件：

- `smart-ticket-agent/src/main/java/com/smartticket/agent/orchestration/AgentContextUpdater.java`

处理策略：

- 保留
- 改为接收聚合后的 summary

建议新增方法：

```java
public void apply(
        AgentSessionContext context,
        IntentRoute route,
        String message,
        AgentExecutionSummary summary
)
```

建议删除或替换的方法：

- 当前基于单个 `AgentToolResult` 的 `apply(...)`

改造要求：

- 同一轮消息只追加一次
- `activeTicketId` 和 `activeAssigneeId` 取聚合结果
- 不再在 ReAct 多工具循环内重复更新

验收标准：

- `recentMessages` 不重复
- 一轮一写

---

## 4.16 `AgentMemoryService`

文件：

- `smart-ticket-agent/src/main/java/com/smartticket/agent/memory/AgentMemoryService.java`

处理策略：

- 保留
- 让写入入口接受聚合结果

建议新增方法：

```java
public void remember(
        CurrentUser currentUser,
        AgentSessionContext context,
        IntentRoute route,
        AgentExecutionSummary summary
)
```

改造要求：

- 参数来源统一从 `summary.getParameters()`
- 结果来源统一从 `summary.getPrimaryResult()`
- 不再在 ReAct 多步调用中重复写 memory

验收标准：

- memory 更新只发生一次
- ReAct 和确定性链路共用一套 memory 写法

---

## 4.17 `AgentTraceService`

文件：

- `smart-ticket-agent/src/main/java/com/smartticket/agent/trace/AgentTraceService.java`

处理策略：

- 保留
- 模型从“保存 reasoning”转为“保存 observation 和结果”

建议新增方法：

```java
public void recordObservations(AgentTraceContext context, List<AgentObservation> observations)
```

```java
public void finish(
        AgentTraceContext context,
        IntentRoute route,
        AgentPlan plan,
        AgentExecutionSummary summary
)
```

建议删除或弱化的方法：

- `recordReasoning(...)`
  - 建议降为 debug 能力，默认不持久化

改造要求：

- trace 面向排障和审计
- 不把中间推理作为核心持久化内容
- `reasoning_json` 要么彻底补全 schema，要么删掉整条链路

验收标准：

- trace 字段和 schema 严格一致
- 一次 finish 足以还原主路径

---

## 4.18 `SkillDefinitionLoader`

文件：

- `smart-ticket-agent/src/main/java/com/smartticket/agent/skill/SkillDefinitionLoader.java`

处理策略：

- 保留
- 增强校验

建议新增方法：

```java
private void validateDefinition(SkillDefinition definition, Resource resource)
```

```java
private void requireText(String value, String fieldName, Resource resource)
```

```java
private void requireNonEmptyIntents(SkillDefinition definition, Resource resource)
```

改造要求：

- 对 `skillCode`
- `skillName`
- `toolBeanName`
- `supportedIntents`
- `riskLevel`

做显式校验

验收标准：

- skill frontmatter 出错时启动期就能暴露问题
- 不再静默加载出半残 skill

---

## 4.19 `AgentToolParameterExtractor`

文件：

- `smart-ticket-agent/src/main/java/com/smartticket/agent/tool/parameter/AgentToolParameterExtractor.java`

处理策略：

- 保留
- 只负责规则提参，不承担补参状态机

建议新增方法：

```java
public AgentToolParameters extractForCreate(String message, AgentSessionContext context)
```

```java
public AgentToolParameters extractForTransfer(String message, AgentSessionContext context)
```

```java
public AgentToolParameters extractForQuery(String message, AgentSessionContext context)
```

作用：

- 降低单个 `extract(...)` 的复杂度
- 让不同意图的提参逻辑更直观

验收标准：

- 提参逻辑按意图拆分
- 更容易单测和调试

---

## 5. 测试改造清单

## 5.1 需要新增的测试类

- `AgentExecutionPolicyServiceTest`
- `AgentReadOnlyReactExecutorTest`
- `PendingActionCoordinatorTest`
- `DeterministicToolExecutorTest`
- `AgentReplyRendererTest`
- `AgentOrchestratorTest`

## 5.2 需要重写或增强的现有测试

- `AgentFacadeTest`
  - 改成只验证入口委派和结果回传
- `SkillRegistryTest`
  - 增加权限和风险过滤的运行时验证
- `AgentTraceServiceTest`
  - 校验新的 trace finish 契约
- `AgentMemoryServiceTest`
  - 校验基于 `AgentExecutionSummary` 的记忆更新

## 5.3 必测场景

- 只读 ReAct 成功调用 1-2 个只读工具
- ReAct 尝试调用写工具时被拒绝
- 创建工单缺参进入 `pendingAction`
- 创建工单补参后成功落单
- 转派工单必须先确认
- 同一轮多工具调用后 `recentMessages` 只追加一次
- 最终 reply 不包含中间 reasoning 文本
- trace / mapper / schema 字段一致

---

## 6. 推荐实施顺序

### 第一阶段：先修基础一致性

- [ ] 修复 `agent/skills/*.md` frontmatter
- [ ] 强化 `SkillDefinitionLoader` 校验
- [ ] 统一 trace entity / mapper / schema 字段
- [ ] 补基线测试

### 第二阶段：拆主链

- [ ] 新增 `AgentExecutionMode`
- [ ] 新增 `AgentExecutionPolicyService`
- [ ] 新增 `AgentOrchestrator`
- [ ] 瘦身 `AgentFacade`

### 第三阶段：拆执行路径

- [ ] 新增 `DeterministicToolExecutor`
- [ ] 新增 `PendingActionCoordinator`
- [ ] 新增 `AgentReadOnlyReactExecutor`
- [ ] 新增 `AgentReactToolCatalog`

### 第四阶段：统一输出与状态

- [ ] 新增 `AgentExecutionSummary`
- [ ] 新增 `AgentReplyRenderer`
- [ ] 改造 `AgentContextUpdater`
- [ ] 改造 `AgentMemoryService`
- [ ] 改造 `AgentTraceService`

### 第五阶段：收尾

- [ ] 删除 `AgentFacade` 中已迁移的私有方法
- [ ] 删除 planner 中不再需要的 ReAct 细节记录
- [ ] 清理无用字段和注释
- [ ] 完成回归测试

---

## 7. 最终验收标准

- `AgentFacade` 成为薄入口类。
- ReAct 只存在于只读查询类意图。
- 写操作全部走确定性执行。
- `pendingAction` 只有一个统一入口。
- 最终回复由 `AgentReplyRenderer` 生成。
- session 和 memory 每轮只提交一次。
- trace 不再依赖 LLM 中间推理文本。
- schema、entity、mapper、代码完全一致。
- `mvn -q -pl smart-ticket-agent -am test` 可以稳定通过。

---

## 8. 直接执行建议

如果按最稳妥的顺序推进，建议你按下面的提交批次做：

1. `docs + skill loader + schema consistency`
2. `execution policy + orchestrator skeleton`
3. `deterministic executor + pending action coordinator`
4. `read-only react executor + tool catalog`
5. `reply renderer + execution summary`
6. `context/memory/trace unification`
7. `test refactor and cleanup`

这套顺序的优点是：

- 每一步都可回归
- 不会一上来就重写大类
- 出问题时容易回滚和定位

