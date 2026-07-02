# 数据事实源与平面划分（Data Ownership）

> 依据：`prd/DEEP_RESEARCH_内部AI资产管理平台设计.md` 第 0、3、4.2 节
> 关联：`docs/adr/ADR-0001-technology-baseline.md`
> 作者：AxeXie

本文件固化平台的"事实源（Source of Truth）"边界与控制面/数据面/版本面分离原则。任何对边界
的更改都必须先新增并被接受的 ADR。

## 1. 四类事实源矩阵

平台不把 Gitea、DVC、MinIO、PostgreSQL 简单拼接，而是明确每类信息的唯一权威事实源：

| 信息类型 | 权威事实源 | 存储内容 | 说明 |
| --- | --- | --- | --- |
| 代码、资产卡片、清单、版本标签 | **Gitea** | `README.md`、`asset.yaml`、配置、脚本、`.dvc`、`dvc.yaml`、`dvc.lock`、Commit、Tag | Git Commit/Tag 是可审计版本锚点；受保护 Tag 阻止绕过发布流程覆盖正式版本 |
| 模型/数据集实际内容 | **DVC + MinIO** | 模型权重、数据集目录、多模态大文件的内容版本 | DVC Hash 标识内容并寻址，MinIO（`dvc-cache`）保存对象 |
| 业务状态、权限、搜索、统计与协作讨论 | **PostgreSQL** | 工作流状态、授权、Discussion/Comment、任务、审计、查询投影、Outbox/Inbox/幂等 | 是业务协作、查询与流程投影，不替代 Git/DVC 的版本事实 |
| 临时上传与预览产物 | **MinIO** | 上传暂存（`asset-staging`）、预览/缩略图/统计（`asset-preview`） | 有生命周期策略，不属于正式版本 |

### 一致性要求

- 正式版本必须同时具备 `Git Tag + Commit SHA + Manifest/DVC Digest`；仅有数据库中的
  `version=x.y.z` 不构成有效版本。
- PostgreSQL 中的资产/版本投影必须带 `source_commit` 与 `manifest_digest`，并能在投影损坏时
  **从 Gitea + DVC 重建**。
- 发布是跨 PostgreSQL、Gitea、MinIO 的分布式操作，使用 Saga + Outbox + Inbox + 幂等 + 定时
  对账保证最终一致，禁止出现"数据库已发布但 Tag 不存在"。
- 暂存与预览对象按生命周期回收，不得被当作正式内容引用。

## 2. 控制面 / 数据面 / 版本面分离

| 平面 | 请求内容 | 路径 |
| --- | --- | --- |
| 控制面 | 搜索、元数据、版本、审批、权限、任务 | Agent/Browser → Backend → PostgreSQL / Gitea |
| 数据面 | 权重、数据文件、压缩包、预览文件 | Client ↔ MinIO |
| 版本面 | Commit、Tag、DVC 指针、Manifest | Client/Worker ↔ Gitea；DVC ↔ MinIO |

分离原则：

1. **元数据走后端 API，大文件不经后端中转。** 后端只负责授权、签名、校验编排与状态更新，
   不得代理 GB/TB 级文件的完整数据流。
2. **大文件通过 MinIO 预签名 URL 或 DVC 直传直下**，避免二进制经过 Agent 上下文或后端内存。
3. **版本面由 Git + DVC 承担**，后端不重新实现 Git 或 DVC 协议，只协调与校验。
4. **下载必须经授权**：通过 DVC 凭据、短期预签名 URL 或后端授权代理获得；正式对象不由用户
   直接浏览 Bucket，正式 DVC Bucket 禁止匿名 List。

## 3. 边界守则

- 不得让 PostgreSQL 成为内容版本或代码的权威事实源。
- 不得把业务发布状态写回 Gitea 作为权威来源（Gitea 权限由后端单向投影，定时 Reconciler
  检测漂移，避免双向同步循环）。
- 不得用 MinIO 暂存/预览对象替代正式版本。
- Asset Discussion/Comment 是 PostgreSQL 业务状态并继承资产授权；不得把 Gitea Issue 或通知表
  静默当作讨论事实源。
- 任何对上述事实源与平面边界的调整都必须先新增 ADR（见 ADR-0001 的变更治理条款）。
