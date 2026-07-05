package com.aihub.asset.discussion.domain;

/** 评论状态。 */
public enum CommentStatus {
    /** 可见。 */
    VISIBLE,
    /** 被 Moderator 隐藏。 */
    HIDDEN,
    /** 被作者撤回。 */
    RETRACTED
}
