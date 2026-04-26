# AGENTS.md

## 1. 项目背景

本仓库是 `smart-ticket-platform`，定位为 Java Spring Boot 智能工单平台，用于展示 Java 后端工程能力和受控型业务 Agent 工程化能力。

当前项目已完成 P0-P2 阶段重构。Agent 架构参考以下文档：

- `docs/agent-architecture.md`
- `docs/project-deep-dive.md`

项目规范和工程约束以本文档为准。

---

## 2. 总体工作原则

- 不要一次性重写整个 Agent 模块。
- 每次只执行用户明确指定的一个阶段，例如 P0、P1、P2。
- 不要提前实现后续阶段内容。
- 保留现有 `/api/agent/chat` 同步接口行为，除非当前阶段明确要求修改。
- 不要在主链尚未拆清楚前实现 SSE 流式输出。
- 不要在未建立执行策略和工具白名单前扩大 LLM Tool Calling 能力。
- 不要让 LLM 直接执行创建、转派、关闭、修改优先级等写操作。
- 写操作必须走确定性 Command / Tool 执行链路，并经过参数校验、权限校验、风险判断和必要确认。
- ReAct 只允许用于查询工单、检索历史案例、诊断建议等只读场景。
- 每轮 Agent turn 原则上只允许一次 session / memory / pendingAction 提交。
- Trace 只记录可审计的结构化决策事实，不记录模型 chain-of-thought。

---

## 3. 阶段执行规则

执行任何改动前，参考：

1. `docs/agent-architecture.md`
2. `docs/project-deep-dive.md`
3. 本文件 `AGENTS.md`

每个阶段必须遵守：

- 只修改当前阶段需要的文件。
- 优先小步提交，不做无关格式化。
- 不重命名大量包和类，除非当前阶段明确要求。
- 不删除已有测试，除非测试已经明显失效且已用新的等价测试覆盖。
- 如果发现当前代码与文档不一致，先以当前代码真实行为为准，补充保护性测试后再重构。
- 如果当前阶段需要新增抽象，先保证旧行为可回归，再逐步切换调用链。

推荐阶段顺序：

```text
P0：基线梳理与保护性测试
P1：AgentExecutionSummary + AgentReplyRenderer
P2：PendingActionCoordinator
P3：WriteCommandExecutor / DeterministicCommandExecutor
P4：AgentExecutionPolicyService
P5：ReadOnlyReactExecutor + Tool 白名单
P6：SessionLock / RateLimit / Timeout / DegradePolicy
P7：SSE 流式接口
P8：Metrics / 压测 / 文档收尾
```

---

## 4. Agent 架构边界

### 4.1 Controller 边界

- Controller 只负责参数接收、认证用户解析、调用 facade、响应转换。
- Controller 不写 Agent 执行策略。
- Controller 不拼接业务回复。
- Controller 不直接操作 pendingAction、memory、trace。

### 4.2 AgentFacade 边界

- `AgentFacade` 应逐步瘦身为入口类。
- `AgentFacade` 只负责对外暴露 `chat(...)` 或后续 `chatStream(...)`。
- 主流程应迁移到 `AgentOrchestrator`。

### 4.3 Orchestrator 边界

`AgentOrchestrator` 只负责编排一轮对话：

```text
load session
hydrate memory
handle pending action
route intent
build/load plan
resolve execution policy
execute by policy
render reply
commit state
record trace
return result
```

它不应该直接实现复杂业务规则，不应该直接访问具体 ticket tool 的底层细节。

### 4.4 写操作边界

创建工单、转派工单、关闭工单、修改优先级等写操作必须遵守：

```text
自然语言输入
  -> intent route
  -> 参数抽取 / slot filling
  -> command draft
  -> guard / permission / risk check
  -> confirmation if needed
  -> deterministic command execution
  -> result summary
  -> reply rendering
```

禁止让 LLM 直接决定是否写库。

### 4.5 只读 ReAct 边界

只读 ReAct 仅允许暴露只读工具，例如：

- 查询工单
- 检索历史案例
- 获取当前会话上下文
- 诊断建议所需的只读工具

禁止暴露：

- 创建工单
- 转派工单
- 关闭工单
- 修改优先级
- 修改处理人
- 任何会改变数据库状态的工具

---

## 5. Java 代码风格

- 使用 Java 17+ 可读写法，避免过度炫技。
- 优先使用清晰的类名、方法名、字段名表达业务语义。
- 避免过长方法；复杂方法应拆成具有明确业务含义的私有方法。
- 避免在一个方法里同时做路由、执行、落库、渲染、trace。
- 避免在 service 中硬编码大量字符串，必要时抽 enum 或常量。
- DTO、Result、Summary 对象应表达明确，不要用 `Map<String, Object>` 承载核心语义。
- 不要为了抽象而抽象；每个新增类必须有清晰职责和调用方。

