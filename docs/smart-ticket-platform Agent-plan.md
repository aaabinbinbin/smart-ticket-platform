# smart-ticket-platform Agent 增强任务清单

## 先定一个总目标

你后续增强的目标可以定成：

**把当前“意图识别 + Tool Calling + RAG”的受控型 Agent，升级成一个面向服务台场景的、具备规划、记忆、技能编排、知识生命周期、评估与观测能力的业务 Agent 系统。** 这个目标和你当前项目定位是一致的，因为当前仓库本来就不是开放式聊天系统，而是围绕固定业务能力做的 Agent 编排层。

------

## P0：先把 Agent 的底座补完整

**目标：让当前 Agent 从“单轮工具助手”升级成“有步骤感、可观测、可评估的受控 Agent”。**

### 任务 1：补一层轻量 Planner，不再只有“路由 -> 调工具”

**为什么先做这个：**
 现在 `AgentFacade` 的核心链路是：加载 session；如果有 pending create draft 就继续补全；否则做意图路由；低于 0.50 就澄清；有条件就尝试 Spring AI Tool Calling；失败则退回 deterministic fallback。这个设计很稳，但更像“受控工具助手”，还不太像“会组织任务过程的 Agent”。

**要做什么：**

1. 在 `smart-ticket-agent` 新增一个 `AgentPlanner`，不要让 `AgentFacade` 直接把“路由”和“执行”写死在一起。
2. 定义统一的计划结构，例如：
   - `goal`
   - `currentStage`
   - `nextAction`
   - `requiredSlots`
   - `completedSteps`
   - `waitingForUser`
   - `riskLevel`
3. 先只支持 4 类目标的计划：
   - 创建工单
   - 查询工单
   - 转派工单
   - 检索历史经验
4. 每类目标拆成 3~5 个阶段，不要一步到位。
   - 例如创建工单：
     - 判断是新建还是查询已有工单
     - 收集必要字段
     - 判断是否需要相似案例检索
     - 执行创建
     - 返回结果并建议下一步
5. 在每次工具执行后，把结果回写到 `AgentPlanState`，再决定下一步，而不是工具执行完就直接结束。

**建议落地位置：**

- `smart-ticket-agent/planner/AgentPlanner.java`

- `smart-ticket-agent/planner/AgentPlan.java`

- `smart-ticket-agent/planner/AgentPlanStep.java`

- ```
  smart-ticket-agent/service/AgentFacade.java
  ```

   中把现有逻辑重构为：

  - `buildOrLoadPlan`
  - `advancePlan`
  - `executeCurrentStep`
  - `finalizePlan`

**验收标准：**

- 用户说一句模糊话时，系统能先判断当前阶段，而不是只做一次路由。
- 创建工单场景可以自然进入“多步推进”。
- 每次会话结果里能看到当前阶段和下一步动作。
- 不影响现有 fallback 机制。

------

### 任务 2：把当前 Tool 升级成 Skill Registry

**为什么做这个：**
 现在 Agent 更像“固定 if/else + 固定工具集合”。文档里当前主要意图只有查询工单、创建工单、转派工单、搜索历史经验，而且 Tool Calling 被定义为业务边界。这个边界是对的，但现在“能力单元”的抽象还不够强。

**要做什么：**

1. 在 `smart-ticket-agent` 新增 `Skill` 抽象，不直接让 `AgentFacade` 硬编码工具选择。
2. 每个 skill 至少定义：
   - `skillCode`
   - `skillName`
   - `description`
   - `inputSchema`
   - `requiredPermissions`
   - `riskLevel`
   - `canAutoExecute`
   - `supportedIntents`
3. 先把现有四个能力改造成 skill：
   - `CreateTicketSkill`
   - `QueryTicketSkill`
   - `TransferTicketSkill`
   - `SearchHistorySkill`
4. 新增 `SkillRegistry`，支持按意图、按上下文筛选可用 skill。
5. 让 Planner 和 Tool Calling 都不直接依赖具体 skill 类，而是走注册表。

**建议落地位置：**

- `smart-ticket-agent/skill/AgentSkill.java`
- `smart-ticket-agent/skill/SkillRegistry.java`
- `smart-ticket-agent/skill/impl/*`
- 未来可以把 Spring AI Tool Calling 暴露的工具能力和内部 skill 做一层映射。

**验收标准：**

- 新增一个 skill 时，不需要再大改 `AgentFacade` 主流程。
- Agent 能基于“当前可用 skill 列表”做决策，而不是写死几个 case。
- 现有四类能力都能迁移过去并保持行为一致。

------

### 任务 3：做 Agent Trace，把每次会话跑过什么记下来

