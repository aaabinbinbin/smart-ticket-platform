# 面试演示指南

这篇文章帮你准备面试时的现场演示。不用全讲——挑 2-3 个最体现设计功底的场景就够了。

---

## 推荐流程（10 分钟版）

### 1. 启动 + 登录（30 秒）

```bash
mvn -pl smart-ticket-app -am spring-boot:run
```

```bash
curl -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"staff1","password":"123456"}'
```

### 2. 创建工单——只传标题和描述（1 分钟）

```bash
curl -X POST localhost:8080/api/tickets \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <token>' \
  -H 'Idempotency-Key: demo-001' \
  -d '{"title":"测试环境登录报500","description":"影响研发自测，已有3人反馈"}'
```

观察返回：工单类型自动设为了 INCIDENT、分类 SYSTEM、优先级 MEDIUM，typeProfile 自动补全了 symptom 和 impactScope。

**讲什么**：系统根据标题里的"报错""500"关键词自动推断出这是故障类工单，用户不需要懂工单分类体系。enrichment 在 TicketCommandService 内部统一调用，HTTP 和 Agent 入口自动受益。

### 3. Agent 创建工单（1 分钟）

```bash
curl -X POST localhost:8080/api/agent/chat \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <token>' \
  -d '{"message":"登录失败，帮我建个工单","sessionId":"session-1"}'
```

Agent 会反问："请补充一下问题描述"。再发一条补充描述后，工单创建成功。

**讲什么**：Agent 走的是多轮澄清链路——缺参数时不瞎猜，而是反问用户。创建走的是确定性命令执行器，不是 LLM 直接调工具。和 HTTP 接口走同一个 TicketCommandService。

### 4. Agent 查工单 + 摘要（1 分钟）

```bash
curl -X POST localhost:8080/api/agent/chat \
  -H 'Authorization: Bearer <token>' \
  -d '{"message":"帮我查下刚才创建的工单的摘要","sessionId":"session-1"}'
```

**讲什么**：同一张工单，提单人/处理人/管理员看到的摘要重点不同。Agent 理解上下文，能推断出"刚才创建的工单"是指哪一张。

### 5. 展示 RAG 检索（1 分钟）

```bash
curl 'localhost:8080/api/rag/search?query=登录失败&topK=3' \
  -H 'Authorization: Bearer <token>'
```

观察 `retrievalPath` 和 `fallbackUsed` 字段，确认检索路径。

**讲什么**：RAG 知识来自已关闭的工单，不是外部文档。双路检索（originalQuery + rewrittenQuery）+ 安全规则保护。rewrite 不安全时自动降级到原始查询。

---

## 如果面试官想深挖

### 关于"受控 Agent"

> "你为什么不直接把创建工单做成 LLM 的 tool？"

回答要点：LLM 可能在某些情况下不调用 tool、调用错误的 tool、或对参数做不当修改。业务写操作应该走确定性路径——参数校验、权限校验、风险确认、执行、记日志——每一步都不能依赖模型行为。LLM 在这里只做理解和表达。

### 关于"enrichment 怎么工作"

> "用户不传 type 和 category，系统怎么知道？"

回答要点：enrichment 有三级策略——用户显式传入 > 规则推断（关键词匹配）> 默认兜底。规则不完备时，LLM enrichment 分支可以启用（需配置），但规则始终作为 fallback。

### 关于"为什么还需要双路检索"

> "你都有 PGVector 了，为什么还要 MySQL fallback？"

回答要点：个人项目和简历项目的演示环境不一定有完整的 PGVector 环境。fallback 保证核心检索链路在任何环境下都能跑通。生产环境下 PGVector 主路径提供更好的语义检索质量。

---

## 演示前检查清单

- [ ] MySQL、Redis 已启动
- [ ] `schema.sql` 和 `seed.sql` 已执行
- [ ] 应用启动无报错
- [ ] `POST /api/auth/login` 能成功拿到 token
- [ ] 提前准备好 curl 命令或 Postman collection
- [ ] 如果网络不稳，把 `llm-enabled` 设为 false（避免 LLM 调用超时）
