/**
 * 功能: 权限相关类型 (Scope 定义)，对齐设计文档第 11.1 节
 * 时间: 2026-06-29
 * 作者: AxeXie
 */

/** 平台权限 Scope，安全判断以后端为准，前端仅做按钮/路由提示 */
export type Scope =
  | 'asset:read'
  | 'asset:preview'
  | 'asset:download'
  | 'asset:write'
  | 'asset:submit'
  | 'asset:publish'
  | 'asset:admin';
