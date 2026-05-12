# Spec: HarnessDG Phase 2 - 治理增强

## 0. 文档信息

| 字段 | 内容 |
|------|------|
| **Feature** | HarnessDG Phase 2 治理增强 |
| **版本** | v1.0.0 |
| **状态** | 待实现 |
| **作者** | AxeXie |
| **创建时间** | 2026-05-11 |
| **关联 PRD** | docs/HarnessDG-v2.md |
| **目标 Phase** | Phase 2: 治理增强（3-4个月） |
| **前置依赖** | Phase 1 最小业务闭环 |

---

## 1. Problem Statement

### 1.1 问题背景

Phase 1 跑通了最小业务闭环后，平台面临以下治理短板：

1. **数据接入依赖手工配置**：业务对象接入需要手动编写 SeaTunnel Pipeline，缺乏标准化模板。
2. **质量规则事后补做**：指标发布后质量规则未自动生成，数据质量问题发现滞后。
3. **审批流缺失**：指标创建、数据接入等操作直接发布，缺少数据治理人员审批环节。
4. **元数据与血缘不完整**：本体层定义了血缘关系，但未与 OpenMetadata 打通，无法可视化展示完整血缘链路。
5. **异常诊断依赖人工**：任务失败或指标波动时，需人工查看日志排查，效率低下。
6. **周报产出仍手动**：经营周报需人工从指标系统导出数据后再编写报告。

### 1.2 目标

补齐 Phase 1 缺失的治理核心能力：

```
数据接入模板化 -> 质量规则自动化 -> 审批流规范化 -> 周报生成智能化 -> 异常诊断初版
```

通过集成 SeaTunnel、OpenMetadata、Dagster 审批流，实现：

- 数据接入任务模板化，减少手工配置
- 指标发布时自动生成质量规则
- 核心操作需经过审批流程
- 经营周报自动从语义层拉取数据生成
- 任务失败时自动关联根因分析

---

## 2. Success Metrics

### 2.1 功能验收标准

| 序号 | 验收项 | 验证方式 |
|------|--------|---------|
| 1 | 数据接入任务模板可用（至少 3 种数据源类型） | 前端可选择数据源类型并完成接入配置 |
| 2 | SeaTunnel Pipeline 自动生成 | 提交接入任务后自动生成 .conf 配置文件 |
| 3 | OpenMetadata 元数据注册完成 | 新创建的 Entity/Metric 可在 OpenMetadata 中查询 |
| 4 | 质量规则自动生成 | 指标发布时自动创建完整性、唯一性、波动阈值规则 |
| 5 | 审批流完整执行 | 指标创建需经过审批后才能发布 |
| 6 | 周报自动生成可用 | 选择经营主题和时间范围后可生成 Markdown 周报 |
| 7 | 异常诊断返回根因分析 | 任务失败时自动关联日志、血缘、变更并给出诊断结论 |
| 8 | 字典管理后台上线 | 平台管理员可维护字典分组和字典项 |

### 2.2 性能指标

| 指标 | 目标值 |
|------|--------|
| SeaTunnel Pipeline 生成耗时 | < 3s |
| OpenMetadata 元数据同步延迟 | < 10s |
| 质量规则校验 P95 响应时间 | < 1s |
| 审批流节点处理耗时 | < 500ms |
| 周报生成耗时（不含数据计算） | < 5s |
| 异常诊断分析耗时 | < 10s |

### 2.3 治理指标

| 指标 | 目标值 |
|------|--------|
| 核心指标质量规则覆盖率 | > 90% |
| 审批流程覆盖率 | 100%（所有发布操作） |
| 血缘链路完整率 | 100% |
| 字典管理后台可维护项 | 20 个字典分组 |

---

## 3. User Stories

### 3.1 数据开发人员 - 创建数据接入任务

**作为** 数据开发人员  
**我希望** 通过模板化向导创建数据接入任务  
**以便** 无需手动编写 SeaTunnel 配置即可完成数据同步

