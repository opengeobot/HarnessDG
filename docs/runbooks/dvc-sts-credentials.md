# DVC 短期凭据 Runbook

> 作者：AxeXie | 日期：2026-07-11

## 概述

DVC 远程存储凭据通过 `DvcConfigurationService#issueCredentials` 签发，**永不返回平台 MinIO root 密钥**。

签发顺序：

1. **MinIO STS AssumeRole**（AWS SDK v2 `sts`，端点指向 MinIO）
2. 回退 **scoped 配置密钥**（`aihub.minio.dvc-access-key` / `dvc-secret-key`）

凭据有效期 ≤ **1 小时**（默认 15 分钟）。

## STS 路径（优先）

- 服务端使用 **dvc-user** 凭据（`aihub.minio.dvc-access-key` / `dvc-secret-key`，仅服务端）调用 STS AssumeRole。
- 会话 inline 策略限制到 `dvc-cache/{assetId}/` 前缀。
- 返回 `credentialType=STS_ASSUME_ROLE` 与 `sessionToken`。

### Compose 配置

`deploy/compose/minio/init.sh` 创建 **`dvc-scoped`** IAM 策略（`GetObject`/`PutObject`/`DeleteObject`/`ListBucket` on `dvc-cache/*`）并绑定 **dvc-user**。Backend 通过 `AIHUB_MINIO_DVC_ACCESS_KEY` / `AIHUB_MINIO_DVC_SECRET_KEY` 注入。

验证：

1. `GET /api/v1/assets/{assetId}/dvc/credentials` 返回的 `accessKey` ≠ MinIO root；
2. 审计事件 `credentialType` 为 `STS_ASSUME_ROLE` 或 `SCOPED_CONFIG_KEY`（均非 root）。

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
cd backend && ./mvnw -Dtest=DvcConfigurationServiceTest test
```

确认审计表 `DVC_CREDENTIALS_ISSUED` 不含完整 secret，仅记录 `accessKeyId` 与 `expiresAt`。
