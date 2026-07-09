package com.aihub.transfer.api;

import com.aihub.shared.api.CursorPage;
import com.aihub.transfer.application.UploadApplicationService;
import com.aihub.transfer.application.UploadSessionView;
import com.aihub.transfer.domain.StoragePort;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 上传会话 REST 控制器。
 *
 * <p>编排上传会话的创建、Part 预签名、完成与取消。
 */
@RestController
@RequestMapping("/api/v1")
public class UploadController {

    private final UploadApplicationService uploadApplicationService;

    public UploadController(UploadApplicationService uploadApplicationService) {
        this.uploadApplicationService = uploadApplicationService;
    }

    /** 创建上传会话。 */
    @PostMapping("/assets/{assetId}/versions/{versionId}/upload-sessions")
    public UploadSessionView createSession(
            @PathVariable String assetId,
            @PathVariable String versionId,
            @RequestBody CreateSessionRequest body) {
        return uploadApplicationService.createSession(
                assetId, versionId,
                body.totalBytes(), body.fileCount(),
                null /* principalId from security context */);
    }

    /** 查询上传会话详情。 */
    @GetMapping("/upload-sessions/{sessionId}")
    public UploadSessionView getSession(@PathVariable String sessionId) {
        return uploadApplicationService.getSession(sessionId);
    }

    /** 签发 Part 上传预签名 URL。 */
    @GetMapping("/upload-sessions/{sessionId}/parts/{partNumber}/presign")
    public Map<String, String> presignPart(@PathVariable String sessionId,
                                            @PathVariable int partNumber) {
        URL url = uploadApplicationService.presignPartUpload(sessionId, partNumber, null);
        return Map.of("url", url.toString());
    }

    /** 完成上传会话。 */
    @PostMapping("/upload-sessions/{sessionId}/complete")
    public UploadSessionView completeSession(@PathVariable String sessionId,
                                              @RequestBody CompleteSessionRequest body) {
        List<StoragePort.PartInfo> parts = body.parts().stream()
                .map(p -> new StoragePort.PartInfo(p.partNumber(), p.etag()))
                .toList();
        List<UploadApplicationService.FileMetadata> files = body.files() == null
                ? List.of()
                : body.files().stream()
                        .map(f -> new UploadApplicationService.FileMetadata(
                                f.path(), f.sha256(), f.size(), f.mediaType(), f.sampleContent()))
                        .toList();
        return uploadApplicationService.completeSession(sessionId, parts, files, null);
    }

    /** 取消上传会话。 */
    @PostMapping("/upload-sessions/{sessionId}/cancel")
    public UploadSessionView cancelSession(@PathVariable String sessionId) {
        return uploadApplicationService.cancelSession(sessionId, null);
    }

    /** 列出资产下的上传会话。 */
    @GetMapping("/assets/{assetId}/upload-sessions")
    public CursorPage<UploadSessionView> listSessions(
            @PathVariable String assetId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return uploadApplicationService.listSessions(assetId, cursor, limit);
    }

    /** 创建上传会话请求体。 */
    public record CreateSessionRequest(long totalBytes, int fileCount, Integer ttlSeconds) {}

    /** 完成上传会话请求体。 */
    public record CompleteSessionRequest(List<PartEntry> parts, List<FileEntry> files) {}

    /** Part 条目。 */
    public record PartEntry(int partNumber, String etag) {}

    /** 文件元数据条目。 */
    public record FileEntry(String path, String sha256, long size,
                            String mediaType, String sampleContent) {}
}
