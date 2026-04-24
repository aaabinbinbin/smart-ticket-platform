# 智能工单平台 Agent 模块重构实施方案

> 适用项目：`smart-ticket-platform`  
> 适用模块：`smart-ticket-agent`、`smart-ticket-api` 中的 Agent Controller 入口  
> 目标用途：指导当前 Agent 模块重构，支撑 Java 后端实习与 Agent 开发实习项目展示  
> 推荐执行方式：先按 P0/P1/P2 完成主链重构，再做流式输出和高压治理增强

---

## 1. 重构目标

本次重构不是为了把项目改成一个“通用 Agent 框架”，而是要把智能工单平台中的 Agent 模块收敛成一套 **业务边界清晰、写操作安全、只读推理可控、工程上可测试、运行时可审计、压力下可降级** 的后端 Agent 编排层。

最终希望形成的能力是：

```text
用户自然语言输入
  -> 会话加载 / Session Lock / 限流
  -> 意图识别 IntentRouter
  -> 执行策略 AgentExecutionPolicy
  -> 计划状态 AgentPlanner
  -> 补槽 / 确认 PendingActionCoordinator
  -> 只读 ReAct 或确定性 Command 执行
  -> 统一结果 AgentExecutionSummary
  -> 统一回复 AgentReplyRenderer
  -> Session / Memory / Trace 一次性提交
  -> 同步返回或 SSE 流式返回
```

核心原则：

```text
1. Agent 不是直接改库的执行者，而是自然语言到业务命令的编排层。
2. LLM 可以参与意图识别、参数补全、只读总结，但不能越权触发高风险写操作。
3. 查询、历史案例检索、诊断建议可以使用只读 ReAct。
4. 创建工单、转派工单、关闭工单、修改优先级等写操作必须走确定性 Command 链路。
5. 所有 NEED_MORE_INFO / NEED_CONFIRMATION 都必须统一进入 pendingAction。
6. 同一轮对话只允许一次 session / memory / trace commit。
7. 流式输出只改变响应体验，不改变业务执行语义。
8. 高压场景下优先保证核心工单链路可用，LLM/RAG 能力可以降级。
```

---

## 2. 当前问题总结

当前 `docs/agent-refactor-checklist.md` 已经识别出主问题：`AgentFacade` 承载过多职责，需要瘦身；只读 ReAct 和写操作确定性链路需要拆开；`pendingAction`、reply、trace、memory、session commit 需要统一。

进一步从工程、业务、可读性和稳定性角度看，当前还需要补充以下问题：

```text
1. AgentFacade 主链过重，路由、执行、fallback、pending、reply、trace 混在一起。
2. 写操作目前容易被称作 fallback，但对业务 Agent 来说，确定性写链路应该是主链。
3. pendingAction 需要成为补参、确认、取消的唯一状态入口。
4. 缺少 AgentTurnStatus / AgentExecutionSummary 这样的统一结果契约。
5. Spring AI Tool Calling 应该只是适配层，不能承担执行策略判断。
6. Controller 当前只有同步一次性返回接口，还未设计 SSE 流式输出协议。
7. 高压访问下缺少 user/session/LLM/RAG/SSE 维度的限流与隔离。
8. 同一个 sessionId 并发请求可能造成 pendingAction、activeTicketId、recentMessages 串写。
9. 缺少 Agent 调用预算，例如每轮最大 LLM 次数、tool 次数、RAG 次数、总耗时。
10. 缺少统一错误码、降级策略、压测指标和可观测性指标。
```

---

## 3. 目标架构

### 3.1 总体分层

```text
smart-ticket-api
└─ controller.agent
   ├─ AgentController                 同步接口 /api/agent/chat
   └─ AgentStreamController 或复用 AgentController
                                      流式接口 /api/agent/chat/stream

smart-ticket-agent
├─ service
│  └─ AgentFacade                     对外薄入口
│
├─ orchestration
│  ├─ AgentOrchestrator               单轮对话主编排
│  ├─ AgentTurnState                  单轮运行时状态
│  ├─ AgentTurnStatus                 本轮最终状态
│  ├─ AgentExecutionSummary           统一执行结果
│  └─ AgentContextUpdater             Session 上下文更新
│
├─ policy
│  ├─ AgentExecutionMode              执行模式
│  ├─ AgentExecutionPolicy            执行策略结果
│  ├─ AgentExecutionPolicyService     根据 intent/权限/风险决策
│  └─ ToolExposurePolicy              工具暴露策略
│
├─ command
│  ├─ AgentCommandType                业务命令类型
│  ├─ AgentCommandDraft               写操作草稿
│  ├─ AgentCommandExecutor            命令执行接口
│  ├─ CreateTicketCommandHandler      创建工单命令处理器
│  ├─ TransferTicketCommandHandler    转派工单命令处理器
│  └─ CloseTicketCommandHandler       后续可扩展
│
├─ execution
│  ├─ ReadOnlyReactExecutor           只读 ReAct 执行器
│  ├─ DeterministicCommandExecutor    确定性写命令执行器
│  ├─ PendingActionCoordinator        补参/确认/取消统一入口
│  ├─ AgentObservation                Tool 观察结果
│  └─ AgentReactToolCatalog           当前轮允许暴露的只读工具集
│
├─ slot
│  ├─ SlotFillingService              缺参判断
│  ├─ SlotMergeService                多轮参数合并
│  ├─ SlotRequirement                 各意图必填字段定义
│  └─ SlotValidationResult            补槽校验结果
│
├─ stream
│  ├─ AgentEventSink                  事件输出接口
│  ├─ NoopAgentEventSink              同步接口使用的空实现
│  ├─ SseAgentEventSink               SSE 实现
│  ├─ AgentStreamEvent                流式事件模型
│  └─ AgentStreamEventType            accepted/route/status/delta/final/error
│
├─ resilience
│  ├─ AgentRateLimitService           用户级/全局限流
│  ├─ AgentSessionLockService         session 级互斥锁
│  ├─ AgentTurnBudgetService          每轮预算控制
│  ├─ AgentDegradePolicyService       降级策略
│  └─ AgentBulkheadConfig             LLM/RAG/fast pool 隔离配置
│
├─ reply
│  ├─ AgentReplyRenderer              统一回复渲染
│  ├─ ReplyTemplateType               回复模板类型
│  └─ ReplySourceType                 TOOL_RESULT / OBSERVATION / LLM_SUMMARY
│
├─ router
├─ planner
├─ memory
├─ trace
├─ prompt
├─ skill
├─ tool
└─ model
```

