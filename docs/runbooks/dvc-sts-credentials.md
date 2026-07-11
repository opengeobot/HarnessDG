# DVC 短期凭据 Runbook

> 作者：AxeXie | 日期：2026-07-11

## 概述

DVC 远程存储凭据通过 `DvcConfigurationService#issueCredentials` 签发，**永不返回平台 MinIO root 密钥**。

签发顺序：

1. **MinIO STS AssumeRole**（AWS SDK v2 `sts`，端点指向 MinIO）
2. 回退 **scoped 配置密钥**（`aihub.minio.dvc-access-key` / `dvc-secret-key`）

凭据有效期 ≤ **1 小时**（默认 15 分钟）。

## STS 路径（优先）

- 服务端使用 `aihub.minio.access-key` / `secret-key` 调用 STS（仅服务端，不返回客户端）。
- 会话策略限制到 `dvc-cache/{assetId}/` 前缀。
- 返回 `credentialType=STS_ASSUME_ROLE` 与 `sessionToken`。

### 当前限制

Compose 默认 MinIO **未配置 IAM AssumeRole 角色**时，STS 调用会失败并自动回退 scoped 配置密钥。生产启用 STS 需：

1. 在 MinIO 配置 `dvc-scoped` 角色与信任策略；
2. 确认 `arn:aws:iam::minio:role/dvc-scoped` 与平台 `MinioStsCredentialIssuer` 一致；
3. 验证 `issueCredentials` 审计事件 `credentialType=STS_ASSUME_ROLE`。

## Scoped 配置密钥回退

配置：

```yaml
aihub:
  minio:
    dvc-access-key: <scoped-readwrite-key>
    dvc-secret-key: <scoped-secret>
```

- `credentialType=SCOPED_CONFIG_KEY`
- `limitationNote` 说明 STS 未集成，依赖 `expireAt` 元数据约束客户端轮换。
- 未配置 scoped 密钥时 **fail-closed**（`ValidationException`）。

## 运维检查

```bash
./mvnw -pl backend -Dtest=DvcConfigurationServiceTest test
```

确认审计表 `DVC_CREDENTIALS_ISSUED` 不含完整 secret，仅记录 `accessKeyId` 与 `expiresAt`。
