# Spec: HarnessDG Phase 3 - 智能化升级

## 0. 文档信息

| 字段 | 内容 |
|------|------|
| **Feature** | HarnessDG Phase 3 智能化升级 |
| **版本** | v1.0.0 |
| **状态** | 待实现 |
| **作者** | AxeXie |
| **创建时间** | 2026-05-11 |
| **关联 PRD** | docs/HarnessDG-v2.md |
| **目标 Phase** | Phase 3: 智能化升级（6个月） |
| **前置依赖** | Phase 1 最小业务闭环、Phase 2 治理增强 |

---

## 1. Problem Statement

### 1.1 问题背景

Phase 1-2 完成了基础业务闭环和治理能力后，平台在智能化和规模化方面仍有提升空间：

1. **Pipeline 生成仍依赖模板**：复杂数据接入场景需要手动调整 Pipeline 配置，缺乏全自动生成能力。
2. **调度基于固定周期**：不支持事件驱动的实时调度，传感器能力缺失。
3. **异常需人工确认修复**：诊断后需人工执行修复操作，低风险场景未实现自愈。
4. **Agent 入口单一**：仅支持 Web 前端交互，未接入企业 IM、Bot 等外部渠道。
5. **单业务域限制**：平台仅支持单一业务域，无法推广到多业务域场景。
6. **缺少开放平台**：外部系统无法通过标准 API 消费平台语义和资产。
7. **语种有限**：仅支持中英文，无法满足国际化业务需求。

### 1.2 目标

形成端到端业务任务型数据平台，实现：

```
全自动 Pipeline -> 事件驱动调度 -> 异常自愈 -> 多渠道 Agent -> 多业务域 -> 开放平台 -> 多语种
```

通过引入全自动生成能力、事件驱动调度、异常自愈、OpenClaw 接入、多租户架构、统一语义 API，实现：

- 复杂数据接入场景全自动生成 Pipeline
- 基于事件触发的实时调度与传感器
- 低风险异常自动修复，无需人工介入
- 支持企业 IM、Bot 等多渠道 AI 交互
- 支持多业务域隔离与推广
- 提供统一语义 API 供外部系统消费
- 支持日语、韩语等多语种

---

## 2. Success Metrics

### 2.1 功能验收标准

| 序号 | 验收项 | 验证方式 |
|------|--------|---------|
| 1 | 全自动 Pipeline 生成（复杂场景） | 输入复杂业务需求，自动生成完整 Pipeline 配置 |
| 2 | 事件驱动调度可用 | 数据到达事件触发任务执行 |
| 3 | 传感器（Sensor）配置与执行 | 支持条件触发、时间触发、数据触发传感器 |
| 4 | 异常自愈（低风险场景） | 任务失败自动重试、自动修复，无需人工确认 |
| 5 | 策略推荐 | 根据历史数据推荐质量规则阈值、调度频率 |
| 6 | OpenClaw 接入（可选） | 通过企业 IM 发送指令可执行数据任务 |
| 7 | 多业务域隔离 | 不同业务域数据、权限、配置相互隔离 |
| 8 | 统一语义 API | 外部系统可通过 API 查询已发布指标 |
| 9 | 日语、韩语界面 | 切换语种后界面文案、字典标签正确显示 |

### 2.2 性能指标

| 指标 | 目标值 |
|------|--------|
| 全自动 Pipeline 生成耗时 | < 10s |
| 事件驱动调度延迟 | < 1s |
| 传感器触发延迟 | < 2s |
| 异常自愈决策耗时 | < 3s |
| 语义 API P95 响应时间 | < 500ms |
| 多语种切换响应时间 | < 1s |

### 2.3 智能化指标

| 指标 | 目标值 |
|------|--------|
| Pipeline 自动生成成功率 | > 80% |
| 异常自愈成功率（低风险） | > 90% |
| 策略推荐准确率 | > 85% |
| 传感器触发准确率 | > 99% |

---

## 3. User Stories

### 3.1 数据开发人员 - 全自动生成 Pipeline

**作为** 数据开发人员  
**我希望** 输入复杂业务需求后自动生成完整 Pipeline  
**以便** 无需手动调整模板配置

**验收条件：**
- [ ] 支持输入多实体关联、多表同步的复杂需求
- [ ] AI 自动生成完整 Pipeline 配置
- [ ] 支持预览和手动调整
- [ ] 生成结果通过校验后可直接执行

### 3.2 业务分析师 - 事件驱动调度

**作为** 业务分析师  
**我希望** 配置基于事件触发的数据任务  
**以便** 数据到达后立即执行计算，无需等待定时调度

