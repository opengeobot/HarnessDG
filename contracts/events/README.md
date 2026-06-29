# 事件契约目录

> 依据：`prd/DEEP_RESEARCH_内部AI资产管理平台设计.md` 第 5.12、6.7、14.1 节
> 作者：AxeXie

本目录用于存放平台事件（Outbox 事件、通知事件、Webhook 事件）的 Schema 定义，作为可靠事件
投递、通知渠道解耦与跨系统一致性（Saga / Outbox / Inbox）的权威来源。

## 状态

P0 阶段仅建立目录骨架；具体事件 Schema 在 **P1+** 随相关业务能力（版本发布、通知、Webhook
消费）按"契约优先"流程填充。

## 规划内容

- 通知/领域事件 Schema，例如：`VERSION_REVIEW_REQUESTED`、`VERSION_APPROVED`、
  `VERSION_REJECTED`、`VERSION_PUBLISHED`、`VERSION_DEPRECATED`、`UPLOAD_FAILED`、
  `JOB_DEAD`、`AGENT_ACCESS_DENIED`、`STORAGE_QUOTA_WARNING`、`SYSTEM_DEPENDENCY_UNHEALTHY`。
- Gitea Webhook 入站事件 Schema（`push`、`create/delete tag`、`repository`、`release`、
  `member/team`），用于 `webhook_inbox` 幂等消费。

## 约束

- 事件基于 Outbox 异步发布，模板与渠道解耦；失败不回滚已完成的核心业务事务，但必须可重试、
  可观测。
- 事件须携带稳定标识与版本字段，便于幂等消费与兼容演进。
- 对外 Webhook 带签名、时间戳与 Delivery ID，并防止 SSRF。
