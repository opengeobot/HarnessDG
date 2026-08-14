package com.modelhub.api.controller;

import com.modelhub.api.support.Principals;
import com.modelhub.catalog.service.CatalogService;
import com.modelhub.catalog.service.GatedAccessService;
import com.modelhub.catalog.service.GatedAccessService.AccessRequestView;
import com.modelhub.catalog.service.InteractionService;
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

/** 个人中心端点（04 §7/§5）：gated 申请列表与 /me/repositories 个人仓库 tab 列表。 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

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
        CursorQuery cursor = CursorQuery.from(new HashMap<>(params));
        CursorResult<AccessRequestView> result =
                gated.listMine(Principals.requireCurrent(request), cursor, params.get("status"));
        return ApiEnvelope.ok(new MyAccessRequestPageData(result.items(), result.nextCursor()));
    }

    /** 个人仓库列表（04 §5 /me/repositories）：tab ∈ {created, likes, favorites}，页码分页。 */
    @GetMapping("/repositories")
    public ApiEnvelope<PageResult<CatalogService.RepoView>> myRepositories(
            @RequestParam Map<String, String> params, HttpServletRequest request) {
        String tab = params.getOrDefault("tab", "created");
        PageQuery page = PageQuery.from(params);
        return ApiEnvelope.ok(interactions.listMine(Principals.requireCurrent(request), tab, page));
    }
}
