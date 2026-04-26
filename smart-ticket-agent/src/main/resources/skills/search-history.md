---
skillCode: "search-history"
intent: SEARCH_HISTORY
toolBean: "searchHistoryTool"
riskLevel: READ_ONLY
requireConfirm: false
requiredFields: []
description: |
  检索历史已关闭工单中的相似案例和处理经验。
  只读操作，用于提供参考经验，不影响当前工单。
  通过 RAG 双路检索（originalQuery + rewrittenQuery）实现。
---

## 使用场景
- 用户想知道类似问题以前是怎么处理的
- 新问题来了，想参考历史方案

## 示例
- "以前有没有类似的登录失败问题"
- "查一下历史案例，数据库连接超时怎么处理"
