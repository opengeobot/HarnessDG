# P0-B 阶段出口验收目录

> 状态：`READY`
> 依据：ADR-0002、设计第 5/11/13/15/16 章。
> 已确认：所有决策问题已闭合（DEC-001~DEC-013），规格包已批准。
> 目的：替代“一个宽泛用例编号代表整类能力”的验收方式。

## 1. 出口规则

P0-B 只有在以下条件全部成立时才能退出：

1. 本目录所有 `MUST` 场景实际 PASS；
2. 没有 MUST 场景 SKIP；
3. 每个 PASS 绑定 Commit SHA、环境和 Evidence Manifest；
4. E3 场景使用真实 PostgreSQL/协议依赖，不以 Mock 替代；
5. E4 场景从 Nginx/Browser/Client 入口执行到真实 Compose 服务；
6. 正常、失败、拒绝和恢复证据均存在；
7. OpenAPI、事件、迁移、UI、Runbook 和追踪矩阵与被测 Commit 一致。

已有 V01-V11 可以作为执行容器，但每个 V 编号必须显式声明其覆盖的 `AC-*`，未声明的场景不视为覆盖。

## 2. 工程与契约门禁

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P0B-ENG-001` | MUST | 从空工作区按锁文件构建后端/前端 | 编译、Lint、类型、单测、ArchUnit 全通过；生成物可复现 | `E1/E2` |
| `AC-P0B-ENG-002` | MUST | 对 OpenAPI/MCP/Event 运行 lint 和示例校验 | 无错误；所有公开操作有 operation/tool ID、权限、错误、审计和实现状态 | `E1/E3` |
| `AC-P0B-ENG-003` | MUST | 对基线契约运行 breaking diff | 未关联 Accepted Decision/ADR 的破坏变更阻断流程 | `E1` |
| `AC-P0B-ENG-004` | MUST | 空 PostgreSQL 执行 V1 至最新 Migration | 全部成功、约束/Seed 存在、Flyway validate 通过 | `E3` |
| `AC-P0B-ENG-005` | MUST | 从上一支持数据库版本升级 | 存量数据不丢失；回填和约束验证通过 | `E3` |
| `AC-P0B-ENG-006` | MUST | 扫描可部署 Profile | 无空 Principal、allow-all、默认全 Scope、可靠任务内存回退或未声明 Noop | `E1/E3` |

## 3. Bootstrap、用户与 JWT

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P0B-IAM-001` | MUST | 空库、从 Secret/交互输入引导首个管理员 | 只创建一次；密码仅保存自适应哈希；日志/历史无明文 | `E4` |
| `AC-P0B-IAM-002` | MUST | 首个管理员以正确临时密码登录 | access JWT 返回；refresh 安全 Cookie；产生成功审计；仅允许改密相关访问 | `E4` |
| `AC-P0B-IAM-003` | MUST | 首登管理员完成符合策略的改密 | 临时密码失效；新密码可登录；force-change 清除；审计追加 | `E4` |
| `AC-P0B-IAM-004` | MUST | 用户名不存在和密码错误分别登录 | 两者均返回 `AUTH_INVALID_CREDENTIALS`，不暴露账号存在性；失败审计无密码 | `E4` |
| `AC-P0B-IAM-005` | MUST | 同一用户连续错误达到配置阈值 | 账户进入 LOCKED；后续正确密码也返回 `AUTH_ACCOUNT_LOCKED`；审计/指标存在 | `E4` |
| `AC-P0B-IAM-006` | MUST | 有效 refresh Cookie 调刷新 | access/refresh 同时轮换；旧 jti 标记失效；新 Cookie 属性正确 | `E4` |
| `AC-P0B-IAM-007` | MUST | 再次使用已轮换的 refresh JWT | 返回 `AUTH_REFRESH_REPLAYED`；Token Family 吊销；新 refresh 也失效；产生拒绝审计 | `E4` |
| `AC-P0B-IAM-008` | MUST | 管理员禁用正在使用 access JWT 的用户 | 旧 access 下一请求立即被拒；refresh/凭据交换被拒；状态和审计一致 | `E4` |
| `AC-P0B-IAM-009` | MUST | 用户登出后重用 refresh JWT | Cookie 清除；Token Family 吊销；重用被拒；登出幂等 | `E4` |
| `AC-P0B-IAM-010` | MUST | access JWT 过期、签名错误、未知 kid、iss/aud 错误 | 均 fail closed；错误不泄露密钥/Claim 细节 | `E3/E4` |
| `AC-P0B-IAM-011` | MUST | 两个并发刷新请求使用同一 refresh JWT | 只允许一个轮换成功；另一个触发定义好的重放/并发语义；无双有效 Token | `E4` |
| `AC-P0B-IAM-012` | MUST | 管理员创建、编辑、重置、禁用和启用用户 | 权限、状态前置条件、强制改密、稳定错误和每项审计均正确 | `E4` |

