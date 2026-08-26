# 字典统一 + 标准 RBAC 菜单权限重构 验证报告

- 日期：2026-08-26
- 依据规格：`prd/v1/specs/02-identity-permissions.md`、`03-domain-data-model.md`、`04-api-contract.md`、`prd/v1/contracts/openapi-v1.yaml`
- 构建证据：
  - 容器全量回归 `mvn -pl modules/app -am clean test`：**145 tests，0 failures，BUILD SUCCESS**
  - `modules/arch-tests` ArchitectureTest：**6/6 绿**
  - 前端 `npm run build`（tsc -b + vite）：**零错误**
  - compose 多阶段镜像重建成功；Flyway 在存量生产库上 **V17 → V18（unify dict）→ V19（sys menu）** 迁移成功（`Successfully applied 2 migrations ... now at version v19`）

## 1. 交付物清单

| 类别 | 内容 |
|---|---|
| 迁移 | V18__unify_dict：`sys_dict_item.parent_id` 两级树；9 个市场分类（V2/V4/V5 taxonomy 种子）全量迁入 `sys_dict`/`sys_dict_item`（`tv_map` 映射表回填层级）；`model_profiles`/`dataset_profiles` 四个外键动态摘除并换算指向 `sys_dict_item`（含 RAISE 自检）；DROP `taxonomy_values`/`taxonomies`。V19__sys_menu：`sys_menu` 表（directory/menu/button + permission_code 外键）+ 4 个新权限点（`admin:menu:view/manage`、`admin:repo:view/manage`）+ `admin_console` 目录与 8 个种子菜单 + platform_admin/auditor 授权 |
| 服务 | `SysMenuService`（menuTreeOf 权限过滤、CRUD 守卫：权限码存在性/父级须 directory/内置目录禁删/有子禁删/ETag 乐观锁/审计留痕）；`SysDictService` 扩展 `parentItemValue`（同字典父项、两级封顶、回根级语义）；`MetadataOptionsService`/`MetadataValidator`/`ProfileProjector` 三消费点切换到字典；`CatalogService.adminRetry/adminDelete` 收敛为 `admin:repo:manage` 权限点 |
| API | `GET /api/v1/me/menus`（登录态，权限派生可见菜单）、`GET/POST /api/v1/admin/menus`、`PATCH/DELETE /api/v1/admin/menus/{code}`、`{code}:disable/:enable`；`/auth/me` 的 UserView 透出 `permissions` |
| 契约 | openapi-v1.yaml：TaxonomyOption +`displayNameEn`、User +`permissions`、DictItem 系 +`parentItemValue`、5 个新 paths、SysMenu/VisibleMenu 等 7 个新 schema；契约测试 `implemented` 列表同步 |
| 前端 | client.ts 菜单 API 段 + 类型；AdminPage 菜单驱动重构（`/me/menus` → path→panel 映射，无权限守卫文案）；MenusPanel 新面板（树表/类型徽章/弹窗表单/启停用/删除）；DictsPanel 层级化（`└─` 缩进 + 父项下拉）；FilterSidebar/RepoFormModal active 过滤 + 双语择显（`labelOf`）；i18n zh/en 各 +22 键（`adminMenu.*` 节 + `parentItem`/`rootItem`/`menuMenus`） |
| 测试 | `DictHierarchyTests`（层级守卫/市场 options 迁移数据/投影外键换算，3）、`MenuTests`（权限派生/端点鉴权/CRUD 守卫+审计，3）、`AuthFlowTests` permissions 断言、契约一致性测试追加 5 paths |

## 2. 自动化测试

| 集合 | 结果 |
|---|---|
| 关键集（DictHierarchy/Menu/AdminConsole/AuthFlow/Contract/CatalogIntegration） | 46 绿（MenuTests 修正一处断言后复跑 3/3） |
| 全量回归 `modules/app -am` | 145/145 |
| ArchitectureTest（catalog→identity 正向依赖等 6 规则） | 6/6 |

修正记录：`MenuTests.meMenusDerivedFromGrantedPermissions` 初版误断言「auditor 不应见用户管理」；实际 V16 即授予 `platform_auditor` `admin:user:view`（只读管理面语义），测试断言已改为与真实授权一致（可见 overview/users/audit/repos，不可见 roles/dicts/menus）。

## 3. 生产栈迁移与浏览器验证

Flyway V18 在存量库（已有 V2~V17 数据）上真实执行：分类迁移 + profile 外键换算 + DROP taxonomy 全部成功，自检（四列非空引用全部落在 `sys_dict_item`）通过。

浏览器（Playwright）验证，截图见 `docs/verification/`：

| 场景 | 结果 | 截图 |
|---|---|---|
| 中文市场侧边栏 | 8 组（任务/框架/开源协议/结构/语种/标签/能力/组织）；任务树两级层级（5 根类：多模态(26)/NLP(21)/语音(19)/CV(15)/科学计算(2)） | dict-market-zh.png |
| 英文界面择显 | 分组标题与 framework/license 选项英文（PyTorch、MIT License…） | dict-market-en.png |
| 管理后台菜单驱动 | `/me/menus` 渲染 8 项（概览/用户/角色/字典/审计/仓库/组织/菜单）；菜单管理面板树表正常 | admin-menu-driven.png |
| 字典列表 | 10 字典 = 9 市场字典（model_task 88 项、dataset_task 68 项…）+ sys_user_status | admin-dicts-list.png |
| 字典层级 CRUD | 根项/子项创建、`└─` 缩进、父项回显与回根级编辑、删除清理全通过 | dict-item-create-root/child.png、dict-item-cleanup.png |
| 菜单 CRUD | 创建（父级仅 directory、权限码下拉）/停用/启用/编辑/删除全通过 | menu-create.png、menu-cleanup.png |
| auditor 菜单收敛 | verifyaud（platform_auditor）见 概览/用户/审计/仓库/组织，**无** 角色/字典/菜单管理 | admin-auditor-menus.png |

说明：
1. 「组织管理」对 auditor 可见属预期——该菜单挂 `admin:system:view`，V16 即授 auditor。
2. model_task/language 等字典项英文标签与中文相同：源 `taxonomy_values` 仅单列中文名，V18 按计划 `label_zh=label_en=display_name` 迁移；后续可经字典管理界面维护英文标签（功能已就绪）。
3. 验证临时数据已全部清理（字典项、菜单、验证用户 verifyaud 已禁用）；测试容器与临时契约副本（`HarnessDG/prd`）已删除。
