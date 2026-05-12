# Spec: HarnessDG 构建验证与集成联调执行计划

## 0. 文档信息

| 字段 | 内容 |
|------|------|
| **Feature** | 构建验证与 Phase 1-2 集成联调 |
| **版本** | v1.0.0 |
| **状态** | 执行中 |
| **作者** | AxeXie |
| **创建时间** | 2026-05-12 |
| **关联 SPEC** | specs/harnessdg-phase1-core.md, specs/harnessdg-phase2-governance.md |

---

## 1. 执行步骤

### Step 1: 构建验证与环境联调（Phase 1 Task 9 收尾）

**目标**: 确认所有服务可独立编译、Docker 镜像可构建、服务可联合启动

| 子任务 | 验收标准 |
|--------|---------|
| 1.1 Backend Maven 编译 | `mvn clean package -DskipTests` 成功 |
| 1.2 Frontend pnpm 构建 | `pnpm install && pnpm build` 成功 |
| 1.3 Agent pip 依赖安装 | `pip install -r requirements.txt` 成功 |
| 1.4 Docker 镜像构建 | 三个 Dockerfile 均可成功构建 |
| 1.5 docker-compose 启动 | 4 个服务全部启动且健康检查通过 |
| 1.6 数据库迁移验证 | V001-V016 执行成功，表结构完整 |
| 1.7 核心 API 验证 | /api/v1/dictionary, /api/v1/ontology 可访问 |

### Step 2: 补充测试覆盖率（Phase 1 质量保障）

**目标**: Service 层单元测试覆盖率 >= 80%

| 子任务 | 验收标准 |
|--------|---------|
| 2.1 DictService 测试 | 已有，确认通过 |
| 2.2 OntologyService 测试 | Entity/Metric CRUD 全覆盖 |
| 2.3 AuthService 测试 | 登录/注册/权限校验覆盖 |
| 2.4 AuditService 测试 | 日志记录与查询覆盖 |
| 2.5 Frontend 组件测试 | DictSelect/DictTag 快照测试 |

### Step 3: SeaTunnel + OpenMetadata 集成完善

**目标**: 数据接入 Pipeline 可生成并提交执行

| 子任务 | 验收标准 |
|--------|---------|
| 3.1 Pipeline 模板引擎 | 根据数据源配置生成 .conf 文件 |
| 3.2 SeaTunnel API 调用 | 提交 Pipeline 到 SeaTunnel 执行 |
| 3.3 OpenMetadata 同步 | Entity/Metric 创建后同步至 OM |
| 3.4 血缘链路注册 | 数据源 -> Entity -> Metric 血缘自动记录 |

### Step 4: 审批流 + 质量规则端到端

**目标**: 指标创建 -> 审批 -> 发布 -> 质量规则生成全流程通畅

| 子任务 | 验收标准 |
|--------|---------|
| 4.1 审批流完整测试 | 提交 -> 审批 -> 通过/驳回状态机正确 |
| 4.2 质量规则自动生成 | 指标发布时自动创建基础规则 |
| 4.3 质量校验执行 | 规则可执行并记录结果 |
| 4.4 前端审批中心验证 | 审批列表、详情、操作正常 |

### Step 5: 周报 + 异常诊断联调

**目标**: 周报生成和异常诊断功能端到端可用

| 子任务 | 验收标准 |
|--------|---------|
| 5.1 周报 Markdown 模板 | 生成包含环比/同比的结构化报告 |
| 5.2 确定性计算模块 | 数值计算结果正确 |
| 5.3 AI 文字归因 | Agent 生成可读的分析文本 |
| 5.4 异常诊断触发 | 任务失败时自动触发诊断分析 |
| 5.5 根因分类与建议 | 返回分类结论和修复建议 |

---

## 2. 约束与注意事项

- Maven 使用阿里云镜像源（settings.xml）
- npm/pnpm 使用国内镜像源（.npmrc）
- pip 使用国内镜像源
- Docker 构建使用多阶段构建优化镜像体积
- 所有命令在 Windows 环境执行
