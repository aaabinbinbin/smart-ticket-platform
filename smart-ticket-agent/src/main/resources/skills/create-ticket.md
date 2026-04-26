---
skillCode: "create-ticket"
intent: CREATE_TICKET
toolBean: "createTicketTool"
riskLevel: LOW_RISK_WRITE
requireConfirm: false
requiredFields:
  - TITLE
  - DESCRIPTION
description: |
  创建一张新工单，只需标题和描述即可。
  系统会自动补全类型、分类、优先级和类型画像。
  写操作，但不需用户确认——由确定性命令链路执行。
---

## 使用场景
- 用户明确要创建新工单
- 用户描述了问题，需要记录为工单

## 示例
- "帮我建一个工单，登录时报 500"
- "创建工单，测试环境 Redis 连接超时"
