package com.aihub.version.api;

import com.aihub.version.application.DvcConfigurationService;
import com.aihub.version.application.DvcConfigurationService.DvcCredentials;
import com.aihub.version.application.DvcConfigurationService.DvcRemoteConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DVC 配置 REST 控制器。
 *
 * <p>提供 DVC Remote 配置生成与短期凭据签发接口，供客户端执行 dvc pull/push。
 */
@RestController
@RequestMapping("/api/v1/assets/{assetId}/dvc")
public class DvcConfigurationController {

    private final DvcConfigurationService dvcConfigurationService;

    public DvcConfigurationController(DvcConfigurationService dvcConfigurationService) {
        this.dvcConfigurationService = dvcConfigurationService;
    }

    /**
     * 获取 DVC Remote 配置。
     *
     * @param assetId 资产 ID
     * @return DVC Remote 配置视图
     */
    @GetMapping("/config")
    public DvcRemoteConfig getRemoteConfig(@PathVariable String assetId) {
        return dvcConfigurationService.generateRemoteConfig(assetId);
    }

    /**
     * 签发短期 DVC 凭据。
     *
     * @param assetId 资产 ID
     * @return DVC 短期凭据视图
     */
    @GetMapping("/credentials")
    public DvcCredentials getCredentials(@PathVariable String assetId) {
        return dvcConfigurationService.issueCredentials(assetId);
    }
}
