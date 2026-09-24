# HarnessDG 平台前后端全面测试报告

- 功能: P1–P5 业务波次前后端功能全面测试执行记录与结论
- 时间: 2026-09-24
- 作者: AxeXie
- 分支/提交: `ai` @ `0620940`（测试基于该工作区状态执行，测试本身未修改任何产品代码）

## 1. 测试范围与层级

| 层级 | 内容 | 工具/命令 |
| --- | --- | --- |
| L1 单元测试 | 后端 140 个测试类；前端 48 个测试文件 / 212 用例 | `mvnw verify`（surefire）/ `pnpm test`（vitest） |
| L2 契约与配置 | AI Spec 校验、OpenAPI lint、Compose 配置 | `validate-spec.ps1` / `redocly lint` / `docker compose config` |
| L3 集成测试（DB 级） | 123 个 Testcontainers `*IT` | `mvnw failsafe:integration-test` |
| L4 全栈行为验收 | Compose 运行栈 V01–V28 端到端断言 | `deploy/compose/scripts/verify.sh` |
| L5 静态质量 | 前端 lint / typecheck / production build | `pnpm lint` / `pnpm typecheck` / `pnpm build` |

## 2. 环境

- OS: Windows；JDK: Temurin 21.0.9（默认 `java` 为 17，需显式 `JAVA_HOME`）；Maven Wrapper；Node v20.19.2 / pnpm 10.33.4；Docker 29.1.2 + Compose v2.40.3；Git Bash 5.2.26。
- Compose 栈预先运行且 backend healthy；数据库为持久化卷（非全新初始化）。

## 3. 执行结果汇总

| # | 项目 | 命令 | 结果 |
| --- | --- | --- | --- |
| 1 | 后端单元 + 构建 | `./mvnw verify "-Dstyle.color=never"` | **BUILD SUCCESS**（05:15）；surefire **752 通过 / 6 跳过 / 0 失败** |
| 2 | 后端集成测试 | `./mvnw failsafe:integration-test failsafe:verify` | **123 全部 skipped**——Testcontainers 无法发现 Docker 环境（见 §6.3），**非测试失败，但也未验证** |
| 3 | 前端 lint | `pnpm lint` | PASS：**0 errors / 2 warnings**（均为既有 `react-refresh/only-export-components`） |
| 4 | 前端 typecheck | `pnpm typecheck` | PASS |
| 5 | 前端单测 | `pnpm test`（vitest run） | 并发/满并行下 26–43 用例超时失败；**限定并发 `--maxWorkers=2` 独占复跑：48 文件 / 212 用例全部通过（exit 0，692s）**（见 §6.1） |
| 6 | 前端 build | `pnpm build` | PASS：3257 模块，产物 gzip 后主包 118kB + antd-vendor 327kB |
| 7 | AI Spec 校验 | `./docs/ai-spec/tools/validate-spec.ps1` | PASS：148 documents / 725 stable IDs |
| 8 | OpenAPI lint | `redocly lint`（主契约 + agent 契约） | valid：**各 3 warnings，0 errors**；`contracts/mcp/tools.yaml` 为 MCP 工具清单（非 OpenAPI），不适用该 lint |
| 9 | Compose 配置 | `docker compose config --quiet` | PASS |
| 10 | 全栈行为验收 | `bash deploy/compose/scripts/verify.sh` | **V01–V28 全部 PASS（38 断言行，exit 0）**——登录/JWT 生命周期/账号锁定/权限过滤/字典标签/日志脱敏/审计/持久化任务/通知/可观测性 |

## 4. 覆盖度结论（按 Evidence 口径）

- **已验证（本环境实测通过）**：后端单元层业务规则与适配器逻辑（752 用例）、前端全部组件/页面/状态逻辑（212 用例）、契约一致性、Compose 全栈 28 项行为验收（E4 级）。
- **未验证（维持 IMPLEMENTED_UNVERIFIED）**：
  1. 123 个 DB 级 Testcontainers 集成测试因环境不兼容被跳过（§6.3），Flyway 真实迁移与 SQL 行为仅通过 Compose 栈间接覆盖；
  2. `verify-journey.sh` 认证业务旅程（P1–P5 既有阻塞项，未在本轮范围）；
  3. E5 演练复验与 `-CheckCompletion` 人工验收（既有阻塞项）。
- 结论与 AGENTS.md 现状口径一致：**P1–P5 不得呈现为 VERIFIED**；本轮测试未发现新的功能缺陷。

## 5. 发现项

### F-01（高）verify.ps1 存在未定义 helper 函数，无法完成 V05c–V05j 断言
`deploy/compose/scripts/verify.ps1` 引用了 `Assert-BootstrapAdminUniqueness`、`New-IamProbeUser`、`Get-RefreshFromSetCookie`、`Get-UserRowFacts`、`Reset-UserLockState` 等**未定义的函数**，运行至 V05c 起连锁 FAIL（`CommandNotFoundException`）。该脚本自称与 verify.sh 等价，实际不可用；本轮以 **verify.sh 为权威验收入口**。建议：补齐或删除缺失断言段（需 Task Card）。

