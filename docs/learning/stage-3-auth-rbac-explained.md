# 阶段 3：认证、登录与基础 RBAC 实现说明

这份文档用于理解当前项目的认证与基础权限实现。它不是接口调用手册，而是解释“为什么这么设计、代码之间如何协作、RBAC 到底是什么”。

## 1. 这个阶段解决什么问题

阶段 3 解决两个基础问题：

1. 认证：系统怎么确认“你是谁”。
2. 基础授权：系统怎么确认“你有没有某类系统角色”。

在本项目中：

- 登录认证使用 Spring Security。
- 登录成功后签发 JWT。
- 后续请求通过 `Authorization: Bearer <token>` 证明自己的身份。
- 基础权限使用 RBAC，也就是基于角色的访问控制。

这个阶段不解决工单业务权限。

例如：

- “你是不是 STAFF”属于 auth 模块。
- “你是不是这张工单的当前处理人”不属于 auth 模块。
- “你能不能关闭这张工单”不属于 auth 模块。

后两者必须放到 `biz` 模块里，结合工单状态、提单人、处理人、管理员身份判断。

## 2. 认证、授权、RBAC 的区别

### 认证 Authentication

认证回答：

> 你是谁？

典型方式：

- 用户名 + 密码。
- 手机号 + 验证码。
- OAuth 第三方登录。
- 企业 SSO。

当前项目只做最基础的用户名密码登录。

对应代码：

- `AuthController`
- `AuthService`
- `CustomUserDetailsService`
- `PasswordEncoder`

### 授权 Authorization

授权回答：

> 你能不能访问这个资源或执行这个动作？

例如：

- 只有登录用户才能访问 `/api/examples/security/me`。
- 只有 STAFF 才能访问 `/api/examples/security/staff`。
- 只有 ADMIN 才能访问 `/api/examples/security/admin`。

对应代码：

- `SecurityConfig`
- `@PreAuthorize`
- `JwtAuthenticationFilter`
- `RestAccessDeniedHandler`

### RBAC

RBAC 是 Role-Based Access Control，中文叫“基于角色的访问控制”。

它的核心思想是：

> 不直接给用户分配权限，而是先给角色分配权限，再把用户绑定到角色。

最小模型是：

```text
用户 -> 角色 -> 权限
```

当前项目第一版没有单独做 `permission` 权限表，而是用角色直接控制接口访问：

```text
用户 -> 角色 -> 接口访问规则
```

例如：

```text
admin1 -> ADMIN -> 可以访问 /api/examples/security/admin
staff1 -> STAFF -> 可以访问 /api/examples/security/staff
user1  -> USER  -> 可以访问 /api/examples/security/user
```

这就是“基础 RBAC”。

## 3. Spring Security 是什么

Spring Security 是 Spring 体系里专门处理“认证和授权”的框架。

它的核心价值是：

> 在请求进入 Controller 之前，先完成登录状态识别和权限判断。

普通接口请求大致会经过：

```text
浏览器 / Postman
  -> Spring Security 过滤器链
  -> Controller
  -> Service
  -> Mapper
  -> Database
```

所以 Spring Security 可以在请求到达 Controller 之前先判断：

```text
有没有登录？
Token 对不对？
Token 是否过期？
角色够不够？
要不要直接返回 401 / 403？
```

这样安全逻辑不会散落在每个 Controller 方法里。

在当前项目中，Spring Security 负责：

1. 登录时校验用户名和密码。
2. 登录成功后让 `AuthService` 生成 JWT。
3. 每次请求时通过 JWT 识别当前用户。
4. 把当前用户放入 `SecurityContextHolder`。
5. 根据 `@PreAuthorize` 判断用户角色是否满足。
6. 统一处理 401 和 403。

## 4. Spring Security 核心对象

理解阶段 3 的代码，先记住几个 Spring Security 核心对象。

### UserDetails

`UserDetails` 是 Spring Security 内部认识的“用户对象”。

项目数据库里的用户实体是：

```text
SysUser
```

但 Spring Security 不直接使用 `SysUser`，它需要一个实现了 `UserDetails` 的对象。

所以项目里定义了：

```text
AuthUser
```

`AuthUser` 包装了：

- `userId`
- `username`
- `password`
- `realName`
- `enabled`
- `authorities`

Spring Security 通过它知道：

```text
当前用户是谁
密码哈希是什么
账号是否启用
当前用户有哪些角色
```

### UserDetailsService

`UserDetailsService` 是 Spring Security 用来“根据用户名加载用户”的接口。

项目里的实现类是：

```text
CustomUserDetailsService
```

它做的事情是：

