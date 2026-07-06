# P0-B 治理、可靠性与观测需求

> 状态：`READY`
> 依据：ADR-0002、设计 5.6-5.14、11、13-15。
> 已确认：`DEC-001`~`DEC-004`、`DEC-006`~`DEC-013`（所有相关 Q 已通过 DEC 闭合）。

## REQ-COM-001 统一 ID、上下文、响应与错误

```yaml
status: READY
priority: MUST
phase: P0-B
minimumEvidenceLevel: E3
```

### 行为

- 业务 ID 使用已登记前缀+ULID，数据库 ID 不外露；
- HTTP/MCP/Worker 入口接受或生成 requestId、trace context、locale 和 PrincipalContext；
- 客户端 X-Request-Id 需校验字符/长度，非法值替换而不是注入日志；
- 成功响应含 data/requestId/traceId/timestamp；
- 失败响应含 code/message/i18nKey/details/retryable/requestId/traceId；
- 未知异常统一 `INTERNAL_ERROR`，不暴露堆栈、SQL、Bucket、内部 URL；
- Error Catalog 每个 code 唯一绑定 HTTP status、i18nKey、retryable、alert level；
- Cursor 为不透明、签名/校验或至少绑定查询条件的令牌，不暴露数据库主键实现；
- 分页/排序类型按资源固定，禁止同一操作不兼容漂移。

### 验收

- REST 正常/校验/认证/授权/冲突/限流/依赖/未知异常快照；
- MCP 对同一 PlatformException 的协议错误映射；
- Worker 对 retryable/不可重试错误分类；
- request/trace 上下文在线程和任务完成后清理；
- Error/Permission/Config/ID 前缀 Catalog 重复扫描。

### 追踪

- Invariants：`INV-COM-001..007`
- NFR：`NFR-MNT-004`
- Acceptance：`AC-P0B-ENG-001/002`

## REQ-TAX-001 字典与运行时国际化

```yaml
status: READY
priority: MUST
phase: P0-B
permissions: [dictionary:read, dictionary:manage]
idempotency: REQUIRED for writes
minimumEvidenceLevel: E4
```

### 行为

- Dict Type 和 Item 使用稳定 dictCode/itemCode；
- MVP Catalog 至少包含 model_task/model_framework/dataset_modality/dataset_format/
  industry_tag/license_catalog/sensitivity_level/deprecation_reason；
- 新建引用只接受 ACTIVE itemCode；
- DISABLED item 禁止新引用但保留历史回显；
- create/update/enable/disable 使用 expected version，递增字典 cache version；
- 每个运营文案 i18nKey 至少有 zh-CN/en-US；
- API 按 locale 返回稳定 code 和可选 display metadata，业务逻辑不依赖翻译；
- 缺失翻译安全回退到 code 并产生可观测信号；
- 稳定状态枚举不得通过字典 API 添加。

### 错误与审计

`DICTIONARY_ITEM_NOT_FOUND/ALREADY_EXISTS/VALUE_INVALID/CONCURRENT_MODIFICATION`；
`DICTIONARY_ITEM_CREATED/UPDATED/ENABLED/DISABLED`。

### 追踪

- Invariants：`INV-DICT-001..006`
- Acceptance：`AC-P0B-TAX-001/002/006`
- Pages：`PAGE-ADM-010`

## REQ-TAG-001 平台/组织受控标签

```yaml
status: READY
priority: MUST
phase: P0-B
permissions: [tag:read, tag:manage]
idempotency: REQUIRED for writes
minimumEvidenceLevel: E4
```

### 行为

- Scope 为 PLATFORM 或 ORGANIZATION；
- 平台管理员管理平台标签，组织管理员只管理本组织标签；
- `(scopeType,scopeId,tagCode)` 唯一；
- 写资产只提交 tagIds，不接受 tags/name/code 自动创建；
- 关联前校验标签 ACTIVE 且对目标资产 Organization 可用；
- DISABLED 标签保留历史关联/显示，禁止新关联；
- enable/disable/update 使用 rowVersion；
- merge 若进入 MVP，必须定义 source/target、引用迁移、幂等、恢复和审计；否则明确非目标；
- 删除仅允许从未引用或完成保留策略的标签，默认使用 DISABLED。