**验收条件：**
- [ ] 支持选择数据源类型（MySQL、PostgreSQL、Kafka 等）
- [ ] 自动填充字段映射建议
- [ ] 生成 SeaTunnel Pipeline 配置文件
- [ ] 支持增量/全量/事件驱动同步模式选择

### 3.2 数据治理人员 - 审批指标发布

**作为** 数据治理人员  
**我希望** 在审批中心查看待审批的指标定义  
**以便** 确认口径合规、确权清晰后方可发布

**验收条件：**
- [ ] 审批列表展示待审批任务
- [ ] 可查看指标定义、口径说明、负责人
- [ ] 支持通过/驳回操作并填写意见
- [ ] 审批通过后指标状态变更为 active

### 3.3 业务分析师 - 查看质量规则

**作为** 业务分析师  
**我希望** 查看已发布指标关联的质量规则  
**以便** 了解数据质量监控情况

**验收条件：**
- [ ] 指标详情页展示关联质量规则列表
- [ ] 支持查看规则类型、阈值、最近校验结果
- [ ] 质量异常时显示告警信息

### 3.4 业务人员 - 生成经营周报

**作为** 业务人员  
**我希望** 选择经营主题后自动生成周报  
**以便** 无需手动导出数据即可获取分析报告

**验收条件：**
- [ ] 支持选择经营主题（数据域）和时间范围
- [ ] 自动拉取已认证指标数据
- [ ] 生成包含环比、同比、排名的结构化报告
- [ ] 支持导出 Markdown 或查看在线版

### 3.5 运维人员 - 异常诊断

**作为** 运维人员  
**我希望** 任务失败时自动诊断根因  
**以便** 快速定位问题并采取修复措施

**验收条件：**
- [ ] 任务失败时自动触发诊断
- [ ] 关联运行日志、血缘依赖、最近变更
- [ ] 区分权限问题、数据缺失、质量异常等根因
- [ ] 给出修复建议（重跑、回滚、改规则等）

### 3.6 平台管理员 - 维护数据字典

**作为** 平台管理员  
**我希望** 通过管理后台维护字典数据  
**以便** 前端分类选项实时生效且无需重新发版

**验收条件：**
- [ ] 支持创建/编辑/停用字典分组
- [ ] 支持增删改字典项、调整排序
- [ ] 支持维护多语言文案
- [ ] 支持导入导出字典数据

---

## 4. Acceptance Criteria

### 4.1 数据接入

| 条件 | 描述 |
|------|------|
| 数据源类型 | 支持 MySQL、PostgreSQL、Kafka、HTTP API |
| 字段映射 | 自动建议源字段与目标字段映射关系 |
| 同步模式 | 支持全量初始化、增量同步、事件驱动 |
| Pipeline 生成 | 根据配置自动生成 SeaTunnel .conf 文件 |
| 任务绑定 | 接入任务必须绑定业务对象和责任人 |

### 4.2 质量规则

| 条件 | 描述 |
|------|------|
| 自动生成 | 指标发布时自动创建基础质量规则 |
| 规则类型 | 完整性（非空）、唯一性（主键）、波动阈值、枚举值校验 |
| 校验频率 | 支持每次执行校验、每日定时校验 |
| 告警机制 | 质量异常时记录告警并通知责任人 |

### 4.3 审批流

| 条件 | 描述 |
|------|------|
| 审批节点 | 指标发布、数据接入需经过审批 |
| 审批人 | 根据数据域自动指定审批人 |
| 审批状态 | 待审批、已通过、已驳回 |
| 审批记录 | 记录审批意见、审批时间、审批人 |

### 4.4 元数据与血缘

| 条件 | 描述 |
|------|------|
| OpenMetadata 集成 | Entity、Metric 定义同步至 OpenMetadata |
| 血缘记录 | 记录数据源 -> Entity -> Metric -> 报表的完整链路 |
| 可视化 | 前端可展示血缘关系图 |

