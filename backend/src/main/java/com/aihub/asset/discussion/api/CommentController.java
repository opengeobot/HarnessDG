package com.aihub.asset.discussion.api;

import com.aihub.asset.api.AssetApiContext;
import com.aihub.asset.discussion.application.CommentView;
import com.aihub.asset.discussion.application.DiscussionApplicationService;
import com.aihub.asset.discussion.application.ThreadView;
import com.aihub.shared.api.ApiResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 线程下的评论 REST 适配器。
 */
@RestController
@RequestMapping("/api/v1/discussions/{threadId}")
public class CommentController {

    private final DiscussionApplicationService discussionService;

    public CommentController(DiscussionApplicationService discussionService) {
        this.discussionService = discussionService;
    }

    /** 发表评论。 */
    @PostMapping("/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentView> createComment(@PathVariable String threadId,
                                                  @RequestBody CreateCommentRequest request) {
        return AssetApiContext.respond(
                discussionService.createComment(threadId, request.parentId(), request.body(),
                        AssetApiContext.principalId()));
    }

    /** 读取线程下的评论列表。 */
    @GetMapping("/comments")
    public ApiResponse<List<CommentView>> listComments(
            @PathVariable String threadId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        return AssetApiContext.respond(
                discussionService.listComments(threadId, cursor, limit));
    }

    /** 编辑评论（作者）。 */
    @PatchMapping("/comments/{commentId}")
    public ApiResponse<CommentView> editComment(@PathVariable String threadId,
                                                @PathVariable String commentId,
                                                @RequestBody EditCommentRequest request) {
        return AssetApiContext.respond(
                discussionService.editComment(commentId, request.body(),
                        AssetApiContext.principalId()));
    }

    /** 撤回评论（作者）。 */
    @PostMapping("/comments/{commentId}/retract")
    public ApiResponse<CommentView> retractComment(@PathVariable String threadId,
                                                   @PathVariable String commentId) {
        return AssetApiContext.respond(
                discussionService.retractComment(commentId, AssetApiContext.principalId()));
    }

    /** 隐藏评论（Moderator）。 */
    @PostMapping("/comments/{commentId}/hide")
    public ApiResponse<CommentView> hideComment(@PathVariable String threadId,
                                                @PathVariable String commentId) {
        return AssetApiContext.respond(
                discussionService.hideComment(commentId, AssetApiContext.principalId()));
    }

    /** 恢复评论（Moderator）。 */
    @PostMapping("/comments/{commentId}/unhide")
    public ApiResponse<CommentView> unhideComment(@PathVariable String threadId,
                                                  @PathVariable String commentId) {
        return AssetApiContext.respond(
                discussionService.unhideComment(commentId, AssetApiContext.principalId()));
    }

    /** 锁定线程（Moderator）。 */
    @PostMapping("/lock")
    public ApiResponse<ThreadView> lockThread(@PathVariable String threadId) {
        return AssetApiContext.respond(
                discussionService.lockThread(threadId, AssetApiContext.principalId()));
    }

    /** 解锁线程（Moderator）。 */
    @PostMapping("/unlock")
    public ApiResponse<ThreadView> unlockThread(@PathVariable String threadId) {
        return AssetApiContext.respond(
                discussionService.unlockThread(threadId, AssetApiContext.principalId()));
    }
}