## 4. Agent/Service/API Client

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P0B-AGT-001` | MUST | 授权管理员注册 Agent | 凭据只显示一次；数据库仅存摘要；默认 Scope/Tool 最小化；产生审计 | `E4` |
| `AC-P0B-AGT-002` | MUST | Agent 用正确/错误凭据换 access JWT | 正确凭据得到受限 JWT；错误凭据统一拒绝；两者有脱敏审计 | `E4` |
| `AC-P0B-AGT-003` | MUST | Agent 被禁用后使用旧 JWT 或凭据 | 全部立即失效，不依赖 JWT 内旧角色/Scope | `E4` |
| `AC-P0B-AGT-004` | MUST | 尝试授予 Agent 高风险默认禁止权限 | 默认拒绝或进入明确人工审批；不得仅靠自由文本 Scope 绕过 | `E4` |
| `AC-P0B-AGT-005` | MUST | 提交未知 MCP Tool 名称到 allowlist | 以受控 Tool Catalog 校验并拒绝；不得创建自由工具值 | `E3/E4` |

## 5. 组织、Team 与授权

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P0B-AUTH-001` | MUST | 平台管理员创建组织，组织管理员创建项目/Team | 作用域、唯一约束、权限和审计正确 | `E4` |
| `AC-P0B-AUTH-002` | MUST | 用户同时加入两个组织并在不同项目获不同角色 | 每个请求按所选/目标作用域实时计算权限，不发生串权 | `E4` |
| `AC-P0B-AUTH-003` | MUST | 无权限用户查询另一个组织/项目/私有资源 | 列表 SQL 不返回；详情使用防枚举语义；审计按策略记录 | `E3/E4` |
| `AC-P0B-AUTH-004` | MUST | 有权限用户执行同一操作 | 成功路径真实通过，证明系统不是“全部拒绝” | `E4` |
| `AC-P0B-AUTH-005` | MUST | Scope 包含动作但 RBAC/ACL 不允许，及反向组合 | 两种情况都拒绝；只有所有条件满足才通过 | `E4` |
| `AC-P0B-AUTH-006` | MUST | 普通管理员尝试给自己绑定更高角色或管理平台角色 | 拒绝自提权；内置角色不可改删；产生拒绝审计 | `E4` |
| `AC-P0B-AUTH-007` | MUST | 删除仍有绑定的角色或重复创建绑定/ACL | 返回稳定冲突；不产生部分写入；审计结果正确 | `E3/E4` |
| `AC-P0B-AUTH-008` | MUST | 禁用组织、项目、角色或移除成员 | 后续访问实时失效；历史审计/治理引用仍可查询 | `E4` |
| `AC-P0B-AUTH-009` | MUST | 用户通过 Team 获得资产角色/ACL后离开 Team | 权限实时撤销；资产 Owner 责任转移规则生效 | `E4` |