**为什么做这个：**
 很多 Agent 项目最弱的一环不是功能，而是“出了问题完全不知道 Agent 怎么想的”。你现在的项目有日志和业务审计，但还缺针对 Agent 过程本身的可观测性。当前 README 也明确写了测试和补齐工作还在后续轮次，这更说明先把观测打牢很有必要。

**要做什么：**

1. 设计一份 

   ```
   AgentTraceRecord
   ```

   ，记录：

   - sessionId
   - userId
   - 原始输入
   - 意图路由结果
   - 置信度
   - 当前 plan/stage
   - 触发的 skill/tool
   - 参数抽取结果
   - 是否使用 Spring AI
   - 是否 fallback
   - 最终回复
   - 执行耗时

2. 设计两层 trace：

   - 会话级 trace
   - 步骤级 trace

3. 对以下节点打点：

   - route 前后
   - clarify
   - planner 决策
   - skill 调用前后
   - Spring AI tool calling 成功/失败
   - deterministic fallback
   - RAG 检索结果摘要

4. 暂时先落 MySQL 或日志 JSON 都行，不必一开始就接 ELK。

5. 提供一个简单的管理接口：

   - 按 sessionId 查 trace
   - 按用户查最近 20 次 trace
   - 按失败类型筛选 trace

**建议落地位置：**

- `smart-ticket-domain/entity/AgentTraceRecord.java`
- `smart-ticket-domain/mapper/AgentTraceRecordMapper.java`
- `smart-ticket-agent/trace/AgentTraceService.java`
- `smart-ticket-api/controller/agent/AgentTraceController.java`

**验收标准：**

- 任意一次 `/api/agent/chat` 都能事后还原主要执行路径。
- 能看到是规则路由成功、Spring AI 成功，还是 fallback 接管。
- 至少能支撑本地调试和面试演示。

------

### 任务 4：做一套离线评估集，不再只靠“我感觉能用”

**为什么做这个：**
 当前项目已经有意图路由、多轮澄清、pending action、RAG fallback，这些都非常适合做评估。没有评估的话，后面每次改 prompt、加 skill、改 planner 都容易把旧能力弄坏。

**要做什么：**

1. 新建一份评估语料，至少 80~120 条。
2. 语料分四类：
   - 正常请求
   - 模糊请求
   - 错误请求
   - 多轮补全请求
3. 每条样本要带标注：
   - expectedIntent
   - expectedNeedClarify
   - expectedSkill
   - expectedKeySlots
   - expectedOutcome
4. 先做三类离线评估：
   - 路由准确率
   - 参数抽取完整率
   - fallback 成功率
5. 再做两类 RAG 评估：
   - topK 相关性
   - rewrite 前后提升
6. 做一个本地可执行脚本，输出评估报告。

**建议落地位置：**

- `docs/agent-eval/seed-cases.json`
- `smart-ticket-agent/eval/AgentEvalRunner.java`
- `smart-ticket-rag/eval/RagEvalRunner.java`

**验收标准：**

- 每次改 Agent 主链后都能跑一次离线评估。
- 可以输出准确率、澄清率、fallback 率。
- 评估报告可直接写进 README 或 docs。

------

### 任务 5：把 Prompt 规范化，不再散落在代码里

**为什么做这个：**
 你现在其实已经有 prompt engineering，只是偏轻量、偏约束型。为了后面扩展 planner、skill 和 RAG 回答格式，最好把 prompt 变成可维护资产。

**要做什么：**

1. 把系统提示词和任务提示词拆开。
2. 至少拆成这几类模板：
   - 意图澄清 prompt
   - skill 选择 prompt
   - 工单创建补全 prompt
   - 历史案例总结 prompt
   - 结果解释 prompt
3. Prompt 不直接写死在方法体里，改成模板加载。
4. 给每个 prompt 加版本号和用途说明。
5. 记录每次调用用的是哪个 prompt 版本。

**建议落地位置：**

- `smart-ticket-agent/prompt/*.md`
- `smart-ticket-agent/prompt/PromptTemplateService.java`

**验收标准：**

- 业务逻辑代码中不再散落长 prompt 字符串。
- 能快速替换某一类 prompt 而不改主流程。
- trace 里能看到 promptVersion。

------

### 任务 6：补 Agent 核心测试

**为什么做这个：**
 README 已经明确写了当前打包是跳过测试，下一轮要统一修复和补齐。Agent 这一层如果不补测试，后面越改越不稳。

**要做什么：**

1. 补 

   ```
   AgentFacade
   ```

    单元测试：

   - 路由低置信度 -> 澄清
   - Spring AI 不可用 -> fallback
   - pending create -> continuePendingCreate