### 4.5 周报生成

| 条件 | 描述 |
|------|------|
| 数据拉取 | 从语义层拉取已认证指标数据 |
| 确定性计算 | 环比、同比、排名由确定性程序计算 |
| AI 叙述 | AI 仅负责文字归因、风险提示，不负责数值计算 |
| 输出格式 | 标准化 Markdown 模板 |

### 4.6 异常诊断

| 条件 | 描述 |
|------|------|
| 自动触发 | 任务失败或指标波动超阈值时触发 |
| 日志关联 | 关联运行日志、Dagster 日志、Agent 决策日志 |
| 根因分类 | 区分权限、数据、质量、逻辑变更、性能问题 |
| 修复建议 | 给出可操作的修复建议 |

### 4.7 字典管理后台

| 条件 | 描述 |
|------|------|
| 分组管理 | 创建/编辑/停用字典分组 |
| 字典项管理 | 增删改、排序、颜色/图标配置 |
| 多语言 | 维护 zh_CN / en_US 文案 |
| 导入导出 | 支持 Excel/JSON 批量操作 |

---

## 5. Non-Goals

以下内容明确 **不在 Phase 2 范围内**：

| 排除项 | 原因 |
|--------|------|
| 全自动 Pipeline 生成 | Phase 3 智能化升级 |
| 事件驱动调度与传感器 | Phase 3 调度增强 |
| 异常自愈与策略推荐 | Phase 3 自愈能力 |
| OpenClaw 接入 | Phase 3 外部 Agent 扩展 |
| 日语、韩语支持 | Phase 3 多语种扩展 |
| 多业务域/多租户 | Phase 3 规模化推广 |

---

## 6. Constraints

### 6.1 技术约束

| 约束 | 描述 |
|------|------|
| SeaTunnel | 通过适配器集成，禁止硬编码 Pipeline 逻辑 |
| OpenMetadata | 通过 REST API 集成，禁止直接操作数据库 |
| 审批流 | 基于现有 task_tables 扩展，不引入新工作流引擎 |
| 周报生成 | 数值计算由确定性程序完成，AI 只负责语义叙述 |
| 字典管理 | 所有变更写入 sys_dict_group/item 表，实时生效 |

### 6.2 安全约束

| 约束 | 描述 |
|------|------|
| 审批权限 | 只有指定审批人可操作审批 |
| 质量告警 | 告警通知只发送给责任人，不泄露敏感数据 |
| 血缘查询 | 血缘数据脱敏后展示 |

### 6.3 开发规范

| 约束 | 描述 |
|------|------|
| 适配器模式 | SeaTunnel、OpenMetadata 集成均使用适配器封装 |
| 模板化 | Pipeline、质量规则、周报均使用模板引擎生成 |
| 审计 | 所有审批、字典变更操作记录审计日志 |

---

## 7. Architecture Decision

### 7.1 Phase 2 新增组件

```
┌──────────────────────────────────────────────────────────────┐
│ 新增前端页面                                                 │
│ 数据接入向导 / 审批中心 / 质量看板 / 周报生成 / 字典管理后台  │
└────────────────────────▲─────────────────────────────────────┘
                         │
┌────────────────────────┴─────────────────────────────────────┐
│ 新增后端服务                                                 │
│ ApprovalService / QualityService / ReportService             │
│ DiagnosisService / DictionaryAdminService                    │
└────────────────────────▲─────────────────────────────────────┘
                         │ HTTP
┌────────────────────────┴─────────────────────────────────────┐
│ 新增外部集成                                                 │
│ SeaTunnel Adapter / OpenMetadata Adapter / Dagster Approval  │
└──────────────────────────────────────────────────────────────┘
```

### 7.2 服务边界扩展

