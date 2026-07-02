# P5 质量与运维需求

> 状态：`OPEN`
> 阻塞：P4 VERIFIED；RPO/RTO、生产拓扑和性能环境需确认。

## REQ-INBOX-001 Gitea Webhook Inbox

```yaml
status: PROPOSED
priority: MUST
phase: P5
minimumEvidenceLevel: E4
```

### 行为

- 接收 push、tag create/delete、repository、release、member/team 事件；
- 在解析业务内容前验证签名、来源、时间窗口和请求大小；
- 以 Gitea Delivery ID 唯一先写 webhook_inbox 后快速响应；
- 重复 Delivery 返回幂等接收结果；
- Worker 异步解析版本化 Payload，未知版本进入 FAILED/DEAD 并告警；
- Payload 不作为最终事实，关键转换回查 Gitea；
- 增量更新 Asset/Version/权限投影；
- 正式 Tag 删除/改指向触发 CRITICAL 一致性事件；
- retry/lease/attempt 复用可靠 Job；
- Payload 脱敏、限制保留期，签名 Secret 不入库/日志。

### 追踪

- Invariants：`INV-EVT-001`
- Consistency：Webhook Inbox
- Journey：`JRN-P5-001`

## REQ-REC-001 全量对账与安全修复

```yaml
status: PROPOSED
priority: MUST
phase: P5
permission: job:manage or dedicated reconciliation permission
minimumEvidenceLevel: E4
```

### 对账范围

- PG Asset ↔ Gitea repo/Card/Manifest；
- PG Published Version ↔ protected Tag/Commit/Manifest/DVC objects；
- PG Role/ACL ↔ Gitea Team/Repo permission projection；
- Upload Session ↔ MinIO staging；
- Job RUNNING ↔ Worker lease；
- Outbox ↔ Delivery；
- 搜索投影 ↔ 权威 Commit/Digest。

### 行为

- 周期调度和人工触发均为持久化 Job；
- scan 有 Cursor/checkpoint/速率限制，不一次加载全量；
- 差异分类为 AUTO_REPAIR、MANUAL_REVIEW、SECURITY_INCIDENT；
- 自动修复仅恢复到权威事实，不做双向合并；
- dry-run 输出脱敏差异；
- apply 使用差异 version/digest 防止扫描后漂移；
- 每项修复幂等，记录 before/after/ruleVersion/principal/trace；
- 无法修复创建通知/告警/人工队列；
- 对账失败不掩盖下一周期，但避免同差异无限通知。

### 验收

模拟丢失 Webhook、重复事件、repo 权限扩大、Tag 删除/改指向、DVC 对象缺失、staging orphan、失租 Job，
分别验证自动/人工/安全分类。

## REQ-PRE-001 数据集/模型安全预览

```yaml
status: READY
priority: MUST
phase: P2/P5
decisions: [DEC-008]
minimumEvidenceLevel: E4/E5
```

- P2 最小支持 CSV、JSONL、Parquet；每个格式的解析器版本、字符编码、压缩白名单和失败错误固定；
- P2 默认最多返回 100 行、50 列和 1 MiB 脱敏响应；服务端可通过类型化配置收紧，不能放宽到无界；
- 不支持的格式返回 `PREVIEW_UNSUPPORTED_FORMAT`，不得以空 items 冒充成功；
- 预览由持久化 Job 生成到 `asset-preview`，不从请求线程读取大对象；
- 只从精确 Version/Artifact 生成并保存 source digest；
- Dataset 支持的格式/采样算法/最大行列/字段类型白名单明确；
- Model 只生成安全元数据/缩略信息，不执行不可信模型代码；
- 按 sensitivity/PII/deidentification 策略脱敏；
- Preview Artifact 有 TTL/版本/访问控制，不是正式版本；
- 下载/查看实时授权，禁止公开 Bucket；
- 生成 Worker 资源/时间/压缩炸弹限制；
- 预览失败不影响 Published Version，但可产生质量警告；
- UI 明确“样例，不代表完整数据”，显示来源版本和生成时间；
- 泄露测试包含 PII/Secret canary，预览/日志出现次数必须为 0。
- REST：`GET /assets/{assetId}/versions/{version}/preview`；MCP：`dataset_get_preview`；
  页面：`PAGE-DST-001`；
- 目标投影表 `asset_preview` 只保存 version/source digest/格式/状态/生命周期和对象引用，不保存完整样本；
- P2 通过 `AC-DST-PRE-001..004` 后交付最小安全预览；P5 扩展更多格式、资源隔离、压力与安全 E5，
  不得把 P2 已通过能力回写成 P5 才存在。

## REQ-BKR-001 备份、恢复与演练

```yaml
status: OPEN
priority: MUST
phase: P5
blockedBy: [RPO, RTO, production topology]
minimumEvidenceLevel: E5
```

### 备份范围

- PostgreSQL 全量+WAL；
- Gitea 仓库、LFS/附件（若启用）和配置；
- MinIO 正式 DVC Bucket、必要 preview/staging 策略；
- 加密 Secret/签名密钥材料及轮换元数据；
- Compose/生产部署配置、版本锁和恢复脚本；
- Evidence/Runbook/恢复演练记录。

### 行为

- 备份加密、最小访问、异地/不同故障域保存；
- 每类数据声明频率、保留、RPO/RTO、完整性摘要；
- 恢复顺序维护事实源边界，避免旧 PG 投影覆盖新 Gitea/DVC；
- JWT 签名密钥恢复/轮换不意外接受已吊销 Token；
- 恢复到隔离环境，凭据不复用生产；
- 随机抽样多个 Published Version 完成 Git Clone、Tag checkout、DVC Pull、Manifest/SHA-256 校验；
- 恢复后运行 Reconciler 并要求无未解释差异；
- 演练失败进入问题跟踪，不以“备份文件存在”通过。