1. 根据 `username` 查询 `sys_user`。
2. 根据 `user_id` 查询 `sys_user_role` 和 `sys_role`。
3. 把 `USER / STAFF / ADMIN` 转成 `ROLE_USER / ROLE_STAFF / ROLE_ADMIN`。
4. 组装成 `AuthUser` 返回给 Spring Security。

所以它是 Spring Security 和项目数据库之间的桥。

### AuthenticationManager

`AuthenticationManager` 是 Spring Security 的认证入口。

登录时，项目没有手写：

```java
if (password.equals(dbPassword)) {
    // 登录成功
}
```

而是交给：

```text
AuthenticationManager
```

它会调用 `CustomUserDetailsService` 加载用户，再调用 `PasswordEncoder` 校验密码。

### PasswordEncoder

`PasswordEncoder` 用于校验密码。

当前本地演示数据中密码是：

```text
{noop}123456
```

含义是：不加密，明文演示。

生产环境应该使用：

```text
{bcrypt}$2a$10$...
```

项目里使用：

```java
PasswordEncoderFactories.createDelegatingPasswordEncoder()
```

它能识别 `{noop}`、`{bcrypt}` 这类 Spring Security 标准密码前缀。

### SecurityContextHolder

`SecurityContextHolder` 可以理解为：

> 当前请求的安全上下文。

JWT 校验通过后，项目会把当前用户写入 `SecurityContextHolder`。

后续的 `@PreAuthorize` 就从这里读取当前用户的角色，判断是否允许访问接口。

## 5. 本项目为什么先做基础 RBAC

工单系统里权限有两层：

1. 系统角色权限。
2. 工单关系权限。

系统角色权限相对简单：

```text
USER / STAFF / ADMIN
```

工单关系权限更复杂：

```text
当前用户是不是提单人？
当前用户是不是当前处理人？
当前工单是不是 CLOSED？
当前用户是不是 ADMIN？
```

如果把这些都塞进 Spring Security，会让 auth 模块变得很混乱。

所以本项目约定：

- `auth` 模块只判断基础角色。
- `biz` 模块判断工单业务权限。

这是一个很重要的边界。

## 6. 数据库如何支撑 RBAC

当前有三张表：

```text
sys_user
sys_role
sys_user_role
```

### sys_user

保存用户基础身份：

- 用户名。
- 密码哈希。
- 真实姓名。
- 邮箱。
- 账号状态。

它回答：

> 系统里有没有这个人？

### sys_role

保存系统角色：

```text
USER
STAFF
ADMIN
```

它回答：

> 系统支持哪些角色？

### sys_user_role

保存用户和角色的绑定关系。

例如：

```text
user1  -> USER
staff1 -> USER + STAFF
admin1 -> USER + STAFF + ADMIN
```

它回答：

> 某个用户拥有哪些角色？

## 7. Spring Security 里的角色长什么样

数据库里保存的是：

```text
USER
STAFF
ADMIN
```

Spring Security 内部使用角色时，默认会加 `ROLE_` 前缀：

```text
ROLE_USER
ROLE_STAFF
ROLE_ADMIN
```

所以代码中有这一步转换：

```java
roleCode -> "ROLE_" + roleCode
```

位置：

```text
smart-ticket-auth/src/main/java/com/smartticket/auth/service/CustomUserDetailsService.java
```

使用 `@PreAuthorize` 时写：

```java
@PreAuthorize("hasRole('ADMIN')")
```

不要写：

```java
@PreAuthorize("hasRole('ROLE_ADMIN')")
```

原因是 `hasRole('ADMIN')` 会自动补成 `ROLE_ADMIN`。

如果你想直接写完整权限名，要使用：

```java
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
```

## 8. 登录链路如何运行

登录接口：

```http
POST /api/auth/login
```

代码入口：

```text
AuthController.login()
```

完整链路：

```text
AuthController
  -> AuthService
  -> AuthenticationManager
  -> CustomUserDetailsService
  -> SysUserMapper / SysUserRoleMapper
  -> PasswordEncoder
  -> JwtTokenProvider
```

### 第一步：Controller 接收请求

`AuthController` 接收：

```json
{
  "username": "admin1",
  "password": "123456"
}
```

它不直接查数据库，也不自己判断密码。

它只把请求交给：

```text
AuthService.login()
```

### 第二步：AuthService 发起认证

`AuthService` 调用：

```java
authenticationManager.authenticate(...)
```

这一步把用户名和密码交给 Spring Security。

### 第三步：加载用户

Spring Security 会调用：

```text
CustomUserDetailsService.loadUserByUsername()
```

这里会：

1. 通过 `SysUserMapper.findByUsername()` 查询用户。
2. 通过 `SysUserRoleMapper.findRolesByUserId()` 查询角色。
3. 把角色转换成 `ROLE_USER`、`ROLE_STAFF`、`ROLE_ADMIN`。
4. 组装成 `AuthUser`。

