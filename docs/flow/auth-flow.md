# 认证与基础权限流程

> 如果你想系统理解阶段 3 的实现原理和 RBAC 概念，请先阅读：
> `../learning/stage-3-auth-rbac-explained.md`

## 1. 范围

当前版本只实现基础认证和基础 RBAC：

- 用户名密码登录。
- Spring Security 认证。
- JWT 签发和校验。
- USER / STAFF / ADMIN 三种系统角色。
- 基于角色的接口访问控制。

当前版本不做：

- OAuth。
- 短信验证码。
- 邮箱验证码。
- 第三方登录。
- 工单级业务权限判断。

注意：`auth` 模块只回答“你是谁”和“你有哪些系统角色”。“你能不能关闭这张工单”“你能不能把这张工单转派给某人”这类判断必须放在 `biz` 模块中，结合工单状态、当前处理人、提单人、管理员身份等业务关系判断。

## 2. 角色说明

系统角色来自 `sys_role` 表：

- `USER`：普通用户，通常用于提单和查看自己可见范围内的工单。
- `STAFF`：处理人员，通常用于接单、处理、评论、解决工单。
- `ADMIN`：管理员或组长，通常用于分配、监管和调整流程。

角色写入 Spring Security 时会自动加上 `ROLE_` 前缀：

- `USER` -> `ROLE_USER`
- `STAFF` -> `ROLE_STAFF`
- `ADMIN` -> `ROLE_ADMIN`

接口上使用：

```java
@PreAuthorize("hasRole('STAFF')")
```

不要写成：

```java
@PreAuthorize("hasRole('ROLE_STAFF')")
```

## 3. 登录流程

登录接口：

```http
POST /api/auth/login
Content-Type: application/json
```

请求体：

```json
{
  "username": "admin1",
  "password": "123456"
}
```

处理流程：

1. `AuthController` 接收用户名和密码。
2. `AuthService` 调用 Spring Security 的 `AuthenticationManager`。
3. `CustomUserDetailsService` 根据用户名查询 `sys_user`。
4. `SysUserRoleMapper` 查询用户绑定的角色。
5. Spring Security 使用 `PasswordEncoder` 校验密码。
6. 认证成功后，`JwtTokenProvider` 生成 JWT。
7. 接口返回 `accessToken`、用户信息和角色列表。

返回示例：

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 7200,
    "userId": 3,
    "username": "admin1",
    "realName": "管理员",
    "roles": ["USER", "STAFF", "ADMIN"]
  }
}
```

## 4. 访问受保护接口

登录成功后，请在请求头中携带 JWT：

```http
Authorization: Bearer <accessToken>
```

示例接口：

```http
GET /api/examples/security/me
GET /api/examples/security/user
GET /api/examples/security/staff
GET /api/examples/security/admin
```

权限要求：

- `/api/examples/security/me`：只要求登录。
- `/api/examples/security/user`：要求 `USER`。
- `/api/examples/security/staff`：要求 `STAFF`。
- `/api/examples/security/admin`：要求 `ADMIN`。

## 5. JWT 校验流程

每次请求受保护接口时：

1. `JwtAuthenticationFilter` 从 `Authorization` 请求头读取 Bearer Token。
2. `JwtTokenProvider` 校验签名和过期时间。
3. 从 JWT subject 中取出 username。
4. `CustomUserDetailsService` 重新加载用户和角色。
5. 构造 `Authentication` 并写入 `SecurityContextHolder`。
6. Spring Security 根据 URL 规则和 `@PreAuthorize` 判断是否允许访问。

当前实现选择“每次请求重新加载用户和角色”，这样用户角色变更后不必等 JWT 过期才生效。代价是每次请求会访问数据库，后续可以通过 Redis 缓存用户权限。

## 6. 异常返回

未登录或 Token 无效：

```json
{
  "success": false,
  "code": "UNAUTHORIZED",
  "message": "请先登录或提供有效令牌"
}
```

已登录但角色不足：

```json
{
  "success": false,
  "code": "FORBIDDEN",
  "message": "当前用户没有访问权限"
}
```

用户名或密码错误：

```json
{
  "success": false,
  "code": "BAD_CREDENTIALS",
  "message": "用户名或密码错误"
}
```

## 7. 本地演示账号

执行 `docs/sql/seed.sql` 后会创建三个演示账号：

```text
user1 / 123456   -> USER
staff1 / 123456  -> USER + STAFF
admin1 / 123456  -> USER + STAFF + ADMIN
```

`seed.sql` 为了本地演示使用 `{noop}123456`。生产环境必须改为 BCrypt 哈希，不允许保存明文或 noop 密码。