### 错误与审计

`TAG_NOT_FOUND/ALREADY_EXISTS/VALUE_INVALID/CONCURRENT_MODIFICATION`；
`TAG_CREATED/UPDATED/ENABLED/DISABLED/MERGED`（Merge 事件待范围确认）。

### 追踪

- Invariants：`INV-TAG-001..007`
- Acceptance：`AC-P0B-TAX-003..005`
- Pages：`PAGE-ADM-011`

## REQ-CFG-001 类型化非敏感运行配置

```yaml
status: READY
priority: MUST
phase: P0-B
permission: system:configure
idempotency: REQUIRED
minimumEvidenceLevel: E4
```

### 行为

- Config Catalog 由代码声明 key/type/default/validator/scope/hotReloadable/sensitivity；
- API 只能管理 Catalog 中允许的运行时 key；
- 支持 STRING/INTEGER/LONG/BOOLEAN/DURATION/JSON 的确定性解析；
- 值同时通过类型、范围、枚举/Schema 和作用域校验；
- key 或 value 命中 Secret 规则时拒绝，不能只按 key 名判断；
- update 要求 expected version 并记录 old/new 的脱敏摘要；
- hotReloadable=true 提交后原子刷新读模型；false 明确返回 restartRequired；
- 安全和发布策略配置要求二次确认，双人审批是否需要由产品决策；
- 配置读取失败使用安全默认或阻止启动，不能静默宽松。

### 错误与审计

`CONFIG_NOT_FOUND/SECRET_FORBIDDEN/VALUE_INVALID/CONCURRENT_MODIFICATION`；
`SYSTEM_CONFIGURATION_UPDATED`，不得记录 Secret/完整高敏 JSON。

### 追踪

- Invariants：`INV-CFG-001..006`
- Acceptance：`AC-P0B-CFG-001..003`
- Page：`PAGE-ADM-012`

## REQ-IDM-001 写接口幂等

```yaml
status: READY
priority: MUST
phase: P0-B
minimumEvidenceLevel: E4
```

### 适用范围

至少覆盖创建用户/Agent/组织/项目/角色/绑定/ACL/字典项/标签、资产创建、上传 complete、版本提交/
发布、任务人工 retry/cancel。每个操作明确 REQUIRED/NOT_REQUIRED，不能由客户端猜。

### 行为

- key 与 principalId、method、canonical path、request digest 共同构成作用域；
- 缺少 REQUIRED key 返回稳定错误（已通过 `IDEMPOTENCY_KEY_CONFLICT` 在 Error Catalog 中定义，DEC-019）；
- 首次请求原子登记 IN_PROGRESS，业务完成后保存原始 status/response；
- 同 key 同 digest 返回首次结果，不重复业务、审计、Outbox；
- 同 key 不同 digest 返回 `IDEMPOTENCY_KEY_CONFLICT`；
- 并发只允许一个执行；
- 崩溃遗留 IN_PROGRESS 有 lease/owner/过期与恢复规则；
- FAILED 是否缓存/可重试按操作与错误 retryable 声明；
- 客户端自动重试复用同一 key，新用户意图生成新 key。

### 追踪

- State：IdempotencyStatus
- Acceptance：`AC-P0B-IDM-001..003`
- Drift：`AUD-006`

## REQ-JOB-001 PostgreSQL 可靠任务

```yaml
status: READY
priority: MUST
phase: P0-B
permissions: [job:read, job:manage]
minimumEvidenceLevel: E4
```

### 行为

- 所有不能丢的 DVC、发布、Webhook、预览、通知和对账工作进入 job_task；
- Task Type/Schema/Handler/最大重试/超时/并发策略在受控 Catalog 注册；
- 入队与触发业务事务原子提交或经 Outbox；
- 使用 `FOR UPDATE SKIP LOCKED` 原子领取，保存 lease owner/expiry；
- Worker 重启/失租后可回收；
- Handler 必须按业务 dedupe key 幂等；
- retryable 错误按指数退避+抖动进入 RETRY_WAIT；
- 达上限或不可重试进入 DEAD 并通知/告警；
- attempt 记录时间、结果、errorCode、脱敏摘要；
- 人工 retry/cancel 检查状态和权限并审计；
- Payload 版本化且不含 Secret；
- 任务传递 trace/principal/resource context，Worker 建 Span Link；
- 正式部署不能只有 SampleJobHandler。

