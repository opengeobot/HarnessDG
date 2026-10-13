---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-081
status: DRAFT
implementationAuthorized: false
phase: P2
baseCommit: a0227961e085a6feae627bff7fcc276937b63b6b
stageGatePassed: false
stageGateEvidence: AI 起草 DRAFT（2026-10-12，DEC-009：AI 不得自批、不得回写审批位）。
  差距事实链（TASK-P2-009 E4 复跑证据，见 EVD-P2009-001.yaml 与
  iteration-plan-2026-09-24.md §4.7）：AC-001/AC-004 PASS；AC-002/AC-003 因
  presign 下载票据双重缺陷 BLOCKED（GET /versions/{id}/download 稳定 500，
  dataset pull/--resume 无法进入下载）。第一层：MinioConfiguration 的
  presignMinioClient/presignDvcCacheMinioClient 未设 region，SDK 在签发前发起
  GetBucketLocation 网络往返，端点解析为 externalEndpoint（容器内 localhost:9000
  不可达）→ ConnectException → MinioStorageAdapter L209
  StorageException("failed to presign download") → 500；同文件 s3Client/s3Presigner
  显式 Region.US_EAST_1，故 070 分片上传票据无恙（票据面不对称）。第二层：
  DownloadApplicationService.resolveObjectKey L142-144 返回
  assetId/versionId/path，与真实物化布局 PreviewJobHandler.dvcObjectKey
  hash[0:2]/hash[2:] 不一致，票据即便签发成功也指向不存在的对象键。第三层：
  票据签发路径无对象存在性守卫（对照 PreviewApplicationService L321-327 先例）。
  本卡生效前置（缺一不得开工）：① 批准人评审本卡并裁定 §9 停止条件列出的三项
  口径（权限位 asset:read 与 asset:download、presignUpload 归属、region 是否暴露为
  配置项）；② 批准人回写本卡审批位并按届时 HEAD 更新 baseCommit、stageGatePassed
  置 true；③ 077/078/079/080 的未提交面由批准人处置并提交后，本卡以干净工作树开工
  （preExistingDirtyPaths 有意留空：本卡不接受以越界面摘要换取 -CheckChangedPaths
  放行，基线未清理时该检查必须失败并据实登记）。
stageGateEvidenceRefs:
  - docs/ai-spec/tasks/evidence/EVD-P2009-001.yaml
  - docs/architecture/iteration-plan-2026-09-24.md
requirements:
  - REQ-DL-001
  - REQ-DST-CLI-001
acceptanceScenarios:
  - AC-P2-DL-001
  - AC-DST-CLI-002
  - AC-DST-CLI-003
decisions:
  - DEC-008
scenarioEvidencePlan:
  - AC-P2-DL-001|E4|真实 Compose 栈：对已 PUBLISHED 且工件已物化到 dvc-cache 的版本
    GET /api/v1/versions/{versionId}/download 返回 200 且 presignedUrl 的 Host 为
    MINIO_PUBLIC_URL、Signature/Expires 齐备；用该 URL 在宿主机 curl 得到与
    artifact.size/digest 一致的字节流；对未物化（键不存在）版本返回 404
    DVC_OBJECT_MISSING 而非 500；后端单测覆盖 region 生效（断言签发过程零
    GetBucketLocation 往返）与键布局断言（票据 objectKey 等于 hash[0:2]/hash[2:]）
  - AC-DST-CLI-002|E4|真实 Compose 栈 + CLI：dataset pull 单工件与整版下载成功、
    落地文件 SHA256 与 manifest digest 一致、无凭据泄漏、失败路径不留临时文件；
    复用 EVD-P2009-001.yaml 的 fixture 版本断言从 500 收敛为 200；既有套件无回归
  - AC-DST-CLI-003|E4|真实 Compose 栈 + CLI：--resume 在票据过期后重新签发
    并续传成功；服务端对象缺失时返回 404 DVC_OBJECT_MISSING 且 CLI 以约定退出码退出、
    不落坏文件
