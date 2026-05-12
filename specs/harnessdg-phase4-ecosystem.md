# Spec: HarnessDG Phase 4 - 规模化运营与生态建设

## 0. 文档信息

| 字段 | 内容 |
|------|------|
| **Feature** | HarnessDG Phase 4 规模化运营与生态建设 |
| **版本** | v1.0.0 |
| **状态** | 待规划 |
| **作者** | AxeXie |
| **创建时间** | 2026-05-11 |
| **关联 PRD** | docs/HarnessDG-v2.md |
| **目标 Phase** | Phase 4: 规模化运营与生态建设 |
| **前置依赖** | Phase 1-3 全部能力 |

---

## 1. Problem Statement

### 1.1 问题背景

Phase 1-3 完成了端到端业务任务型数据平台后，平台在规模化运营和生态建设方面仍有提升空间：

1. **模板市场缺失**：行业模板、业务域模板无法共享，各团队重复搭建。
2. **插件生态未建立**：第三方开发者无法扩展平台能力（自定义连接器、质量规则类型、Agent 技能）。
3. **运营数据不完善**：缺乏平台自身的使用数据分析，无法优化产品体验。
4. **多租户资源隔离不足**：多业务域共享底层资源，缺乏资源配额与成本分摊。
5. **开放生态未建立**：与第三方数据平台、BI 工具、AI 平台的集成能力不足。
6. **合规与审计增强**：需要满足更严格的数据合规要求（如 GDPR、等保三级）。
7. **平台能力产品化**：需要将平台能力封装为可对外输出的产品方案。

### 1.2 目标

构建 HarnessDG 规模化运营与生态体系：

```
模板市场 -> 插件生态 -> 运营分析 -> 资源治理 -> 开放集成 -> 合规增强 -> 产品化输出
```

通过引入模板市场、插件框架、运营分析中心、资源配额管理、开放集成中心、合规审计增强，实现：

- 行业模板与业务模板可共享、可交易
- 第三方开发者可扩展平台能力
- 平台运营数据可视化，指导产品优化
- 多租户资源隔离与成本分摊
- 与第三方平台深度集成
- 满足严格的数据合规要求
- 形成可对外输出的产品方案

---

## 2. Success Metrics

### 2.1 功能验收标准

| 序号 | 验收项 | 验证方式 |
|------|--------|---------|
| 1 | 模板市场上线 | 可浏览、搜索、安装行业模板 |
| 2 | 插件框架可用 | 第三方开发者可注册自定义连接器、质量规则、Agent 技能 |
| 3 | 运营分析中心上线 | 展示平台使用数据（活跃用户、任务数、指标数等） |
| 4 | 资源配额管理 | 支持按业务域配置 CPU、内存、存储配额 |
| 5 | 第三方平台集成 | 至少集成 2 个主流 BI 工具（如 Tableau、PowerBI） |
| 6 | 合规审计增强 | 满足 GDPR 数据导出/删除要求 |
| 7 | 产品化输出 | 形成标准化部署方案与产品文档 |

### 2.2 运营指标

| 指标 | 目标值 |
|------|--------|
| 模板市场模板数 | > 50 个 |
| 插件生态插件数 | > 20 个 |
| 平台月活跃用户 | > 500 人 |
| 平台月任务执行数 | > 10,000 次 |
| 资源利用率 | > 70% |

### 2.3 生态指标

| 指标 | 目标值 |
|------|--------|
| 第三方集成数 | > 5 个 |
| 外部开发者数 | > 10 人 |
| 合规审计通过率 | 100% |

---

## 3. User Stories

### 3.1 业务人员 - 使用模板市场

**作为** 业务人员  
**我希望** 从模板市场选择行业模板快速搭建业务场景  
**以便** 无需从零开始配置本体、指标、任务

**验收条件：**
- [ ] 支持浏览、搜索、筛选模板
- [ ] 支持预览模板内容（Entity、Metric、Task 配置）
- [ ] 支持一键安装模板到当前业务域
- [ ] 安装后可自定义调整

### 3.2 第三方开发者 - 开发插件

**作为** 第三方开发者  
**我希望** 通过插件框架扩展平台能力  
**以便** 自定义连接器、质量规则、Agent 技能可集成到平台

**验收条件：**
- [ ] 提供插件 SDK 和开发文档
- [ ] 支持注册自定义连接器
- [ ] 支持注册自定义质量规则
- [ ] 支持注册自定义 Agent 技能
- [ ] 插件经过审核后可上架