### 3.2 模块边界

```text
AgentFacade
  只保留对外入口，不写主流程细节。

AgentOrchestrator
  只负责编排，不写具体业务规则，不直接调用数据库 mapper。

AgentExecutionPolicyService
  决定本轮走什么执行模式、允许暴露哪些工具、是否需要确认、是否允许自动执行。

ReadOnlyReactExecutor
  只处理 QUERY_TICKET、SEARCH_HISTORY、DIAGNOSE_TICKET 等只读场景。

DeterministicCommandExecutor
  处理 CREATE_TICKET、TRANSFER_TICKET 等写操作。

PendingActionCoordinator
  是 pendingAction 的唯一创建、恢复、清理入口。

AgentReplyRenderer
  只负责把结构化 summary 转成用户可读回复，不执行工具、不查库、不写 trace。

AgentTraceService
  记录路由、策略、工具、结果、pending、错误码、耗时等决策事实，不保存模型 chain-of-thought。

AgentEventSink
  只负责向前端输出事件，同步接口用空实现，流式接口用 SSE 实现。
```

---

## 4. 核心模型设计

### 4.1 AgentTurnStatus

```java
public enum AgentTurnStatus {
    COMPLETED,
    NEED_MORE_INFO,
    NEED_CONFIRMATION,
    CANCELLED,
    DEGRADED,
    FAILED
}
```

含义：

```text
COMPLETED           本轮已完成
NEED_MORE_INFO      缺少必要参数，已写入 SLOT_FILLING pending
NEED_CONFIRMATION   高风险操作待确认，已写入 HIGH_RISK_CONFIRMATION pending
CANCELLED           用户取消 pending 操作
DEGRADED            LLM/RAG 等能力降级后返回
FAILED              本轮失败，返回 errorCode 和 traceId
```

### 4.2 AgentExecutionMode

```java
public enum AgentExecutionMode {
    CLARIFICATION,
    READ_ONLY_REACT,
    READ_ONLY_DETERMINISTIC,
    WRITE_COMMAND_DRAFT,
    WRITE_COMMAND_EXECUTE,
    HIGH_RISK_CONFIRMATION,
    PENDING_CONTINUATION
}
```

说明：

```text
CLARIFICATION              低置信度或无法识别意图，需要澄清
READ_ONLY_REACT            允许 LLM 使用只读工具进行查询/检索/总结
READ_ONLY_DETERMINISTIC    不使用 LLM，直接走确定性查询
WRITE_COMMAND_DRAFT        写操作参数不完整，只生成草稿和 pending
WRITE_COMMAND_EXECUTE      参数完整且风险允许，执行命令
HIGH_RISK_CONFIRMATION     高风险写操作必须先确认
PENDING_CONTINUATION       当前消息用于继续之前的补参或确认
```

### 4.3 AgentExecutionPolicy

```java
public class AgentExecutionPolicy {
    private AgentExecutionMode mode;
    private List<String> allowedToolNames;
    private boolean allowReact;
    private boolean allowAutoExecute;
    private boolean requireConfirmation;
    private ToolRiskLevel maxRiskLevel;
    private Duration timeout;
    private int maxLlmCalls;
    private int maxToolCalls;
    private int maxRagCalls;
}
```

### 4.4 AgentExecutionSummary

```java
public class AgentExecutionSummary {
    private AgentTurnStatus status;
    private AgentExecutionMode mode;
    private AgentIntent intent;

    private AgentToolParameters parameters;
    private AgentCommandDraft commandDraft;
    private AgentToolResult primaryResult;
    private List<AgentObservation> observations;

    private AgentPendingAction pendingAction;
    private String renderedReply;

    private boolean llmUsed;
    private boolean springAiUsed;
    private boolean toolCalled;
    private boolean fallbackUsed;
    private boolean degraded;

    private String failureCode;
    private String failureReason;

    public boolean isSuccess() {
        return status == AgentTurnStatus.COMPLETED || status == AgentTurnStatus.DEGRADED;
    }

    public boolean needsPendingPersist() {
        return status == AgentTurnStatus.NEED_MORE_INFO
            || status == AgentTurnStatus.NEED_CONFIRMATION;
    }
}
```

设计目的：

```text
1. session update、memory update、trace finish、reply render 都只读这个 summary。
2. 不让各执行分支分别拼 AgentChatResult。
3. 统一表达成功、缺参、确认、取消、降级、失败。
4. 支持后续同步返回和流式 final 事件复用同一个结果。
```

### 4.5 AgentObservation

```java
public class AgentObservation {
    private String toolName;
    private AgentToolStatus status;
    private String reply;
    private Object data;
    private Long activeTicketId;
    private Long activeAssigneeId;
    private long latencyMs;
    private String errorCode;
}
```

作用：

```text
1. 统一表达一次只读 tool 调用结果。
2. ReAct 路径不直接操作 Spring AI 内部 tool call record。
3. Trace 记录 observation，而不是记录模型推理文本。
```

---

## 5. 执行策略矩阵