## 6. 字典、标签与国际化

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P0B-TAX-001` | MUST | 管理员创建/更新字典项 | itemCode 稳定；版本递增；i18nKey 有 zh/en 文案；产生审计 | `E4` |
| `AC-P0B-TAX-002` | MUST | 停用已被资产引用的字典项 | 新写入拒绝 `DICTIONARY_VALUE_INVALID`；历史资产仍按 locale 回显 | `E4` |
| `AC-P0B-TAX-003` | MUST | 创建平台标签和两个组织的同 code 标签 | 唯一约束按作用域生效；组织标签不覆盖平台语义 | `E3/E4` |
| `AC-P0B-TAX-004` | MUST | 普通用户提交自由标签/未知 tagId/跨组织 tagId | 返回 `TAG_VALUE_INVALID`；不产生资产关联 | `E4` |
| `AC-P0B-TAX-005` | MUST | 停用已关联标签 | 历史关联可回显；新关联被拒；审计存在 | `E4` |
| `AC-P0B-TAX-006` | MUST | 用户在 zh-CN/en-US 间切换 | 菜单、按钮、字典、错误、通知使用相应语言，无业务文案硬编码 | `E4` |

## 7. 配置、幂等与任务

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P0B-CFG-001` | MUST | 管理员提交合法/非法类型配置 | 合法更新版本并说明热更新；非法返回 `CONFIG_VALUE_INVALID` | `E4` |
| `AC-P0B-CFG-002` | MUST | 尝试写 password/token/privateKey/credential 类 Key/Value | 返回 `CONFIG_SECRET_FORBIDDEN`；日志和审计不保留值 | `E4` |
| `AC-P0B-CFG-003` | MUST | 两个客户端用同版本并发更新 | 只允许一个成功；另一个得到 `CONCURRENT_MODIFICATION` | `E3/E4` |
| `AC-P0B-IDM-001` | MUST | 同主体、方法、路径、请求摘要和幂等键重放 | 返回首次状态码/响应；只发生一次业务和审计副作用 | `E4` |
| `AC-P0B-IDM-002` | MUST | 同幂等键提交不同请求摘要 | 返回 `IDEMPOTENCY_KEY_CONFLICT`；第二请求无副作用 | `E4` |
| `AC-P0B-IDM-003` | MUST | 两个并发请求使用同一幂等键 | 只有一个执行；另一个等待/复用定义结果，不出现重复记录 | `E4` |
| `AC-P0B-JOB-001` | MUST | 多 Worker 并发领取一批任务 | 每个租约同一时刻只属于一个 Worker；无重复并行执行 | `E4` |
| `AC-P0B-JOB-002` | MUST | Worker 领取后被强制终止 | 租约过期后重新领取；Handler 幂等；最终成功 | `E4` |
| `AC-P0B-JOB-003` | MUST | 可重试依赖错误持续后恢复 | 指数退避+抖动；恢复后成功；Trace/attempt 完整 | `E4` |
| `AC-P0B-JOB-004` | MUST | 不可恢复或达到最大重试 | 进入 DEAD；通知/告警/审计产生；不无限重试 | `E4` |
| `AC-P0B-JOB-005` | MUST | 未授权用户重试/取消任务，有权运维执行同操作 | 前者拒绝，后者遵守状态前置条件并审计 | `E4` |

