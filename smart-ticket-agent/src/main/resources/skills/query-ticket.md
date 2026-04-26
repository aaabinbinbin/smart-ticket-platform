---
skillCode: "query-ticket"
intent: QUERY_TICKET
toolBean: "queryTicketTool"
riskLevel: READ_ONLY
requireConfirm: false
requiredFields:
  - TICKET_ID
description: |
  查询工单的详情、状态、列表或摘要。
  只读操作，不需要用户确认。
  如果用户提供了工单ID，查详情；否则查列表。
---

## 使用场景
- 用户询问某张工单的状态或详情
- 用户想看自己的工单列表
- 用户想看工单的摘要

## 示例
- "查下工单 1001"
- "我有哪些待处理的工单"
- "最近有哪些紧急工单"
