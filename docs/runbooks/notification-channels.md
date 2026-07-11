# 通知外发渠道 Runbook

> 作者：AxeXie | 日期：2026-07-11

## 概述

平台通知分两层：

1. **站内通知（in-app）**：同步写入 `notification` 表，由 `NotificationService.sendInAppNotification` 创建。
2. **外发渠道（channel）**：通过 `NotificationChannel` 端口异步投递，当前实现：

| 渠道 | 类 | 状态 | 启用方式 |
| --- | --- | --- | --- |
| Webhook | `WebhookChannelAdapter` | 已实现（写 Outbox + `WebhookDeliveryService`） | 默认装配 |
| Email | `EmailChannelAdapter` | 桩（结构化日志） | `aihub.notification.email.enabled=true` |
| IM | `ImChannelAdapter` | 桩（结构化日志） | 默认装配 |

## Email 渠道

```yaml
aihub:
  notification:
    email:
      enabled: true
      from: noreply@example.com
      smtp-host: smtp.example.com
      smtp-port: 587
```

当前桩实现不实际连接 SMTP。生产对接需：

- 注入 SMTP 凭据（环境变量/Secret，禁止入 Git）
- 在 `EmailChannelAdapter` 中接入 Jakarta Mail 或 Spring Mail
- 为每位用户维护邮箱映射（`principal_id → email`）

## IM 渠道

`ImChannelAdapter` 为占位实现。对接企业 IM 时建议：

1. 定义 `ImChannelProperties`（webhook URL、appId、secret）
2. 按 `eventType` 映射消息模板
3. 复用 Outbox 可靠投递与重试机制（与 Webhook 对齐）

## Webhook 渠道

`WebhookChannelAdapter.send(notification, recipient)` 将 `recipient` 解释为 Webhook URL，
写入 Outbox headers，由 `WebhookDeliveryService` 签名投递。SSRF 防护与签名见 `contracts/events/README.md`。

## 事件覆盖（Wave E）

| eventType | 站内 fan-out | Outbox |
| --- | --- | --- |
| `VERSION_REVIEW_REQUESTED` | reviewers (`asset:review`) | 已有 |
| `VERSION_APPROVED` / `VERSION_REJECTED` | submitter | 已有 |
| `VERSION_PUBLISHED` | submitter + owners + subscribers | 已有 |
| `VERSION_DEPRECATED` | asset owners | 已有 |
| `DISCUSSION_REPLIED` | subscribers | 新增 |
| `DISCUSSION_MENTIONED` | mentioned principals | 新增 |
| `STORAGE_QUOTA_WARNING` | `system:observe` | 新增 |
| `SYSTEM_DEPENDENCY_UNHEALTHY` | `system:observe` | 新增 |

## 剩余差距

- Email/IM 真实外发与收件人地址簿
- 用户级渠道偏好（站内/邮件/IM 开关）
- Outbox fan-out 到多渠道路由表（当前 Webhook 需 headers 显式 URL）
- Compose E4 行为级验收
