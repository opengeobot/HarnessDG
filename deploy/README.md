# ModelHub v1 部署（Docker Compose）

一套 Compose 拉起全部依赖与业务进程：PostgreSQL 16 + Redis 7 + MinIO（S3）+ Gitea 1.24 + `modelhub-app`（Spring Boot）+ Nginx（唯一业务入口）。镜像构建使用仓库根目录的 `Dockerfile`（多阶段：Maven 构建 → JRE 运行）。

## 快速开始

```powershell
# 在仓库根目录（HarnessDG/）执行
docker compose -f deploy/docker-compose.yml up -d --build

# 首次启动较慢：Maven 镜像内全量构建 + Flyway 迁移 + Gitea/MinIO 初始化。
# 观察 app 就绪：docker compose -f deploy/docker-compose.yml logs -f app
```

启动完成后的入口：

| 入口 | URL | 说明 |
| --- | --- | --- |
| 业务 API（Nginx 入口） | http://localhost:8081/api/v1/... | `POST /api/v1/auth/login` 等 |
| 健康检查 | http://localhost:8081/actuator/health | 匿名可读；`/actuator/prometheus` 需 Bearer token |
| app 直连（调试用） | http://localhost:8080 | 与 Nginx 同源内容 |
| MinIO S3 API | http://localhost:9000 | **预签名 URL 公网端点（FILE-002）** |
| MinIO 控制台 | http://localhost:9001 | 凭据同 MinIO root |
| Gitea | http://localhost:3000 | 管理员 `modelhub / ModelHub-Root-1x` |

## 默认凭据（本机部署默认值，与集成测试一致；生产必须经 `.env` 覆盖）

| 用途 | 用户/键 | 密码/密钥 |
| --- | --- | --- |
| 首个平台管理员（app bootstrap，登录 API 用） | `platform-root` | `Boot-Strap-1x` |
| Gitea 管理员（provisioning 用） | `modelhub` | `ModelHub-Root-1x` |
| MinIO root（= app artifact access/secret key） | `modelhub` | `ModelHub-Minio-1x` |
| PostgreSQL | `modelhub` | `modelhub` |

首个平台管理员由 `BootstrapAdminRunner` 在应用首次启动时一次性创建（平台无任何平台角色时生效，用户名禁止 `demo`）。

## FILE-002：客户端可达对象 public URL

设计要求（prd/v1）：对象预签名 URL 必须对真实浏览器/CLI 可达，URL 中**不得**出现内部 Docker 服务名。部署按 `ArtifactConfiguration` 的双端点约定实现：

- **内部端点** `MODEHUB_ARTIFACT_INTERNAL_ENDPOINT=http://minio:9000` —— 仅 Java/Worker 的服务端流量（multipart 初始化、校验、清理）走 Compose 内部网络；
- **公网基址** `MODEHUB_ARTIFACT_PUBLIC_BASE_URL=http://localhost:9000`（默认值，可经 `.env` 覆盖）—— S3Presigner 用它签发上传 part URL 与下载 URL，指向宿主机映射端口；
- **MinIO 直接对外暴露 9000 端口**，预签名 URL 不经 Nginx 反代：预签名覆盖 `Host` 头，代理转发会改变 Host 导致 `SignatureDoesNotMatch`。v1 采用「Nginx 只管 `/api/` 与 `/actuator/`，预签名直连 MinIO」的最简正确拓扑（见 `nginx.conf` 头部注释）。

远程服务器部署时，把 `MINIO_PUBLIC_BASE_URL` 与 `PUBLIC_API_BASE_URL` 改为客户端真正可达的地址（如 `http://<host>:9000`、`http://<host>:8081`）。

### 自检脚本

从**宿主机**（即 Compose 网络之外的真实客户端视角）走完整 API 流程验证：

```powershell
# Windows
powershell -ExecutionPolicy Bypass -File deploy/presign-selfcheck.ps1
# 凭据/入口被 .env 覆盖时：
powershell -File deploy/presign-selfcheck.ps1 -ApiBase http://localhost:8081 `
    -MinioPublicBaseUrl http://localhost:9000 -Username platform-root -Password Boot-Strap-1x
```

```bash
# Linux / macOS / Git-Bash（依赖 curl + python3|jq）
./deploy/presign-selfcheck.sh
```

脚本流程：登录 → 建仓库等 `active` → 取分支 head → 发起上传等 `uploading` → 签发 part 1 预签名 URL → 断言 URL 以公网基址开头且不含内部服务名 → 宿主机 PUT 小对象 + HEAD 回探 → abort 清理，最终输出 `RESULT: PASS/FAIL`。

> 说明：v1 默认 `ContentScanner` 为 fail-closed（未配置真实扫描器即拒绝发布），因此自检在「预签名 PUT 直传成功」处止步——这已完整覆盖 FILE-002 的可达性与签名有效性；`complete/publish` 之后的链路需接入真实扫描器（如 ClamAV）后另行验证。

## 环境变量覆盖（.env）

在 `deploy/` 下复制 `.env.example` 为 `.env` 修改即可，未设置的键回落到 compose 内置默认值：

- 数据库：`POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD`
- MinIO：`MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` / `MINIO_PUBLIC_BASE_URL`（FILE-002 关键）
- Gitea：`GITEA_ROOT_URL` / `GITEA_ADMIN_USER` / `GITEA_ADMIN_PASSWORD`
- 应用：`BOOTSTRAP_USERNAME` / `BOOTSTRAP_PASSWORD` / `MODEHUB_ALLOWED_ORIGINS`（逗号分隔，为空则 refresh/logout fail-closed 拒绝）/ `PUBLIC_API_BASE_URL`
- JWT：`MODEHUB_JWT_PRIVATE_KEY_PEM`（PKCS#8 PEM，换行用 `\n`；留空则每次启动生成临时密钥，重启后已发 token 全部失效，仅限非生产）

应用其余配置键的含义见 `modules/app/src/main/resources/application.yml` 与各模块 `*Properties`（`modelhub.artifact.*` / `modelhub.gitea.*` / `modelhub.catalog.*`）。

## 常用运维命令

```powershell
docker compose -f deploy/docker-compose.yml ps                       # 状态/健康
docker compose -f deploy/docker-compose.yml logs -f app              # 应用日志
docker compose -f deploy/docker-compose.yml up -d --build app        # 改代码后仅重建 app
docker compose -f deploy/docker-compose.yml down                     # 停止（保留数据卷）
docker compose -f deploy/docker-compose.yml down -v                  # 停止并清空数据卷（慎用）
```

## 已知边界（v1）

- **内容扫描 fail-closed**：未配置真实扫描器时上传无法通过 `scanning` 阶段发布；接入 ClamAV 等实现后方可走完 publish。
- **JWT 临时密钥**：`MODEHUB_JWT_PRIVATE_KEY_PEM` 为空时每次重启轮换密钥，所有已发 access/refresh token 失效。
- **Nginx 端口**：宿主机 8081（避免与本机 80 冲突）；app 同时映射 8080 便于直连调试。
- **Gitea**：sqlite 单机模式、注册关闭，管理员由 `gitea-init` 一次性服务幂等创建。
