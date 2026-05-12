# Spec: HarnessDG Phase 1 - 最小业务闭环实现

## 0. 文档信息

| 字段 | 内容 |
|------|------|
| **Feature** | HarnessDG Phase 1 最小业务闭环 |
| **版本** | v1.0.0 |
| **状态** | 待实现 |
| **作者** | AxeXie |
| **创建时间** | 2026-05-11 |
| **关联 PRD** | docs/HarnessDG-v2.md |
| **目标 Phase** | Phase 1: 最小业务闭环（2个月） |

---

## 1. Problem Statement

### 1.1 问题背景

当前数据平台开发存在以下核心问题：

1. **业务语言与数据语言断层**：业务人员定义指标需要依赖数据团队翻译需求，需求传递链路长、易失真。
2. **指标口径不一致**：同一业务指标在不同报表中定义不同，重复建模严重。
3. **治理滞后**：血缘、权限、质量规则往往在任务上线后补做，导致治理缺失。
4. **AI 辅助零散**：缺少统一的 AI 输入输出规范和执行闭环，AI 生成结果不稳定、不可追溯。

### 1.2 目标

构建 HarnessDG Phase 1 最小业务闭环，跑通以下端到端链路：

```
业务对象定义 -> 指标创建 -> AI 问数 -> 治理记录
```

通过 PostgreSQL + AGE + pgvector 统一底座，结合 QwenPaw Agent 和 React 前端工作台，实现：

- 业务人员可自助创建指标
- AI 可理解业务问数意图并返回结果
- 全流程产生可审计的治理记录

---

## 2. Success Metrics

### 2.1 功能验收标准

| 序号 | 验收项 | 验证方式 |
|------|--------|---------|
| 1 | 完成 3 个核心业务对象建模：Order、Revenue、Channel | 数据库 ontology 表可查询到定义 |
| 2 | 支持至少 3 个原子指标的创建与发布 | 前端指标列表页可展示已发布指标 |
| 3 | AI 问数支持自然语言查询已发布指标 | 输入"最近30天各渠道收入"可返回正确结果 |
| 4 | 指标血缘链路可追溯 | 查询指标可查看其依赖的 Entity 和 DDL |
| 5 | 全链路审计日志可查询 | 操作日志表可追溯指标创建人、审批人、时间 |
| 6 | 前端支持中英文切换 | 切换语言后所有页面文案无硬编码中文/英文 |
| 7 | 至少 10 个核心字典分组驱动前端选项 | 前端下拉/筛选组件从字典 API 加载数据 |
| 8 | 统一日志覆盖 trace_id、task_id、operator | 日志查询可通过 trace_id 串联全流程 |

### 2.2 性能指标

| 指标 | 目标值 |
|------|--------|
| 前端首屏 FCP | < 1.5s |
| 前端首屏 LCP | < 2.5s |
| 语义查询 API P95 响应时间 | < 2s |
| 字典 API P95 响应时间 | < 200ms |
| AI 问数意图识别响应时间 | < 5s |

### 2.3 体验指标

| 指标 | 目标值 |
|------|--------|
| 指标创建任务完成率（无外部帮助） | > 85% |
| 前端 i18n 覆盖率 | 100%（零硬编码文案） |
| 字典驱动率 | > 95% |

---

## 3. User Stories

### 3.1 业务分析师 - 创建指标

**作为** 业务分析师  
**我希望** 通过向导式表单创建并发布标准化指标  
**以便** 指标定义统一、可复用、可审计

**验收条件：**
- [ ] 支持选择 Entity、定义指标公式、配置维度、设置调度策略
- [ ] 指标类型、聚合方式、刷新策略等选项从数据字典加载
- [ ] 提交后进入审批流（Phase 1 可简化为直接发布）
- [ ] 发布后指标可在指标列表页查看和消费

### 3.2 业务人员 - AI 问数

**作为** 业务人员  
**我希望** 用自然语言查询已发布的指标数据  
**以便** 无需编写 SQL 即可获取数据结果

**验收条件：**
- [ ] 支持输入自然语言问句
- [ ] AI 识别指标、时间范围、维度
- [ ] 查询结果包含数据表格和口径解释
- [ ] 若指标不存在，提示需先创建指标（禁止直查底表）

### 3.3 平台管理员 - 维护数据字典

