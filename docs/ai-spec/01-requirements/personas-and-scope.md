# Persona、Principal 与授权作用域

> 状态：`PROPOSED`
> 已确认：`DEC-004`（单公司租户、多组织、多项目）。
> 未决：`Q-102` 至 `Q-106`。

## 1. 三个概念必须分开

```text
Persona（用户为何使用平台）
        ↓
PrincipalType（谁在调用）
        ↓
Role Binding + Permission + Scope + ACL（当前能否执行）
```

- “平台管理员”“审核者”“资产维护者”是 Persona/角色，不是 PrincipalType；
- `USER`、`AGENT`、`SERVICE`、`API_CLIENT`、`WORKER` 是 PrincipalType；
- JWT Scope 只限制凭据能力上限，不直接授予资源权限；
- 前端是否显示按钮不能代替后端授权；
- `PUBLIC` 资产也只向状态正常的已认证 Principal 开放。

## 2. Persona 目录

| ID | Persona | 核心目标 | 默认作用域 | 高风险限制 |
| --- | --- | --- | --- | --- |
| `PER-001` | 平台管理员 | 管理平台级身份、治理、配置和审计 | PLATFORM | 高风险配置和凭据操作需二次确认 |
| `PER-002` | 组织管理员 | 管理本组织成员、项目、Team、标签和授权 | ORGANIZATION | 不得管理其他组织或平台级配置 |
| `PER-003` | 项目管理员 | 管理指定项目成员、配额和资产授权 | PROJECT | 不得提升自身到组织/平台权限 |
| `PER-004` | 资产 Owner | 对资产治理、维护者和生命周期负责 | ASSET/PROJECT | 发布审批是否可自审待确认 |
| `PER-005` | 资产维护者 | 创建草稿、维护卡片、上传并提交版本 | PROJECT/ASSET | 默认不能审批或直接发布 |
| `PER-006` | 审核者 | 查看风险材料并批准/驳回版本 | ORGANIZATION/PROJECT | 默认不能修改待审内容 |
| `PER-007` | 普通用户 | 搜索、查看和下载获授权资产 | ORGANIZATION/PROJECT | 不得查看管理、审计和系统诊断 |
| `PER-008` | 只读 Agent | 搜索、解析版本并申请下载 | PROJECT/ASSET | Tool 白名单；禁止发布、删除和配置 |
| `PER-009` | 自动化写入 Agent | 创建草稿、上传或提交 | PROJECT | 写工具默认关闭，必须显式授权和幂等 |
| `PER-010` | 平台 Worker | 执行持久化任务 | WORKLOAD | 只能处理已领取任务，不继承管理员权限 |
| `PER-011` | 审计/运维人员 | 查询审计、任务、依赖和指标 | PLATFORM | 只读；敏感载荷仍脱敏 |

## 3. 建议的作用域层级

```text
PLATFORM
└─ ORGANIZATION
   ├─ Team
   └─ PROJECT
      └─ ASSET
         └─ VERSION
```

建议规则：

1. 角色定义是 Permission 模板，角色绑定决定其生效作用域；
2. PLATFORM 授权不会自动被普通自定义角色获得；
3. ORGANIZATION 绑定覆盖其项目，但高风险动作可要求更窄绑定；
4. PROJECT 绑定不跨项目；
5. ASSET ACL 只能授予已有 Permission，不能突破 JWT Scope 或敏感等级；
6. VERSION 权限继承 Asset，但评审/发布还要检查版本状态；
7. Team 通过成员关系被授予角色/ACL，禁止把成员逐个复制为 Owner 字符串。

## 4. 初始职责矩阵

`A`=最终负责，`R`=执行，`V`=只读查看，`-`=默认无权。此表仅为产品确认草案。

| 用例 | 平台管理员 | 组织管理员 | 项目管理员 | Owner/维护者 | 审核者 | 普通用户 | Agent |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 创建组织 | A/R | - | - | - | - | - | - |
| 创建项目/Team | V | A/R | R（待确认） | - | - | - | - |
| 管理组织成员 | V | A/R | - | - | - | - | - |
| 创建资产草稿 | V | V | A | R | - | - | R（显式授权） |
| 修改卡片/治理字段 | V | V | A | R | - | - | R（显式授权） |
| 上传内容 | V | V | A | R | - | - | R（显式授权） |
| 提交审核 | V | V | A | R | - | - | R（显式授权） |
| 审批版本 | 紧急 A | V | - | - | R | - | - |
| 发布版本 | 紧急 A | A（待确认） | - | - | R（待确认） | - | 默认禁止 |
| 搜索/查看 | V | V | V | V | V | V（有权） | V（双控） |
| 下载 | V | V | V | V | V | V（有权） | V（双控） |
| 查看审计/诊断 | A/R | 组织范围 V（待确认） | - | - | - | - | - |

## 5. 当前实现与草案的明显偏差

- 内置 `READER` 当前含 `user:read`、`authorization:read`、`audit:read`、`job:read` 和
  `system:observe`，明显超出普通用户“搜索/浏览/下载”的产品 Persona；是否保留必须明确决策。
- `ASSET_AUTHOR` 含 `asset:deprecate`，但是否允许维护者直接弃用正式版本未定义。
- `Permissions.java` 使用 `asset:manage`，V4 权限 Seed 没有该权限。
- 管理页面以自由文本录入 Scope 和 Tool，会绕过可选值治理与最小权限体验。
- Organization Member 的 OWNER/MEMBER 与 RBAC Role Binding 职责重叠。

在权限矩阵被用户确认前，不得把当前 Seed 角色当作产品授权需求。