### 追踪

- Journey：`JRN-P5-002`
- NFR：`NFR-REL-007/008`

## REQ-PERF-001 性能、容量与回归

```yaml
status: OPEN
priority: MUST
phase: P5
blockedBy: [Q-303, production sizing]
minimumEvidenceLevel: E5
```

### 工作负载

- 10 万 Asset、合理 Organization/Project/ACL/Tag/Version 分布；
- 100 并发列表/详情 5 分钟；
- 20 并发 MCP 搜索；
- 10 并发下载票据；
- 5 并发 1 GiB Multipart；
- Webhook/Job/Outbox 背景流量同时存在；
- 权限冷热缓存、不同角色和否定查询。

### 报告

P50/P95/P99、吞吐/错误率、状态码、DB pool/慢 SQL/锁、JVM/GC/thread、CPU/memory/disk/network、
Gitea/MinIO latency、Job queue/lease、Trace sample。

### 门禁

- Search P95 ≤500 ms；
- ticket P95 ≤300 ms；
- Webhook projection P95 ≤10s；
- 错误率/资源上限和基线回归百分比待确认；
- 性能测试不能关闭授权、审计、Trace 或治理校验；
- 报告绑定数据生成器版本、环境、Commit；
- Compose 冒烟与生产容量报告明确区分。

### 追踪

- Journey：`JRN-P5-003`
- NFR：`NFR-PERF-*`

## REQ-SEC-001 安全验证与内容供应链

```yaml
status: PROPOSED
priority: MUST
phase: P5
minimumEvidenceLevel: E5
```

### 自动化范围

- SAST、依赖/镜像漏洞、Secret、许可证扫描；
- JWT 算法/kid/iss/aud/重放/时钟边界；
- RBAC/ACL/Scope/Tool 横向和纵向越权；
- SQL 注入、排序/筛选注入、资源枚举；
- SSRF/DNS rebinding/redirect；
- path traversal/symlink/Unicode/Zip Slip/压缩炸弹；
- 恶意 Pickle/动态代码/未知可执行文件；
- Markdown/HTML/XSS/外链/Prompt Injection；
- 预签名 URL 泄漏/过期/范围；
- 日志/审计/Trace/错误/浏览器存储的 Secret canary；
- 限流、并发、幂等和资源耗尽。

### 门禁

- Critical/High 漏洞阈值和例外流程需确认；
- 例外必须有 owner、到期、缓解和 Accepted 风险；
- 扫描结果保留 SBOM/镜像 digest/依赖锁；
- 上传内容永不在业务服务/预览 Worker 中执行；
- Skill/Agent 配置纳入代码评审和扫描；
- 安全失败有稳定错误、审计和必要告警，不回显攻击细节。

### 追踪

- Journey：`JRN-P5-004`
- NFR：`NFR-SEC-*`

## REQ-OPS-001 运行 Dashboard、告警与 Runbook

```yaml
status: OPEN
priority: MUST
phase: P5
blockedBy: production/on-call model
minimumEvidenceLevel: E5
```

### Dashboard

- API/MCP SLI；
- Auth failure/lock/replay/denial；
- Job pending/running/retry/dead/lease；
- Inbox/Outbox/Delivery backlog；
- Gitea/MinIO/DVC/DB/JVM；
- Published consistency；
- Storage capacity/quota；
- Agent Tool calls/denials/high-risk；
- Asset/publish/download business metrics。

### Alert

每条有 severity、threshold/window、dedupe、owner、runbook、silence 和恢复条件。至少覆盖：

- 服务/关键依赖不可用；
- 错误率/延迟；
- DEAD/积压/租约过期；
- Tag/Manifest/DVC 一致性；
- Token 重放/异常拒绝；
- 存储容量/备份失败；
- Reconciler MANUAL/SECURITY；
- Webhook 连续失败。

### Runbook

- 症状、影响、首查 Dashboard/Query；
- 安全的诊断命令（不打印 Secret）；
- 缓解、恢复和验证；
- 人工 retry/compensation 的权限与审计；
- 升级/回退（DB 只前向）；
- 联系/升级路径；
- 演练频率。

## REQ-REL-001 发布候选全量恢复与一致性审计

```yaml
status: PROPOSED
priority: MUST
phase: P5
minimumEvidenceLevel: E5
```

在发布候选 Commit：

1. 从干净环境启动 Compose；
2. 执行 P0-B、P1、P2、P3、P4 全部 MUST Journey；
3. 注入依赖/Worker/Webhook 故障并恢复；
4. 运行 Reconciler 无未解释差异；
5. 执行备份→隔离恢复→随机 Published DVC 校验；
6. 执行性能/安全门禁；
7. OpenAPI/MCP/Event/Flyway/前端生成类型无漂移；
8. Evidence Manifest 全部绑定同一 Commit/版本组合；
9. 无 MUST SKIP、无过期风险例外、无 Placeholder/Noop 进入可部署 Profile；
10. 生成发布说明和剩余 SHOULD/COULD，不把它们误写成已实现。

## P5 出口

- Inbox/Reconciler 对所有权威事实源闭环；
- 预览 MUST 满足隐私、授权、资源隔离和支持格式矩阵；
- 真实恢复演练达 RPO/RTO；
- 性能和安全门禁通过；
- Dashboard/Alert/Runbook 经故障演练；
- 全量发布候选审计无 MUST 缺口。
