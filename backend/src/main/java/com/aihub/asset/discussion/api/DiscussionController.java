package com.aihub.asset.discussion.api;

import com.aihub.asset.api.AssetApiContext;
import com.aihub.asset.discussion.application.DiscussionApplicationService;
import com.aihub.asset.discussion.application.ThreadView;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.api.CursorPage;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 讨论 REST 适配器。
 *
 * <p>提供资产的讨论线程/评论 CRUD 端点，权限继承资产授权。
 */
@RestController
@RequestMapping("/api/v1/assets/{assetId}/discussions")
public class DiscussionController {

    private final DiscussionApplicationService discussionService;

    public DiscussionController(DiscussionApplicationService discussionService) {
        this.discussionService = discussionService;
    }

    /** 订阅或取消订阅资产讨论通知。 */
    @PostMapping("/subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateSubscription(@PathVariable String assetId,
                                     @RequestBody DiscussionSubscriptionRequest request) {
        String principalId = AssetApiContext.principalId();
        if (request.subscribed()) {
            discussionService.subscribe(assetId, principalId);
        } else {
            discussionService.unsubscribe(assetId, principalId);
        }
    }

    /** 查询当前主体是否已订阅资产讨论。 */
    @GetMapping("/subscription")
    public ApiResponse<Boolean> getSubscription(@PathVariable String assetId) {
        return AssetApiContext.respond(
                discussionService.isSubscribed(assetId, AssetApiContext.principalId()));
    }

    /** 创建讨论线程。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ThreadView> createThread(@PathVariable String assetId,
                                                @RequestBody CreateThreadRequest request) {
        return AssetApiContext.respond(
                discussionService.createThread(assetId, request.title(),
                        AssetApiContext.principalId()));
    }

    /** 列出资产下的讨论线程。 */
    @GetMapping
    public ApiResponse<CursorPage<ThreadView>> listThreads(
            @PathVariable String assetId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        return AssetApiContext.respond(
                discussionService.listThreads(assetId, cursor, limit));
    }
}
