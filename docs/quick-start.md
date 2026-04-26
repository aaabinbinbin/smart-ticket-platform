# 快速启动

5 分钟让这个项目在你的机器上跑起来。

---

## 你需要什么

| 组件 | 版本 | 作用 |
|------|------|------|
| JDK | 17+ | 编译运行 |
| Maven | 3.9+ | 构建 |
| MySQL | 8.x | 主数据库 |
| Redis | 7.x | 幂等锁、缓存、Session |
| RabbitMQ | 3.x（可选） | 知识构建消息 |
| PostgreSQL + pgvector | 15+（可选） | 向量检索主路径 |

实际上只装 JDK + MySQL + Redis 就能跑核心链路。RabbitMQ 和 PGVector 影响知识构建和向量检索，缺了也不影响工单 CRUD。

---

## 第一步：初始化数据库

MySQL 里建库建表：

```bash
mysql -h127.0.0.1 -P3306 -uroot -p < docs/sql/schema.sql
mysql -h127.0.0.1 -P3306 -uroot -p < docs/sql/seed.sql
```

`schema.sql` 建 26 张表，`seed.sql` 写入演示数据（3 个用户、默认组/队列、SLA 策略、分派规则、审批模板）。

---

## 第二步：检查配置

配置文件只有一个：`smart-ticket-app/src/main/resources/application.yml`

默认连接：

```yaml
MySQL:    127.0.0.1:3306  root/123456
Redis:    127.0.0.1:6379  无密码
RabbitMQ: 192.168.100.128:5672  admin/admin
PGVector: 192.168.100.128:5432  postgres/123456
```

如果你的环境和上面不同，改一下 `spring.datasource`、`spring.data.redis` 这几行就行。

RabbitMQ 和 PGVector 不通也没关系——启动不会挂，相关功能会自动降级。

---

## 第三步：启动

```bash
mvn -pl smart-ticket-app -am spring-boot:run
```

看到 `Started SmartTicketApplication` 就是好了。

---

## 第四步：验证

先登录拿 token：

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin1","password":"123456"}'
```

拿到 token 后，创建一张工单试试：

```bash
curl -X POST http://localhost:8080/api/tickets \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <token>' \
  -H 'Idempotency-Key: test-001' \
  -d '{"title":"测试环境登录报500","description":"影响研发自测"}'
```

应该返回创建成功。工单类型、分类、优先级都会自动补全。

---

## 演示账号

| 用户名 | 密码 | 角色 | 能做什么 |
|--------|------|------|---------|
| `user1` | `123456` | USER | 提工单、查自己的工单 |
| `staff1` | `123456` | STAFF | 处理工单、认领 |
| `admin1` | `123456` | ADMIN | 全部权限、管理配置 |

---

## 跑测试

```bash
mvn test
```

当前覆盖：工单创建/流转、SLA 扫描、Agent Tool 执行、RAG 检索/改写/重排、API 层幂等键校验。

---

## 启动不了？

| 现象 | 可能原因 |
|------|---------|
| 连不上 MySQL | 检查 `application.yml` 里的 datasource 配置 |
| Redis 连接失败 | `spring.data.redis.host` 不对，或 Redis 没启动 |
| 端口被占用 | 改 `server.port` |
| RabbitMQ 报错 | 没装的话把 `smart-ticket.knowledge.rabbit.enabled` 改成 `false` |