### F-02（高）Docker Engine 29.x 与 Testcontainers 1.21.x 不兼容，123 个 IT 静默跳过
- 症状：failsafe 报告 `Tests run: 123, Skipped: 123`；日志为 npipe 连通后 `/info` 返回 `BadRequestException (Status 400)` 且 JSON 全空（仅 `Labels:["com.docker.desktop.address=…docker_cli"]`）。
- 根因：Docker Engine 29 将最低 API 版本提升至 1.44，TC 1.21.x 内置 docker-java 以旧 API 协商被拒。环境变量 `DOCKER_HOST`、`DOCKER_API_VERSION=1.51` 均无效；命令行覆盖 `-Dtestcontainers.version=1.21.3` 复测仍复现。佐证：[Spring Boot #48104](https://github.com/spring-projects/spring-boot/issues/48104)、[Testcontainers #11235](https://github.com/testcontainers/testcontainers-java/issues/11235)、[Qiita 处置记录](https://qiita.com/only/items/feb48bb9e449d0edfda5)。
- 建议修复（二选一，均属依赖变更须 Task Card 授权，本轮未擅改 `backend/pom.xml`）：升级 Spring Boot 至 ≥3.5.8（BOM 随带兼容 TC），或直接覆盖 `testcontainers.version` 至 2.x（major 迁移，改动面大）。**在其间，任何 “verify 通过” 不应被解读为 IT 已验证。**
- 另注：pom 中 failsafe 注释明示 “无 Docker 时 Testcontainers 自行跳过，不阻断 verify”——该静默跳过策略在 CI 中应配合 `-Dfailsafe.skip=false` + Docker 可用性门禁，防止假绿。

### F-03（中）持久化环境 admin 口令漂移 + 账号锁定，verify.ps1 无兜底
本轮 Compose 栈复用历史数据卷：admin 先因失败登录累计被锁（423 AUTH_ACCOUNT_LOCKED，已用 SQL 解锁 `iam_user`/`iam_principal`），随后 `.env` 口令与库内 bcrypt 哈希漂移致 V05 登录 401 连锁失败。verify.sh 自带 `reset_admin_password`（worker 容器 python crypt 生成 bcrypt 后 SQL 重置）**兜底后全过**；verify.ps1 无此兜底。**副作用申报：当前环境 admin 口令已被 verify.sh 重置为 `.env` 值（change-me-…）。** 建议：全新验收一律从干净卷初始化（`docker compose down -v`）。

### F-04（低）前端 vitest 在满并行 + 同机负载下大面积 5s 超时（flaky）
首轮 26 失败（与后端 mvn 并发）、复跑 43 失败、独占复跑仍 34 失败（全部 `Test timed out in 5000ms`，其中 1 个连带断言失败）；`--maxWorkers=2` 独占复跑 **212/212 通过**。判定为**测试环境问题而非功能缺陷**：antd + jsdom 重组件页面在 CPU 争用下超过默认 5s 钩子超时（48 文件 `import` 累计耗时 >1000s 显示转译/加载是瓶颈）。建议（需授权）：`vite.config.ts` 的 `test` 节提高 `testTimeout`/`hookTimeout` 或限制默认并发，并在 CI 中与后端测试串行调度。

### F-05（低）Windows PowerShell 5.1 无法直接运行 verify.ps1
无 BOM UTF-8 中文脚本被按 GBK 解码导致语法崩溃。建议脚本转存为带 BOM UTF-8，或文档化要求以 PowerShell 7 运行。

## 6. 日志取证

全部原始日志在 `build-logs/`（未纳入 Git）：`backend-verify.log`、`backend-it.log`/`backend-it2.log`/`backend-it3.log`（三种 DOCKER_HOST/版本组合均 123 skipped）、`frontend-lint.log`、`frontend-typecheck.log`、`frontend-vitest.log`/`-2`/`-3`/`-5.log`（-5 为通过基准）、`frontend-build.log`、`openapi-lint.log`、`compose-verify-ps.log`（F-01/F-03 取证）、`compose-verify-sh.log`（V01–V28 PASS）。

## 7. 后续行动建议（按优先级）

1. 以 Task Card 授权升级 Spring Boot BOM（≥3.5.8）使 123 个 IT 恢复执行，并在 CI 增加 “Docker 可用则 IT 必须实际运行” 门禁（对应 F-02）。
2. 修复或裁剪 `verify.ps1`，与 `verify.sh` 断言集对齐（对应 F-01）。
3. 前端测试超时与并发配置加固（对应 F-04）。
4. 推进既有 P1–P5 出口阻塞项：`verify-journey.sh`、`-CheckCompletion`、E5 演练复验。