**作为** 平台管理员  
**我希望** 在后台维护数据字典项  
**以便** 前端分类选项、状态枚举等实时生效且无需重新发版

**验收条件：**
- [ ] 支持创建/编辑/停用字典分组和字典项
- [ ] 支持维护多语言文案（zh_CN / en_US）
- [ ] 字典变更后前端刷新即可生效

### 3.4 数据治理人员 - 查看审计日志

**作为** 数据治理人员  
**我希望** 查看所有操作的审计日志  
**以便** 追溯谁定义、谁审批、谁执行

**验收条件：**
- [ ] 支持按任务类型、操作人、时间范围筛选日志
- [ ] 日志包含 trace_id 可关联全链路
- [ ] 日志包含 AI 决策记录（模型版本、输入输出）

---

## 4. Acceptance Criteria

### 4.1 本体建模

| 条件 | 描述 |
|------|------|
| Entity 定义 | 支持 Entity 名称、编码、描述、数据域、负责人字段 |
| Metric 定义 | 支持 Metric 名称、编码、公式、聚合方式、所属 Entity、维度列表 |
| Dimension 定义 | 支持 Dimension 名称、编码、类型、取值范围 |
| Relation 定义 | 支持两个 Entity 之间的关系类型和方向 |
| 版本管理 | 本体对象支持草稿态、发布态、废弃态 |

### 4.2 数据字典

| 条件 | 描述 |
|------|------|
| 字典分组 | 支持 code、名称（i18n）、树形/非树形、启用/禁用 |
| 字典项 | 支持 code、label（i18n）、value、颜色、图标、排序 |
| API 支持 | 按分组获取、批量获取、搜索、树形加载 |
| 缓存策略 | 前端缓存 5 分钟标记过期、30 分钟清除 |

### 4.3 AI 问数

| 条件 | 描述 |
|------|------|
| 意图识别 | 识别 ask_data、build_metric、diagnose 等任务类型 |
| 语义补全 | 自动补全指标名称、时间范围、维度粒度 |
| 结果解释 | 返回数据同时解释口径、来源、时间范围 |
| 安全边界 | 指标不存在时禁止直查底层，需引导创建 |

### 4.4 国际化

| 条件 | 描述 |
|------|------|
| 语种支持 | zh_CN、en_US 首期，ja_JP、ko_KR 预留 |
| 静态文案 | 所有组件通过 `t('key')` 获取文案，禁止硬编码 |
| 动态内容 | 后端返回 I18nText 结构，前端按 locale 取值 |
| 格式规范 | 日期、数字、货币按 locale 格式化 |

### 4.5 审计日志

| 条件 | 描述 |
|------|------|
| 操作日志 | 记录操作人、操作类型、操作对象、操作时间 |
| 运行日志 | 记录任务执行状态、耗时、错误信息 |
| AI 决策日志 | 记录模型版本、输入 prompt、输出结果 |
| Trace 关联 | 全链路通过 trace_id 关联检索 |

---

## 5. Non-Goals

以下内容明确 **不在 Phase 1 范围内**：

| 排除项 | 原因 |
|--------|------|
| SeaTunnel 数据接入流程 | Phase 2 数据接入能力 |
| OpenMetadata 元数据打通 | Phase 2 治理增强 |
| 自动质量规则生成 | Phase 2 质量治理 |
| 审批流引擎完整实现 | Phase 1 简化为直接发布，Phase 2 完善 |
| 周报自动生成 | Phase 2 智能化升级 |
| 异常诊断与自愈 | Phase 2/3 能力 |
| OpenClaw 接入 | Phase 3 外部 Agent 扩展 |
| 日语、韩语支持 | Phase 3 多语种扩展 |
| 多业务域/多租户 | Phase 3 规模化推广 |

---

## 6. Constraints

### 6.1 技术约束

| 约束 | 描述 |
|------|------|
| 数据库 | 必须使用 PostgreSQL + AGE + pgvector，不引入新存储组件 |
| 后端框架 | Java + Spring Boot 3.x + MyBatis-Plus |
| Agent 服务 | Python + FastAPI，首选 QwenPaw 运行时 |
| 前端框架 | React 18 + TypeScript + Vite + Ant Design 5.x |
| 国际化 | react-i18next，禁止组件内硬编码文案 |
| 数据字典 | 前端所有分类选项从字典 API 加载，禁止硬编码枚举 |
| API 规范 | RESTful 风格，版本化路径 `/api/v1/...` |
| 日志格式 | 统一结构化 JSON 日志 |

