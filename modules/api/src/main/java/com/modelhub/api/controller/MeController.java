package com.modelhub.api.controller;

import com.modelhub.api.support.Principals;
import com.modelhub.catalog.service.CatalogService;
import com.modelhub.catalog.service.GatedAccessService;
import com.modelhub.catalog.service.GatedAccessService.AccessRequestView;
import com.modelhub.catalog.service.InteractionService;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.modelhub.shared.paging.CursorQuery;
import com.modelhub.shared.paging.CursorResult;
import com.modelhub.shared.paging.PageQuery;
import com.modelhub.shared.paging.PageResult;
import com.modelhub.shared.web.ApiEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 个人中心端点（04 §7/§5）：gated 申请列表与 /me/repositories 个人仓库 tab 列表。 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    /** 契约 /me/access-requests status 过滤枚举（AccessRequest.status；withdrawn 已移除）。 */
    private static final Set<String> ACCESS_REQUEST_STATUSES =
            Set.of("pending", "approved", "rejected", "revoked", "expired");

    /** 契约 typeKey 格式（与 /resource-types/{typeKey} 同 pattern）。 */
    private static final java.util.regex.Pattern TYPE_KEY_PATTERN =
            java.util.regex.Pattern.compile("^[a-z][a-z0-9_-]{1,63}$");

    /** cursor 响应 data：items + nextCursor。 */
    public record MyAccessRequestPageData(List<AccessRequestView> items, String nextCursor) {}

    private final GatedAccessService gated;
    private final InteractionService interactions;

    public MeController(GatedAccessService gated, InteractionService interactions) {
        this.gated = gated;
        this.interactions = interactions;
    }

    @GetMapping("/access-requests")
    public ApiEnvelope<MyAccessRequestPageData> myAccessRequests(@RequestParam Map<String, String> params,
                                                                 HttpServletRequest request) {
        String status = params.get("status");
        if (status != null && !status.isBlank() && !ACCESS_REQUEST_STATUSES.contains(status)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "非法 status 过滤值: " + status,
                    List.of(new ApiException.Detail("status", "invalid_value")));
        }
        CursorQuery cursor = CursorQuery.from(new HashMap<>(params));
        CursorResult<AccessRequestView> result =
                gated.listMine(Principals.requireCurrent(request), cursor, status);
        return ApiEnvelope.ok(new MyAccessRequestPageData(result.items(), result.nextCursor()));
    }

    /** 个人仓库列表（04 §5 /me/repositories）：tab ∈ {created, likes, favorites}，
     *  可选 type 按资源类型过滤，页码分页。 */
    @GetMapping("/repositories")
    public ApiEnvelope<PageResult<CatalogService.RepoView>> myRepositories(
            @RequestParam Map<String, String> params, HttpServletRequest request) {
        String tab = params.getOrDefault("tab", "created");
        String type = params.get("type");
        if (type != null && !type.isBlank() && !TYPE_KEY_PATTERN.matcher(type).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "非法 type 过滤值: " + type,
                    List.of(new ApiException.Detail("type", "invalid_format")));
        }
        PageQuery page = PageQuery.from(params);
        return ApiEnvelope.ok(interactions.listMine(Principals.requireCurrent(request), tab, type, page));
    }
}