`AuthUser` 是 Spring Security 能理解的用户对象，它实现了 `UserDetails`。

### 第四步：校验密码

Spring Security 使用 `PasswordEncoder` 校验请求密码和数据库密码是否匹配。

当前 `seed.sql` 为了本地演示使用：

```text
{noop}123456
```

含义是：不加密，明文演示。

这只适合本地开发。

生产环境必须使用 BCrypt，例如：

```text
{bcrypt}$2a$10$...
```

### 第五步：生成 JWT

认证成功后，`JwtTokenProvider.generateToken()` 生成 JWT。

JWT 里放了：

- username。
- userId。
- realName。
- roles。
- 签发时间。
- 过期时间。

JWT 里不放：

- 密码。
- 工单权限判断结果。
- 敏感业务数据。

## 9. JWT 是什么

JWT 可以理解为一个被签名的登录凭证。

它解决的问题是：

> 用户登录成功后，后续请求如何证明自己已经登录？

传统 Session 方式：

```text
服务端保存 Session
浏览器保存 SessionId
```

JWT 方式：

```text
服务端签发 Token
客户端每次请求带 Token
服务端校验 Token 签名和过期时间
```

本项目采用 JWT，所以 Spring Security 配置为无状态：

```java
sessionCreationPolicy(SessionCreationPolicy.STATELESS)
```

含义是：

> 服务端不依赖 HTTP Session 保存登录状态。

JWT 通常由三部分组成：

```text
header.payload.signature
```

大概长这样：

```text
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbjEifQ.xxx
```

你不需要手动解析 JWT，本项目使用 JJWT 库完成生成、解析和签名校验。

### 为什么用了 JWT 还要每次查数据库

当前项目在请求携带 JWT 后，会根据 Token 里的 username 重新查询数据库加载用户和角色。

好处是：

- 用户被禁用后能尽快生效。
- 用户角色变更后能尽快生效。
- 不完全依赖 JWT 中旧的角色信息。

代价是：

- 每个受保护请求都会访问数据库。

后续可以通过 Redis 缓存用户权限来优化。

## 10. 请求受保护接口时发生了什么

假设访问：

```http
GET /api/examples/security/admin
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

请求链路：

```text
JwtAuthenticationFilter
  -> JwtTokenProvider
  -> CustomUserDetailsService
  -> SecurityContextHolder
  -> @PreAuthorize
  -> Controller
```

### 第一步：过滤器读取 Token

`JwtAuthenticationFilter` 从请求头读取：

```http
Authorization: Bearer <token>
```

如果没有 Token，请求不会被认证。

### 第二步：校验 Token

`JwtTokenProvider` 校验：

- 签名是否正确。
- Token 是否过期。

校验失败会清空安全上下文，后续返回 401。

### 第三步：重新加载用户角色

当前实现不是完全信任 JWT 里的角色，而是重新查数据库加载用户和角色。

好处：

- 用户角色变更后可以尽快生效。
- 用户被禁用后可以尽快拦截。

代价：

- 每次请求都要查数据库。

后续可以用 Redis 缓存优化。

### 第四步：写入 SecurityContext

认证成功后，会构造：

```text
UsernamePasswordAuthenticationToken
```

并写入：

```text
SecurityContextHolder
```

这表示：

> 当前请求已经知道是谁发起的，以及他拥有哪些角色。

### 第五步：执行 RBAC 判断

例如接口上写了：

```java
@PreAuthorize("hasRole('ADMIN')")
```

Spring Security 会检查当前用户有没有：

```text
ROLE_ADMIN
```

有则继续进入 Controller。

没有则返回 403。

## 11. 401 和 403 的区别

这两个状态码很容易混。

### 401 Unauthorized

含义：

> 你还没有被系统识别。

常见原因：

- 没登录。
- 没带 Token。
- Token 过期。
- Token 签名错误。

对应处理类：

```text
JwtAuthenticationEntryPoint
```

返回：

```json
{
  "success": false,
  "code": "UNAUTHORIZED",
  "message": "请先登录或提供有效令牌"
}
```

### 403 Forbidden

含义：

> 系统知道你是谁，但你权限不够。

常见原因：

- `user1` 访问 ADMIN 接口。
- `staff1` 访问 ADMIN 接口。

对应处理类：

```text
RestAccessDeniedHandler
```

返回：

```json
{
  "success": false,
  "code": "FORBIDDEN",
  "message": "当前用户没有访问权限"
}
```

一句话记忆：

```text
401：没认证，系统不知道你是谁
403：已认证，系统知道你是谁，但你权限不够
```

## 12. 当前代码文件职责

### auth 模块

```text
SecurityConfig
```

Spring Security 总配置：

- 关闭 CSRF。
- 设置无状态 Session。
- 配置哪些接口放行。
- 注册 JWT 过滤器。
- 开启方法级权限控制。

```text
JwtAuthenticationFilter
```

每个请求进来时检查 JWT。

```text
JwtTokenProvider
```

生成、解析和校验 JWT。

```text
CustomUserDetailsService
```

从数据库加载用户和角色。

```text
AuthService
```

执行登录认证并返回登录结果。

```text
AuthUser
```

Spring Security 使用的当前用户对象。

```text
JwtAuthenticationEntryPoint
```

处理 401。

```text
RestAccessDeniedHandler
```

处理 403。

### api 模块

```text
AuthController
```

登录接口。

```text
SecurityExampleController
```

RBAC 示例接口。

```text
ApiExceptionHandler
```

处理登录失败、参数校验失败等 Controller 层异常。

## 13. 为什么工单权限不能放进 auth

假设有一个接口：

```http
POST /api/tickets/1001/close
```

能不能关闭这张工单，不只取决于角色。

还要看：

- 当前用户是不是提单人？
- 当前用户是不是管理员？
- 工单是不是已经 RESOLVED？
- 工单是不是已经 CLOSED？
- 当前用户和这张工单有没有关系？

这些都属于工单业务规则。

如果写进 `auth`，会导致：

- `auth` 依赖工单业务。
- 权限逻辑分散。
- 以后 Agent 调用、HTTP 调用、后台任务调用很难复用同一套业务判断。

所以正确做法是：

```text
auth：判断基础身份和角色
biz：判断工单业务权限
```

后续可以在 `biz` 中实现：

```text
TicketPermissionService
```

负责：

- 是否可查看工单。
- 是否可分配工单。
- 是否可转派工单。
- 是否可关闭工单。

## 14. 阶段三完整链路

### 登录链路

```text
POST /api/auth/login
  -> AuthController
  -> AuthService
  -> AuthenticationManager
  -> CustomUserDetailsService
  -> SysUserMapper 查询用户
  -> SysUserRoleMapper 查询角色
  -> PasswordEncoder 校验密码
  -> JwtTokenProvider 生成 JWT
  -> 返回 token
