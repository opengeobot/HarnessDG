# Compose 部署与验证 Runbook

本文档说明如何在本地通过 Docker Compose 启动 AIHub 平台基线环境并执行 P0 验收。Compose 仅用于本地开发、CI 功能验证与单机 PoC，不提供高可用，生产重要数据不得只保存在单机 Volume 中。

## 目录

```text
deploy/compose/
├─ compose.yaml                       # 八服务编排
├─ .env.example                       # 环境变量示例（复制为 .env）
├─ nginx/nginx.conf                   # 单域名反向代理路由
├─ postgres/init/01-create-gitea-db.sql
├─ minio/init.sh                      # 创建 4 个 Bucket 与最小权限账号
├─ scripts/bootstrap.ps1              # 引导（准备 .env、构建、启动）
├─ scripts/verify.ps1                 # P0 验收（V01-V03）
├─ scripts/verify.sh                  # 同上，Shell 等价
└─ fixtures/model/                    # 验证用最小资产 fixture
```

## 服务与端口

| 服务 | 说明 | 暴露 |
| --- | --- | --- |
| postgres | 业务库 + Gitea 库 | 容器网络 |
| minio | 对象存储数据面 | 控制台 127.0.0.1:9001 |
| minio-init | 一次性初始化 Bucket 与账号 | 容器网络 |
| gitea | 代码托管 | SSH 127.0.0.1:2222；Web 仅经 Nginx http://localhost:8080/git/ |
| backend | REST/MCP 控制面 | 经 Nginx |
| worker | 可靠任务（含 DVC 运行时） | 容器网络 |
| frontend | React 静态资源 | 经 Nginx |
| nginx | 单域名网关 | 127.0.0.1:8080 |

Nginx 路由（见 `nginx/nginx.conf`）：`/` → 前端、`/api/` → 后端 REST、`/mcp` → 后端 MCP（关闭缓冲）、`/actuator/health` → 健康检查、`/git/` → Gitea。

## Gitea 访问与未来 SSO 免登（预留）

Gitea Web 仅通过 Nginx 子路径入口 `http://localhost:8080/git/` 访问，不再单独对外暴露 3000 端口。`GITEA__server__ROOT_URL`（由 `.env` 的 `GITEA_PUBLIC_URL` 提供）固定为该地址，确保页面静态资源与链接均带 `/git/` 前缀。

“平台登录后免登跳转 Gitea”当前**未实现**，为预留项，待 P3 身份与权限阶段认证体系就绪后落地。候选方案：

- **方案 A（推荐）**：平台作为 OIDC Provider，Gitea 配置为 OAuth2 客户端，用户登录平台后经标准授权码流程进入 Gitea，无需二次登录；回调地址以 `http://localhost:8080/git/` 为基准。
- **方案 B**：在 Nginx `/git/` 前置 `auth_request` 校验平台会话，配合 Gitea `ENABLE_REVERSE_PROXY_AUTHENTICATION` 透传可信用户头实现免登。

上述方案均以现有 `/git/` 入口为基准，接入时该入口地址保持不变。

## 启动流程

PowerShell：

```powershell
cd deploy/compose
Copy-Item .env.example .env   # 按需修改占位密码
docker compose pull
docker compose build
docker compose config --quiet
docker compose up -d
./scripts/bootstrap.ps1
./scripts/verify.ps1
```

Bash：

```bash
cd deploy/compose
cp .env.example .env
docker compose build
docker compose config --quiet
docker compose up -d
./scripts/verify.sh
```

> `.env` 与 `.env.local` 已被 `.gitignore` 忽略，禁止提交。`GITEA_SERVICE_TOKEN` 由 bootstrap 后在 Gitea 中创建并写回，勿提交 Git。

## P0 验收范围

`verify.ps1` / `verify.sh` 当前覆盖：

- **V01** Compose 配置合法（`docker compose config --quiet`）
- **V02** 核心服务健康（postgres / minio / gitea / backend）
- **V03** 四个 Bucket 存在且非匿名（gitea-storage / dvc-cache / asset-staging / asset-preview）

业务端到端用例（V05–V21：创建资产、DVC 往返、发布、MCP、权限等）属 P1+ 阶段，脚本中以提示占位，不冒充已实现。

## 健康检查分层

| 端点 | 用途 |
| --- | --- |
| `/actuator/health/liveness` | 容器是否需重启（仅 JVM 基本状态） |
| `/actuator/health/readiness` | 是否接收流量（含 PostgreSQL 探测） |

## 常见问题

- 后端 readiness 一直 DOWN：检查 PostgreSQL 是否 healthy、数据源 URL/账号是否匹配 `.env`。
- minio-init 失败：查看 `docker compose logs minio-init`，确认 Root 凭据与 mc 版本兼容。
- Gitea 无法启动：确认 `01-create-gitea-db.sql` 已在 postgres 首次初始化时执行（仅空数据卷首启执行）。
