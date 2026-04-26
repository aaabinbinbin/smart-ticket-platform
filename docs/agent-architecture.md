# Agent 架构

这是"受控型业务 Agent"——让 LLM 参与理解和表达，但业务动作由后端严格控制。

---

## 为什么不是"接个 LLM 让用户聊天"

把 LLM 直接暴露给用户的 Agent 有三个问题：

1. **不可控**：模型可能胡说、编造操作、绕过权限
2. **不稳定**：同一句话今天走 tool A，明天可能走 tool B
3. **不可审计**：你不知道每一步决策是什么、为什么

这个项目的 Agent 解法是：**把决策链路拆开，让规则管住边界，让 LLM 只在安全区内工作**。

---

## 一条请求怎么走

```
POST /api/agent/chat  {"message":"登录失败，帮我建工单"}

┌─ loadSession()           ← 从 Redis 恢复上下文（上一轮的状态、活跃工单）
├─ hydrateMemory()         ← 加载用户偏好记忆（过滤过期的）
├─ routeIntent()           ← LLM 分类 → 失败 → 关键词匹配 → "CREATE_TICKET"
├─ resolvePolicy()         ← 决定执行模式：直接执行 / 需要确认 / 降级
├─ buildPlan()             ← 生成执行计划：当前阶段、下一步动作、缺哪些参数
├─ execute()
│   ├─ 缺参数 → saveDraft → 反问用户（多轮澄清）
│   ├─ 写操作 → guard → 高风险二次确认 → 确定性命令执行
│   └─ 读操作 → ReAct（只暴露只读工具）或 fallback 确定性查询
├─ renderReply()           ← 把结果翻译成自然语言
├─ remember()              ← 记录用户偏好、短期上下文
└─ writeTrace()            ← 持久化完整的执行轨迹
```

---

## 核心设计：意图路由

路由不是全交给 LLM。它是两层：

```
请求进来
  ↓
LLM 分类（可选，配置开启）
  ├── 成功 → 用 LLM 结果
  └── 失败/不可用 → 关键词匹配
```

关键词匹配用简单规则：消息里出现"历史""类似"→ SEARCH_HISTORY，出现"转""移交"→ TRANSFER，出现"创建""新建"→ CREATE，默认→ QUERY。

这样即使 LLM 服务挂了，核心 Agent 链路仍然可用。

---

## 核心设计：写操作不走 LLM

这是和很多 AI Agent 项目最大的不同。

创建工单、转派工单——这些**不是**从 LLM Tool Calling 直接调用的。

流程是：

```
intent route → parameter extraction → guard → 确定性命令执行
```

LLM 只负责**理解用户意图**和**补全缺失参数**。一旦参数齐了，执行由 `DeterministicCommandExecutor` 完成——它直接调 biz 层的 `TicketCommandService`，和 HTTP 接口走的完全是同一个入口。

为什么？因为这样：
- 权限检查不会被绕过
- 日志和审计完整统一
- Agent 不会变成"第二套业务入口"

---

## 核心设计：高风险操作二次确认

转派工单标记了 `HIGH_RISK_WRITE + requireConfirmation=true`。

执行链路：

```
用户说"转给张三"
→ guard 识别为高风险 → 不执行 → 保存 pendingAction
→ 回复"确认将工单 1001 转派给张三吗？"
→ 用户回复"确认"
→ pendingActionCoordinator 恢复 → 再次 guard → 这次放行 → 执行
```

用户不确认、或回复"取消"，不会发生任何业务变更。

---

## 核心设计：确定性和 Spring AI 双路径

```
读操作（查询工单、检索历史）
├── Spring AI 可用 → ReAct（暴露只读工具给模型）
└── Spring AI 不可用 → 确定性查询（直接调 biz 层读接口）

写操作（创建、转派）
└── 始终走确定性命令（不经过 LLM Tool Calling）
```

这保证了：**没有模型服务，系统也能正常运行**。面试官问到这一层时，你可以说"在 LLM 不可用时，我设计了完整的确定性降级路径"。

---

## 三类记忆

| 记忆类型 | 存储 | 生命周期 | 例子 |
|---------|------|---------|------|
| 工作记忆 | Redis Session | 会话有效 | "当前正在处理的工单是 1001" |
| 工单领域记忆 | Redis | 30min | "刚才查过这张单的状态" |
| 用户偏好记忆 | MySQL | 持久化 | "user1 常用的工单类型是 INCIDENT" |

每条记忆都有可靠性元数据：`source`（来源）、`confidence`（置信度 0-1）、`expiresAt`（过期时间）。低置信度记忆只推荐不自动执行。

> 记忆不是权威事实源。涉及工单状态、处理人、审批——必须实时查数据库。

---

## 多轮澄清（补槽）

创建工单时，用户可能只说"登录失败，帮我建工单"。

Agent 的处理：

```
第一轮：用户说"登录失败，帮我建工单"
→ 参数提取：title="登录失败"，缺 description
→ 反问："请补充一下问题描述，比如具体报错是什么、影响了多少人"

第二轮：用户补充"登录时报 500 错误，影响研发自测"
→ 参数完整 → guard 通过 → 创建
```

`PendingActionCoordinator` 在这里负责：记住草稿、合并新参数、判断是否补全了。

---

## 可观测性

每次 Agent 调用有完整 Trace：

```http
GET /api/agent/traces/by-session?sessionId=xxx
GET /api/agent/traces/metrics/recent-by-user?userId=1&limit=50
```

Trace 记录：意图、置信度、用了 Spring AI 还是 fallback、每一步耗时、最终结果。

---

## Session 管理

- 同一 sessionId 的请求互斥（JVM 锁），防止并发写入冲突
- Session TTL 2 小时
- PendingAction TTL 30 分钟，过期自动失效

---

## 限流与降级

- 用户级限流：60 req/min（可配置）
- 全局限流：600 req/min
- 预算控制：每轮最多 1 次 LLM 调用、4 次 Tool 调用、1 次 RAG 调用
- 超时：单轮 120s

降级策略：
- LLM 不可用 → 关键词路由 + 确定性查询
- ReAct 失败 → 确定性 fallback
- 预算超限 → 返回结构化降级摘要

---

## 当前边界

- Agent 是受控型，不是完全自治
- Skill 由 Java 类注册（不是动态加载）
- Session lock 是单实例 JVM 锁，多实例需换 Redis lock
- Agent 不会主动查数据库做决策——它只调用 Tool 和执行确定性命令
