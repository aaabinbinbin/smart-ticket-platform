# 快速启动

## 环境要求

- JDK 17
- Maven 3.9+
- MySQL 8.x
- Redis
- RabbitMQ
- 可选：PostgreSQL + pgvector

## 初始化数据库

创建数据库：

```sql
CREATE DATABASE smart_ticket_platform DEFAULT CHARACTER SET utf8mb4;
```

执行初始化脚本：

```bash
mysql -h127.0.0.1 -uroot -p123456 smart_ticket_platform < docs/sql/schema.sql
mysql -h127.0.0.1 -uroot -p123456 smart_ticket_platform < docs/sql/seed.sql
```

## 默认本地配置

配置文件：

- `smart-ticket-app/src/main/resources/application.yml`

当前默认依赖：

- MySQL：`127.0.0.1:3306`
- Redis：`127.0.0.1:6379`
- RabbitMQ AMQP：`192.168.100.128:5672`
- RabbitMQ 管理后台：`192.168.100.128:15672`
- pgvector：`192.168.100.128:5432`

RabbitMQ 当前账号：

```text
admin / admin
```

pgvector 当前配置：

```yaml
smart-ticket:
  ai:
    pgvector:
      url: jdbc:postgresql://192.168.100.128:5432/smart_ticket_vector
      username: postgres
      password: 123456
```

注意：`15672` 是 RabbitMQ 管理后台端口，Java 应用连接 RabbitMQ 使用 `5672`。

## 启动项目

直接启动：

```bash
mvn -pl smart-ticket-app -am spring-boot:run
```

或先打包再启动：

```bash
mvn -DskipTests package
java -jar smart-ticket-app/target/smart-ticket-app-0.0.1-SNAPSHOT.jar
```

## 运行测试

执行全部测试：

```bash
mvn test
```

只验证 Agent 和 API 相关模块：

```bash
mvn test -pl smart-ticket-api -am
```

## 演示账号

- `user1 / 123456`：普通用户
- `staff1 / 123456`：处理人员
- `admin1 / 123456`：管理员

## 常用接口

- Agent 对话：`POST /api/agent/chat`
- Agent trace 查询：`GET /api/agent/traces/by-session`
- Agent 最近指标：`GET /api/agent/traces/metrics/recent-by-user`
- 知识候选列表：`GET /api/agent/knowledge-candidates`
- 知识候选通过：`POST /api/agent/knowledge-candidates/{candidateId}/approve`
- 知识候选拒绝：`POST /api/agent/knowledge-candidates/{candidateId}/reject`
