# 演示脚本

更新时间：`2026-04-22`

本文档用于面试演示或本地走查，重点覆盖：

1. `C5` 多视角摘要
2. `PGvector` 主检索路径

## 1. C5 多视角摘要演示

### 目标

展示同一张工单，系统可以从不同角色视角输出不同摘要。

### 建议演示数据

- 工单类型：`ACCESS_REQUEST`
- 状态：`PENDING_ASSIGN` 或 `PROCESSING`
- 已提交审批，审批状态为 `PENDING`
- 至少有一条评论和一条操作日志

### 演示接口

```http
GET /api/tickets/{ticketId}/summary?view=SUBMITTER
GET /api/tickets/{ticketId}/summary?view=ASSIGNEE
GET /api/tickets/{ticketId}/summary?view=ADMIN
```

### 演示要点

- `SUBMITTER`：强调当前状态、处理人、审批状态、下一步
- `ASSIGNEE`：强调问题现象、类型资料、最新评论、最近操作
- `ADMIN`：强调风险级别、审批阻塞、是否久未更新、是否存在转派过多

### Agent 演示话术

可以直接使用：

- `帮我总结一下 1001 号工单当前进展`
- `从处理人视角总结 1001`
- `给我一个 1001 号工单的管理员风险摘要`

预期效果：

- Agent 仍走 `QUERY_TICKET` 工具链
- 查询结果返回结构化摘要对象
- 回复文本会直接引用摘要标题与摘要正文

## 2. PGvector 主路径演示

### 目标

证明项目不是只有 MySQL fallback，而是具备可切换的向量主检索路径。

### 环境准备

1. 准备 PostgreSQL 并安装 `pgvector` 扩展
2. 配置如下环境变量

```powershell
$env:SMART_TICKET_AI_VECTOR_STORE_ENABLED="true"
$env:SMART_TICKET_AI_EMBEDDING_ENABLED="true"
$env:PGVECTOR_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:5432/smart_ticket_vector"
$env:PGVECTOR_DATASOURCE_USERNAME="postgres"
$env:PGVECTOR_DATASOURCE_PASSWORD="123456"
```

3. 确保 `smart-ticket-app/src/main/resources/application.yml` 中的默认配置可被上面环境变量覆盖

### 演示步骤

1. 启动应用
2. 准备至少一条已关闭工单，触发知识构建
3. 通过 Agent 或检索服务发起历史检索请求，例如：

- `查一下历史上类似的登录失败处理方案`

4. 观察应用日志

### 预期日志

成功走主路径时，应看到：

```text
rag retrieval path=PGVECTOR, fallbackUsed=false
```

若主路径未准备好，会看到：

```text
spring ai vector store retrieval failed, fallback to mysql vectors
rag retrieval path=MYSQL_FALLBACK, fallbackUsed=true
```

### 面试时建议表述

- 当前代码已经支持 PGvector 主路径与 MySQL fallback 双路径
- 本地开发默认允许 fallback，保证没有外部依赖时主链仍可演示
- 真实演示时可通过环境变量切换到 PGvector 主路径，并通过日志确认生效

## 3. 推荐的面试演示顺序

1. 登录并创建一张多类型工单
2. 展示自动分派 / 审批流
3. 展示工单详情与 `summary` 接口
4. 用 Agent 发起摘要型查询
5. 用 Agent 发起历史检索，并展示 `PGvector` 或 fallback 路径日志