| Intent | 场景 | 默认执行模式 | 是否允许 ReAct | 是否允许写操作自动执行 | 是否需要确认 | 允许工具 |
|---|---|---|---:|---:|---:|---|
| QUERY_TICKET | 查询工单 | READ_ONLY_REACT 或 READ_ONLY_DETERMINISTIC | 是 | 否 | 否 | queryTicket |
| SEARCH_HISTORY | 检索历史案例 | READ_ONLY_REACT | 是 | 否 | 否 | searchHistory |
| DIAGNOSE_TICKET | 诊断建议，后续新增 | READ_ONLY_REACT | 是 | 否 | 否 | queryTicket, searchHistory |
| CREATE_TICKET | 创建工单，参数不足 | WRITE_COMMAND_DRAFT | 否 | 否 | 视策略 | createTicketDraft |
| CREATE_TICKET | 创建工单，参数完整 | WRITE_COMMAND_EXECUTE | 否 | 可配置 | 可配置 | createTicket |
| TRANSFER_TICKET | 转派工单 | HIGH_RISK_CONFIRMATION | 否 | 否 | 是 | transferTicket |
| CLOSE_TICKET | 关闭工单，后续新增 | HIGH_RISK_CONFIRMATION | 否 | 否 | 是 | closeTicket |
| UNKNOWN / LOW_CONFIDENCE | 意图不明确 | CLARIFICATION | 否 | 否 | 否 | 无 |

关键约束：

```text
1. 不允许 allTools() 无差别暴露所有工具。
2. 当前轮 allowedToolNames 必须进入 toolContext。
3. SpringAiToolSupport 执行前必须校验当前 tool 是否在 allowedToolNames 内。
4. 写操作 executor 不允许依赖 ChatClient。
5. 高风险写操作必须进入 NEED_CONFIRMATION，除非后续明确增加强权限 bypass 策略。
```

---

## 6. 主流程设计

### 6.1 AgentFacade

`AgentFacade` 只保留两个入口：

```java
@Service
public class AgentFacade {

    private final AgentOrchestrator agentOrchestrator;

    public AgentChatResult chat(CurrentUser currentUser, String sessionId, String message) {
        return agentOrchestrator.handle(currentUser, sessionId, message, NoopAgentEventSink.INSTANCE);
    }

    public void chatStream(CurrentUser currentUser, String sessionId, String message, AgentEventSink sink) {
        agentOrchestrator.handle(currentUser, sessionId, message, sink);
    }
}
```

验收标准：

```text
1. AgentFacade 总行数建议控制在 80 行以内。
2. 不再保留 agentReActLoop、executeDeterministicFallback、continuePendingConfirmation 等复杂私有方法。
3. AgentFacadeTest 只验证入口委派和结果回传。
```

### 6.2 AgentOrchestrator

主流程伪代码：

```java
public AgentChatResult handle(
        CurrentUser currentUser,
        String sessionId,
        String message,
        AgentEventSink sink
) {
    AgentTurnState state = turnStateFactory.create(currentUser, sessionId, message);

    try {
        sink.accepted(sessionId);

        rateLimitService.check(currentUser, sessionId);
        sessionLockService.lock(sessionId);

        loadContext(state);
        startTrace(state);
        hydrateMemory(state);

        AgentExecutionSummary summary;

        if (pendingActionCoordinator.hasPendingAction(state.getContext())) {
            state.setPolicy(policyService.pendingPolicy(currentUser, state.getContext()));
            sink.status("正在继续上一轮未完成操作");
            summary = pendingActionCoordinator.continuePendingAction(state, sink);
        } else {
            routeIntent(state);
            sink.route(state.getRoute());

            buildOrLoadPlan(state);
            resolveExecutionPolicy(state);
            budgetService.init(state.getPolicy());

            summary = executeByPolicy(state, sink);
        }

        String reply = replyRenderer.render(state, summary);
        summary.setRenderedReply(reply);

        commitTurnOnce(state, summary);
        traceService.finish(state.getTrace(), state.getRoute(), state.getPlan(), summary);

        AgentChatResult result = buildChatResult(state, summary);
        sink.finalResult(result);
        return result;
    } catch (AgentException ex) {
        AgentExecutionSummary failure = failureHandler.handle(state, ex);
        traceService.finishFailure(state.getTrace(), failure);
        sink.error(failure.getFailureCode(), failure.getFailureReason(), state.traceId());
        return buildChatResult(state, failure);
    } finally {
        sessionLockService.unlock(sessionId);
        sink.closeQuietly();
    }
}
```

注意：

```text
1. Orchestrator 只编排，不做具体业务执行。
2. executeByPolicy 只根据 mode 分发，不写复杂业务规则。
3. commitTurnOnce 是唯一 session/memory 写入口。
4. trace finish 只调用一次。
5. sink 只输出事件，不反向影响业务结果。
```

---

## 7. 写操作 Command 化

### 7.1 为什么要 Command 化

创建工单、转派工单、关闭工单这类写操作不应该被称为“fallback”。对业务 Agent 来说，确定性写链路本身就是主路径。

LLM 可以做：

```text
1. 辅助理解用户意图。
2. 辅助提取标题、描述、优先级等参数。
3. 辅助生成自然语言回复。
```

LLM 不应该做：

```text
1. 绕过权限直接执行转派。
2. 绕过确认直接关闭工单。
3. 用模型文本直接决定数据库写入。
4. 在没有 allowedToolNames 的情况下自由选择写工具。
```

### 7.2 AgentCommandDraft

```java
public class AgentCommandDraft {
    private AgentCommandType commandType;
    private AgentToolParameters parameters;
    private List<AgentToolParameterField> missingFields;
    private boolean highRisk;
    private boolean needConfirmation;
    private String previewText;
}
```

### 7.3 DeterministicCommandExecutor