| 服务 | Phase 2 新增职责 |
|------|-----------------|
| **Backend** | 审批流、质量规则、周报模板、字典管理后台 API |
| **Agent** | 异常诊断分析、周报文字归因 |
| **Frontend** | 接入向导、审批中心、质量看板、周报生成、字典管理 |
| **SeaTunnel** | 数据接入 Pipeline 执行 |
| **OpenMetadata** | 元数据存储、血缘可视化 |

---

## 8. Module Breakdown

### 8.1 新增模块清单

| 模块 | 目录 | 职责 |
|------|------|------|
| `backend/harness-app` | Java 后端主服务 | 新增审批、质量、周报、字典管理 API |
| `agent/app/services` | Python Agent 服务 | 新增异常诊断引擎、周报生成服务 |
| `frontend/apps/web` | React Web 应用 | 新增接入向导、审批、质量、周报、字典管理页面 |
| `agent/app/adapters` | Python 适配器 | SeaTunnel Adapter、OpenMetadata Adapter |

---

## 9. Interface Contracts

### 9.1 数据接入 API

#### 9.1.1 创建接入任务

```http
POST /api/v1/ingestion/tasks
Content-Type: application/json

{
  "name": "订单数据接入",
  "entity_code": "Order",
  "source_type": "mysql",
  "source_config": {
    "host": "mysql.internal",
    "port": 3306,
    "database": "biz_db",
    "table": "orders"
  },
  "sync_mode": "incremental",
  "owner_id": "user_001"
}

Response 201:
{
  "id": "ing_001",
  "pipeline_conf": "source { MySQL { ... } } transform { ... } sink { ... }",
  "status": "pending_approval"
}
```

### 9.2 审批流 API

#### 9.2.1 提交审批

```http
POST /api/v1/approvals/submit
{
  "target_type": "metric",
  "target_id": "met_001",
  "approver_role": "data_governor",
  "reason": "新指标发布"
}

Response 201:
{
  "id": "appr_001",
  "status": "pending"
}
```

#### 9.2.2 审批操作

```http
PUT /api/v1/approvals/{approvalId}/action
{
  "action": "approve",
  "comment": "口径确认无误"
}

Response 200:
{
  "id": "appr_001",
  "status": "approved",
  "approved_at": "2026-05-11T14:30:00Z"
}
```

### 9.3 质量规则 API

#### 9.3.1 查询指标关联质量规则

```http
GET /api/v1/quality/rules?metric_id=met_001
Response 200:
[
  {
    "id": "qr_001",
    "type": "completeness",
    "field": "order_amount",
    "rule": "NOT NULL",
    "last_result": "pass",
    "checked_at": "2026-05-11T10:00:00Z"
  }
]
```

### 9.4 周报生成 API

#### 9.4.1 生成周报

```http
POST /api/v1/reports/generate
{
  "report_type": "weekly",
  "domain_code": "sales",
  "time_range": {
    "start": "2026-05-04",
    "end": "2026-05-10"
  },
  "metrics": ["revenue", "order_count"]
}

Response 200:
{
  "id": "rpt_001",
  "content": "# 经营周报 - 销售域\n\n## 核心指标\n\n### 收入\n- 本周收入：¥1,234,567\n- 环比：+5.2%\n...",
  "format": "markdown",
  "generated_at": "2026-05-11T10:00:00Z"
}
```

### 9.5 异常诊断 API

#### 9.5.1 触发诊断

```http
POST /api/v1/diagnosis/trigger
{
  "task_id": "task_001",
  "exception_type": "task_failed"
}

Response 200:
{
  "id": "diag_001",
  "root_cause": "数据源连接失败",
  "category": "data_missing",
  "suggestions": ["检查数据源配置", "确认网络连通性"],
  "related_logs": ["log_001", "log_002"]
}
```

### 9.6 字典管理后台 API

#### 9.6.1 创建字典分组

