# ModelHub v1（HarnessDG 实施仓库）

ModelHub 模型/数据集/Studio 资源门户平台 v1 的实施仓库。

- **设计规格权威来源**：`../prd/v1`（优先级：ADR > 领域规格 > OpenAPI > canvas > demo，见 `../prd/v1/README.md` §3）。
- **技术栈**：Java 21 + Spring Boot 3.3（Maven 模块化单体）；Python 3.12 Worker（预览/扫描）；Vite + React + TS Web 前端；Picocli CLI；PostgreSQL 16 / Redis 7 / MinIO / Gitea；Docker Compose 部署（Nginx 唯一业务入口）。
- **里程碑**：M1 地基 → M2 目录与文件 → M3 性能治理 → M4 异步工作流 → M5 SDK/预览 → GA。
- **执行循环**：每阶段 = 开发 → 自动化测试 → 文档对齐验证（`docs/verification/<阶段>.md`）→ git 提交（`feat(stageN): ...`）+ 里程碑 tag（`m1`…`ga`）。

## 目录规划

```
pom.xml                # modelhub-parent
modules/shared         # envelope、错误码、public_id、分页、时间、Idempotency
modules/identity-access  modules/catalog  modules/artifact
modules/interaction    modules/workflow   modules/governance
modules/api            # REST Controller（仅编排）
modules/app            # Spring Boot 启动、配置、Worker 装配
modules/arch-tests     # ArchUnit
modules/cli            # Picocli CLI
worker/                # Python 预览/扫描 Worker
web/                   # Vite+React SPA
deploy/                # docker-compose、nginx、env 模板、preflight
docs/verification/     # 每阶段对齐报告
```

## 构建与测试

```powershell
mvn verify          # 单元 + 集成测试（Testcontainers：PostgreSQL/Redis/MinIO/Gitea）
```

环境要求：JDK 21、Maven 3.9+、Docker、Node ≥ 20。

## 部署

`deploy/` 提供 Docker Compose 一键部署（PostgreSQL 16 + Redis 7 + MinIO + Gitea 1.24 + app + Nginx 唯一业务入口），详见 `deploy/README.md`：

```powershell
docker compose -f deploy/docker-compose.yml up -d --build
```

- API 入口：`http://localhost:8081/api/v1/...`；MinIO 控制台 `:9001`；Gitea `:3000`。
- FILE-002 自检（预签名对象 URL 对客户端可达）：`deploy/presign-selfcheck.ps1` / `presign-selfcheck.sh`。
- 凭据/端点覆盖：复制 `deploy/.env.example` 为 `deploy/.env`。
