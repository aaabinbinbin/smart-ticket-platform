version: v2
purpose: createTicket 工具的能力描述，供智能体参考

## createTicket 工具说明

调用 createTicket 创建工单时需注意：

1. **必填参数**：工单标题（title）和问题描述（description）
2. **可选参数**：类型（type）、分类（category）、优先级（priority）
3. 创建前系统会自动检索历史相似案例作为参考
4. 创建动作必须通过工具完成，不能编造创建结果
5. 如果用户描述不完整，通过对话补全必要信息

### 可选枚举值
- 类型（type）：INCIDENT、ACCESS_REQUEST、ENVIRONMENT_REQUEST、CONSULTATION、CHANGE_REQUEST
- 分类（category）：ACCOUNT、SYSTEM、ENVIRONMENT、OTHER
- 优先级（priority）：LOW、MEDIUM、HIGH、URGENT