### 6.2 安全约束

| 约束 | 描述 |
|------|------|
| 认证鉴权 | 所有 API 需要 JWT Token 认证 |
| 敏感数据 | 密码、Token 禁止出现在日志中 |
| 输入校验 | 所有用户输入必须经过校验 |
| SQL 安全 | 必须使用参数化查询，禁止字符串拼接 |

### 6.3 开发规范

| 约束 | 描述 |
|------|------|
| 代码质量 | 单元测试覆盖率 >= 80% |
| 文档 | 所有公共 API 必须有 OpenAPI 3.0 文档 |
| 优雅关闭 | 所有服务必须支持 Graceful Shutdown |
| 配置管理 | 配置项通过环境变量注入，禁止硬编码 |
| 依赖管理 | npm、pip 依赖使用国内镜像源，HTTPS 模式 |

---

## 7. Architecture Decision

### 7.1 分层架构

```
┌──────────────────────────────────────────────────────────────┐
│ 应用体验层（React Web）                                        │
│ 任务中心 / 本体建模 / AI 问数 / 字典管理 / 审计日志             │
└────────────────────────▲─────────────────────────────────────┘
                         │ REST API + i18n
┌────────────────────────┴─────────────────────────────────────┐
│ 业务服务层（Java Spring Boot）                                  │
│ 本体服务 / 字典服务 / 权限服务 / 配置中心 / 日志服务             │
└────────────────────────▲─────────────────────────────────────┘
                         │ HTTP
┌────────────────────────┴─────────────────────────────────────┐
│ Agent 服务层（Python FastAPI）                                  │
│ QwenPaw 集成 / 意图识别 / 任务计划 / 结果解释                   │
└────────────────────────▲─────────────────────────────────────┘
                         │
┌────────────────────────┴─────────────────────────────────────┐
│ 数据存储层（PostgreSQL + AGE + pgvector）                       │
│ 本体表 / 字典表 / 任务表 / 日志表 / 配置表                      │
└──────────────────────────────────────────────────────────────┘
```

### 7.2 服务边界

| 服务 | 职责 | 技术栈 |
|------|------|--------|
| **Backend** | 核心业务逻辑、权限、字典、配置、审计 | Java + Spring Boot |
| **Agent** | AI 意图识别、任务计划、结果解释 | Python + FastAPI + QwenPaw |
| **Frontend** | 业务任务型工作台、i18n、字典驱动 UI | React + TypeScript |
| **Database** | 结构化数据、图关系、向量检索 | PostgreSQL + AGE + pgvector |

---

## 8. Module Breakdown

### 8.1 模块清单

| 模块 | 目录 | 职责 |
|------|------|------|
| `backend/harness-app` | Java 后端主服务 | 本体、字典、权限、配置、日志 API |
| `backend/harness-agent-gateway` | Agent 网关 | 转发 Backend <-> Agent 请求 |
| `agent` | Python Agent 服务 | QwenPaw 集成、意图识别、任务计划 |
| `frontend/apps/web` | React Web 应用 | 任务中心、本体建模、AI 问数等页面 |
| `frontend/packages/i18n` | i18n 共享包 | 翻译资源配置、Hook、Provider |
| `frontend/packages/dict-components` | 字典组件包 | DictSelect、DictTag、DictCascader 等 |
| `frontend/packages/design-tokens` | 设计 Token 包 | 色彩、排版、间距、圆角等 CSS 变量 |

---

## 9. Interface Contracts

### 9.1 本体管理 API

#### 9.1.1 创建 Entity

```http
POST /api/v1/ontology/entities
Content-Type: application/json
Authorization: Bearer <token>

{
  "code": "Order",
  "name": { "zh_CN": "订单", "en_US": "Order" },
  "description": { "zh_CN": "业务订单事实表", "en_US": "Order fact table" },
  "domain_code": "sales",
  "owner_id": "user_001"
}

Response 201:
{
  "id": "ent_001",
  "code": "Order",
  "status": "draft"
}
```

#### 9.1.2 创建 Metric