### 3.3 平台管理员 - 查看运营数据

**作为** 平台管理员  
**我希望** 查看平台运营分析数据  
**以便** 了解平台使用情况并优化产品体验

**验收条件：**
- [ ] 展示活跃用户数、任务执行数、指标数
- [ ] 展示各业务域使用情况
- [ ] 展示任务成功率、质量告警趋势
- [ ] 支持导出运营报告

### 3.4 资源管理员 - 配置资源配额

**作为** 资源管理员  
**我希望** 按业务域配置资源配额  
**以便** 避免资源争抢，实现成本分摊

**验收条件：**
- [ ] 支持配置 CPU、内存、存储配额
- [ ] 支持配置任务并发数限制
- [ ] 资源超限时告警
- [ ] 展示资源使用报表

### 3.5 BI 分析师 - 集成第三方工具

**作为** BI 分析师  
**我希望** 将平台指标数据对接到 BI 工具  
**以便** 利用 BI 工具的可视化能力制作报表

**验收条件：**
- [ ] 支持与 Tableau 集成
- [ ] 支持与 PowerBI 集成
- [ ] 平台指标可在 BI 工具中直接使用
- [ ] 数据同步实时更新

### 3.6 合规人员 - 数据合规管理

**作为** 合规人员  
**我希望** 管理平台数据合规策略  
**以便** 满足 GDPR 等数据法规要求

**验收条件：**
- [ ] 支持数据导出请求处理
- [ ] 支持数据删除请求处理
- [ ] 支持数据访问审计
- [ ] 生成合规报告

---

## 4. Acceptance Criteria

### 4.1 模板市场

| 条件 | 描述 |
|------|------|
| 模板分类 | 按行业、业务域、任务类型分类 |
| 模板内容 | 包含 Entity、Metric、Task、Quality Rule 配置 |
| 安装机制 | 一键安装到当前业务域，支持冲突检测 |
| 审核机制 | 模板上架前需平台审核 |

### 4.2 插件框架

| 条件 | 描述 |
|------|------|
| SDK 支持 | 提供 Python/Java SDK |
| 插件类型 | 连接器、质量规则、Agent 技能、报表模板 |
| 沙箱运行 | 插件在沙箱环境运行，不影响核心服务 |
| 审核上架 | 插件需通过安全审核后上架 |

### 4.3 运营分析

| 条件 | 描述 |
|------|------|
| 用户分析 | 活跃用户、任务完成率、功能使用率 |
| 任务分析 | 任务执行数、成功率、耗时分布 |
| 资产分析 | 指标数、实体数、字典项数 |
| 趋势分析 | 按周/月展示趋势变化 |

### 4.4 资源治理

| 条件 | 描述 |
|------|------|
| 配额管理 | CPU、内存、存储、任务并发数 |
| 成本分摊 | 按业务域展示资源消耗与成本 |
| 告警机制 | 资源超限告警 |
| 弹性扩缩 | 支持自动弹性扩缩（Kubernetes） |

### 4.5 开放集成

| 条件 | 描述 |
|------|------|
| BI 集成 | Tableau、PowerBI、FineBI |
| 数据平台 | Hadoop、Snowflake、Databricks |
| AI 平台 | 支持接入第三方 LLM（OpenAI、Claude） |
| 消息通知 | 钉钉、飞书、企业微信、Slack |

### 4.6 合规增强

| 条件 | 描述 |
|------|------|
| GDPR | 数据导出、数据删除、数据访问同意管理 |
| 等保三级 | 安全审计、漏洞扫描、渗透测试 |
| 审计增强 | 全量操作日志、数据变更审计 |
| 合规报告 | 定期生成合规报告 |

### 4.7 产品化输出

| 条件 | 描述 |
|------|------|
| 部署方案 | 标准化部署文档与脚本 |
| 产品文档 | 用户手册、API 文档、运维手册 |
| 培训材料 | 视频教程、最佳实践指南 |
| 商业支持 | 技术支持服务 SLA |

---

## 5. Non-Goals

以下内容明确 **不在 Phase 4 范围内**：

| 排除项 | 原因 |
|--------|------|
| 自研 BI 工具 | 优先集成第三方 BI，不自研 |
| 自研 LLM | 继续集成第三方 LLM，不自研大模型 |
| 硬件优化 | 不涉及底层硬件优化 |
| 跨平台数据同步 | 不属于平台核心能力 |

---

## 6. Constraints

### 6.1 技术约束