```java
public AgentExecutionSummary execute(AgentTurnState state, AgentEventSink sink) {
    sink.status("正在提取业务参数");
    AgentToolParameters parameters = parameterExtractor.extract(state.getMessage(), state.getContext(), state.getRoute());

    sink.status("正在校验必要参数");
    SlotValidationResult slotResult = slotFillingService.validate(state.getRoute().getIntent(), parameters);
    if (!slotResult.isComplete()) {
        AgentPendingAction pending = pendingActionCoordinator.buildSlotFillingPending(state, parameters, slotResult);
        return AgentExecutionSummary.needMoreInfo(state.getRoute(), parameters, pending);
    }

    sink.status("正在校验权限与风险");
    AgentExecutionGuardResult guardResult = executionGuard.check(state.getCurrentUser(), state.getRoute(), parameters);
    if (guardResult.needConfirmation()) {
        AgentPendingAction pending = pendingActionCoordinator.buildConfirmationPending(state, parameters, guardResult);
        return AgentExecutionSummary.needConfirmation(state.getRoute(), parameters, pending);
    }

    sink.toolStart(resolveToolName(state.getRoute()));
    AgentToolResult result = commandHandlerRegistry.execute(state.getCurrentUser(), state.getRoute(), parameters);
    sink.toolResult(resolveToolName(state.getRoute()), result.getStatus());

    return AgentExecutionSummary.completed(state.getRoute(), parameters, result);
}
```

### 7.4 创建工单两阶段

创建工单建议拆成：

```text
CREATE_TICKET_DRAFT
  -> 参数提取
  -> 缺参判断
  -> 预览
  -> 用户补参或确认
  -> CREATE_TICKET_EXECUTE
```

示例回复：

```text
我已识别到你想创建工单：

标题：线上登录失败
优先级：高
分类：账号/登录

还缺少以下信息：
1. 具体报错信息
2. 影响范围
3. 联系方式或期望处理人

请补充这些信息，或回复“直接创建”使用当前信息创建草稿。
```

### 7.5 转派工单确认链路

转派必须检查：

```text
1. 当前用户是否有转派权限。
2. ticketId 是否存在。
3. 当前用户是否有该工单的数据权限。
4. 目标处理人是否存在。
5. 目标处理人是否属于可处理队列。
6. 工单当前状态是否允许转派。
7. 是否重复转派给当前处理人。
8. 是否需要转派原因。
```

确认前回复：

```text
请确认是否执行以下转派操作：

工单：#1001 线上登录失败
当前处理人：李四
目标处理人：张三
转派原因：用户指定转派

回复“确认”后执行，回复“取消”放弃本次操作。
```

---

## 8. PendingAction 统一设计

### 8.1 AgentPendingType

```java
public enum AgentPendingType {
    SLOT_FILLING,
    HIGH_RISK_CONFIRMATION
}
```

### 8.2 状态流转

```text
缺少参数
  -> NEED_MORE_INFO
  -> 保存 SLOT_FILLING pending
  -> 用户补参
  -> 合并参数
  -> 完整后执行或进入确认

高风险操作
  -> NEED_CONFIRMATION
  -> 保存 HIGH_RISK_CONFIRMATION pending
  -> 用户确认
  -> 执行命令
  -> 清空 pending

用户取消
  -> CANCELLED
  -> 清空 pending
```

### 8.3 PendingActionCoordinator 职责

```text
1. 判断当前 session 是否存在 pendingAction。
2. 识别用户消息是否为确认、取消或补参。
3. 对 SLOT_FILLING pending 合并新参数。
4. 对 HIGH_RISK_CONFIRMATION pending 执行或取消。
5. 在 summary 中返回 pendingAction 变更，不直接写 session。
6. 所有 pendingAction 的创建、恢复、清空只在这一处处理。
```

---

## 9. 只读 ReAct 设计

### 9.1 允许场景

只允许以下场景使用 ReAct：

```text
QUERY_TICKET
SEARCH_HISTORY
DIAGNOSE_TICKET
```

不允许 ReAct 自主执行：

```text
CREATE_TICKET
TRANSFER_TICKET
CLOSE_TICKET
CHANGE_PRIORITY
DELETE / ARCHIVE / MODIFY 类操作
```

### 9.2 AgentReactToolCatalog

```java
public Object[] buildTools(AgentExecutionPolicy policy) {
    return policy.getAllowedToolNames().stream()
        .map(toolRegistry::getSpringAiTool)
        .filter(this::isReadOnlyTool)
        .toArray();
}
```

校验：

```text
1. policy.allowReact 必须为 true。
2. allowedToolNames 不能为空。
3. tool riskLevel 必须 <= READ_ONLY。
4. tool supportedIntent 必须包含当前 route intent。
```

### 9.3 ReadOnlyReactExecutor 输出

ReadOnlyReactExecutor 不直接返回最终回复，而是返回：

```text
1. observations
2. primaryResult
3. optional llmSummary
4. llmUsed / springAiUsed
5. failureCode / degraded flag
```

最终由 AgentReplyRenderer 生成用户看到的内容。

---

## 10. 流式输出设计

### 10.1 接口设计

保留同步接口：

```text
POST /api/agent/chat
Content-Type: application/json
```

新增流式接口：

```text
POST /api/agent/chat/stream
Content-Type: application/json
Accept: text/event-stream
Produces: text/event-stream
```

Spring MVC 项目推荐用 `SseEmitter`，不必为了这一项强行迁移 WebFlux。

### 10.2 Controller 示例

```java
@PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
@Operation(summary = "智能体流式对话", description = "通过 SSE 返回 Agent 执行过程与最终结果")
public SseEmitter chatStream(
        Authentication authentication,
        @Valid @RequestBody AgentChatRequest request
) {
    SseEmitter emitter = new SseEmitter(60_000L);
    CurrentUser currentUser = currentUserResolver.resolve(authentication);
    SseAgentEventSink sink = new SseAgentEventSink(emitter);

    agentStreamExecutor.execute(() -> {
        try {
            agentFacade.chatStream(currentUser, request.getSessionId(), request.getMessage(), sink);
        } catch (Exception ex) {
            sink.error("AGENT_STREAM_FAILED", "流式对话失败", null);
        } finally {
            sink.closeQuietly();
        }
    });

    return emitter;
}
```