```http
POST /api/v1/ontology/metrics
Content-Type: application/json

{
  "code": "revenue",
  "name": { "zh_CN": "收入", "en_US": "Revenue" },
  "entity_code": "Order",
  "type": "atomic",
  "formula": "SUM(order_amount)",
  "agg_method": "sum",
  "dimensions": ["date", "channel"],
  "refresh_strategy": "daily"
}

Response 201:
{
  "id": "met_001",
  "code": "revenue",
  "status": "draft"
}
```

#### 9.1.3 查询已发布指标列表

```http
GET /api/v1/ontology/metrics?status=active&page=1&page_size=20
Response 200:
{
  "total": 3,
  "items": [
    {
      "id": "met_001",
      "code": "revenue",
      "name": { "zh_CN": "收入", "en_US": "Revenue" },
      "entity_code": "Order",
      "status": "active"
    }
  ]
}
```

### 9.2 数据字典 API

#### 9.2.1 获取字典项列表

```http
GET /api/v1/dictionary/items/{groupCode}?locale=zh_CN&status=active
Response 200:
[
  {
    "id": "di_001",
    "code": "atomic",
    "label": "原子指标",
    "value": "atomic",
    "color": "#52C41A",
    "sort_order": 1
  }
]
```

#### 9.2.2 批量获取字典项

```http
POST /api/v1/dictionary/items/batch
{
  "group_codes": ["metric_type", "time_granularity", "task_status"]
}

Response 200:
{
  "metric_type": [...],
  "time_granularity": [...],
  "task_status": [...]
}
```

### 9.3 AI 问数 API

#### 9.3.1 意图识别

```http
POST /api/v1/agent/intent
{
  "query": "最近30天各渠道收入如何",
  "locale": "zh_CN"
}

Response 200:
{
  "intent": "ask_data",
  "bindings": {
    "metric": "revenue",
    "time_range": "last_30_days",
    "dimension": "channel",
    "agg": "group_by"
  },
  "confidence": 0.92
}
```

#### 9.3.2 执行查询

```http
POST /api/v1/agent/query
{
  "intent": "ask_data",
  "bindings": {
    "metric": "revenue",
    "time_range": "last_30_days",
    "dimension": "channel"
  }
}

Response 200:
{
  "data": [
    { "channel": "APP", "revenue": 1234567.89 },
    { "channel": "Web", "revenue": 987654.32 }
  ],
  "explanation": {
    "zh_CN": "最近30天各渠道收入统计，数据来源：Order 事实表",
    "en_US": "Revenue by channel for last 30 days, source: Order fact table"
  },
  "trace_id": "trace_abc123"
}
```

### 9.4 审计日志 API

#### 9.4.1 查询操作日志

```http
GET /api/v1/audit/logs?trace_id=trace_abc123
Response 200:
[
  {
    "id": "log_001",
    "trace_id": "trace_abc123",
    "operator": "user_001",
    "action": "create_metric",
    "target": "revenue",
    "result": "success",
    "created_at": "2026-05-11T10:00:00Z"
  }
]
```

---

## 10. Database Schema

### 10.1 核心表清单

| 表名 | 用途 | 迁移脚本 |
|------|------|---------|
| `sys_dict_group` | 字典分组 | V001 |
| `sys_dict_item` | 字典项 | V001 |
| `ontology_entity` | 业务实体 | V002 |
| `ontology_metric` | 指标定义 | V002 |
| `ontology_dimension` | 维度定义 | V002 |
| `ontology_relation` | 实体关系 | V002 |
| `sys_user` | 系统用户 | V003 |
| `sys_role` | 角色 | V003 |
| `sys_user_role` | 用户角色关联 | V003 |
| `task_instance` | 任务实例 | V004 |
| `sys_config` | 系统配置 | V005 |
| `audit_log` | 审计日志 | V005 |
| `approval_record` | 审批记录 | V012 |
| `quality_rule` | 质量规则 | V013 |
| `lineage_record` | 血缘记录 | V013 |
| `datasource_config` | 数据源配置 | V014 |
| `report_template` | 报表模板 | V015 |

### 10.2 关键字段说明