| 约束 | 描述 |
|------|------|
| 插件沙箱 | 插件运行在隔离沙箱，禁止访问核心数据库 |
| 模板安全 | 模板安装前进行 Schema 校验和权限检查 |
| 资源配额 | 基于 Kubernetes ResourceQuota 实现 |
| 合规数据 | 合规相关数据加密存储 |

### 6.2 安全约束

| 约束 | 描述 |
|------|------|
| 插件审核 | 所有插件需通过安全审核 |
| 模板审核 | 所有模板需通过内容审核 |
| API 鉴权 | 所有开放集成 API 严格鉴权 |

### 6.3 开发规范

| 约束 | 描述 |
|------|------|
| 插件 SDK | 提供完整的 SDK、文档、示例 |
| 模板规范 | 模板遵循统一的 Schema 规范 |
| 审计 | 所有审核、安装操作记录审计日志 |

---

## 7. Architecture Decision

### 7.1 Phase 4 架构扩展

```
┌──────────────────────────────────────────────────────────────┐
│ 生态层                                                       │
│ 模板市场 / 插件中心 / 开放集成 / 运营分析 / 合规管理           │
└────────────────────────▲─────────────────────────────────────┘
                         │
┌────────────────────────┴─────────────────────────────────────┐
│ 平台核心层（Phase 1-3）                                       │
│ 本体 / 字典 / 任务 / Agent / 调度 / 质量 / 审批 / 周报        │
└────────────────────────▲─────────────────────────────────────┘
                         │
┌────────────────────────┴─────────────────────────────────────┐
│ 基础设施层                                                   │
│ Kubernetes / PostgreSQL / SeaTunnel / OpenMetadata / Dagster  │
└──────────────────────────────────────────────────────────────┘
```

### 7.2 服务边界扩展

| 服务 | Phase 4 新增职责 |
|------|-----------------|
| **Backend** | 模板市场、插件框架、运营分析、资源管理、合规管理 |
| **Agent** | 插件技能执行、模板智能推荐 |
| **Frontend** | 模板市场、插件中心、运营看板、资源管理页面 |
| **Marketplace** | 模板存储、审核、分发 |
| **Plugin Registry** | 插件注册、沙箱运行、版本管理 |

---

## 8. Module Breakdown

### 8.1 新增模块清单

| 模块 | 目录 | 职责 |
|------|------|------|
| `backend/harness-app` | Java 后端主服务 | 模板市场、插件框架、运营分析、资源管理、合规 |
| `backend/harness-plugin-sdk` | 插件 SDK | Python/Java 插件开发 SDK |
| `frontend/apps/web` | React Web 应用 | 模板市场、插件中心、运营看板、资源管理页面 |
| `marketplace` | 模板市场服务 | 模板存储、审核、分发、安装 |
| `plugin-registry` | 插件注册服务 | 插件注册、沙箱运行、版本管理 |

---

## 9. Interface Contracts

### 9.1 模板市场 API

#### 9.1.1 浏览模板

```http
GET /api/v1/marketplace/templates?category=retail&page=1&page_size=20
Response 200:
{
  "total": 15,
  "items": [
    {
      "id": "tmpl_001",
      "name": "零售行业指标体系",
      "category": "retail",
      "description": "包含订单、商品、客户实体及收入、订单数等指标",
      "entities_count": 3,
      "metrics_count": 10,
      "downloads": 1234,
      "rating": 4.8
    }
  ]
}
```

#### 9.1.2 安装模板

```http
POST /api/v1/marketplace/templates/{templateId}/install
{
  "domain_code": "sales",
  "conflict_resolution": "skip"
}

Response 200:
{
  "installation_id": "inst_001",
  "status": "completed",
  "installed_entities": 3,
  "installed_metrics": 10,
  "skipped_items": []
}
```

### 9.2 插件框架 API

#### 9.2.1 注册插件

```http
POST /api/v1/plugins/register
{
  "name": "custom-kafka-connector",
  "type": "connector",
  "version": "1.0.0",
  "author": "third_party_dev",
  "manifest": { ... },
  "security_scan_result": "passed"
}

Response 201:
{
  "id": "plugin_001",
  "status": "pending_review"
}
```

#### 9.2.2 执行插件技能

```http
POST /api/v1/plugins/{pluginId}/execute
{
  "action": "custom_quality_check",
  "input": { ... }
}

Response 200:
{
  "result": "pass",
  "details": { ... }
}
```

### 9.3 运营分析 API

