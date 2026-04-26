---
skillCode: "transfer-ticket"
intent: TRANSFER_TICKET
toolBean: "transferTicketTool"
riskLevel: HIGH_RISK_WRITE
requireConfirm: true
requiredFields:
  - TICKET_ID
  - ASSIGNEE_ID
description: |
  将工单转派给其他处理人。
  高风险写操作，执行前必须经用户二次确认。
  只有管理员或当前处理人可以转派。
---

## 使用场景
- 用户要求将某张工单转给其他人处理

## 示例
- "把工单 1001 转给张三"
- "这张单转给 staff1 处理"