### 10.3 事件类型

```java
public enum AgentStreamEventType {
    ACCEPTED,
    ROUTE,
    STATUS,
    TOOL_START,
    TOOL_RESULT,
    DELTA,
    NEED_MORE_INFO,
    NEED_CONFIRMATION,
    FINAL,
    ERROR,
    TRACE,
    HEARTBEAT
}
```

### 10.4 SSE 事件示例

只读检索：

```text
event: accepted
data: {"sessionId":"s-001"}

event: route
data: {"intent":"SEARCH_HISTORY","confidence":0.91}

event: status
data: {"message":"正在检索历史案例"}

event: tool_start
data: {"toolName":"searchHistory"}

event: tool_result
data: {"toolName":"searchHistory","status":"SUCCESS"}

event: delta
data: {"text":"我找到了 3 个相似案例，"}

event: delta
data: {"text":"主要集中在账号锁定、缓存异常和登录网关超时。"}

event: final
data: {"sessionId":"s-001","reply":"完整最终回答...","traceId":"xxx"}
```

写操作确认：

```text
event: accepted
data: {"sessionId":"s-002"}

event: route
data: {"intent":"TRANSFER_TICKET","confidence":0.94}

event: status
data: {"message":"正在校验工单状态和转派权限"}

event: need_confirmation
data: {"reply":"确认将 #1001 工单转派给张三吗？"}

event: final
data: {"status":"NEED_CONFIRMATION","traceId":"xxx"}
```

### 10.5 流式输出约束

```text
1. 同步接口继续保留。
2. 流式接口只改变响应方式，不改变业务执行语义。
3. 只读查询、历史检索、诊断建议允许 delta 文本输出。
4. 创建、转派、关闭等写操作只输出状态事件，不允许 token 直接驱动写入。
5. final 事件必须包含完整 AgentChatResult。
6. error 事件必须包含 errorCode、message、traceId。
7. 客户端断开后后端必须释放资源。
8. 一个 SSE 连接最长建议 60 秒。
9. 普通用户最多允许 3 个活跃 stream。
10. 每 10 秒发送一次 heartbeat，防止连接被中间代理断开。
```

---

## 11. 高压访问治理

### 11.1 限流维度

```text
1. userId 级限流：防止单个用户刷爆 Agent。
2. sessionId 级串行：防止同一会话并发修改 pendingAction。
3. LLM 全局限流：防止模型 API QPS/TPM 被打满。
4. RAG 检索限流：防止向量库、embedding、rerank 被打满。
5. SSE 连接数限制：防止长连接耗尽线程和连接资源。
```

建议初始策略：

```text
普通用户：
  agent.chat: 20 次 / 分钟
  agent.stream: 3 个活跃连接
  agent.llm: 10 次 / 分钟

管理员：
  agent.chat: 60 次 / 分钟
  agent.stream: 10 个活跃连接
  agent.llm: 30 次 / 分钟

全局：
  agent.llm.concurrent: 10
  agent.rag.concurrent: 20
  agent.stream.concurrent: 100
```

### 11.2 Session Lock

同一个 `sessionId` 同一时间只允许一个 Agent 请求执行。

```text
Redis key: agent:session:lock:{sessionId}
TTL: 30s
失败错误码: AGENT_SESSION_BUSY
返回文案: 当前会话正在处理中，请稍后再试
```

原因：

```text
1. 防止两个请求同时补参。
2. 防止一个请求确认，一个请求取消，最终状态错乱。
3. 防止 activeTicketId、activeAssigneeId、recentMessages 串写。
4. 防止同一轮 memory 被重复写入。
```

### 11.3 Bulkhead 资源隔离

建议至少拆三个执行资源池：

```text
agent-fast-pool
  用于路由、参数校验、普通查询、轻量业务逻辑

agent-llm-pool
  用于 LLM 调用、LLM 总结、只读 ReAct

agent-rag-pool
  用于历史案例检索、embedding、rerank
```

建议配置：

```text
LLM 调用：
  maxConcurrentCalls = 10
  timeout = 15s
  retry = 0 或 1
  circuitBreaker = enabled

RAG 检索：
  maxConcurrentCalls = 20
  timeout = 3s - 5s
  rerankTimeout = 2s

普通工单查询：
  不走 LLM bulkhead
  只受数据库连接池和普通接口限流约束
```

### 11.4 Agent Turn Budget

每轮 Agent 请求必须有预算：

```text
最大 LLM 调用次数：1
最大只读 tool 调用次数：2
最大写 tool 调用次数：1
最大 RAG 检索次数：1
最大 rerank 次数：1
最大总耗时：30s
最大输出 token：按模型配置限制
```

超限行为：

```text
1. 停止继续调用工具。
2. 返回已有部分结果。
3. 写入 trace。
4. 提示用户缩小问题范围。
5. 不执行任何未确认写操作。
```

### 11.5 降级策略

| 场景 | 降级策略 |
|---|---|
| LLM 超时 | QUERY_TICKET 继续走确定性查询 |
| LLM 不可用 | CREATE_TICKET 继续走规则提参和草稿创建 |
| LLM 不可用 | TRANSFER_TICKET 继续走确定性确认链路 |
| RAG 超时 | SEARCH_HISTORY 返回“历史检索暂不可用”，不影响普通工单查询 |
| rerank 超时 | 使用初召回结果 |
| SSE 连接数满 | 返回错误或提示前端改用同步接口 |
| Trace 写失败 | 不影响主业务结果，只记录日志 |
| Memory 写失败 | 不影响当前回复，后续可补偿或记录 warning |

---

## 12. 统一错误码

建议新增或统一 Agent 错误码：