```http
POST /api/v1/dictionary/groups
{
  "code": "new_category",
  "name": { "zh_CN": "新分类", "en_US": "New Category" },
  "is_tree": false,
  "is_editable": true
}

Response 201:
{
  "id": "dg_001",
  "code": "new_category"
}
```

#### 9.6.2 导入字典项

```http
POST /api/v1/dictionary/items/import
Content-Type: multipart/form-data

file: <excel_file>
group_code: "metric_type"

Response 200:
{
  "imported_count": 10,
  "errors": []
}
```

---

## 10. Database Schema

### 10.1 Phase 2 新增/扩展表

| 表名 | 用途 | 迁移脚本 |
|------|------|---------|
| `approval_record` | 审批记录（扩展 Phase 1 表） | V012 |
| `quality_rule` | 质量规则定义 | V013 |
| `quality_result` | 质量校验结果 | V013 |
| `lineage_record` | 血缘链路记录 | V013 |
| `datasource_config` | 数据源配置 | V014 |
| `ingestion_task` | 接入任务 | V014 |
| `report_template` | 报表模板 | V015 |
| `report_instance` | 报表实例 | V015 |
| `sys_dict_group` | 字典分组（扩展管理字段） | V001/V016 |

### 10.2 approval_record 关键字段

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PK | UUID |
| target_type | VARCHAR(50) | NOT NULL | 审批对象类型：metric/ingestion |
| target_id | VARCHAR(36) | NOT NULL | 审批对象 ID |
| approver_id | VARCHAR(36) | NOT NULL | 审批人 ID |
| status | VARCHAR(20) | DEFAULT 'pending' | pending/approved/rejected |
| comment | TEXT | | 审批意见 |
| created_at | TIMESTAMP | DEFAULT NOW() | 提交时间 |
| approved_at | TIMESTAMP | | 审批时间 |

### 10.3 quality_rule 关键字段

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PK | UUID |
| metric_id | VARCHAR(36) | FK, NOT NULL | 关联指标 ID |
| type | VARCHAR(50) | NOT NULL | completeness/uniqueness/threshold/enumeration |
| field | VARCHAR(100) | NOT NULL | 校验字段 |
| rule_expression | TEXT | NOT NULL | 规则表达式 |
| threshold | DECIMAL(10,2) | | 阈值 |
| frequency | VARCHAR(20) | DEFAULT 'each_run' | 校验频率 |
| enabled | BOOLEAN | DEFAULT true | 是否启用 |
| created_at | TIMESTAMP | DEFAULT NOW() | 创建时间 |

---

## 11. Risk Assessment

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| SeaTunnel 服务不可用 | 数据接入任务失败 | 提供手动配置回退路径 |
| OpenMetadata API 变更 | 元数据同步失败 | 适配器封装，定期测试 API 兼容性 |
| 审批流阻塞业务 | 指标发布延迟 | 支持快速审批通道和预授权 |
| 质量规则误报 | 告警疲劳 | 阈值可调节，支持规则白名单 |
| 周报数值计算错误 | 报告可信度降低 | 数值由确定性程序计算，AI 不介入 |

---

## 12. Tasks

### Task 1: SeaTunnel 适配器实现

- 实现 SeaTunnel Pipeline 生成器
- 实现数据源配置转 .conf 模板映射
- 实现 Pipeline 提交执行 API
- **验证方式**：创建接入任务后自动生成并执行 Pipeline

### Task 2: OpenMetadata 适配器实现

- 实现 Entity/Metric 元数据同步
- 实现血缘链路注册
- 实现元数据查询 API 代理
- **验证方式**：创建指标后 OpenMetadata 中可查询

### Task 3: 审批流服务实现

- 实现审批提交、审批操作 API
- 实现审批人路由（按数据域）
- 实现审批状态机
- **验证方式**：指标创建后需审批才能发布

### Task 4: 质量规则服务实现

