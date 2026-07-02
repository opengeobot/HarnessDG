# 非功能需求与质量门禁

> 状态：`PROPOSED`
> 未决：`Q-301` 至 `Q-305`，以及备份 RPO/RTO、浏览器矩阵和可访问性等级。

## 1. 适用规则

- 非功能要求与业务功能具有相同完成约束，不能统一推迟到“优化阶段”；
- 每项指标必须记录环境、数据规模、持续时间、分位值和失败率；
- Compose 冒烟用于回归，不代表生产容量；
- 安全与数据隔离要求不因环境是开发或 Compose 而放宽；
- 未确定的阈值标为 `OPEN`，AI 不得选择一个容易通过的数字。

## 2. 性能与容量

| ID | 要求 | 测量条件 | 通过标准 | 最低证据 |
| --- | --- | --- | --- | --- |
| `NFR-PERF-001` | 资产元数据搜索延迟 | 10 万资产；权限过滤开启；典型筛选/全文查询组合 | P95 ≤ 500 ms；错误率阈值待确认 | `E5` |
| `NFR-PERF-002` | 下载票据签发延迟 | 包含 JWT、业务授权、版本状态、敏感度检查 | P95 ≤ 300 ms | `E5` |
| `NFR-PERF-003` | Webhook 投影延迟 | Gitea push 至授权用户可查询新投影 | P95 ≤ 10 秒 | `E5` |
| `NFR-PERF-004` | 只读 Agent 接入时间 | 已有网络和管理员账号；从空客户端配置开始 | ≤ 30 分钟且不接触永久对象凭据 | `E4` |
| `NFR-PERF-005` | Compose 性能冒烟 | 100 API 并发、20 MCP 搜索、10 签名、5 个 1 GiB 上传，持续 5 分钟 | 无明显错误/资源失控；具体回归阈值待基线 | `E5` |
| `NFR-CAP-001` | Web 上传策略上限 | 单 upload session | 默认 ≤ 20 GiB；超限明确引导 CLI/DVC | `E4` |
| `NFR-CAP-002` | 分页响应大小 | REST/MCP 列表 | 默认 20、最大 100；不得返回二进制或无限列表 | `E3` |

性能报告必须同时包含 P50/P95/P99、吞吐、错误码分布、数据库连接池、JVM/CPU/内存和测试数据分布。

## 3. 安全

| ID | 要求 | 通过标准 | 最低证据 |
| --- | --- | --- | --- |
| `NFR-SEC-001` | 默认拒绝 | 除明确匿名端点外，所有入口无有效 Principal 均拒绝 | `E4` |
| `NFR-SEC-002` | 浏览器 Token 安全 | access 仅内存；refresh 为 Secure/HttpOnly/SameSite Cookie；LocalStorage/埋点/报告均无 JWT | `E4` |
| `NFR-SEC-003` | 密码与凭据 | 自适应哈希；非对称 JWT+`kid`；数据库只存凭据/Token 摘要 | `E3` |
| `NFR-SEC-004` | 日志脱敏 | Authorization/Cookie/密码/JWT/预签名查询串/对象凭据在 HTTP、MCP、Worker、审计边界均不可见 | `E4` |
| `NFR-SEC-005` | 资源防枚举 | 私有资源不存在与无权访问对外语义一致；列表在 SQL 阶段过滤 | `E4` |
| `NFR-SEC-006` | Agent 最小权限 | Scope、Permission、资源策略、敏感度和 Tool Allowlist 全部通过才执行 | `E4` |
| `NFR-SEC-007` | 上传内容安全 | 路径穿越、恶意压缩包、类型/大小、Pickle/动态代码风险按策略检测 | `E5` |
| `NFR-SEC-008` | Webhook SSRF/重放 | 目标校验；签名、时间戳、Delivery ID；重复投递幂等 | `E4` |
| `NFR-SEC-009` | 供应链 | Secret 扫描；镜像和依赖锁定；漏洞检查达到待确认阈值 | `E5` |

安全场景必须包含攻击输入和被拒绝后的审计/告警证据，不能只检查配置文件。

## 4. 一致性、可靠性和恢复

| ID | 要求 | 通过标准 | 最低证据 |
| --- | --- | --- | --- |
| `NFR-REL-001` | 发布版本一致性 | 100% 发布版本同时有 Tag、Commit、Manifest/DVC Digest | `E4` |
| `NFR-REL-002` | 发布不可变 | 覆盖 Tag、修改已发布内容和复用版本号全部拒绝并审计 | `E4` |
| `NFR-REL-003` | 可靠任务恢复 | Worker 崩溃后租约到期可重新领取；无任务丢失或重复业务副作用 | `E4` |
| `NFR-REL-004` | 外部事件幂等 | 重复/乱序 Webhook 不产生重复版本或错误倒退状态 | `E4` |
| `NFR-REL-005` | 投影可重建 | PostgreSQL 资产/版本投影可由 Gitea+DVC 重建并校验摘要 | `E4` |
| `NFR-REL-006` | 通知隔离 | 渠道失败不回滚核心事务，恢复后最终送达或进入 DEAD 并告警 | `E4` |
| `NFR-REL-007` | 备份恢复 | 恢复后随机发布版本能 Git Clone+DVC Pull+SHA-256 校验 | `E5` |
| `NFR-REL-008` | RPO/RTO | PostgreSQL、Gitea、MinIO 和 Secret 的目标值 | `OPEN` |