#### ontology_entity

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PK | UUID |
| code | VARCHAR(100) | UNIQUE, NOT NULL | 实体编码 |
| name_zh | VARCHAR(200) | NOT NULL | 中文名称 |
| name_en | VARCHAR(200) | NOT NULL | 英文名称 |
| description_zh | TEXT | | 中文描述 |
| description_en | TEXT | | 英文描述 |
| domain_code | VARCHAR(50) | | 所属数据域 |
| owner_id | VARCHAR(36) | | 负责人 ID |
| status | VARCHAR(20) | DEFAULT 'draft' | 状态：draft/active/deprecated |
| created_at | TIMESTAMP | DEFAULT NOW() | 创建时间 |
| updated_at | TIMESTAMP | DEFAULT NOW() | 更新时间 |

#### ontology_metric

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PK | UUID |
| code | VARCHAR(100) | UNIQUE, NOT NULL | 指标编码 |
| name_zh | VARCHAR(200) | NOT NULL | 中文名称 |
| name_en | VARCHAR(200) | NOT NULL | 英文名称 |
| entity_id | VARCHAR(36) | FK, NOT NULL | 所属实体 ID |
| type | VARCHAR(20) | NOT NULL | 类型：atomic/derived/composite |
| formula | TEXT | NOT NULL | 计算公式 |
| agg_method | VARCHAR(50) | NOT NULL | 聚合方式 |
| dimensions | JSONB | | 维度列表 |
| refresh_strategy | VARCHAR(20) | DEFAULT 'daily' | 刷新策略 |
| status | VARCHAR(20) | DEFAULT 'draft' | 状态 |
| created_at | TIMESTAMP | DEFAULT NOW() | 创建时间 |
| updated_at | TIMESTAMP | DEFAULT NOW() | 更新时间 |

---

## 11. Risk Assessment

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| AGE/pgvector 插件未正确安装 | 图关系和向量检索不可用 | Dockerfile 中明确安装并验证插件 |
| QwenPaw 服务不可用 | AI 问数功能降级 | 提供手动查询回退路径 |
| 字典数据初始化不完整 | 前端选项缺失 | 迁移脚本 V007/V016 预置数据 |
| i18n 翻译遗漏 | 页面显示空白文案 | CI 检查翻译覆盖率，缺失阻断发布 |
| 前端硬编码枚举 | 字典变更需重新发版 | Code Review 检查 DictSelect 使用 |

---

## 12. Success Criteria Validation Plan

### 12.1 自动化测试

| 测试类型 | 覆盖范围 | 目标 |
|---------|---------|------|
| 单元测试 | Service 层逻辑 | 覆盖率 >= 80% |
| 集成测试 | API 端点 | 所有 API 返回正确状态码和数据 |
| E2E 测试 | 指标创建流程、AI 问数流程 | 关键路径自动化 |
| i18n 检查 | 翻译 Key 完整性 | 零缺失 |

### 12.2 手动验证

| 验证项 | 方法 |
|--------|------|
| 前端 FCP/LCP | Chrome DevTools Performance 面板 |
| 字典驱动 | 检查前端组件是否从 API 加载选项 |
| 审计日志完整性 | 创建指标后查询日志表确认记录 |
| 中英文切换 | 切换语言后验证所有文案 |

---

## 13. Dependencies

### 13.1 外部依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| PostgreSQL | 15+ | 主数据库 |
| Apache AGE | 1.5+ | 图关系扩展 |
| pgvector | 0.5+ | 向量检索 |
| QwenPaw | 最新 | AI 运行时 |
| OpenJDK | 17+ | Java 运行时 |
| Node.js | 18+ | 前端构建 |

### 13.2 内部依赖

| 依赖方 | 被依赖方 | 依赖内容 |
|--------|---------|---------|
| Frontend | Backend | 所有 REST API |
| Frontend | Agent | AI 问数 API |
| Agent | Backend | 本体查询 API |
| Backend | Database | 所有数据表 |

---

## 14. Deployment Plan

### 14.1 环境

| 环境 | 用途 | 部署方式 |
|------|------|---------|
| Local | 开发调试 | docker-compose up |
| Dev | 集成测试 | Docker Compose |
| Prod | 生产环境 | Kubernetes（Phase 3） |

### 14.2 启动顺序

```
1. PostgreSQL (healthcheck: pg_isready)
2. Backend (depends_on: postgres healthy)
3. Agent (depends_on: backend started)
4. Frontend (depends_on: agent started)
```

### 14.3 迁移执行

```bash
# Backend 启动时自动执行 Flyway 迁移
# 迁移脚本顺序：
V001 -> V002 -> V003 -> V004 -> V005 -> V006 -> 
V007 -> V008 -> V009 -> V010 -> V011 -> V012 -> 
V013 -> V014 -> V015 -> V016
```