```text
AGENT_SESSION_BUSY           当前 session 正在处理中
AGENT_RATE_LIMITED           请求过于频繁
AGENT_STREAM_LIMITED         流式连接数超限
AGENT_LLM_TIMEOUT            模型响应超时
AGENT_LLM_UNAVAILABLE        模型服务不可用
AGENT_RAG_TIMEOUT            RAG 检索超时
AGENT_RAG_UNAVAILABLE        RAG 服务不可用
AGENT_TOOL_NOT_ALLOWED       当前工具未被本轮策略授权
AGENT_PERMISSION_DENIED      用户权限不足
AGENT_NEED_MORE_INFO         缺少必要信息
AGENT_NEED_CONFIRMATION      需要用户确认
AGENT_BUDGET_EXCEEDED        本轮 Agent 调用预算超限
AGENT_EXECUTION_FAILED       Agent 执行失败
AGENT_TRACE_WRITE_FAILED     Trace 写入失败，但不影响主流程
```

统一响应建议：

```json
{
  "code": "AGENT_LLM_TIMEOUT",
  "message": "模型响应超时，已切换为确定性处理路径",
  "traceId": "xxx"
}
```

---

## 13. Trace 与可观测性

### 13.1 Trace 记录内容

Trace 不记录模型 chain-of-thought，而是记录结构化决策事实：

```json
{
  "traceId": "xxx",
  "sessionId": "s-001",
  "userId": 1,
  "intent": "TRANSFER_TICKET",
  "routeConfidence": 0.92,
  "executionMode": "HIGH_RISK_CONFIRMATION",
  "allowedTools": ["transferTicket"],
  "calledTools": [],
  "pendingAction": {
    "type": "HIGH_RISK_CONFIRMATION",
    "tool": "transferTicket"
  },
  "resultStatus": "NEED_CONFIRMATION",
  "failureCode": null,
  "latencyMs": 123
}
```

### 13.2 推荐指标

```text
agent_chat_total
agent_chat_latency_ms
agent_stream_total
agent_stream_active_connections
agent_route_intent_total
agent_llm_call_total
agent_llm_timeout_total
agent_rag_call_total
agent_rag_timeout_total
agent_tool_call_total
agent_tool_not_allowed_total
agent_pending_action_total
agent_session_busy_total
agent_rate_limited_total
agent_degraded_total
agent_failure_total
```

---

## 14. 推荐实施顺序

### P0：基线修复与一致性检查

目标：在动主链之前先保证现有行为可回归。

任务：

```text
1. 修复乱码注释、乱码 prompt、无效注释。
2. 强化 SkillDefinitionLoader 校验。
3. 统一 trace entity / mapper / schema 字段。
4. 补 AgentFacade 当前黄金路径测试。
5. 补 CREATE / TRANSFER / QUERY / SEARCH_HISTORY 四类基线测试。
```

验收：

```text
mvn -q -pl smart-ticket-agent -am test 通过
现有 /api/agent/chat 行为不变
```

建议提交名：

```text
chore(agent): add baseline tests and schema consistency checks
```

---

### P1：统一结果模型与回复渲染

目标：先引入统一结果契约，降低后续拆分风险。

新增：

```text
AgentTurnStatus
AgentExecutionMode
AgentExecutionSummary
AgentReplyRenderer
```

改造：

```text
1. AgentFacade 暂时保留主流程。
2. 各分支最终都转换成 AgentExecutionSummary。
3. 最终回复统一由 AgentReplyRenderer 生成。
4. 不再直接返回 LLM 原始输出。
```

验收：

```text
1. AgentReplyRendererTest 覆盖成功、缺参、确认、失败、降级。
2. 最终 reply 不包含中间 reasoning 文本。
3. session/memory/trace 可以从 summary 读取统一结果。
```

建议提交名：

```text
refactor(agent): introduce execution summary and reply renderer
```

---

### P2：拆 PendingActionCoordinator 与 Slot 层

目标：统一补参、确认、取消状态。

新增：

```text
PendingActionCoordinator
AgentPendingType
SlotFillingService
SlotMergeService
SlotRequirement
SlotValidationResult
```

迁移：

```text
continuePendingConfirmation
continuePendingCreate
syncCreatePendingAction
buildConfirmationResult
isConfirmMessage
isCancelMessage
mergeCreateDraftParameters
```

验收：

```text
1. 创建工单缺参进入 SLOT_FILLING pending。
2. 用户补参后可以继续创建。
3. 用户取消后 pending 清空。
4. 转派工单进入 HIGH_RISK_CONFIRMATION pending。
5. 用户确认后执行，取消后不执行。
6. pendingAction 创建、恢复、清空只有一个入口。
```

建议提交名：

```text
refactor(agent): centralize pending action and slot filling
```

---

### P3：写操作 Command 化

目标：将写操作从 fallback 概念升级为正式确定性主链。

新增：

```text
AgentCommandType
AgentCommandDraft
AgentCommandExecutor
DeterministicCommandExecutor
CreateTicketCommandHandler
TransferTicketCommandHandler
```

改造：

```text
1. CREATE_TICKET 走 WriteCommandExecutor / DeterministicCommandExecutor。
2. TRANSFER_TICKET 走 WriteCommandExecutor / DeterministicCommandExecutor。
3. 写操作执行前必须经过 parameterExtractor、slot validation、executionGuard。
4. 高风险写操作必须先返回 NEED_CONFIRMATION。
5. 写 executor 不依赖 ChatClient。
```

验收：

```text
1. CREATE_TICKET 完整参数可以成功落单。
2. CREATE_TICKET 缺参不会落单。
3. TRANSFER_TICKET 未确认不会执行。
4. TRANSFER_TICKET 确认后才执行。
5. 写操作链路不使用 ReAct。
```

建议提交名：

```text
refactor(agent): move write actions to deterministic command executor
```

---

### P4：拆执行策略与只读 ReAct

目标：让 Agent 根据 intent、权限、风险选择执行模式。

新增：