**验收条件：**
- [ ] 支持配置数据到达事件触发
- [ ] 支持配置时间条件触发（如每月 1 号）
- [ ] 支持配置数据量条件触发（如数据量 > 1000）
- [ ] 传感器执行记录可查询

### 3.3 运维人员 - 异常自愈

**作为** 运维人员  
**我希望** 低风险异常自动修复，无需人工介入  
**以便** 减少人工操作成本，提高系统可用性

**验收条件：**
- [ ] 定义低风险异常类型（如临时网络抖动、数据延迟）
- [ ] 自动重试机制可配置
- [ ] 自动修复后通知责任人
- [ ] 修复记录可审计

### 3.4 平台管理员 - 多业务域管理

**作为** 平台管理员  
**我希望** 配置多个业务域并实现数据隔离  
**以便** 平台可推广到全公司使用

**验收条件：**
- [ ] 支持创建业务域并配置负责人
- [ ] 业务域间数据、权限、配置隔离
- [ ] 支持跨域数据共享（需审批）
- [ ] 业务域独立管理字典、模板

### 3.5 外部系统 - 消费语义 API

**作为** 外部系统开发者  
**我希望** 通过标准 API 查询已发布指标  
**以便** 第三方系统可直接消费平台数据

**验收条件：**
- [ ] 提供统一语义查询 API
- [ ] 支持 OAuth2.0 认证
- [ ] API 有完整文档和 SDK
- [ ] API 调用有审计日志

### 3.6 国际用户 - 多语种界面

**作为** 日语/韩语用户  
**我希望** 使用母语操作系统界面  
**以便** 无障碍使用平台功能

**验收条件：**
- [ ] 支持日语界面切换
- [ ] 支持韩语界面切换
- [ ] 字典标签、状态说明正确翻译
- [ ] 日期、数字格式按 locale 适配

---

## 4. Acceptance Criteria

### 4.1 全自动 Pipeline

| 条件 | 描述 |
|------|------|
| 复杂场景 | 支持多表关联、多数据源、增量+全量混合 |
| AI 生成 | AI 根据需求自动生成完整 .conf 文件 |
| 校验 | 生成结果通过 Schema 校验和语法检查 |
| 回退 | 生成失败时可回退至 Phase 2 模板模式 |

### 4.2 事件驱动调度

| 条件 | 描述 |
|------|------|
| 事件类型 | 支持数据到达、时间到达、条件满足等事件 |
| 传感器 | 支持 Dagster Sensor 配置与执行 |
| 调度记录 | 传感器触发记录可查询 |
| 容错 | 传感器失败不影响定时任务 |

### 4.3 异常自愈

| 条件 | 描述 |
|------|------|
| 风险分级 | 定义低、中、高风险异常分类 |
| 自动重试 | 低风险异常自动重试（可配置次数） |
| 自动修复 | 支持自动回滚、自动改配置等修复动作 |
| 通知 | 修复后通知责任人，记录审计日志 |

### 4.4 策略推荐

| 条件 | 描述 |
|------|------|
| 质量阈值 | 根据历史波动数据推荐阈值 |
| 调度频率 | 根据数据更新频率推荐调度策略 |
| 资源分配 | 根据任务复杂度推荐资源配置 |

### 4.5 OpenClaw 接入

| 条件 | 描述 |
|------|------|
| Agent Adapter | 通过适配器接入，不与 QwenPaw 耦合 |
| 渠道支持 | 支持企业 IM（如钉钉、飞书）消息接收 |
| 任务执行 | 通过消息指令可执行数据查询、任务创建 |
| 安全边界 | 敏感操作需 Web 端确认 |

### 4.6 多业务域

| 条件 | 描述 |
|------|------|
| 域隔离 | 数据、权限、配置按业务域隔离 |
| 跨域共享 | 支持跨域数据共享（需审批流） |
| 域管理 | 业务域负责人可独立管理域内资源 |

### 4.7 统一语义 API

| 条件 | 描述 |
|------|------|
| 标准接口 | RESTful API，版本化路径 `/api/v1/semantic/...` |
| 认证 | OAuth2.0 + API Key 双认证 |
| 文档 | OpenAPI 3.0 文档，SDK 支持 |
| 限流 | API 调用频率限制 |

### 4.8 多语种

| 条件 | 描述 |
|------|------|
| 语种 | ja_JP（日语）、ko_KR（韩语） |
| 静态文案 | 所有组件文案完整翻译 |
| 字典标签 | 字典项维护日语、韩语文案 |
| 格式 | 日期、数字按 locale 格式化 |

---

## 5. Non-Goals