---

## 6. 中文注释规范

生成或修改代码时必须写清楚中文注释，但不要写无意义注释。

### 6.1 使用 `/** */` 的位置

以下位置使用 Javadoc 风格注释：

- 新增或重构后的类
- 接口
- 枚举
- 核心 `public` 方法
- 复杂的包内方法或 protected 方法
- 关键策略类、执行器、协调器、渲染器、结果模型

Javadoc 应说明：

- 这个类 / 方法的职责是什么
- 它在 Agent 主链中的位置
- 关键业务约束是什么
- 入参代表什么
- 返回值代表什么
- 是否允许执行写操作
- 是否会修改 session、memory、pendingAction、trace

示例：

```java
/**
 * Agent 单轮对话编排器。
 *
 * <p>该类只负责串联会话加载、意图路由、执行策略选择、工具执行、回复渲染和状态提交，
 * 不直接实现具体工单业务规则。写操作必须委托给确定性 Command 执行链路，避免 LLM 直接修改业务数据。</p>
 */
public class AgentOrchestrator {
}
```

### 6.2 使用 `//` 的位置

以下位置使用 `//` 行注释：

- 重要字段
- 非显而易见的布尔开关
- 复杂分支判断
- 方法内部关键步骤
- 状态转换点
- 降级、熔断、限流、session lock 等工程保护逻辑
- 写操作确认、补参、取消 pendingAction 等关键业务节点

示例：

```java
// 写操作必须先进入确认态，避免模型在未确认的情况下触发业务变更。
if (policy.isRequireConfirmation()) {
    return pendingActionCoordinator.createConfirmation(state, commandDraft);
}
```

### 6.3 不要写这些注释

禁止添加低价值注释：

```java
// 设置名称
user.setName(name);

// 返回结果
return result;

// 判断是否为空
if (value == null) {
}
```

不要给以下代码写重复性注释：

- getter / setter
- 简单构造器
- 简单字段赋值
- 简单 null 判断
- 明显的日志输出
- 与方法名完全重复的说明

### 6.4 注释重点

注释要解释：

```text
为什么这样设计
业务约束是什么
这里保护了什么风险
和 Agent 主链其他部分的关系是什么
哪些场景不能使用这个方法
```

不要只是复述代码正在做什么。

---

## 7. 测试要求

每个阶段完成前，至少运行：

```bash
mvn -q -pl smart-ticket-agent -am test
```

如果修改了 API 层、Controller、Response DTO 或接口协议，还要运行：

```bash
mvn -q -pl smart-ticket-api -am test
```

如果修改了 common、domain、infra、rag 等被 agent 依赖的模块，应运行：

```bash
mvn -q test
```

不要在没有说明原因的情况下跳过测试。

---

## 8. 必须保护的核心场景

重构过程中必须保护以下行为：

- `QUERY_TICKET` 正常查询。
- `SEARCH_HISTORY` 正常检索历史案例。
- `CREATE_TICKET` 参数不足时进入补参 pendingAction。
- `CREATE_TICKET` 补参后可以继续处理。
- `TRANSFER_TICKET` 必须确认后才能执行。
- 用户取消 pendingAction 后不能继续执行旧操作。
- ReAct 不能调用写工具。
- session / memory / pendingAction 不应在同一轮中被多处重复提交。
- traceId 应能返回给前端。
- LLM 不可用时，能走确定性降级路径的场景不应整体失败。

---

## 9. 不允许做的事情

- 不要一次性删除并重写 `smart-ticket-agent` 模块。
- 不要把所有逻辑塞进新的 `AgentOrchestrator`，避免从一个大类变成另一个大类。
- 不要让 `ReplyRenderer` 查询数据库或调用 tool。
- 不要让 `TraceService` 影响主业务成功或失败。
- 不要让 `MemoryService` 修改 pendingAction。
- 不要让 `SpringAiToolSupport` 决定执行策略。
- 不要在 prompt 中替代后端权限校验、风险校验和参数校验。
- 不要为了展示 Agent 能力引入不必要的多 Agent 架构。
- 不要提前实现 SSE、高压治理、指标等后续阶段内容。

---

## 10. 完成每个阶段后的输出格式

每个阶段完成后，必须输出以下内容：

```text
## 本阶段改动文件
- ...

## 核心设计说明
- ...

## 兼容性影响
- 是否改变接口：是/否
- 是否改变数据库 schema：是/否
- 是否改变现有行为：是/否，如有请说明

## 测试结果
- 执行命令：...
- 结果：通过/失败
- 失败项及原因：...

## 剩余风险
- ...

## 是否可以进入下一阶段
- 是/否
- 下一阶段建议：...
```

如果测试失败，必须先说明失败原因和已尝试修复的内容，不要直接宣称完成。
