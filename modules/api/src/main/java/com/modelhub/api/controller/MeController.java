package com.modelhub.api.controller;

import com.modelhub.api.support.Principals;
import com.modelhub.catalog.service.GatedAccessService;
import com.modelhub.catalog.service.GatedAccessService.AccessRequestView;
import com.modelhub.shared.paging.CursorQuery;
import com.modelhub.shared.paging.CursorResult;
import com.modelhub.shared.web.ApiEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 个人中心端点（04 §7）：当前阶段仅 gated 申请列表，/me/repositories 由 interaction 阶段扩展。 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    /** cursor 响应 data：items + nextCursor。 */
    public record MyAccessRequestPageData(List<AccessRequestView> items, String nextCursor) {}

    private final GatedAccessService gated;

    public MeController(GatedAccessService gated) {
        this.gated = gated;
    }

    @GetMapping("/access-requests")
    public ApiEnvelope<MyAccessRequestPageData> myAccessRequests(@RequestParam Map<String, String> params,
                                                                 HttpServletRequest request) {
        CursorQuery cursor = CursorQuery.from(new HashMap<>(params));
        CursorResult<AccessRequestView> result =
                gated.listMine(Principals.requireCurrent(request), cursor, params.get("status"));
        return ApiEnvelope.ok(new MyAccessRequestPageData(result.items(), result.nextCursor()));
    }
}