## 5. 可观测性

| ID | 要求 | 通过标准 | 最低证据 |
| --- | --- | --- | --- |
| `NFR-OBS-001` | 上下文关联 | Browser/Agent→Nginx→REST/MCP→应用→DB/外部依赖→Worker 可按 request/trace/principal/resource 关联 | `E4` |
| `NFR-OBS-002` | 异步 Trace | 持久化 Trace Context；Worker 建新 Span 并使用 Span Link | `E4` |
| `NFR-OBS-003` | 指标覆盖 | API、MCP、任务、Gitea、MinIO/DVC、DB、JVM 和业务指标可查询 | `E4` |
| `NFR-OBS-004` | 健康分层 | liveness 不依赖外部系统；readiness 只包含关键依赖；诊断端点受权且脱敏 | `E4` |
| `NFR-OBS-005` | 告警 | DEAD、依赖不可用、Webhook 积压、一致性差异和安全拒绝达到阈值时触发 | `E5` |

`AUD-005` 的 OTel 范围冲突未解决前，本组不能进入 `READY`。

## 6. API、MCP 与数据兼容

| ID | 要求 | 通过标准 | 最低证据 |
| --- | --- | --- | --- |
| `NFR-COMP-001` | OpenAPI 兼容 | lint、示例校验、breaking diff；未接受的破坏变更阻断 CI | `E1/E3` |
| `NFR-COMP-002` | MCP 兼容 | initialize/tools-list/tools-call、Schema 渲染、错误与分页在锁定客户端版本通过 | `E4` |
| `NFR-COMP-003` | 数据库升级 | 空库迁移和从上一支持版本升级均通过 PostgreSQL 测试 | `E3` |
| `NFR-COMP-004` | 事件兼容 | Schema 版本明确；新增字段向后兼容；重复投递可处理 | `E3` |
| `NFR-COMP-005` | Shell/PowerShell 等价 | 两个 Verify 包装执行同一权威断言并产生相同结果语义 | `E4` |

Breaking diff 作为 `continue-on-error` 的“评审信号”不足以成为强制门禁；允许的破坏变更必须关联 Accepted
ADR/Decision。

## 7. UI、国际化与可访问性

| ID | 要求 | 通过标准 | 最低证据 |
| --- | --- | --- | --- |
| `NFR-UI-001` | 完整页面状态 | 每个异步页面都有 Loading/Empty/Error/Retry/Success | `E4` |
| `NFR-UI-002` | 权限体验 | 路由和按钮按真实权限展示；后端仍做最终判断；403 不泄露资源存在性 | `E4` |
| `NFR-UI-003` | 国际化 | zh-CN/en-US 可切换；菜单、按钮、错误、字典、通知和向导不硬编码业务文案 | `E4` |
| `NFR-UI-004` | 上传恢复 | 页面刷新后恢复会话和 Part 状态，只重传失败 Part | `E4` |
| `NFR-UI-005` | 可访问性 | 键盘操作、标签、焦点、对比度达到待确认 WCAG 等级 | `OPEN` |
| `NFR-UI-006` | 浏览器支持 | 支持矩阵待确认 | `OPEN` |
| `NFR-UI-007` | 视觉回归 | 关键页面在目标视口有基准截图且无未批准差异 | `E4` |

## 8. 可维护性与 AI 工程门禁

| ID | 要求 | 通过标准 | 最低证据 |
| --- | --- | --- | --- |
| `NFR-MNT-001` | 模块边界 | Controller/MCP/Worker 不调 Mapper；Domain 不依赖框架；跨模块只走 API/Port | `E1/E2` |
| `NFR-MNT-002` | 单一业务内核 | 同一 REST/MCP/Worker 用例引用同一 Application Service、授权和审计 | `E2/E4` |
| `NFR-MNT-003` | 契约生成 | 前端 DTO 不手工漂移；生成结果可复现并有 diff | `E1` |
| `NFR-MNT-004` | 状态一致性 | 需求、契约、迁移、测试和状态报告由追踪矩阵绑定 | `E1` |
| `NFR-MNT-005` | 无占位完成 | 生产 Profile 不含 Noop/allow-all/空主体/内存可靠任务/默认全 Scope | `E3/E4` |

