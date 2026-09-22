# P5 质量与运维出口验收目录

> 状态：`READY`
> 依据：`p5-quality-operations.md`、`user-journey-catalog.md`（JRN-P5-001..004）、`dataset-experience.md`（DEC-008）。
> 前置：P4 Agent 接入 gap closure committed；DEC-010 连续实施授权。
> 预览专项：P2 已通过 `AC-DST-PRE-001..004`；P5 扩展 E5 能力，不在此重复定义 P2 最小预览 AC。

## 1. 出口规则

P5 只有在以下条件全部成立时才能退出：

1. 本目录所有 `MUST` 场景实际 PASS；
2. 没有 MUST 场景 SKIP；
3. 每个 PASS 绑定 Commit SHA、环境和 Evidence Manifest；
4. E4 场景使用真实 PostgreSQL/Gitea/MinIO/DVC 依赖；
5. E5 场景在隔离恢复环境、目标规模或安全扫描环境中执行，不以 Mock 替代；
6. 正常、失败、拒绝和恢复证据均存在；
7. Runbook、Dashboard、Alert、备份脚本和追踪矩阵与被测 Commit 一致；
8. P2 最小预览 `AC-DST-PRE-*` 在 P5 扩展后仍回归 PASS。

## 2. Gitea Webhook Inbox

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P5-INBOX-001` | MUST | Gitea 投递 push/tag/repo/release/member 事件并注入重复/恶意请求 | 签名/来源/时间窗口/大小校验；Delivery ID 幂等写入 webhook_inbox；未知 Payload 版本进入 FAILED/DEAD 并告警；Payload 脱敏且有限保留期 | `E4` |

## 3. 全量对账与安全修复

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P5-REC-001` | MUST | 模拟丢失 Webhook、重复事件、repo 权限扩大、Tag 删除/改指向、DVC 缺失、staging orphan、失租 Job | Reconciler 分类 AUTO_REPAIR/MANUAL_REVIEW/SECURITY_INCIDENT；dry-run 输出脱敏差异；apply 幂等并记录 before/after；无法修复产生告警/通知 | `E4` |

## 4. 预览 E5 扩展

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P5-PRE-001` | MUST | 在 P2 最小预览基础上扩展格式矩阵、资源隔离、压力与安全 canary | Worker 资源/时间/压缩炸弹限制；PII/Secret canary 泄漏为 0；不支持格式仍返回 PREVIEW_UNSUPPORTED_FORMAT；P2 AC-DST-PRE-* 回归 PASS | `E5` |

## 5. 备份、恢复与演练

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P5-BKR-001` | MUST | 执行 PostgreSQL/Gitea/MinIO/Secret 备份并在隔离环境恢复 | 备份加密且完整性可验证；恢复顺序维护事实源边界；JWT 密钥恢复不意外接受已吊销 Token；随机 Published Version Git/DVC/Manifest/SHA-256 校验；恢复后 Reconciler 无未解释差异 | `E5` |

## 6. 性能与容量

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P5-PERF-001` | MUST | 在 10 万 Asset 目标规模下执行搜索/详情/MCP/下载/上传/Webhook 混合负载 | Search P95 ≤500 ms；ticket P95 ≤300 ms；Webhook projection P95 ≤10s；错误率在门禁内；性能测试不关闭授权/审计/Trace；报告绑定数据生成器版本和 Commit | `E5` |

## 7. 安全验证

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P5-SEC-001` | MUST | 执行 SAST/依赖/镜像/Secret 扫描及越权/注入/SSRF/压缩炸弹/Prompt Injection 测试 | Critical/High 漏洞在门禁阈值内；Secret canary 在日志/审计/Trace/浏览器存储中为 0；上传内容不在业务服务/预览 Worker 中执行；安全失败有稳定错误和审计 | `E5` |

## 8. Dashboard、告警与 Runbook

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P5-OPS-001` | MUST | 故障注入触发服务不可用、DEAD Job、一致性差异、备份失败、Webhook 连续失败 | Dashboard 显示 API/MCP SLI、Job/Inbox/Outbox、一致性、Agent 拒绝；Alert 有 severity/threshold/dedupe/runbook；Runbook 演练记录绑定 Commit | `E5` |

## 9. 发布候选全量审计

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P5-REL-001` | MUST | 在发布候选 Commit 从干净环境执行 P0-B 至 P4 全部 MUST Journey 并注入故障恢复 | 备份→隔离恢复→随机 DVC 校验；Reconciler 无未解释差异；OpenAPI/MCP/Event/Flyway/前端类型无漂移；Evidence Manifest 全部绑定同一 Commit；无 MUST SKIP | `E5` |

## 10. 下载量与热度统计（DEC-016）

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P5-OBS-001` | MUST | 多个 Principal 对模型/数据集执行授权下载后查看卡片热度 | 基于 audit_log 下载授权事件聚合查询；统计与实时授权一致；无权主体不能枚举他人下载行为；DEC-016 推迟项在 P5 完成 | `E5` |
