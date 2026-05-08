# HarnessDG - AI Ontology Data OS

HarnessDG 是一款面向业务人员的业务任务型数据平台，以"动态本体（Dynamic Ontology）"为单一真相源，通过 AI 认知层理解业务意图，将业务定义自动转化为可执行的数据资产、任务和治理规则。

## 产品定位

以动态本体为核心、以业务任务为入口、以标准化输入输出为约束，构建真正面向业务人员的数据操作系统。

## 技术栈

### 后端
- **Java 21** + Spring Boot 3.2.5 + 虚拟线程
- MyBatis-Plus 3.5.7
- PostgreSQL + AGE（图查询）+ pgvector（向量检索）
- Flyway 数据库迁移
- JWT 认证 + Spring Security

### 前端
- React 18 + TypeScript + Vite 6
- Ant Design 5.x + react-i18next
- Zustand + TanStack Query
- Framer Motion

### Agent
- Python FastAPI + QwenPaw
- httpx 异步 HTTP

### 第三方集成（Phase 2）
- **Dagster** - 任务编排与执行
- **SeaTunnel** - 数据接入与同步
- **OpenMetadata** - 元数据与血缘管理

## 快速启动

### 前置要求
- JDK 21+
- Node.js 18+ / pnpm 9+
- PostgreSQL 15+
- Maven 3.8+
- Python 3.11+（Agent 服务）

### 数据库初始化

```bash
# 启动 PostgreSQL（推荐 Docker）
docker run -d --name harnessdg-postgres \
  -e POSTGRES_USER=harness \
  -e POSTGRES_PASSWORD=harness_dev \
  -e POSTGRES_DB=harnessdg \
  -p 5432:5432 \
  postgres:15

# 启用扩展
docker exec -it harnessdg-postgres psql -U harness -d harnessdg -c "CREATE EXTENSION IF NOT EXISTS age; CREATE EXTENSION IF NOT EXISTS vector;"
```

### 后端启动

```bash
cd backend
mvn clean install -DskipTests
cd harness-app
mvn spring-boot:run
```

访问 API 文档：http://localhost:8080/swagger-ui.html

### 前端启动

```bash
cd frontend
pnpm install
pnpm dev
```

访问前端：http://localhost:5173

### Agent 服务启动

```bash
cd agent
pip install -e .
uvicorn app.main:app --host 0.0.0.0 --port 8088
```

## 项目结构

```
HarnessDG/
├── backend/                    # Java 后端
│   ├── harness-common/         # 公共基础设施
│   ├── harness-model/          # 数据模型（Entity/DTO/Request）
│   ├── harness-dictionary/     # 统一数据字典
│   ├── harness-config/         # 系统配置管理
│   ├── harness-auth/           # 认证授权
│   ├── harness-audit/          # 审计日志
│   ├── harness-ontology/       # 本体建模（Entity/Metric/Dimension）
│   ├── harness-relation/       # 实体关系管理（Phase 2）
│   ├── harness-task/           # 任务管理
│   ├── harness-integration/    # 第三方集成（Phase 2）
│   ├── harness-approval/       # 审批流引擎（Phase 2）
│   ├── harness-quality/        # 质量规则管理（Phase 2）
│   ├── harness-lineage/        # 血缘追踪（Phase 2）
│   ├── harness-datasource/     # 数据源管理（Phase 2）
│   ├── harness-report/         # 报告生成（Phase 2）
│   ├── harness-agent-gateway/  # Agent 网关
│   └── harness-app/            # Spring Boot 启动入口
├── frontend/                   # React 前端
│   ├── apps/web/               # 主应用
│   └── packages/               # 共享包
│       ├── design-tokens/      # 设计系统 Token
│       ├── dict-components/    # 字典组件库
│       ├── i18n/               # 国际化资源
│       └── shared/             # 共享类型/工具
├── agent/                      # Python Agent 服务
├── infra/                      # 基础设施
│   ├── flyway/migrations/      # 数据库迁移脚本
│   └── docker-compose.yml      # Docker 编排
└── docs/                       # 产品文档
```

## 架构设计

### 分层架构
```
应用体验层（React Web）
    ↓
业务服务与语义层（Java Spring Boot）
    ↓
Agent 决策与编排层（Python + QwenPaw）
    ↓
执行与调度层（Dagster）
    ↓
数据接入与治理层（SeaTunnel + OpenMetadata）
    ↓
平台基础能力层（字典/日志/配置/Auth/i18n）
    ↓
数据存储层（PostgreSQL + AGE + pgvector）
```

### 核心设计原则
1. **动态本体是单一真相源** - 所有指标、口径、维度、关系统一建模
2. **业务任务优先** - 界面以任务为中心，而非技术模块
3. **数据字典驱动前端** - 禁止前端硬编码枚举值
4. **国际化优先** - 所有可见文本通过 i18n 管理
5. **全流程可审计** - 所有操作留痕，可追溯、可回放

## 开发规范

- 所有新文件需标注功能、时间、作者
- SQL 脚本需有表注释和字段注释
- 前端禁止硬编码中文/英文，使用 i18n
- 前端分类选项统一从数据字典获取
- 使用国内镜像源（Maven 阿里云、npm npmmirror）

## 文档

- [产品 PRD（优化版 V2.1）](docs/HarnessDG-v2.md)

## License

Apache 2.0
