# Security Runbook

> 功能: 安全验证与脱敏门禁（TASK-P5-005）。
> 时间: 2026-07-10
> 作者: AxeXie

## 脱敏不变式

以下敏感模式**不得**出现在日志、审计 `request_summary` 或错误响应中:

- JWT / Bearer Token
- 明文密码
- MinIO/S3 预签名查询参数（`X-Amz-Signature`, `X-Amz-Credential` 等）
- 服务账号 Token、Cookie、API Key

实现: `SensitiveDataMasker`（字段级 + 文本模式）；`AuditService` 落库前强制脱敏。

## 本地验证

```bash
# 运行脱敏金丝雀测试（JWT + 密码 + 预签名 URL）
./deploy/security/run-redaction-canary.sh
```

脚本执行:

1. `SensitiveDataMaskerTest` — 单元级掩码规则
2. `SecurityVerificationTest` — JWT/预签名集成断言
3. `AuditServiceTest#shouldMaskSensitiveFieldsInSummary` — 审计路径不脱敏泄漏

## 演练检查清单

- [ ] 登录失败日志不含提交密码
- [ ] 下载票据审计不含 `presignedUrl` 明文
- [ ] Agent Token 交换审计不含 refresh token
- [ ] Compose V08 审计表无明文口令

## 供应链扫描（后续）

`deploy/security/` 预留 SAST/依赖/镜像扫描配置入口；CI 接入前以本地 canary 脚本为门禁。