```text
AgentExecutionPolicy
AgentExecutionPolicyService
ToolExposurePolicy
ReadOnlyReactExecutor
AgentReactToolCatalog
AgentObservation
```

改造：

```text
1. QUERY_TICKET / SEARCH_HISTORY / DIAGNOSE_TICKET 允许只读 ReAct。
2. CREATE_TICKET / TRANSFER_TICKET 不允许 ReAct 自主执行。
3. allowedToolNames 进入 toolContext。
4. SpringAiToolSupport 执行前校验工具是否被授权。
5. ReAct 输出 observations，不直接改 session，不直接拼最终回复。
```

验收：

```text
1. ReAct 只能调用 queryTicket / searchHistory 等只读工具。
2. ReAct 尝试调用写工具时被拒绝。
3. AgentExecutionPolicyServiceTest 覆盖所有 intent。
4. AgentReadOnlyReactExecutorTest 覆盖 tool whitelist。
```

建议提交名：

```text
refactor(agent): isolate read-only react and execution policy
```

---

### P5：引入 AgentOrchestrator，瘦身 AgentFacade

目标：形成清晰主链。

新增：

```text
AgentOrchestrator
AgentTurnState
```

改造：

```text
1. AgentFacade.chat 委派给 AgentOrchestrator.handle。
2. Orchestrator 串联 loadContext、hydrateMemory、route、policy、execute、render、commit、trace。
3. AgentContextUpdater.apply 接收 AgentExecutionSummary。
4. AgentMemoryService.remember 接收 AgentExecutionSummary。
5. AgentTraceService.finish 接收 AgentExecutionSummary。
```

验收：

```text
1. AgentFacade <= 80 行。
2. AgentOrchestrator 单个 public method 清晰。
3. session 和 memory 每轮只提交一次。
4. trace finish 每轮只调用一次。
5. recentMessages 不重复追加。
```

建议提交名：

```text
refactor(agent): introduce orchestrator and slim facade
```

---

### P6：高压治理

目标：使 Agent 模块在压力下可控、可降级。

新增：

```text
AgentRateLimitService
AgentSessionLockService
AgentTurnBudgetService
AgentDegradePolicyService
AgentBulkheadConfig
AgentErrorCode
```

改造：

```text
1. AgentOrchestrator 入口先做 rate limit。
2. 同一 sessionId 加 Redis lock。
3. LLM/RAG 使用独立 bulkhead。
4. 每轮请求有 maxLlmCalls、maxToolCalls、timeout。
5. LLM/RAG 失败时按 intent 降级。
```

验收：

```text
1. 同一 sessionId 并发 10 个请求，只允许 1 个进入主链。
2. LLM timeout 后 QUERY_TICKET 可以走确定性查询。
3. RAG timeout 后 SEARCH_HISTORY 不影响普通工单查询。
4. 超出预算返回 AGENT_BUDGET_EXCEEDED。
5. trace 记录 degraded/failureCode。
```

建议提交名：

```text
feat(agent): add rate limit session lock and degrade policy
```

---

### P7：SSE 流式输出

目标：提升前端聊天体验，但不改变业务执行语义。

新增：

```text
AgentEventSink
NoopAgentEventSink
SseAgentEventSink
AgentStreamEvent
AgentStreamEventType
/api/agent/chat/stream
```

改造：

```text
1. AgentOrchestrator.handle 增加 AgentEventSink 参数。
2. 同步接口传 NoopAgentEventSink。
3. 流式接口传 SseAgentEventSink。
4. 只读总结可输出 delta。
5. 写操作只输出 status / need_confirmation / final。
```

验收：

```text
1. /api/agent/chat 继续可用。
2. /api/agent/chat/stream 返回 text/event-stream。
3. final 事件包含完整 AgentChatResult。
4. error 事件包含 errorCode/message/traceId。
5. 客户端断开后资源释放。
6. 单用户 stream 连接数受限。
```

建议提交名：

```text
feat(agent): add optional sse streaming response
```

---

### P8：测试、压测与文档收尾

目标：让重构结果可证明、可展示、可面试讲清楚。

任务：

```text
1. 补单元测试。
2. 补集成测试。
3. 补并发测试。
4. 补 SSE 测试。
5. 补降级测试。
6. 更新 README Agent 部分。
7. 更新 docs/agent-refactor-checklist.md 或新增 docs/agent-refactor-implementation-plan.md。
8. 增加 docs/agent-architecture.md 描述最终架构。
```

验收：

```text
mvn -q -pl smart-ticket-agent -am test 通过
mvn -q -pl smart-ticket-api -am test 通过
核心 Agent 流程全部有测试覆盖
文档能解释清楚为什么这样设计
```

建议提交名：

```text
test(agent): add regression concurrency and streaming tests
```

---

## 15. 测试清单

### 15.1 单元测试

```text
AgentExecutionPolicyServiceTest
  - QUERY_TICKET -> READ_ONLY_REACT
  - SEARCH_HISTORY -> READ_ONLY_REACT
  - CREATE_TICKET -> WRITE_COMMAND_DRAFT / WRITE_COMMAND_EXECUTE
  - TRANSFER_TICKET -> HIGH_RISK_CONFIRMATION
  - UNKNOWN -> CLARIFICATION

PendingActionCoordinatorTest
  - 创建 pending
  - 补参合并
  - 确认执行
  - 取消清理
  - 非确认消息继续等待

DeterministicCommandExecutorTest
  - 创建工单完整参数成功
  - 创建工单缺参进入 NEED_MORE_INFO
  - 转派工单进入 NEED_CONFIRMATION
  - 权限不足返回 AGENT_PERMISSION_DENIED

ReadOnlyReactExecutorTest
  - 只暴露只读工具
  - 写工具不进入 tool catalog
  - observation 正确生成

AgentReplyRendererTest
  - 成功回复
  - 缺参回复
  - 确认回复
  - 降级回复
  - 失败回复

AgentTurnBudgetServiceTest
  - LLM 调用超限
  - tool 调用超限
  - 总耗时超限
```

