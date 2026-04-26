# 智能工单平台

一个让你在面试中聊得起来的 Java 后端项目。

## 它解决了什么问题

你平时提工单可能只需要填个标题和描述。但工单系统背后，**真正的复杂度**在于：

- 不同类型的工单需要不同的信息结构（报故障要现象和影响范围，申权限要账号和资源，变更要回滚方案）
- 谁派单、谁处理、谁审批——这些不是写死规则，而是动态匹配
- SLA 违约了怎么办？只记录不行，要自动升级
- 用户不想手动填类型、分类、优先级——系统应该能从"登录报 500"自动推断出这是一个系统类故障工单
- 能不能用自然语言直接操作？"帮我查下 1001 的状态"、"把这张单转给张三"
- 历史工单里的处理经验，能不能在下次遇到相似问题时自动推出来

这个项目把上述问题一一落地了。它不是"又一个 Spring Boot CRUD demo"。

## 你可以在面试中怎么聊

**讲架构**：9 个模块，怎么分层、为什么这么分、边界在哪里
**讲业务建模**：工单不是一张表，是状态机 + 类型系统 + 权限模型 + 审批 + SLA 的耦合体
**讲 Agent 设计**：为什么是"受控 Agent"，不是让大模型为所欲为；意图路由、确定性命令、高风险确认是怎么实现的
**讲 RAG 设计**：知识从哪来（工单关闭 → 知识构建），怎么检索（双路召回 + 重排），怎么保证可靠（MQ + 定时补偿）
**讲工程取舍**：模块化单体 vs 微服务、规则兜底 vs LLM 兜底、双路检索 vs 纯向量检索

## 启动只需要 5 分钟

```bash
# 1. 建表 + 种子数据
mysql -h127.0.0.1 -P3306 -uroot -p < docs/sql/schema.sql
mysql -h127.0.0.1 -P3306 -uroot -p < docs/sql/seed.sql

# 2. 启动
mvn -pl smart-ticket-app -am spring-boot:run

# 3. 试试
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin1","password":"123456"}'
```

前置：JDK 17、MySQL 8.x、Redis。RabbitMQ 和 PGVector 是可选的。

演示账号：`user1` / `staff1` / `admin1`，密码都是 `123456`。

## 继续阅读

| 你想了解什么 | 看这篇 |
|-------------|--------|
| 这个项目到底能做什么、为什么这么设计 | [项目概览](docs/project-overview.md) |
| 想亲手跑起来 | [快速启动](docs/quick-start.md) |
| API 怎么调用、有哪些接口 | [接口说明](docs/ticket-api.md) |
| 每个模块承担什么角色、设计决策 | [详细设计](docs/project-deep-dive.md) |
| Agent 的架构、执行链路、安全机制 | [Agent 架构](docs/agent-architecture.md) |
| 面试时怎么演示 | [演示脚本](docs/demo-playbook.md) |
| RAG 评估指标和方法 | [RAG 评估](docs/rag-evaluation.md) |
| 部署 PGVector | [PGVector 部署](docs/pgvector-setup.md) |

## 技术栈

Java 17 · Spring Boot 3.4 · Spring Security · MyBatis · MySQL · Redis · RabbitMQ · Spring AI · PostgreSQL + pgvector
