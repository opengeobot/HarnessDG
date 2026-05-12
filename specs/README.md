# HarnessDG SPEC 总览

## 文档信息

| 字段 | 内容 |
|------|------|
| **项目** | HarnessDG（AODO - AI Ontology Data OS） |
| **版本** | v1.0.0 |
| **创建时间** | 2026-05-11 |
| **作者** | AxeXie |
| **关联 PRD** | docs/HarnessDG-v2.md |

---

## SPEC 文档索引

| Phase | 文档 | 目标 | 核心能力 |
|-------|------|------|---------|
| **Phase 1** | [harnessdg-phase1-core.md](./harnessdg-phase1-core.md) | 最小业务闭环 | 本体建模、数据字典、AI 问数、国际化、审计日志 |
| **Phase 2** | [harnessdg-phase2-governance.md](./harnessdg-phase2-governance.md) | 治理增强 | 数据接入、SeaTunnel、OpenMetadata、质量规则、审批流、周报、异常诊断 |
| **Phase 3** | [harnessdg-phase3-intelligence.md](./harnessdg-phase3-intelligence.md) | 智能化升级 | 全自动 Pipeline、事件驱动调度、异常自愈、OpenClaw、多业务域、语义 API、多语种 |
| **Phase 4** | [harnessdg-phase4-ecosystem.md](./harnessdg-phase4-ecosystem.md) | 规模化运营与生态建设 | 模板市场、插件生态、运营分析、资源治理、开放集成、合规增强、产品化输出 |

---

## Phase 演进路线图

```
Phase 1 (2个月)          Phase 2 (3-4个月)        Phase 3 (6个月)         Phase 4
最小业务闭环      ->      治理增强          ->     智能化升级       ->     规模化运营与生态
├─ 本体建模               ├─ 数据接入模板            ├─ 全自动 Pipeline       ├─ 模板市场
├─ 数据字典               ├─ SeaTunnel 集成          ├─ 事件驱动调度          ├─ 插件生态
├─ AI 问数                ├─ OpenMetadata 打通       ├─ 异常自愈              ├─ 运营分析
├─ 国际化(zh/en)          ├─ 质量规则自动生成        ├─ OpenClaw 接入         ├─ 资源治理
├─ 审计日志               ├─ 审批流                  ├─ 多业务域              ├─ 开放集成
├─ 任务中心               ├─ 周报自动生成            ├─ 统一语义 API          ├─ 合规增强
└─ 设计系统               ├─ 异常诊断初版            ├─ 多语种(ja/ko)         └─ 产品化输出
                          └─ 字典管理后台            └─ 策略推荐
```

---

## 各 Phase 核心指标对比

| 指标 | Phase 1 | Phase 2 | Phase 3 | Phase 4 |
|------|---------|---------|---------|---------|
| **业务对象** | 3 个核心对象 | 扩展数据源 | 多表关联 | 模板市场 50+ |
| **指标管理** | 手动创建 | 审批流 | 全自动 | 模板一键安装 |
| **AI 能力** | 意图识别 | 异常诊断 | 自愈决策 | 智能推荐 |
| **治理覆盖** | 基础血缘 | 质量+审批 | 自愈 | 合规增强 |
| **语种支持** | zh/en | zh/en | zh/en/ja/ko | 多语种 |
| **调度方式** | 定时 | 定时+手动 | 事件驱动 | 全场景 |
| **集成能力** | 无 | SeaTunnel/OM | OpenClaw | BI/LLM/消息 |
| **目标用户** | 业务分析师 | 数据治理 | 运维/开发 | 全角色 |

---

## 技术栈演进

| 层级 | Phase 1 | Phase 2 | Phase 3 | Phase 4 |
|------|---------|---------|---------|---------|
| **前端** | React + i18n + Dict | 审批/质量/周报页面 | 传感器/自愈/多域 | 模板/插件/运营 |
| **后端** | Spring Boot 核心服务 | 审批/质量/周报服务 | 多域/语义 API | 模板/插件/合规 |
| **Agent** | QwenPaw 意图识别 | 诊断/周报生成 | 自愈/策略推荐 | 智能推荐 |
| **存储** | PG + AGE + pgvector | 扩展表 | 扩展表 | 扩展表 |
| **集成** | 无 | SeaTunnel/OM | OpenClaw/Dagster Sensor | BI/LLM/消息 |
| **部署** | Docker Compose | Docker Compose | Kubernetes | K8s + 弹性扩缩 |

---

## 依赖关系

```
Phase 4
  ↑
Phase 3
  ↑
Phase 2
  ↑
Phase 1
  ↑
基础设施（PostgreSQL、Docker、Node.js、OpenJDK）
```

---

## 成功标准汇总

| Phase | 关键成功标准 |
|-------|-------------|
| **Phase 1** | 跑通"对象定义 -> 指标创建 -> AI 问数 -> 治理记录"闭环 |
| **Phase 2** | 补齐数据接入、质量、审批、周报、诊断能力 |
| **Phase 3** | 实现全自动 Pipeline、事件驱动、异常自愈、多业务域 |
| **Phase 4** | 建立模板市场、插件生态、运营体系、合规能力 |

---

## 风险汇总

| 风险类型 | Phase 1 | Phase 2 | Phase 3 | Phase 4 |
|---------|---------|---------|---------|---------|
| **技术风险** | AGE/pgvector 插件 | SeaTunnel/OM 兼容性 | 自愈误操作 | 插件安全 |
| **产品风险** | 本体设计复杂 | 审批流阻塞业务 | Pipeline 生成失败 | 模板质量 |
| **运营风险** | 用户接受度 | 治理流程过重 | 多域性能 | 资源配额 |
| **安全风险** | 认证鉴权 | 审批权限 | 自愈审计 | 合规要求 |

---

## 下一步行动

1. **审阅 SPEC 文档**：确认各 Phase 范围、指标、任务是否合理
2. **制定详细计划**：基于 SPEC 文档制定 Phase 1 详细实施计划
3. **启动 Phase 1 开发**：按 Task 清单逐项实现
4. **定期回顾**：每 Phase 完成后进行回顾，调整后续 Phase 计划
5. **持续迭代**：根据用户反馈和市场变化调整产品方向

---

## 文档维护

| 日期 | 版本 | 变更内容 | 作者 |
|------|------|---------|------|
| 2026-05-11 | v1.0.0 | 初始版本，创建 Phase 1-4 SPEC 文档 | AxeXie |
