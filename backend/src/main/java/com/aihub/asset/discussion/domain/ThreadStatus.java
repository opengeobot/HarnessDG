package com.aihub.asset.discussion.domain;

/** 讨论线程状态。 */
public enum ThreadStatus {
    /** 开放：可回复。 */
    OPEN,
    /** 锁定：Moderator 锁定，不可回复。 */
    LOCKED,
    /** 关闭：已关闭。 */
    CLOSED
}