2. 补 planner 测试：

   - 创建工单缺字段
   - 创建工单需要澄清
   - 转派失败后的下一步

3. 补 skill registry 测试：

   - 意图映射
   - 权限过滤
   - 风险级别过滤

4. 补 trace 测试：

   - 成功路径 trace
   - fallback 路径 trace
   - 错误路径 trace

**验收标准：**

- `smart-ticket-agent` 至少有一套能稳定跑通的单元测试。
- 后续改动不再完全靠手测。

------

## P1：把 RAG 和记忆补成“知识系统”

**目标：让 Agent 不只是会查历史，而是会管理和解释知识。**

### 任务 7：做知识准入，不是所有关闭工单都直接进库

**为什么做这个：**
 当前文档里 RAG 已经是“知识构建 + Embedding + rewrite + rerank + fallback”链路，而且 `checkSimilarCasesBeforeCreate` 只返回参考案例、不阻断创建。下一步要补的是“什么样的工单值得沉淀为知识”。

**要做什么：**

1. 设计知识准入规则：
   - 标题和描述是否完整
   - 评论是否足够
   - 是否有明确解决结论
   - 是否含敏感信息
   - 是否是重复/低质量工单
2. 关闭工单后，不直接入库，先进入 `KNOWLEDGE_CANDIDATE`。
3. 做一层知识候选审核：
   - 自动通过
   - 自动拒绝
   - 人工复核
4. 知识条目增加质量分和状态字段。

**建议落地位置：**

- `smart-ticket-domain/entity/TicketKnowledgeCandidate.java`
- `smart-ticket-rag/service/KnowledgeAdmissionService.java`

**验收标准：**

- 不是所有已关闭工单都直接进入知识库。
- 能解释“为什么这张工单被纳入/未纳入知识库”。

------

### 任务 8：把知识切片从“整单 embedding”升级成结构化知识

**为什么做这个：**
 当前 RAG 已经有 query rewrite、rerank 和双路径检索。下一步最大提升点，是让知识本身更可复用，而不是只靠整单相似度。

**要做什么：**

1. 每张候选知识自动生成这些字段：
   - 问题现象摘要
   - 根因摘要
   - 处理步骤摘要
   - 风险与注意事项
   - 适用范围
2. embedding 不只做全文，还要做：
   - title embedding
   - symptom embedding
   - resolution embedding
3. 检索结果返回时区分“相似工单”和“可复用解决片段”。
4. 给 RAG 结果加可解释字段：
   - whyMatched
   - similarFields
   - differenceFields

**验收标准：**

- 检索返回的不再只是工单列表，而是更像知识片段。
- Agent 能引用“处理步骤摘要”，而不是只说“找到了一张类似工单”。

------

### 任务 9：给 RAG 加反馈闭环

**为什么做这个：**
 现在有检索链路，但还缺“这条建议到底有没有用”的反馈。没有反馈，RAG 很难持续优化。

**要做什么：**

1. 在 Agent 回复历史案例后，加一个反馈接口：
   - 有帮助
   - 一般
   - 无帮助
   - 错误引用
2. 保存反馈数据，和知识条目、查询词关联起来。
3. 对低分知识做降权，对高分知识做提权。
4. 做一个简单统计报表：
   - 哪类知识最常被引用
   - 哪类知识反馈最差
   - 哪些查询最容易检索失败

**验收标准：**

- 每条知识都能积累使用反馈。
- 后续可据此调整 rerank 或知识质量分。

------

### 任务 10：把 session context 升级成三层记忆

**为什么做这个：**
 当前 Agent 已经有 pending action 和会话上下文，这很好，但还偏“临时状态”。文档也明确说明 pending action 是为了让多轮创建真正可用。下一步适合把记忆体系化。

**要做什么：**

1. 拆三层记忆：
   - 工作记忆：当前任务阶段、已收集字段、最近工具结果
   - 用户偏好记忆：用户常见工单类型、常用术语、偏好表达风格
   - 工单域记忆：某张工单最近关键事件、风险状态、审批状态
2. 工作记忆保留在 session 里。
3. 用户偏好记忆做弱持久化，不存敏感内容。
4. 工单域记忆做短期摘要缓存，供 Agent 查询和回复使用。

**验收标准：**

- Agent 在连续对话里能自然承接上下文。
- 查询同一张工单时，不必每次重新总结长评论链。

------

### 任务 11：补 Human-in-the-loop 机制

**为什么做这个：**
 你这个场景是工单和服务台，不适合一味追求自动执行。当前项目已经坚持 Tool Calling 只是业务边界，不绕过业务规则，这正好适合继续强化“该确认时就确认”的设计。

