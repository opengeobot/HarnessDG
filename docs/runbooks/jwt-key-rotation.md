# JWT 密钥轮换 Runbook

> 作者：AxeXie | 日期：2026-07-11

## 概述

平台使用 RSA 非对称 JWT（RS256）。签发始终使用**当前**私钥与 `kid`；校验在轮换重叠期同时接受**当前**与**上一**公钥。

配置前缀：`aihub.security.jwt`

| 属性 | 说明 |
| --- | --- |
| `private-key-pem` | 当前签名私钥（PKCS#8 PEM） |
| `public-key-pem` | 当前校验公钥（X.509 PEM） |
| `previous-public-key-pem` | 重叠期保留的上一公钥（仅校验，不签发） |
| `key-id` | JWT 头 `kid`（留空则由公钥指纹派生） |

## 轮换步骤

1. **生成新密钥对**，记录新 `kid`（建议与公钥指纹一致）。
2. **部署新私钥/公钥**到所有签发实例（api profile）。
3. **将旧公钥写入** `previous-public-key-pem`，保持旧公钥可校验。
4. **观察**访问/刷新令牌在重叠 TTL 内是否正常（旧 access 仍可通过上一公钥校验）。
5. **重叠期结束**（≥ 最长 access TTL，默认 15 分钟）后，移除 `previous-public-key-pem`。
6. **吊销**仍使用旧 `tokenVersion` 的主体（改密/禁用会递增 version）。

## 验证

```bash
# 单元测试覆盖：上一密钥签发的 token 在配置 previous-public-key-pem 后可通过校验
cd backend && ./mvnw -Dtest=JwtTokenServiceTest test
```

## 禁止事项

- 不得将私钥写入 Git、日志或前端。
- 不得在重叠期移除旧公钥前缩短 access TTL 导致大量 401。
- 生产环境禁止使用临时生成的 ephemeral 密钥对。
