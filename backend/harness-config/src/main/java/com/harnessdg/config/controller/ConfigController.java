/**
 * 功能：系统配置 REST 控制器
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.config.controller;

import com.harnessdg.audit.aspect.AuditAspect.Auditable;
import com.harnessdg.common.page.PageRequest;
import com.harnessdg.common.page.PageResult;
import com.harnessdg.common.response.R;
import com.harnessdg.config.service.ConfigService;
import com.harnessdg.model.config.dto.ConfigCreateRequest;
import com.harnessdg.model.config.dto.ConfigDTO;
import com.harnessdg.model.config.dto.ConfigHistoryDTO;
import com.harnessdg.model.config.dto.ConfigUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @GetMapping
    @PreAuthorize("hasRole('admin')")
    public R<PageResult<ConfigDTO>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String environment,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageRequest pr = new PageRequest();
        pr.setPage(page);
        pr.setPageSize(pageSize);
        return R.ok(configService.listConfigs(category, keyword, environment, pr));
    }

    @GetMapping("/{key}")
    @PreAuthorize("hasRole('admin')")
    public R<ConfigDTO> get(@PathVariable String key) {
        return R.ok(configService.getConfig(key));
    }

    @PostMapping
    @PreAuthorize("hasRole('admin')")
    @Auditable(action = "create", resourceType = "config")
    public R<ConfigDTO> create(@Valid @RequestBody ConfigCreateRequest request) {
        return R.ok(configService.createConfig(request));
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasRole('admin')")
    @Auditable(action = "update", resourceType = "config")
    public R<ConfigDTO> update(@PathVariable String key, @Valid @RequestBody ConfigUpdateRequest request) {
        return R.ok(configService.updateConfig(key, request));
    }

    @DeleteMapping("/{key}")
    @PreAuthorize("hasRole('admin')")
    @Auditable(action = "delete", resourceType = "config")
    public R<Void> delete(@PathVariable String key) {
        configService.deleteConfig(key);
        return R.ok();
    }

    @GetMapping("/{key}/history")
    @PreAuthorize("hasRole('admin')")
    public R<List<ConfigHistoryDTO>> history(@PathVariable String key) {
        return R.ok(configService.getHistory(key));
    }
}