**要做什么：**

1. 给每个 skill 定义风险级别：
   - low：查工单、查知识
   - medium：创建工单
   - high：转派、关闭、审批相关操作
2. 高风险 skill 执行前必须二次确认。
3. 回复中展示执行摘要：
   - 我将对哪张工单做什么
   - 当前识别到的参数是什么
   - 是否存在歧义
4. 对敏感或高影响操作，默认只给建议，不自动执行。

**验收标准：**

- Agent 不会对高风险动作“默默执行”。
- 用户能在执行前看到清晰的操作摘要。

------

## P2：把 Agent 从“系统内助手”补到“跨系统助手”

**目标：让 Agent 开始具备外部能力接入和跨系统协作能力。**

### 任务 12：先做统一 Tool/Skill 协议，再接 MCP

**为什么做这个：**
 现在直接上 MCP，容易变成“为了名词而名词”。更好的顺序是先统一内部 skill 协议，再让外部能力也按同一协议接进来。

**要做什么：**

1. 统一内部 skill 的输入输出 schema。
2. 设计 `ExternalCapabilityAdapter`。
3. 先做 mock MCP 接入，不急着连复杂外部系统。
4. 让 Planner 能区分：
   - internal skill
   - external capability

**验收标准：**

- 即使还没接真实 MCP，系统内部已经具备统一能力协议。
- 新增外部能力时，不需要重写 Planner。

------

### 任务 13：接两个只读外部能力，证明“跨系统”

**为什么做这个：**
 真正让项目更像完整 Agent 的，不是更会聊天，而是更会串系统。

**推荐优先接的两个：**

1. 组织架构/人员信息查询
   - 查某用户属于哪个组
   - 查某工单当前处理人或组负责人
2. 知识文档查询
   - 查某系统操作手册
   - 查内部 FAQ

**后续再接：**

- CMDB
- 告警系统
- 发布记录
- 权限系统

**验收标准：**

- Agent 在工单场景中能同时使用内部工单能力和至少两个外部只读能力。
- 回答里能明确引用外部来源。

------

### 任务 14：加跨系统权限与来源解释

**为什么做这个：**
 外部能力一旦接进来，最容易出问题的是权限和可信度。

**要做什么：**

1. 外部能力调用前做权限校验。
2. 每次外部能力结果都带：
   - sourceSystem
   - sourceType
   - fetchedAt
   - trustLevel
3. 回答中区分：
   - 系统事实
   - 历史案例建议
   - 模型归纳总结

**验收标准：**

- 用户能看出来某条信息来自哪。
- 不会把外部系统原始信息和模型推断混为一谈。

------

## P3：最后才考虑多 Agent

**目标：在不破坏现有可控性的前提下，做明确分工的多 Agent。**

### 任务 15：只拆“值得拆”的角色

**为什么放最后：**
 你当前项目先做单 Agent + Planner + Skill + RAG + Eval，价值更高。多 Agent 放太早，复杂度会暴涨。

**建议只拆这几类角色：**

1. ```
   RoutingAgent
   ```

   - 专门做意图判断和阶段选择

2. ```
   KnowledgeAgent
   ```

   - 专门做案例检索、摘要和引用解释

3. ```
   ExecutionAgent
   ```

   - 专门做工单操作前确认和执行

4. ```
   SummaryAgent
   ```

   - 专门按 submitter / assignee / admin 生成摘要

**不建议现在就做：**

- 自由对话型多 Agent
- 大量角色互相争论式架构
- 完全自治规划型架构

**验收标准：**

- 每个 agent 的职责边界清楚。
- 单 Agent 主链仍可作为 fallback，不因为多 Agent 变得不稳定。

------

# 推荐执行顺序

## 第一阶段（最先做）

1. 轻量 Planner
2. Skill Registry
3. Agent Trace
4. 离线评估集
5. Prompt 规范化
6. Agent 核心测试

这一步做完，你的项目就会明显从“Agent 功能已接入”升级成“有完整 Agent 工程底座”。这也最符合当前 README 里“高完成度 MVP”的下一步方向。

## 第二阶段

1. 知识准入
2. 结构化知识切片
3. RAG 反馈闭环
4. 三层记忆
5. Human-in-the-loop

这一步做完，项目会更像“企业内真正能用的业务 Agent”。

## 第三阶段

1. 统一能力协议
2. 外部只读能力接入
3. 权限与来源解释

这一步做完，项目会开始从“工单系统内 Agent”变成“跨系统服务台 Agent”。

## 第四阶段

1. 多 Agent 明确分工

这一步是锦上添花，不是前置条件。