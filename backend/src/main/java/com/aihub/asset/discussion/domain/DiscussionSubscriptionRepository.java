/*
 * 功能: 资产讨论订阅仓储端口，约定订阅关系的持久化与查询。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.asset.discussion.domain;

import java.util.Set;

/**
 * 资产讨论订阅仓储端口。
 */
public interface DiscussionSubscriptionRepository {

    /** 查询资产的全部订阅主体 ID。 */
    Set<String> findSubscribers(String assetId);

    /** 判断主体是否已订阅资产讨论。 */
    boolean isSubscribed(String assetId, String principalId);

    /** 订阅资产讨论通知。 */
    void subscribe(String assetId, String principalId);

    /** 取消订阅资产讨论通知。 */
    void unsubscribe(String assetId, String principalId);
}