### 错误/审计/指标

`JOB_NOT_FOUND/STATE_NOT_ALLOWED/RETRY_EXHAUSTED`；
`JOB_MANUALLY_RETRIED/CANCELLED/DEAD`；
pending/running/retry/dead、claim conflict、lease expiry、duration、attempt 指标。

### 追踪

- Invariants：`INV-JOB-001..009`
- State：JobStatus
- Acceptance：`AC-P0B-JOB-001..005`
- Page：`PAGE-ADM-013`

## REQ-LOG-001 结构化运行日志与脱敏

```yaml
status: READY
priority: MUST
phase: P0-B
minimumEvidenceLevel: E4
```

### 行为

- stdout 使用统一 JSON encoder；
- HTTP/MCP/Worker/Job/外部依赖自动注入 service/module/traceId/spanId/requestId/principalType/
  principalId/organizationId/projectId/resourceId/jobId/action/result/errorCode/durationMs；
- 字段由 Filter/Interceptor/Task context 自动注入，业务不拼接整段 JSON；
- Authorization、Cookie、密码、JWT、credential、私钥、预签名 query、对象凭据统一脱敏；
- 请求/响应正文默认不完整记录，只允许字段白名单和大小上限；
- URL 记录时移除敏感 query；
- 异常堆栈只在统一边界记录一次；
- 日志失败不能泄露数据或改变业务结果；
- Principal/Trace context 在线程复用前清理。

### 验收

在 Header/Cookie/JSON/Form/Query/Job Payload/外部异常中植入唯一测试 Secret，扫描所有容器日志和审计表，
原文出现次数必须为 0。

### 追踪

- Acceptance：`AC-P0B-AUD-002/004/005`
- NFR：`NFR-SEC-004`、`NFR-OBS-001`

## REQ-AUD-001 不可变业务审计

```yaml
status: READY
priority: MUST
phase: P0-B
permission: audit:read
minimumEvidenceLevel: E4
```

### 行为

- AuditService 是唯一持久化入口；
- 审计与业务事务的原子性/失败隔离按事件类型声明：成功写操作必须与结果一致，认证失败即使业务回滚也必须保留；
- 记录 eventType/action/principal/resource/scope/result/errorCode/trace/request/time/脱敏 attributes；
- SUCCEEDED/FAILED/DENIED 分开；
- 设计列出的登录、Token、成员、Role/ACL/Tool、资产、版本、下载、配置、字典、Webhook、人工任务/
  对账事件 100% 覆盖；
- 不提供 Update/Delete API，数据库阻止普通应用角色改删；
- 查询使用 Cursor、受 `audit:read` 和数据范围限制；
- 保留期和归档不能由普通管理员绕过；
- 审计写失败的业务处理策略明确：高风险写应 fail closed 还是可靠缓冲需分类，不允许仅 warn 后永久丢失。

### 追踪

- Invariants：`INV-EVT-005..008`
- Acceptance：`AC-P0B-AUD-001..005`
- Page：`PAGE-ADM-014`

## REQ-NOT-001 站内通知与 Outbox

```yaml
status: READY
priority: MUST
phase: P0-B
permission: notification:read
minimumEvidenceLevel: E4
```

### 行为

- 业务事务内创建 Notification 和 Outbox；
- 接收者、eventType、i18nKey、参数、severity、resource reference 稳定；
- 只允许目标 Principal 查询/标记已读；
- 标记已读幂等，不允许修改别人通知；
- 模板按 locale 渲染，参数作为数据而非可执行模板；
- 渠道失败不回滚核心业务；
- 事务回滚时 Notification/Outbox 同时不存在；
- `VERSION_*`、`UPLOAD_FAILED`、`JOB_DEAD`、`AGENT_ACCESS_DENIED`、`STORAGE_QUOTA_WARNING`、
  `SYSTEM_DEPENDENCY_UNHEALTHY` 进入受控 Event Catalog；