### 15.2 集成测试

```text
AgentOrchestratorIntegrationTest
  - 查询工单完整主链
  - 检索历史案例完整主链
  - 创建工单缺参 -> 补参 -> 创建成功
  - 转派工单 -> 确认 -> 转派成功
  - 转派工单 -> 取消 -> 不执行
  - LLM 不可用时降级
  - RAG 不可用时降级
```

### 15.3 并发测试

```text
同一 sessionId 并发 10 个请求：
  - 只有 1 个进入主链
  - 其余返回 AGENT_SESSION_BUSY

不同 sessionId 并发 100 个请求：
  - 不出现 pendingAction 串写
  - 不出现 activeTicketId 串写
  - 不出现 recentMessages 重复提交
```

### 15.4 流式测试

```text
/api/agent/chat/stream 查询工单：
  - accepted
  - route
  - status
  - tool_start
  - tool_result
  - final

/api/agent/chat/stream 历史案例检索：
  - accepted
  - route
  - status
  - delta
  - final

/api/agent/chat/stream 转派工单：
  - accepted
  - route
  - status
  - need_confirmation
  - final

客户端断开：
  - 后端关闭 emitter
  - 释放 session lock
  - 不继续输出事件
```

---

## 16. 最终验收标准

完成后，项目应满足：

```text
1. AgentFacade 成为薄入口。
2. AgentOrchestrator 串起完整主链。
3. ReAct 只存在于只读类意图。
4. 写操作全部走确定性 Command 执行。
5. pendingAction 只有一个统一入口。
6. 最终回复由 AgentReplyRenderer 生成。
7. session 和 memory 每轮只提交一次。
8. trace 不依赖模型中间推理文本。
9. Spring AI tool calling 不会越权调用未授权工具。
10. /api/agent/chat 同步接口继续可用。
11. /api/agent/chat/stream 可选支持 SSE 流式输出。
12. 同一 sessionId 并发请求有互斥保护。
13. LLM/RAG 慢调用不会拖垮核心工单链路。
14. 限流、超时、降级、错误码、指标具备基础实现。
15. mvn -q -pl smart-ticket-agent -am test 可以稳定通过。
```

---

## 17. 面试与简历表达

完成重构后，简历可以写：

```text
设计并重构智能工单平台中的受控型业务 Agent 模块，构建 IntentRouter -> ExecutionPolicy -> Planner -> Guard -> CommandExecutor/ReadOnlyReactExecutor -> ReplyRenderer -> Trace 的可审计执行链路；将创建、转派等写操作从 LLM Tool Calling 中解耦为确定性 Command 执行，支持多轮补槽、高风险确认、只读 ReAct 检索、工具白名单暴露、Session/Memory 单次提交和 Trace 全链路复盘。
```

如果完成流式输出和高压治理，可以再加：

```text
为 Agent 对话设计同步 / SSE 双接口输出模型，基于事件流返回路由、工具调用、检索进度、增量文本和最终结构化结果；针对高并发访问引入用户级限流、会话级互斥、LLM/RAG Bulkhead、超时熔断和降级策略，避免慢模型调用拖垮核心工单链路。
```

---

## 18. 给 Codex 的执行提示词模板

可以把下面内容交给 Codex 分阶段执行：

```text
你现在要重构 smart-ticket-platform 的 smart-ticket-agent 模块。请严格按 docs/agent-refactor-implementation-plan.md 执行，不要一次性大改。

当前阶段：P1：统一结果模型与回复渲染。

要求：
1. 先阅读 docs/agent-refactor-implementation-plan.md 和 docs/agent-refactor-checklist.md。
2. 只完成当前阶段，不要提前实现 SSE、高压治理或大规模重排包结构。
3. 新增 AgentTurnStatus、AgentExecutionMode、AgentExecutionSummary、AgentReplyRenderer。
4. 让现有 AgentFacade 的主要返回路径都能生成 AgentExecutionSummary，再由 AgentReplyRenderer 生成最终 reply。
5. 不改变 /api/agent/chat 的接口协议。
6. 补 AgentReplyRendererTest 和必要的 AgentFacade 回归测试。
7. 生成代码时必须补充清晰的中文注释：类、接口、枚举、核心 public 方法使用 `/** */` Javadoc 说明职责、入参、返回值和关键约束；字段、分支判断、复杂方法内部关键步骤使用 `//` 行内注释；不要为 getter/setter、构造器、简单赋值、显而易见的代码写无意义注释；注释要解释“为什么这样设计 / 业务约束是什么”，不要只重复代码在做什么。
8. 执行 mvn -q -pl smart-ticket-agent -am test，并修复失败。
9. 输出改动文件列表、核心设计说明、测试结果。
```

每次只换“当前阶段”即可，例如 P2、P3、P4。

---

## 19. 执行建议

最稳妥的执行策略：

```text
第一步：不要动流式输出，先完成 P0-P5 主链重构。
第二步：再补 P6 高压治理，尤其是 session lock、rate limit、timeout、degrade。
第三步：最后做 P7 SSE 流式输出。
第四步：补 P8 测试和文档，用于简历和面试讲解。
```

原因：

```text
1. 主链没拆清楚时做 SSE，会让 AgentFacade 更复杂。
2. 没有 session lock 时做流式输出，会放大多轮状态错乱风险。
3. 没有限流和预算时做 ReAct，会放大 LLM/RAG 资源消耗。
4. 先统一 summary/reply/trace，再做流式 final 事件，会简单很多。
```

最终项目定位应保持为：

```text
单 Agent 入口
多执行模式
多业务 Command
强 Guard
强 Trace
可选流式输出
高压可降级
测试可验证
```

不要为了“多 Agent”而多 Agent。这个项目最有价值的亮点是 **Java 后端业务工程能力 + 受控型 Agent 工程化落地能力**。