- 实现质量规则自动生成
- 实现质量校验执行引擎
- 实现质量结果查询 API
- **验证方式**：指标发布后自动生成规则并校验

### Task 5: 周报生成服务实现

- 实现经营主题与指标映射
- 实现确定性计算模块（环比、同比、排名）
- 实现 Markdown 模板渲染
- 集成 AI 文字归因
- **验证方式**：选择主题后生成完整周报

### Task 6: 异常诊断服务实现

- 实现日志关联分析
- 实现根因分类引擎
- 实现修复建议生成
- **验证方式**：任务失败时自动触发诊断并返回结论

### Task 7: 字典管理后台前端

- 实现字典分组列表页
- 实现字典项 CRUD 表单
- 实现多语言文案编辑
- 实现导入导出功能
- **验证方式**：管理员可完整维护字典数据

### Task 8: 前端新增页面

- 实现数据接入向导（4 步表单）
- 实现审批中心列表与详情
- 实现质量看板
- 实现周报生成页面
- **验证方式**：用户可完成所有 Phase 2 核心任务

### Task 9: 端到端联调

- 验证数据接入全流程
- 验证审批流
- 验证质量规则生成与校验
- 验证周报生成
- 验证异常诊断
- **验证方式**：所有 Success Metrics 验收通过

---

## 13. Dependencies

### 13.1 外部依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| SeaTunnel | 2.3+ | 数据接入 Pipeline 执行 |
| OpenMetadata | 0.12+ | 元数据与血缘管理 |
| Dagster | 1.5+ | 任务调度与审批流集成 |

### 13.2 内部依赖

| 依赖方 | 被依赖方 | 依赖内容 |
|--------|---------|---------|
| Phase 2 | Phase 1 | 本体模型、数据字典、i18n、审计日志 |
| SeaTunnel Adapter | Backend | 数据源配置、Pipeline 生成 |
| OpenMetadata Adapter | Backend | 元数据同步、血缘注册 |
| Frontend | Backend | 审批、质量、周报、字典管理 API |
| Agent | Backend | 日志关联、根因分析 |

---

## 14. Appendix

### A. Phase 2 新增字典分组

| 分组编码 | 分组名称（zh） | 项数 |
|---------|--------------|------|
| `data_source_type` | 数据源类型 | 6 |
| `sync_mode` | 同步方式 | 3 |
| `quality_rule_type` | 质量规则类型 | 5 |
| `approval_status` | 审批状态 | 3 |
| `report_type` | 报表类型 | 3 |
| `exception_type` | 异常类型 | 5 |
| `lifecycle_state` | 生命周期状态 | 5 |
| `permission_action` | 权限动作 | 8 |
| `user_role` | 用户角色 | 5 |
| `refresh_strategy` | 刷新策略 | 4 |

### B. SeaTunnel Pipeline 模板示例

```conf
env {
  job.name = "order_ingestion"
  job.mode = "BATCH"
}

source {
  MySQL {
    host = "mysql.internal"
    port = 3306
    database = "biz_db"
    table = "orders"
    username = "reader"
    password = "readonly_pwd"
  }
}

transform {
  # 字段映射与类型转换
}

sink {
  PostgreSQL {
    host = "postgres"
    port = 5432
    database = "harnessdg"
    table = "ods_orders"
    username = "harness"
    password = "harness_dev"
  }
}
```

### C. 周报 Markdown 模板

```markdown
# 经营周报 - ${domain_name}

## 报告周期
${start_date} 至 ${end_date}

## 核心指标概览

### ${metric_name}
- 本期数值：${current_value}
- 上期数值：${previous_value}
- 环比变化：${wow_change}
- 同比变化：${yoy_change}

## 维度分析

### 按 ${dimension_name} 分布
| ${dimension} | 数值 | 占比 |
|-------------|------|------|
${dimension_table}

## 风险提示
${risk_warnings}

## 下周建议
${next_week_suggestions}
```