#### 9.3.1 查询运营数据

```http
GET /api/v1/analytics/overview?period=last_30_days
Response 200:
{
  "active_users": 523,
  "tasks_executed": 12345,
  "metrics_published": 234,
  "entities_created": 45,
  "success_rate": 98.5,
  "trends": { ... }
}
```

### 9.4 资源管理 API

#### 9.4.1 配置资源配额

```http
PUT /api/v1/resource/quotas/{domainCode}
{
  "cpu_limit": "16",
  "memory_limit": "32Gi",
  "storage_limit": "500Gi",
  "max_concurrent_tasks": 50
}

Response 200:
{
  "domain_code": "sales",
  "quota_updated": true
}
```

### 9.5 合规管理 API

#### 9.5.1 数据导出请求

```http
POST /api/v1/compliance/data-export
{
  "user_id": "user_001",
  "reason": "GDPR data subject access request"
}

Response 200:
{
  "request_id": "req_001",
  "status": "processing",
  "estimated_completion": "2026-05-12T10:00:00Z"
}
```

---

## 10. Database Schema

### 10.1 Phase 4 新增表

| 表名 | 用途 | 迁移脚本 |
|------|------|---------|
| `marketplace_template` | 模板市场模板 | 新增 |
| `template_installation` | 模板安装记录 | 新增 |
| `plugin_registry` | 插件注册信息 | 新增 |
| `plugin_execution_log` | 插件执行日志 | 新增 |
| `analytics_snapshot` | 运营分析快照 | 新增 |
| `resource_quota` | 资源配额配置 | 新增 |
| `resource_usage` | 资源使用记录 | 新增 |
| `compliance_request` | 合规请求记录 | 新增 |
| `compliance_audit_log` | 合规审计日志 | 新增 |

### 10.2 marketplace_template 关键字段

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PK | UUID |
| name | VARCHAR(200) | NOT NULL | 模板名称 |
| category | VARCHAR(50) | NOT NULL | 行业分类 |
| description_zh | TEXT | NOT NULL | 中文描述 |
| description_en | TEXT | NOT NULL | 英文描述 |
| content | JSONB | NOT NULL | 模板内容（Entity/Metric/Task 配置） |
| author | VARCHAR(100) | NOT NULL | 作者 |
| version | VARCHAR(20) | NOT NULL | 版本号 |
| downloads | INT | DEFAULT 0 | 下载数 |
| rating | DECIMAL(3,2) | DEFAULT 0 | 评分 |
| status | VARCHAR(20) | DEFAULT 'pending' | pending/approved/rejected |
| created_at | TIMESTAMP | DEFAULT NOW() | 创建时间 |

### 10.3 plugin_registry 关键字段

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PK | UUID |
| name | VARCHAR(200) | NOT NULL | 插件名称 |
| type | VARCHAR(50) | NOT NULL | connector/quality_rule/agent_skill/report_template |
| version | VARCHAR(20) | NOT NULL | 版本号 |
| author | VARCHAR(100) | NOT NULL | 作者 |
| manifest | JSONB | NOT NULL | 插件配置 |
| sandbox_enabled | BOOLEAN | DEFAULT true | 是否启用沙箱 |
| security_scan_status | VARCHAR(20) | DEFAULT 'pending' | pending/passed/failed |
| status | VARCHAR(20) | DEFAULT 'pending_review' | pending_review/active/suspended |
| created_at | TIMESTAMP | DEFAULT NOW() | 创建时间 |

---

## 11. Risk Assessment

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 插件安全漏洞 | 可能影响核心服务 | 沙箱隔离、安全审核、权限最小化 |
| 模板质量参差不齐 | 降低用户体验 | 审核机制、用户评价、下架机制 |
| 资源配额配置不当 | 影响任务执行 | 默认配额、告警提示、自动扩缩 |
| 合规要求变更 | 可能不满足新法规 | 持续跟踪法规变化，预留合规接口 |
| 第三方集成稳定性 | 依赖外部服务 | 熔断机制、降级策略、监控告警 |

---

## 12. Tasks

### Task 1: 模板市场服务

- 实现模板上传、审核、浏览、搜索 API
- 实现模板安装与冲突检测机制
- 实现模板评价与下载统计
- **验证方式**：用户可浏览、安装模板

### Task 2: 插件框架

- 实现插件 SDK（Python/Java）
- 实现插件注册、审核、上架流程
- 实现插件沙箱运行环境
- 实现插件执行 API
- **验证方式**：第三方开发插件并上架

