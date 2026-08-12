package com.modelhub.api.controller;

import com.modelhub.api.dto.RepoRequests.AddCollaboratorRequest;
import com.modelhub.api.dto.RepoRequests.UpdateCollaboratorRequest;
import com.modelhub.api.support.Principals;
import com.modelhub.catalog.service.CollaboratorService;
import com.modelhub.catalog.service.CollaboratorService.CollaboratorView;
import com.modelhub.shared.paging.CursorQuery;
import com.modelhub.shared.paging.CursorResult;
import com.modelhub.shared.web.ApiEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 协作者端点（04 §4.3、02 §3.3）：cursor 列表 + 显式授权管理。 */
@RestController
@RequestMapping("/api/v1/repositories/{repoId}/collaborators")
public class CollaboratorsController {

    /** cursor 响应 data：items + nextCursor。 */
    public record CollaboratorPageData(List<CollaboratorView> items, String nextCursor) {}

    private final CollaboratorService collaborators;

    public CollaboratorsController(CollaboratorService collaborators) {
        this.collaborators = collaborators;
    }

    @GetMapping
    public ApiEnvelope<CollaboratorPageData> list(@PathVariable UUID repoId,
                                                  @RequestParam Map<String, String> params,
                                                  HttpServletRequest request) {
        CursorQuery cursor = CursorQuery.from(new HashMap<>(params));
        CursorResult<CollaboratorView> result =
                collaborators.list(Principals.requireCurrent(request), repoId, cursor);
        return ApiEnvelope.ok(new CollaboratorPageData(result.items(), result.nextCursor()));
    }

    @PostMapping
    public ResponseEntity<ApiEnvelope<CollaboratorView>> add(@PathVariable UUID repoId,
                                                             @Valid @RequestBody AddCollaboratorRequest body,
                                                             HttpServletRequest request) {
        CollaboratorView view = collaborators.add(Principals.requireCurrent(request), repoId,
                body.subjectType(), body.subjectId(), body.role(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.created(view));
    }

    @PatchMapping("/{subjectId}")
    public ApiEnvelope<CollaboratorView> update(@PathVariable UUID repoId, @PathVariable UUID subjectId,
                                                @Valid @RequestBody UpdateCollaboratorRequest body,
                                                @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                HttpServletRequest request) {
        return ApiEnvelope.ok(collaborators.update(Principals.requireCurrent(request), repoId,
                subjectId, body.role(), null, ifMatch));
    }

    @DeleteMapping("/{subjectId}")
    public ResponseEntity<Void> remove(@PathVariable UUID repoId, @PathVariable UUID subjectId,
                                       @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                       HttpServletRequest request) {
        collaborators.remove(Principals.requireCurrent(request), repoId, subjectId, ifMatch);
        return ResponseEntity.noContent().build();
    }
}