```

### 访问接口链路

```text
GET /api/examples/security/admin
  -> JwtAuthenticationFilter
  -> JwtTokenProvider 校验 JWT
  -> CustomUserDetailsService 重新加载用户
  -> SecurityContextHolder 保存当前用户
  -> @PreAuthorize("hasRole('ADMIN')")
  -> 角色满足：进入 Controller
  -> 角色不满足：返回 403
```

可以把阶段三理解成三层：

```text
用户名密码 -> 确认你是谁
登录成功 -> 发一个 JWT
后续请求 -> 用 JWT 识别当前用户，再用 RBAC 判断接口角色
```

## 15. 如何测试理解 RBAC

执行 `seed.sql` 后有三个账号：

```text
user1  / 123456 -> USER
staff1 / 123456 -> USER + STAFF
admin1 / 123456 -> USER + STAFF + ADMIN
```

建议按下面顺序测试：

1. 不带 Token 访问 `/api/examples/security/me`，应返回 401。
2. 用 `user1` 登录，访问 `/user` 成功，访问 `/staff` 返回 403。
3. 用 `staff1` 登录，访问 `/user` 和 `/staff` 成功，访问 `/admin` 返回 403。
4. 用 `admin1` 登录，访问 `/user`、`/staff`、`/admin` 都成功。

这样可以清楚看到：

```text
没登录 -> 401
登录了但角色不够 -> 403
角色满足 -> 200
```

## 16. 当前实现的取舍

### 已完成

- 用户名密码登录。
- JWT 签发。
- JWT 过滤器校验。
- USER / STAFF / ADMIN 角色。
- `@PreAuthorize` 方法级权限。
- 401 / 403 JSON 返回。
- 登录失败和参数校验失败处理。

### 暂不做

- OAuth。
- 短信验证码。
- 邮箱验证码。
- 刷新 Token。
- Token 黑名单。
- 权限表和菜单权限。
- 工单级业务权限。

这些能力不是不重要，而是第一版项目要先保持认证链路清晰、可运行、可解释。

## 17. 你需要记住的核心结论

1. 认证是“你是谁”。
2. 授权是“你能做什么”。
3. RBAC 是“用户绑定角色，接口要求角色”。
4. Spring Security 内部角色一般是 `ROLE_` 前缀。
5. JWT 是无状态登录凭证，不应该放敏感数据。
6. 401 表示没认证，403 表示已认证但没权限。
7. `auth` 只管基础身份和角色，工单业务权限必须放在 `biz`。