以下内容明确 **不在 Phase 3 范围内**：

| 排除项 | 原因 |
|--------|------|
| 自研 Agent 框架 | 继续基于 QwenPaw/OpenClaw 适配器，不自研 |
| 底层计算引擎优化 | 不替代 Spark/Flink 计算能力 |
| 数据可视化大屏 | 不属于核心数据治理能力 |
| 移动端 APP | 优先完善 Web 端和 IM 集成 |

---

## 6. Constraints

### 6.1 技术约束

| 约束 | 描述 |
|------|------|
| Agent 解耦 | OpenClaw 通过 Agent Adapter 接入，不修改核心 Agent 逻辑 |
| 多租户 | 基于现有数据域扩展，不引入新租户框架 |
| 语义 API | 复用现有查询服务，增加 OAuth2.0 认证层 |
| 自愈策略 | 仅限低风险场景，中高风险需人工确认 |

### 6.2 安全约束

| 约束 | 描述 |
|------|------|
| 跨域共享 | 需经过审批流 |
| 语义 API | 严格限流和鉴权 |
| 自愈动作 | 记录完整审计日志，可回滚 |

### 6.3 开发规范

| 约束 | 描述 |
|------|------|
| 适配器模式 | OpenClaw、多语种接入均使用适配器封装 |
| 多语言 | 翻译资源与代码同步发布，支持热更新 |
| 审计 | 所有自愈动作记录审计日志 |

---

## 7. Architecture Decision

### 7.1 Phase 3 架构扩展

```
┌──────────────────────────────────────────────────────────────┐
│ 多渠道接入层                                                 │
│ Web 前端 / 企业 IM（OpenClaw） / Bot / API Gateway           │
└────────────────────────▲─────────────────────────────────────┘
                         │
┌────────────────────────┴─────────────────────────────────────┐
│ Agent 决策层（多 Agent 协作）                                  │
│ QwenPaw（主） / OpenClaw（扩展） / 自愈决策器 / 策略推荐引擎  │
└────────────────────────▲─────────────────────────────────────┘
                         │
┌────────────────────────┴─────────────────────────────────────┐
│ 执行与调度层（事件驱动）                                       │
│ Dagster Sensor / 事件总线 / 自愈执行器 / 策略执行器            │
└────────────────────────▲─────────────────────────────────────┘
                         │
┌────────────────────────┴─────────────────────────────────────┐
│ 多业务域服务层                                               │
│ 域隔离服务 / 跨域共享服务 / 语义 API 服务 / 多语种服务         │
└──────────────────────────────────────────────────────────────┘
```

### 7.2 服务边界扩展

| 服务 | Phase 3 新增职责 |
|------|-----------------|
| **Backend** | 多业务域隔离、跨域共享、语义 API、OAuth2.0 |
| **Agent** | 全自动 Pipeline 生成、策略推荐、自愈决策 |
| **Frontend** | 多业务域切换、传感器配置、自愈策略管理、多语种 |
| **OpenClaw** | 企业 IM 消息接收、任务指令路由 |
| **Dagster** | 事件驱动调度、传感器执行 |

---

## 8. Module Breakdown

### 8.1 新增模块清单

| 模块 | 目录 | 职责 |
|------|------|------|
| `backend/harness-app` | Java 后端主服务 | 多业务域、语义 API、OAuth2.0 |
| `agent/app/services` | Python Agent 服务 | 全自动 Pipeline 生成、策略推荐、自愈决策 |
| `agent/app/adapters` | Python 适配器 | OpenClaw Adapter |
| `frontend/apps/web` | React Web 应用 | 多业务域、传感器配置、自愈管理、多语种 |
| `frontend/locales` | 多语种资源 | ja_JP、ko_KR 翻译文件 |

---

## 9. Interface Contracts

### 9.1 全自动 Pipeline API

#### 9.1.1 生成复杂 Pipeline

```http
POST /api/v1/agent/pipeline/generate
{
  "requirement": {
    "sources": [
      { "type": "mysql", "table": "orders", "domain": "sales" },
      { "type": "kafka", "topic": "user_events", "domain": "behavior" }
    ],
    "target": { "type": "postgresql", "table": "dwd_order_events" },
    "transformations": ["join", "filter", "aggregate"],
    "sync_mode": "incremental"
  }
}

Response 200:
{
  "pipeline_conf": "source { ... } transform { ... } sink { ... }",
  "validation_result": "pass",
  "explanation": "生成了 MySQL 订单表与 Kafka 用户事件表的关联 Pipeline..."
}
```

### 9.2 事件驱动调度 API