crossCuttingPlan:
  - AUTHN|复用既有 JWT 过滤器与 PrincipalContextHolder；不新增匿名面；业务模块内不解析令牌
  - AUTHZ|保持 DownloadApplicationService.issueTicket 现有 requirePermission 调用形态，
    权限位口径（asset:read 对齐 REQ-DL-001 的 asset:download）由批准人裁定（§9）；
    fail closed，先于任何存储调用；不新增权限位（除批准人裁定要求）
  - DB_FILTER|无查询面变更；权限谓词与列表过滤不受影响
  - STATE|无状态机变更；不触碰版本状态与非 PUBLISHED → 409 语义
  - IDEMPOTENCY|票据签发为只读副作用（仅审计），重复调用等价；不引入新作业/幂等键
  - CONSISTENCY|对象键布局单一权威实现：dvcObjectKey 算法上提到 version.domain，
    PreviewJobHandler 与 DownloadApplicationService 共同委托，禁止复制实现导致漂移
  - ERRORS|复用既有 DVC_OBJECT_MISSING（404）；本卡不新增 ErrorCode 枚举项、不改
    contracts/openapi 响应结构（票据字段与状态码集合不变）
  - AUDIT|沿用 DOWNLOAD_TICKET_ISSUED/download:presign 既有审计；新增 404 路径产生
    与既有失败路径一致的审计，不新增 actionType
  - NOTIFICATION|N/A - 无通知面
  - TAXONOMY_I18N|N/A - 无字典/tag 与前端文案变更
  - CONFIG|MinioProperties 新增 region 属性位（缺省 us-east-1，保持现有部署行为）；
    application.yml 可选显式声明；不改 deploy/compose.yaml 与 .env（宿主机 9000 已
    映射，票据 Host 仍为 MINIO_PUBLIC_URL，可达性不变）
  - OBSERVABILITY|沿用既有请求级指标与 Trace；如实现中便于以日志区分签发失败原因，
    仅记录归因，不新增指标或告警
  - SECRETS|票据签名与凭据不落 Git、日志、测试夹具或 AI 会话输出；断言测试不打印完整
    presignedUrl 的签名参数
allowedPaths:
  - backend/src/main/java/com/aihub/integration/minio/infrastructure/MinioConfiguration.java
  - backend/src/main/java/com/aihub/integration/minio/infrastructure/MinioProperties.java
  - backend/src/main/java/com/aihub/integration/minio/infrastructure/MinioStorageAdapter.java
  - backend/src/main/java/com/aihub/transfer/application/DownloadApplicationService.java
  - backend/src/main/java/com/aihub/version/domain
  - backend/src/main/java/com/aihub/version/infrastructure/PreviewJobHandler.java
  - backend/src/main/java/com/aihub/version/application/PreviewApplicationService.java
  - backend/src/main/resources/application.yml
  - backend/src/test
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-081.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-081-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - deploy
  - contracts/openapi/aihub-v1.yaml
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/resources/db/migration/V15__discussion.sql
  - backend/src/main/resources/db/migration/V16__version_transfer.sql
  - backend/src/main/resources/db/migration/V44__preview_split_stats.sql
  - frontend
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-081-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-081.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-081.md -CheckChangedPaths
  - ./mvnw.cmd -q verify（backend 目录；覆盖 region 生效、键布局单一实现、票据存在性守卫 404 映射与 preview 链无回归）
  - docker compose config --quiet（deploy/compose 目录，确认零改动仍成立）
  - ./deploy/compose/scripts/verify.sh（真实 Compose 栈 E4：download 票据 200 + dataset pull 与 --resume 全链，既有 V01-V28 无回归）
approvedBy: <human-or-controlled-approval>
approvedAt: <UTC-timestamp>
---

# TASK-P0BR-081：presign 下载票据双重缺陷修复（AC-DST-CLI-002/003 阻塞根因，草案待批准）

> 状态：`DRAFT`（AI 起草，2026-10-12；DEC-009——批准人回写本卡审批位前，禁止实施）
> 规则：一个任务只交付一个可独立验收的纵向结果——「已发布版本的工件可经票据真实下载」。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。

## 1. 目标

使 `REQ-DL-001` / `AC-P2-DL-001` 与 `REQ-DST-CLI-001` 的 `AC-DST-CLI-002`、`AC-DST-CLI-003` 在真实 Compose 栈上从 BLOCKED 收敛为可验证 PASS：下载票据能够签发、票据指向的对象真实存在、对象缺失时以 404 `DVC_OBJECT_MISSING` 明确失败而非 500。

## 2. 差距与根因（E4 证据已固定）

1. **签发即失败（第一层）**：`MinioConfiguration` 中 `presignMinioClient`（L72-78）与 `presignDvcCacheMinioClient`（L81-93）未设置 region。MinIO Java SDK 在 region 未知时，`getPresignedObjectUrl` 会先向签发端点发起 `GetBucketLocation`。签发端点取 `resolvePresignEndpoint()` = `externalEndpoint`，Compose 中由 `AIHUB_MINIO_PUBLIC_ENDPOINT=${MINIO_PUBLIC_URL}` 注入为 `http://localhost:9000`，在 backend 容器内不可达 → `ConnectException` → `MinioStorageAdapter.presignDownload` L209 `StorageException("failed to presign download")` → HTTP 500。对照同文件 `s3Client`（L104）与 `s3Presigner`（L119-120）显式 `Region.US_EAST_1`（纯本地签名，无网络往返），故 070 的分片上传票据正常——这是票据面不对称的直接来源。
2. **键布局错配（第二层）**：`DownloadApplicationService.resolveObjectKey` L142-144 返回 `assetId/versionId/artifact.path()`；真实物化布局为 `PreviewJobHandler.dvcObjectKey(dvcHash, path)` = `hash[0:2]/hash[2:]`（068 链三处调用的唯一权威实现）。修好第一层后，票据仍指向不存在的对象。
3. **无存在性守卫（第三层）**：`presignDownload` 不校验对象存在。对照 `PreviewApplicationService` L321-327 已有 `storagePort.objectExists` 守卫先例。

