/**
 * 功能: 权限相关类型 (Scope 定义)，对齐设计文档第 5.5、11.1 节。
 *       Scope 即后端权限编码 resource:action（如 user:manage），由真实会话下发；
 *       安全判断以后端为准，前端仅做按钮/路由体验提示。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */

/**
 * 平台权限 Scope，采用后端权限编码 resource:action 形态。
 * 使用模板字面量类型保留结构约束，同时兼容后端动态下发的权限集合。
 */
export type Scope = `${string}:${string}`;
