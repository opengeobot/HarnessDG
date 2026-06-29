/*
 * 功能: 偏移分页结果，适用于小型后台字典与角色等低增长列表。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.shared.api;

import java.util.List;

/**
 * 偏移分页结果。
 *
 * <p>面向小型后台列表（字典、角色），提供总量与页码信息以便前端渲染分页器。
 * 高增长列表应改用 {@link CursorPage}。
 *
 * @param items    当前页数据
 * @param total    匹配的总记录数
 * @param page     当前页码（从 1 开始）
 * @param pageSize 每页大小
 * @param <T>      元素类型
 */
public record PageResult<T>(List<T> items, long total, int page, int pageSize) {
}
