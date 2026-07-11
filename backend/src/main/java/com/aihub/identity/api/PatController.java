/*
 * 功能: PAT REST 适配器——创建、列出与吊销个人访问令牌。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.identity.api;

import com.aihub.identity.application.CreatedPatView;
import com.aihub.identity.application.PatApplicationService;
import com.aihub.identity.application.PatView;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * PAT REST 控制器。
 */
@RestController
@RequestMapping("/api/v1/tokens")
public class PatController {

    private final PatApplicationService patService;

    public PatController(PatApplicationService patService) {
        this.patService = patService;
    }

    /** 创建 PAT（完整 Token 仅返回一次）。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedPatView createPat(@RequestBody CreatePatRequest request) {
        return patService.createPat(request.name(), request.scopes(), request.ttlDays());
    }

    /** 列出当前用户的 PAT。 */
    @GetMapping
    public List<PatView> listPats() {
        return patService.listOwnPats();
    }

    /** 吊销 PAT。 */
    @DeleteMapping("/{tokenId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokePat(@PathVariable String tokenId) {
        patService.revokePat(tokenId);
    }

    /** 创建 PAT 请求体。 */
    public record CreatePatRequest(String name, Set<String> scopes, Integer ttlDays) {}
}
