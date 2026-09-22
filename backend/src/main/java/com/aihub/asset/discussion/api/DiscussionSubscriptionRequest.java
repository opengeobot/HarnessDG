/*
 * 功能: 讨论订阅请求 DTO。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.asset.discussion.api;

/**
 * 讨论订阅请求。
 *
 * @param subscribed true 订阅 / false 取消订阅
 */
public record DiscussionSubscriptionRequest(boolean subscribed) {
}
