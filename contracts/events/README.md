# 事件契约目录

> 依据：`prd/DEEP_RESEARCH_内部AI资产管理平台设计.md` 第 5.12、6.7、14.1 节
> 作者：AxeXie

本目录用于存放平台事件（Outbox 事件、通知事件、Webhook 事件）的 Schema 定义，作为可靠事件
投递、通知渠道解耦与跨系统一致性（Saga / Outbox / Inbox）的权威来源。

## 状态

P0-A 只建立了目录骨架。P0-B 已先行定义统一事件信封（Envelope）与设计第 5.12 节列出的
通知/领域事件负载 Schema，见 [`events-v1.yaml`](./events-v1.yaml)，再实现 Outbox/Inbox 和
通知能力。版本发布、上传、Webhook 消费等业务事件在 P1+ 继续沿用同一 Envelope，按"契约优先"
流程增加。Gitea Webhook 入站事件 Schema 在 P1+ 接入时按同一约定补充。

## 权威 Schema

- [`events-v1.yaml`](./events-v1.yaml)：统一 `EventEnvelope` 与各 `eventType` 的脱敏负载，
  含 `VERSION_REVIEW_REQUESTED`、`VERSION_APPROVED`、`VERSION_REJECTED`、`VERSION_PUBLISHED`、
  `VERSION_DEPRECATED`、`UPLOAD_FAILED`、`JOB_DEAD`、`AGENT_ACCESS_DENIED`、
  `STORAGE_QUOTA_WARNING`、`SYSTEM_DEPENDENCY_UNHEALTHY`。
  信封字段至少携带 `eventId`、`eventType`、`schemaVersion`、`occurredAt`、`traceId`、
  `principalId`、`aggregateType`、`aggregateId` 和脱敏后的 `payload`。

## Outbox 投递与签名约定

- **Outbox 原子写入**：事件在产生事件的同一数据库事务内写入 `outbox_event` 表，与核心业务
  变更原子提交；再由后台可靠任务（`job_task` + Worker）轮询发布到站内通知、邮件、Webhook 与
  Agent Callback。发布失败不回滚已完成的核心业务事务，但必须可重试、可观测；超过阈值的投递
  进入 `DEAD` 并告警。
- **幂等**：消费方以 `eventId` 作为幂等键去重；入站 Gitea Webhook 以 `deliveryId` 写入
  `webhook_inbox` 去重。
- **Webhook 签名**：对外 Webhook 投递携带 `X-AIHub-Event`、`X-AIHub-Delivery`、
  `X-AIHub-Timestamp` 与 `X-AIHub-Signature`（`sha256=<hex>`，对 `{timestamp}.{rawBody}`
  做 HMAC-SHA256）。接收方校验签名与时间戳容忍窗口以防重放；出站侧做 SSRF 防护。
  签名密钥属于部署安全配置，不进入数据库配置中心、Git、日志或事件负载。
  详见 `events-v1.yaml` 的 `x-delivery-conventions` 扩展。

## 规划内容

- 通知/领域事件 Schema，例如：`VERSION_REVIEW_REQUESTED`、`VERSION_APPROVED`、
  `VERSION_REJECTED`、`VERSION_PUBLISHED`、`VERSION_DEPRECATED`、`UPLOAD_FAILED`、
  `JOB_DEAD`、`AGENT_ACCESS_DENIED`、`STORAGE_QUOTA_WARNING`、`SYSTEM_DEPENDENCY_UNHEALTHY`。
- Gitea Webhook 入站事件 Schema（`push`、`create/delete tag`、`repository`、`release`、
  `member/team`），用于 `webhook_inbox` 幂等消费。
- P0-B 公共事件至少包括：用户启用/禁用、角色/ACL 变更、字典/标签/配置变更、Token 吊销、
  `JOB_DEAD`、`AGENT_ACCESS_DENIED`、通知请求和签名 Webhook Delivery。

## 约束

- 事件基于 Outbox 异步发布，模板与渠道解耦；失败不回滚已完成的核心业务事务，但必须可重试、
  可观测。
- 事件须携带稳定标识与版本字段，便于幂等消费与兼容演进。
- 公共 Envelope 至少携带 `eventId`、`eventType`、`schemaVersion`、`occurredAt`、`traceId`、
  `principalId`、`aggregateType`、`aggregateId` 和脱敏后的 `payload`。
- 对外 Webhook 带签名、时间戳与 Delivery ID，并防止 SSRF。
