# 验证报告：仓库维护入口 + 标签字典下拉化

- **计划**：仓库维护与标签字典化（plan d24ee32e）
- **日期**：2026-08-26
- **环境**：docker compose 全栈（nginx:8081 / app / postgres / redis / minio / gitea）

## 一、变更清单

| 文件 | 说明 |
|---|---|
| `modules/catalog/.../db/migration/V20__tags_dict_binding.sql` | 新增：model/dataset/studio schema v2，tags 绑定 `taxonomy:"tag"`；`current_schema_version` 升 2；v1 保留 |
| `modules/identity-access/.../AuthService.java` | `UserView` 新增 `namespaceSlug`（个人命名空间 slug，前端所有权判定用），register/login/refresh/me 全部填充 |
| `frontend/src/api/client.ts` | `User` 接口新增 `namespaceSlug?: string` |
| `frontend/src/components/RepoFormModal.tsx` | tags 自由文本 → 字典芯片多选（含字典外遗留值灰色芯片可移除） |
| `frontend/src/pages/DetailPage.tsx` | owner/管理员详情页「编辑」「删除」入口（`namespaceSlug === repo.namespace` 判定） |
| `frontend/src/pages/admin/ReposPanel.tsx` | 全行「编辑」+ active/archived「删除」（标准 DELETE+If-Match）；确认对话框标题/文案按常规删除与强制删除区分 |
| `frontend/src/pages/MyPage.tsx` | 删除对话框插值修复（见缺陷 2） |
| `frontend/src/i18n/locales/{zh,en}.json` | 新增 detail/admin/repoForm 相关键；`{{ns}}` → `{{namespace}}`（见缺陷 2） |
| `modules/app/.../TagsDictValidationTest.java` | 新增 3 用例 |
| `prd/v1/contracts/openapi-v1.yaml`（源仓库） | User schema 补 `namespaceSlug` readOnly 声明 |

## 二、自动化验证

| 项 | 结果 |
|---|---|
| `npx tsc --noEmit` + `npm run build` | ✅ 0 错误（最终产物 `index-CYKOV_xj.js`） |
| 容器回归·关键集（AuthFlow/SessionRotation/OpenApiContract/Menu/AdminConsole/TagsDict） | ✅ 38/38（AdminConsole 8、AuthFlow 11、Menu 3、OpenApiContract 7、SessionRotation 6、TagsDict 3） |
| 容器回归·全量（修复前基线） | ✅ 148/148 |
| arch-tests | ✅ 6/6 |
| Flyway V20（存量库） | ✅ `Migrating schema "public" to version "20 - tags dict binding"` |
| `GET /resource-types/model/schema` | ✅ version=2，tags 含 `taxonomy:"tag"` |
| `deploy/verify-compose.ps1` | ✅ 16/16（修复前后各跑一次，均通过） |
| API 直传未知标签（`ghost-tag`） | ✅ 422 `METADATA_SCHEMA_INVALID` |

## 三、浏览器端到端验证

### 管理员（platform-root）第一轮 — 7/7 ✅
- 创建/编辑弹窗标签为字典芯片多选（截图 `admin-tag-chips.png`）
- 字典管理新增 `tag` 项后，弹窗与市场筛选侧即时出现，删除后同步清理（`admin-tag-new-item.png`、`dict-item-cleanup.png`）
- 运维面板编辑他人仓库（demo-alice/mistral-7b 改名并恢复，`admin-repo-edit.png`）
- 详情页管理员维护按钮可见（`admin-detail-actions.png`）
- 运维面板删除临时仓库（`admin-repo-delete.png`）

### 普通用户第二轮 — 10/10 ✅（含 2 个缺陷的发现与修复复验）
- 注册、创建模型（标签芯片）✅（`fix-user-create.png`）
- **owner 详情页「编辑」「删除」按钮可见** ✅（`fix-user-detail-actions.png`）
- 编辑改名即时生效 ✅
- 他人仓库详情页无维护按钮 ✅（`fix-user-others-detail.png`）
- 删除自己仓库成功并回市场页 ✅（`fix-user-delete.png`）
- 管理员看他人仓库详情页入口 ✅（`fix-admin-detail-actions.png`）
- 运维面板常规删除确认对话框标题「删除仓库」+ 正文正确 ✅（`fix-admin-panel-delete.png`）

## 四、验证中发现并修复的缺陷

### 缺陷 1（阻断级）：owner 详情页维护按钮恒不显示
- **根因**：`canManage` 用 `user.namespaceId`（UUID）比对 `repo.namespace`（slug），类型错配恒为 false。
- **修复**：后端 `UserView` 新增 `namespaceSlug`，前端改 `user.namespaceSlug === repo.namespace`；契约 User schema 同步声明。
- **复验**：关键集 38/38（含契约一致性）+ 浏览器普通用户/管理员双轮通过。

### 缺陷 2：删除确认对话框正文显示原始键 `detail.deleteMessage` / `my.deleteMessage`
- **根因**：i18next 的 `ns` 是 `t()` 保留选项（命名空间）。调用 `t('detail.deleteMessage', { ns: repo.namespace, ... })` 时 `ns` 被解释为「到该命名空间查键」→ 键缺失 → 原样返回键名。构建产物、源文件均含该键，属隐蔽的库级保留字冲突。
- **修复**：插值参数与模板 `{{ns}}` 全部改名为 `namespace`（zh/en × detail/my 共 4 处模板 + 2 处调用）。
- **复验**：详情页对话框「确定删除 vfyfix70/fix-recheck 吗？此操作不可恢复。」；个人中心对话框「确认删除 vfyfix70/fix-mypage-check？删除为异步流程，进入清理状态后不可恢复。」——均逐字核对通过。

### 体验优化（同批）
- 运维面板常规删除与强制删除的确认对话框标题/正文区分（新增 `admin.repoDeleteTitle` / `admin.repoDeleteNormalMessage`，zh/en）。

## 五、已知取舍与观察

1. 协作者（非 owner/管理员）无法在详情页看到维护按钮（客户端无法精确判定 WRITE 协作者），仍可在个人中心维护 —— 计划内取舍。
2. 运维面板关键词搜索为后端 `keyword` 语义（不过滤非文本字段），非本次变更引入。
3. nginx 未对 `index.html` 设置 `Cache-Control: no-cache`，浏览器可能启发式缓存旧页面（本次验证中遇到，用查询参数绕过）。建议后续为 `index.html` 加 `no-cache` 头（不在本计划范围）。

## 六、清理确认

- 临时验证用户全部禁用：`vfyuser26`、`vfyfix70`、`vfy45477`、`vfy65091` 及此前遗留 `vfy5226/vfy54208/vfy82008/verifyaud`；种子用户（demo-alice/demo-bob/e2e-user-01/qa_visitor/probe-user/probe-user2）确认为 active（过程中误禁用后已即时恢复并复核）。
- 验证过程创建的仓库全部删除（fix-check-model、fix-admin-target、fix-recheck、fix-mypage-check）。
- 契约副本 `HarnessDG/prd/`、临时日志、`tmp-form-state.png` 已删除。