以上现场证据见 `docs/ai-spec/tasks/evidence/EVD-P2009-001.yaml` 与 `docs/architecture/iteration-plan-2026-09-24.md` §4.7。

## 3. 修复方案（三层同做，缺一则纵向结果仍不可验收）

1. **票据客户端 region 化**：`MinioProperties` 新增 `region`（缺省 `us-east-1`），`MinioConfiguration` 对 `MinioClient` Bean（至少两个 presign Bean）统一 `.region(...)`，使签名本地完成、消除 `GetBucketLocation` 往返。签发 URL 的 Host 仍来自 `resolvePresignEndpoint()`，故对宿主/CLI 的可达性不变，`deploy/` 零改动。
2. **对象键布局单一权威**：把 `dvcObjectKey` 算法上提为 `com.aihub.version.domain` 的 `DvcObjectKey`（纯函数、无框架依赖），`PreviewJobHandler` 与 `DownloadApplicationService` 共同委托。此举同时消除 transfer → version.infrastructure 的分层反向依赖，并满足禁止复制漂移的既有约束。
3. **票据面存在性守卫**：签发前 `storagePort.objectExists(resolveBucket(asset), objectKey)`，缺失抛 `NotFoundException(ErrorCode.DVC_OBJECT_MISSING)`（404，`error-catalog.md` L161 / `ErrorCode.java` L202 既有项）。`resolveBucket` 现返回字面量 `dvc-cache`，与 `AIHUB_DVC_BUCKET: dvc-cache` 的一致性以断言/测试固定。

## 4. 行为切片

| 切片 | 场景 | 判据 |
| --- | --- | --- |
| S1 | region 生效 | 容器内 `localhost:9000` 不可达时票据仍签发成功；测试断言签发路径无 `GetBucketLocation` 调用 |
| S2 | 键布局正确 | 票据 objectKey 严格等于 `hash[0:2]/hash[2:]`；宿主 `curl` 得 200 且摘要一致 |
| S3 | 缺失即 404 | 未物化工件 → 404 `DVC_OBJECT_MISSING`，不再 500；审计与错误映射符合既有约定 |
| S4 | CLI 全链 | `dataset pull` 与 `--resume` 通过（AC-DST-CLI-002/003），无凭据泄漏、失败不留残档 |
| S5 | 无回归 | 068 preview 链、070 分片上传票据、Compose V01-V28 全绿 |

## 5. 契约与数据先行

- 无 OpenAPI 变更：`DownloadController` 路径与 `DownloadTicket` 字段集合、状态码语义保持（新增 404 属既有错误码集合内的可达状态，`error-catalog` 已登记）。
- 无 Flyway 迁移：不涉及数据模型。
- 无 MCP / 事件契约变更。
- `contracts/openapi/aihub-v1.yaml` 列入 `forbiddenPaths` 以机器化固定「零契约 diff」承诺。

## 6. 权限与审计

沿用 `DOWNLOAD_TICKET_ISSUED` / `download:presign` 审计与既有 fail-closed 授权前置。权限位口径差异（实现 `asset:read` vs `REQ-DL-001` 声明 `asset:download`）列为 §9 裁定点，未经批准人不擅自改动。

## 7. 验证

按 Front Matter `requiredValidationCommands` 执行；E4 必须在真实 Compose 栈复跑 `dataset pull` / `--resume`，并保留 EVD-P2009-001 中的失败现场作为修复前基线对照。

## 8. 证据

`docs/ai-spec/tasks/evidence/EVD-P0BR-081-001.yaml`（模板：`docs/ai-spec/templates/evidence-manifest.yaml`），逐项 AC 记录 E4 现场、命令、退出码与脱敏确认。

## 9. 停止条件（须回请批准人裁定，不得自行收敛）

1. 权限位：改为 `asset:download`（对齐 REQ-DL-001，属行为收紧，可能影响既有 e2e 与客户端）还是修订 REQ-DL-001 口径？
2. `presignUpload`（`StoragePort` L41 声明、全仓无生产调用方）：随本卡一并 region 化并保持未使用，还是另卡清理？本卡默认仅统一 Bean 层 region，不动接口。
3. `region` 是否需要暴露为可配置项（缺省 `us-east-1`）还是硬编码常量？本卡按可配置缺省起草。
4. 若在实施中发现守卫与预览链存在桶/凭据不一致（`resolveBucket` 字面量 vs `AIHUB_DVC_BUCKET`），停止并回报，不以放宽断言换取通过。
5. 任何为使测试转绿而削弱校验、授权、数据库约束或测试的做法一律禁止。
6. 批准人若裁定本卡与 077/078 未提交面存在 baseCommit 冲突，以本卡 FM 生效前置③为准：先处置提交面，再以届时 HEAD 重建 baseCommit。
