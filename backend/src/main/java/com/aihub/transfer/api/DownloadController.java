package com.aihub.transfer.api;

import com.aihub.transfer.application.DownloadApplicationService;
import com.aihub.transfer.application.DownloadApplicationService.DownloadTicket;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 下载票据 REST 控制器。
 *
 * <p>按权限/状态签发下载票据，支持单工件 PRESIGNED_URL 与整版 GIT_DVC 方法。
 */
@RestController
@RequestMapping("/api/v1/versions/{versionId}")
public class DownloadController {

    private final DownloadApplicationService downloadApplicationService;

    public DownloadController(DownloadApplicationService downloadApplicationService) {
        this.downloadApplicationService = downloadApplicationService;
    }

    /**
     * 签发下载票据。
     *
     * @param versionId  版本 ID
     * @param artifactId 工件 ID（可空）
     * @return 下载票据视图
     */
    @GetMapping("/download")
    public DownloadTicket issueDownloadTicket(@PathVariable String versionId,
                                              @RequestParam(required = false) String artifactId) {
        return downloadApplicationService.issueTicket(versionId, artifactId);
    }
}
