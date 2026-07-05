package com.aihub.asset.discussion.api;

/** 创建评论请求。 */
public record CreateCommentRequest(String parentId, String body) {
}