## 8. 日志与审计

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P0B-AUD-001` | MUST | 分别触发写操作成功、业务失败和权限拒绝 | 必需事件均追加记录正确 result/errorCode/主体/资源/Trace | `E4` |
| `AC-P0B-AUD-002` | MUST | 在 Header/Cookie/Body/URL/任务 Payload 放入测试 Secret 标记 | HTTP/MCP/Worker/依赖日志和 audit_log 均不出现原文 | `E4` |
| `AC-P0B-AUD-003` | MUST | 普通管理员尝试更新/删除审计；审计员查询 | 修改删除无 API/DB 路径且被约束阻止；查询按权限和游标工作 | `E3/E4` |
| `AC-P0B-AUD-004` | MUST | 单请求触发 DB、外部依赖和后台任务 | JSON 日志可用 requestId/traceId/principalId/resourceId/jobId 关联 | `E4` |
| `AC-P0B-AUD-005` | MUST | 发送超大或含敏感样本的请求 | 日志正文受大小和字段规则限制，不记录完整敏感内容 | `E4` |

## 9. 通知与 Webhook

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P0B-NOT-001` | MUST | 核心事务同时创建通知和 Outbox 后提交 | 两者原子可见；接收者可分页查询并标记已读 | `E4` |
| `AC-P0B-NOT-002` | MUST | 核心事务回滚 | 通知和 Outbox 均不存在 | `E3/E4` |
| `AC-P0B-NOT-003` | MUST | Webhook 首次 5xx 后恢复 2xx | 核心事务不回滚；Delivery 按策略重试并最终 DELIVERED | `E4` |
| `AC-P0B-NOT-004` | MUST | Webhook 目标为 loopback、私网、保留地址或 DNS 重绑定 | SSRF 防护拒绝 `WEBHOOK_TARGET_FORBIDDEN`；产生安全审计/指标 | `E4` |
| `AC-P0B-NOT-005` | MUST | 接收端验证签名、时间戳和 Delivery ID | 合法事件可验签；载荷/时间戳被改动则失败；重复 ID 可去重 | `E4` |

## 10. 指标、Trace、健康与诊断

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P0B-OBS-001` | MUST | 发起 REST 请求并创建后台任务 | Nginx→REST→DB/依赖→Job 全链关联；Worker 使用 Span Link | `E4` |
| `AC-P0B-OBS-002` | MUST | Prometheus 抓取应用和 Worker | Target UP；API/MCP/任务/依赖/JVM/业务关键指标存在 | `E4` |
| `AC-P0B-OBS-003` | MUST | 停止 PostgreSQL 与非关键通知渠道 | 前者 readiness DOWN 但 liveness UP；后者不让 API readiness DOWN | `E4` |
| `AC-P0B-OBS-004` | MUST | 无权/有权主体访问诊断端点 | 无权拒绝；有权响应不含凭据和不必要内部拓扑 | `E4` |
| `AC-P0B-OBS-005` | MUST | 制造 DEAD、积压、依赖故障和安全拒绝 | 对应指标和告警产生，恢复后状态可确认 | `E5` |

## 11. 公共管理端

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P0B-UI-001` | MUST | 未登录访问受保护路由，登录后返回原路由 | 正确跳转；access Token 只在内存；refresh 仅 Cookie | `E4` |
| `AC-P0B-UI-002` | MUST | 权限不同的用户访问菜单、路由和操作按钮 | UI 按真实权限变化；直接请求仍由后端拒绝 | `E4` |
| `AC-P0B-UI-003` | MUST | 管理页面分别遇到加载、空、错误和成功 | Loading/Empty/Error/Retry/Success 均有可用交互 | `E4` |
| `AC-P0B-UI-004` | MUST | 创建 Agent、角色、标签、配置等 | Scope/Permission/Tool/Tag/字典均来自受控选择，不接受任意 tags 输入 | `E4` |
| `AC-P0B-UI-005` | MUST | 切换 locale 并触发后端错误 | 页面和错误使用稳定 i18nKey 渲染对应语言 | `E4` |
| `AC-P0B-UI-006` | MUST | 运行前端组件测试和浏览器 E2E | 关键表单、权限、Token、路由和页面状态均有自动化证据 | `E2/E4` |

## 12. 当前 V01-V11 不足以领取的场景

按现有 Runbook 描述：

- V05 只覆盖 `AC-P0B-IAM-002` 的一小部分；
- V06/V07/V09/V10/V11 的 401/403 只能覆盖对应拒绝分支的一小部分；
- V08 只覆盖一次登录审计和特定明文口令扫描；
- V04 只证明表存在，不能替代 Migration 升级、约束和业务行为。

因此即使 V01-V11 全部 PASS，本目录绝大多数 MUST 场景仍是未验证状态。这是有意设计：验收结果只能
领取实际断言到的范围。