#### 9.2.1 创建传感器

```http
POST /api/v1/sensors
{
  "name": "order_data_arrived",
  "type": "data_arrival",
  "condition": {
    "table": "ods_orders",
    "min_rows": 100
  },
  "trigger_task_id": "task_001"
}

Response 201:
{
  "id": "sensor_001",
  "status": "active"
}
```

### 9.3 异常自愈 API

#### 9.3.1 配置自愈策略

```http
POST /api/v1/remediation/policies
{
  "exception_type": "network_timeout",
  "risk_level": "low",
  "action": "retry",
  "max_retries": 3,
  "retry_interval": "30s",
  "notify_owner": true
}

Response 201:
{
  "id": "policy_001",
  "status": "active"
}
```

### 9.4 统一语义 API

#### 9.4.1 查询指标数据

```http
GET /api/v1/semantic/metrics/{metricCode}/data?start_date=2026-05-01&end_date=2026-05-10&dimensions=channel
Authorization: Bearer <oauth_token>

Response 200:
{
  "metric_code": "revenue",
  "data": [
    { "channel": "APP", "revenue": 1234567.89, "date": "2026-05-10" }
  ],
  "metadata": {
    "definition": "订单收入总和",
    "formula": "SUM(order_amount)",
    "owner": "sales_team"
  }
}
```

### 9.5 多业务域 API

#### 9.5.1 创建业务域

```http
POST /api/v1/domains
{
  "code": "finance",
  "name": { "zh_CN": "财务域", "en_US": "Finance" },
  "owner_id": "user_002",
  "settings": {
    "isolated_dict": true,
    "isolated_templates": true
  }
}

Response 201:
{
  "id": "domain_001",
  "code": "finance"
}
```

### 9.6 多语种支持

#### 9.6.1 切换语言

```http
POST /api/v1/users/preferences
{
  "locale": "ja_JP"
}

Response 200:
{
  "locale": "ja_JP",
  "available_locales": ["zh_CN", "en_US", "ja_JP", "ko_KR"]
}
```

---

## 10. Database Schema

### 10.1 Phase 3 新增/扩展表

| 表名 | 用途 | 迁移脚本 |
|------|------|---------|
| `domain_config` | 业务域配置 | 新增 |
| `cross_domain_share` | 跨域共享记录 | 新增 |
| `sensor_config` | 传感器配置 | 新增 |
| `sensor_execution_log` | 传感器执行日志 | 新增 |
| `remediation_policy` | 自愈策略配置 | 新增 |
| `remediation_log` | 自愈执行记录 | 新增 |
| `strategy_recommendation` | 策略推荐记录 | 新增 |
| `semantic_api_token` | 语义 API Token | 新增 |
| `sys_dict_item` | 字典项（扩展 ja/ko 字段） | 扩展 |

### 10.2 domain_config 关键字段

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PK | UUID |
| code | VARCHAR(50) | UNIQUE, NOT NULL | 域编码 |
| name_zh | VARCHAR(200) | NOT NULL | 中文名称 |
| name_en | VARCHAR(200) | NOT NULL | 英文名称 |
| owner_id | VARCHAR(36) | NOT NULL | 负责人 ID |
| isolated_dict | BOOLEAN | DEFAULT false | 独立字典 |
| isolated_templates | BOOLEAN | DEFAULT false | 独立模板 |
| status | VARCHAR(20) | DEFAULT 'active' | 状态 |
| created_at | TIMESTAMP | DEFAULT NOW() | 创建时间 |

### 10.3 sensor_config 关键字段

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PK | UUID |
| name | VARCHAR(200) | NOT NULL | 传感器名称 |
| type | VARCHAR(50) | NOT NULL | data_arrival/time_condition/data_condition |
| condition_expression | TEXT | NOT NULL | 条件表达式 |
| trigger_task_id | VARCHAR(36) | FK | 触发任务 ID |
| enabled | BOOLEAN | DEFAULT true | 是否启用 |
| created_at | TIMESTAMP | DEFAULT NOW() | 创建时间 |

### 10.4 remediation_policy 关键字段

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PK | UUID |
| exception_type | VARCHAR(50) | NOT NULL | 异常类型 |
| risk_level | VARCHAR(20) | NOT NULL | low/medium/high |
| action | VARCHAR(50) | NOT NULL | retry/rollback/config_change |
| max_retries | INT | DEFAULT 3 | 最大重试次数 |
| retry_interval | VARCHAR(20) | DEFAULT '30s' | 重试间隔 |
| notify_owner | BOOLEAN | DEFAULT true | 是否通知责任人 |
| enabled | BOOLEAN | DEFAULT true | 是否启用 |
| created_at | TIMESTAMP | DEFAULT NOW() | 创建时间 |