---

## 15. Tasks

### Task 1: 数据库迁移脚本验证

- 验证 V001-V016 迁移脚本在空库上成功执行
- 验证字典数据（V007、V016）正确灌入
- 验证演示本体数据（V008）正确创建
- **验证方式**：`docker-compose up postgres backend` 后查询表结构和数据

### Task 2: 本体管理 API 实现

- 实现 Entity CRUD API
- 实现 Metric CRUD API
- 实现 Dimension CRUD API
- 实现 Relation CRUD API
- 实现状态机：draft -> active -> deprecated
- **验证方式**：集成测试覆盖率 > 80%，API 文档完整

### Task 3: 数据字典 API 实现

- 实现分组管理 API
- 实现字典项 CRUD API
- 实现批量获取 API
- 实现树形加载 API
- **验证方式**：前端下拉组件可正确加载选项

### Task 4: AI Agent 服务实现

- 实现意图识别端点
- 实现任务计划端点
- 实现查询执行端点
- 集成 QwenPaw 客户端
- **验证方式**：输入自然语言问句，返回正确意图和查询结果

### Task 5: 前端任务工作台

- 实现任务中心首页（6 张任务卡片）
- 实现指标创建向导（5 步表单）
- 实现 AI 问数对话界面
- 实现本体建模页面
- 实现字典组件集成（DictSelect、DictTag 等）
- **验证方式**：用户可完成指标创建全流程

### Task 6: 国际化基础设施

- 配置 react-i18next
- 划分命名空间（common、navigation、task、ontology 等）
- 提供 zh_CN / en_US 翻译文件
- 实现语言切换组件
- 实现动态内容 I18nText 处理 Hook
- **验证方式**：切换语言后所有文案正确显示

### Task 7: 审计日志服务

- 实现操作日志记录
- 实现运行日志记录
- 实现 AI 决策日志记录
- 实现日志查询 API
- **验证方式**：trace_id 可串联全流程日志

### Task 8: 设计系统落地

- 引入 design-tokens 包
- 配置 CSS 变量（色彩、排版、间距、圆角）
- 实现暗色模式支持
- 实现响应式断点
- **验证方式**：页面视觉风格符合 PRD 设计规范

### Task 9: 端到端联调

- 验证完整指标创建流程
- 验证 AI 问数流程
- 验证字典驱动选项加载
- 验证中英文切换
- 验证审计日志记录
- **验证方式**：所有 Success Metrics 验收通过

---

## 16. Appendix

### A. 字典分组初始化清单（Phase 1 首批 10 个）

| 分组编码 | 分组名称（zh） | 项数 |
|---------|--------------|------|
| `task_type` | 任务类型 | 6 |
| `metric_type` | 指标类型 | 3 |
| `metric_agg_method` | 聚合方式 | 5 |
| `dimension_type` | 维度类型 | 3 |
| `time_granularity` | 时间粒度 | 6 |
| `data_domain` | 数据域 | 3 |
| `asset_status` | 资产状态 | 5 |
| `task_status` | 任务状态 | 5 |
| `priority_level` | 优先级 | 3 |
| `sensitivity_level` | 敏感等级 | 4 |

### B. 演示本体数据（V008 迁移）

| 对象 | 编码 | 名称（zh） |
|------|------|-----------|
| Entity | Order | 订单 |
| Entity | Customer | 客户 |
| Entity | Product | 商品 |
| Metric | revenue | 收入 |
| Metric | order_count | 订单数 |
| Metric | refund_amount | 退款金额 |
| Dimension | date | 日期 |
| Dimension | channel | 渠道 |
| Dimension | region | 地区 |
| Relation | customer_order | 客户下单 |
| Relation | order_product | 订单包含商品 |

### C. i18n 命名空间划分

| 命名空间 | 文件 | 内容 |
|---------|------|------|
| `common` | common.json | 通用按钮、状态、操作文案 |
| `navigation` | navigation.json | 侧边导航菜单 |
| `task` | task.json | 任务中心卡片、向导步骤 |
| `ontology` | ontology.json | 本体建模页面文案 |
| `validation` | validation.json | 表单校验消息 |
| `notification` | notification.json | Toast 通知消息 |