- 通知管理权限（模板/Delivery/重发）与本人读取权限分离，当前 Permission Catalog 需补决策。

### 错误/指标

`NOTIFICATION_NOT_FOUND` 防止跨主体枚举；记录 unread、created、delivery state 指标，不以 principalId 做 label。

### 追踪

- Acceptance：`AC-P0B-NOT-001/002`
- Pages：`PAGE-COM-003`、`PAGE-ADM-015`

## REQ-WHK-001 签名 Webhook 投递

```yaml
status: READY
priority: MUST
phase: P0-B
minimumEvidenceLevel: E4
```

### 行为

- 每个业务事件/目标有稳定 Delivery ID；
- 请求带算法版本、签名、时间戳、Delivery ID；
- Secret 来自部署 Secret Store，不进入 DB 配置/日志；
- 目标 URL 创建和每次投递都做 scheme/host/port/DNS/IP 校验；
- 拒绝 loopback、link-local、private、reserved、metadata 地址和 DNS rebinding；
- 连接超时、5xx、允许的 429 重试；其他 4xx 默认 DEAD；
- retry 使用同 Delivery ID 和业务载荷，attempt 递增；
- 签名覆盖原始字节、时间戳和 Delivery ID；
- 达上限进入 DEAD 并告警；
- 接收方可按 Delivery ID 去重；
- 重试调度使用持久化任务/Outbox 状态，不依赖纯内存 Scheduler 保证可靠性。

### 错误/审计

`WEBHOOK_TARGET_FORBIDDEN`；目标创建/修改、人工重发和 DEAD 处置审计。

### 追踪

- Acceptance：`AC-P0B-NOT-003..005`
- NFR：`NFR-SEC-008`

## REQ-OBS-001 指标、Trace、健康、诊断与告警

```yaml
status: READY
priority: MUST
phase: P0-B
permission: system:observe
minimumEvidenceLevel: E4/E5
```

### 必需行为

- Actuator/Micrometer 暴露受保护 Prometheus 指标；
- Browser/Agent→Nginx→REST/MCP→Application→DB/Gitea/MinIO→Worker Trace 贯通；
- 持久化任务保存 Trace Context，Worker 使用新 Span + Span Link；
- liveness 只反映进程，readiness 反映接流量所需关键依赖；
- `/system/metrics/summary` 返回运营摘要，不替代 Prometheus；
- `/system/dependencies` 受权并隐藏 Endpoint、凭据、拓扑敏感细节；
- API/MCP/Job/依赖/DB/JVM/业务指标覆盖设计清单；
- DEAD、Inbox/Outbox 积压、一致性差异、依赖故障和安全事件有告警；
- observability Compose Profile 含 Collector/Prometheus/Grafana（Loki 可选策略需确认）；
- 目标是实际 Trace/Target/Dashboard/Alert 证据，不是 Bean 存在。

### 追踪

- Acceptance：`AC-P0B-OBS-001..005`
- NFR：`NFR-OBS-001..005`

## REQ-UI-001 公共管理端

```yaml
status: READY
priority: MUST
phase: P0-B
minimumEvidenceLevel: E4
```

### 行为

- 实现 `PAGE-AUTH-*`、`PAGE-COM-*` 和 P0-B `PAGE-ADM-*`；
- 所有 API 经统一生成 Client，页面不手写 URL/fetch/重复 DTO；
- Permission Provider 来自认证会话和资源授权，不默认全 Scope；
- Route Catalog、导航和 Guard 共用权限元数据；
- 所有异步页面有 Loading/Empty/Error/Retry/Success；
- 写表单使用受控 Scope/Permission/Tool/Principal/Team/Dictionary/Tag 选择；
- access 仅内存、refresh 对 JS 不可见；
- zh-CN/en-US 运行时切换，错误依赖 code/i18nKey；
- 403/404 防枚举；
- 写动作正确复用 Idempotency-Key，乐观锁冲突不覆盖；
- 关键页面有 Vitest/Testing Library 和浏览器 E2E/视觉证据；
- 页面存在或 Vite build 不构成完成。

### 追踪

- Acceptance：`AC-P0B-UI-001..006`
- NFR：`NFR-UI-001..007`
- IA/Page Catalog：`04-ui/*`
