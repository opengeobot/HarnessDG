# PAGE-<domain>-<number>：<页面名称>

> 状态：`DRAFT`

## 1. 页面目的

- Route：
- 所属 Journey：
- Actor/Persona：
- 最小 Permission：
- 资源级策略：
- 非目标：

## 2. 入口与离开

| 来源 | 前置上下文 | 返回/完成位置 | 保留状态 |
| --- | --- | --- | --- |
|  |  |  |  |

定义未登录、无权、资源不存在和失效链接行为。

## 3. 数据契约

| UI 区域 | Query/Mutation | 请求字段 | 响应字段 | 缓存 Key | 刷新/失效 |
| --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  |

所有字段引用 OpenAPI 生成类型；页面不得声明重复 DTO。

## 4. 布局与组件

用小型线框或区域表说明层级：

| 区域 | 内容 | 可见条件 | 交互 |
| --- | --- | --- | --- |
| Header |  |  |  |
| Filters |  |  |  |
| Main |  |  |  |
| Actions |  |  |  |
| Detail/Drawer |  |  |  |

## 5. 字段规格

| 字段 | 类型/控件 | 必填 | 来源 | 校验 | 权限 | i18nKey |
| --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  |  |

受治理字段必须说明 itemCode/tagId/Principal/Team/Tool Catalog 来源，不得使用任意 tags 输入。

## 6. 状态

| 状态 | 触发 | 展示 | 可执行动作 |
| --- | --- | --- | --- |
| Loading |  |  |  |
| Empty |  |  |  |
| Filter Empty |  |  |  |
| Error Retryable |  |  |  |
| Error Non-retryable |  |  |  |
| Unauthorized |  |  |  |
| Conflict |  |  |  |
| Success |  |  |  |

## 7. 操作

| Action ID | 按钮/入口 | Permission/前置状态 | 确认 | Idempotency | 成功 | 失败 |
| --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  |  |

## 8. 安全与隐私

- Token/credential/预签名 URL：
- 日志/埋点/错误上报脱敏：
- 防资源枚举：
- 剪贴板：
- 缓存清理：

## 9. 国际化与可访问性

- zh-CN 文案键：
- en-US 文案键：
- 日期/数字/大小格式：
- 键盘顺序：
- 焦点管理：
- ARIA/label：
- 非颜色状态表达：

## 10. 验收

| AC ID | 类型 | Given | When | Then | 证据 |
| --- | --- | --- | --- | --- | --- |
|  | NORMAL |  |  |  | E2/E4 |
|  | EMPTY |  |  |  | E2/E4 |
|  | FAILURE |  |  |  | E2/E4 |
|  | UNAUTHORIZED |  |  |  | E4 |
|  | CONFLICT |  |  |  | E4 |

附目标视口截图、浏览器版本和 Evidence Manifest。