---

## 11. Risk Assessment

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 全自动 Pipeline 生成失败 | 复杂场景仍需手动 | 提供回退至模板模式的路径 |
| 自愈误操作 | 可能引发二次故障 | 仅限低风险场景，完整审计日志可回滚 |
| OpenClaw 接入安全性 | IM 渠道可能被滥用 | 敏感操作需 Web 端二次确认 |
| 多业务域性能下降 | 域隔离增加查询复杂度 | 数据库索引优化，缓存域配置 |
| 多语种维护成本 | 翻译资源增加 | AI 辅助翻译 + 人工审核 |

---

## 12. Tasks

### Task 1: 全自动 Pipeline 生成服务

- 实现复杂需求解析引擎
- 实现多表关联 Pipeline 生成器
- 实现语法校验和预览
- **验证方式**：输入复杂需求，自动生成并通过校验

### Task 2: 事件驱动调度服务

- 实现 Dagster Sensor 配置 API
- 实现传感器执行器
- 实现传感器日志记录
- **验证方式**：配置传感器，数据到达后触发任务

### Task 3: 异常自愈服务

- 实现风险分级策略引擎
- 实现自动重试与修复执行器
- 实现自愈通知与审计
- **验证方式**：低风险异常自动修复成功

### Task 4: 策略推荐服务

- 实现历史数据分析模块
- 实现质量阈值推荐
- 实现调度频率推荐
- **验证方式**：推荐结果与人工配置对比准确率 > 85%

### Task 5: OpenClaw 适配器

- 实现 OpenClaw Adapter
- 实现 IM 消息接收与路由
- 实现任务指令解析与执行
- **验证方式**：通过 IM 发送指令可执行数据查询

### Task 6: 多业务域服务

- 实现业务域 CRUD API
- 实现域隔离与跨域共享
- 实现域内资源管理
- **验证方式**：创建两个业务域，数据相互隔离

### Task 7: 统一语义 API

- 实现 OAuth2.0 认证层
- 实现语义查询 API
- 实现限流与审计
- 生成 OpenAPI 文档和 SDK
- **验证方式**：外部系统通过 API 查询指标数据

### Task 8: 多语种支持

- 提供 ja_JP、ko_KR 翻译文件
- 扩展字典项多语言字段
- 实现 locale 格式化
- **验证方式**：切换日语/韩语后界面正确显示

### Task 9: 前端 Phase 3 页面

- 实现传感器配置页面
- 实现自愈策略管理页面
- 实现多业务域切换组件
- 实现语义 API 文档页
- **验证方式**：用户可完成所有 Phase 3 配置操作

### Task 10: 端到端联调

- 验证全自动 Pipeline 流程
- 验证事件驱动调度
- 验证异常自愈
- 验证多业务域隔离
- 验证语义 API
- 验证多语种
- **验证方式**：所有 Success Metrics 验收通过

---

## 13. Dependencies

### 13.1 外部依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| OpenClaw | 最新 | 企业 IM Agent 接入 |
| Dagster | 1.5+ | 传感器与事件驱动 |
| OAuth2.0 Provider | - | 语义 API 认证 |

### 13.2 内部依赖

| 依赖方 | 被依赖方 | 依赖内容 |
|--------|---------|---------|
| Phase 3 | Phase 1-2 | 本体、字典、审批、质量、周报、诊断 |
| OpenClaw Adapter | Agent | 消息路由、指令解析 |
| 语义 API | Backend | 指标数据查询服务 |
| 多业务域 | Backend | 域隔离与跨域共享 |
| 自愈服务 | Agent | 风险分级、修复执行 |

---

## 14. Appendix

### A. Phase 3 新增字典分组

| 分组编码 | 分组名称（zh） | 项数 |
|---------|--------------|------|
| `sensor_type` | 传感器类型 | 3 |
| `risk_level` | 风险等级 | 3 |
| `remediation_action` | 自愈动作 | 4 |
| `domain_isolation_mode` | 域隔离模式 | 2 |
| `api_auth_type` | API 认证类型 | 2 |

### B. 自愈策略示例

```json
{
  "exception_type": "network_timeout",
  "risk_level": "low",
  "action": "retry",
  "max_retries": 3,
  "retry_interval": "30s",
  "notify_owner": true,
  "rollback_on_failure": false
}
```

### C. 语义 API 限流配置

```yaml
rate_limit:
  default: 100 requests/minute
  premium: 1000 requests/minute
  burst: 50 requests/second
```