### Task 3: 运营分析中心

- 实现运营数据采集与聚合
- 实现运营看板前端页面
- 实现运营报告导出
- **验证方式**：管理员可查看完整运营数据

### Task 4: 资源治理服务

- 实现资源配额配置 API
- 实现资源使用统计与告警
- 实现弹性扩缩（Kubernetes 集成）
- 实现资源使用报表
- **验证方式**：按业务域配置配额，超限告警

### Task 5: 开放集成中心

- 实现 BI 工具集成（Tableau、PowerBI）
- 实现数据平台集成
- 实现第三方 LLM 接入
- 实现消息通知集成
- **验证方式**：BI 工具可查询平台指标

### Task 6: 合规增强服务

- 实现 GDPR 数据导出/删除
- 实现等保三级合规检查
- 实现合规审计日志增强
- 实现合规报告生成
- **验证方式**：通过合规审计

### Task 7: 产品化输出

- 编写标准化部署文档
- 编写用户手册、API 文档、运维手册
- 制作培训视频教程
- 制定技术支持 SLA
- **验证方式**：可独立完成平台部署与使用

### Task 8: 前端 Phase 4 页面

- 实现模板市场页面
- 实现插件中心页面
- 实现运营看板页面
- 实现资源管理页面
- 实现合规管理页面
- **验证方式**：用户可完成所有 Phase 4 操作

### Task 9: 端到端联调

- 验证模板市场全流程
- 验证插件开发上架流程
- 验证运营数据准确性
- 验证资源配额生效
- 验证 BI 集成
- 验证合规流程
- **验证方式**：所有 Success Metrics 验收通过

---

## 13. Dependencies

### 13.1 外部依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| Kubernetes | 1.25+ | 资源配额与弹性扩缩 |
| Tableau | 最新 | BI 集成 |
| PowerBI | 最新 | BI 集成 |
| GDPR 合规框架 | - | 数据合规 |

### 13.2 内部依赖

| 依赖方 | 被依赖方 | 依赖内容 |
|--------|---------|---------|
| Phase 4 | Phase 1-3 | 全部核心能力 |
| 模板市场 | Backend | 本体、指标、任务服务 |
| 插件框架 | Agent | Agent 技能执行 |
| 运营分析 | Backend | 所有操作日志 |
| 开放集成 | 语义 API | 指标数据查询 |

---

## 14. Appendix

### A. 模板 Schema 示例

```json
{
  "template_id": "tmpl_retail_001",
  "name": "零售行业指标体系",
  "version": "1.0.0",
  "entities": [
    { "code": "Order", "name_zh": "订单", "fields": [...] },
    { "code": "Product", "name_zh": "商品", "fields": [...] },
    { "code": "Customer", "name_zh": "客户", "fields": [...] }
  ],
  "metrics": [
    { "code": "revenue", "name_zh": "收入", "formula": "SUM(order_amount)" },
    { "code": "order_count", "name_zh": "订单数", "formula": "COUNT(order_id)" }
  ],
  "tasks": [
    { "type": "data_ingestion", "entity": "Order", "source_type": "mysql" },
    { "type": "build_metric", "metrics": ["revenue", "order_count"] }
  ],
  "quality_rules": [
    { "metric": "revenue", "type": "completeness", "field": "order_amount" }
  ]
}
```

### B. 插件 Manifest 示例

```json
{
  "plugin_id": "plugin_custom_kafka_001",
  "name": "Custom Kafka Connector",
  "type": "connector",
  "version": "1.0.0",
  "runtime": {
    "language": "python",
    "entry_point": "main.py",
    "dependencies": ["kafka-python>=2.0.0"]
  },
  "capabilities": {
    "source": true,
    "sink": true,
    "incremental": true
  },
  "permissions": [
    "network:outbound:kafka"
  ]
}
```

### C. 运营分析指标清单

| 指标类别 | 指标名称 | 计算方式 |
|---------|---------|---------|
| 用户分析 | 月活跃用户 | 当月登录用户数 |
| 用户分析 | 任务完成率 | 成功任务数 / 总任务数 |
| 任务分析 | 月执行数 | 当月执行任务数 |
| 任务分析 | 平均耗时 | 总耗时 / 任务数 |
| 资产分析 | 指标总数 | 已发布指标数 |
| 资产分析 | 实体总数 | 已发布实体数 |
| 质量分析 | 告警趋势 | 近 30 天质量告警数 |
