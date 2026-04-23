# 快速启动

## 1. 环境要求

- JDK 17
- Maven 3.9+
- MySQL 8.x
- Redis
- 可选：PostgreSQL + pgvector

## 2. 初始化数据库

先创建数据库：

```sql
CREATE DATABASE smart_ticket_platform DEFAULT CHARACTER SET utf8mb4;
```

然后执行：

```bash
mysql -h127.0.0.1 -uroot -p123456 < docs/sql/schema.sql
mysql -h127.0.0.1 -uroot -p123456 < docs/sql/seed.sql
```

## 3. 检查本地配置

应用配置文件：

- [application.yml](/D:/aaaAgent/smart-ticket-platform/smart-ticket-app/src/main/resources/application.yml)

当前默认依赖：

- MySQL：`127.0.0.1:3306`
- Redis：`192.168.100.128:6379`
- 可选 PGvector：`192.168.100.128:5432`

如果你的本地资源地址不同，请先修改 `application.yml`。

## 4. 启动项目

直接启动：

```bash
mvn -pl smart-ticket-app -am spring-boot:run
```

或者先打包再启动：

```bash
mvn "-Dmaven.test.skip=true" package
java -jar smart-ticket-app/target/smart-ticket-app-0.0.1-SNAPSHOT.jar
```

## 5. 测试与打包

执行全部测试：

```bash
mvn test
```

只验证主代码打包：

```bash
mvn "-Dmaven.test.skip=true" package
```

## 6. 演示账号

- `user1 / 123456`：普通用户
- `staff1 / 123456`：处理人员
- `admin1 / 123456`：管理员

## 7. 常用入口

- API 说明：[ticket-api.md](/D:/aaaAgent/smart-ticket-platform/docs/ticket-api.md)
- 演示脚本：[demo-playbook.md](/D:/aaaAgent/smart-ticket-platform/docs/demo-playbook.md)
- 项目概览：[project-overview.md](/D:/aaaAgent/smart-ticket-platform/docs/project-overview.md)
