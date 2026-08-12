package com.modelhub.artifact.storage;

import com.modelhub.artifact.config.ArtifactProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchUploadException;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 对象存储操作封装（05 §2/§6）：服务端一律走 internal endpoint；
 * 预签名 URL 由 S3Presigner 以 public base URL 签发。
 * Multipart 初始化幂等：按稳定 object key 认领已有 upload（05 §6.1 第 7 条）。
 */
@Component
public class ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);

    /** Provider 确认真实已上传的分片（05 §6.2：upload_parts 投影不得比 ListParts 更权威）。 */
    public record ProviderPart(int partNumber, long sizeBytes, String etag) {}

    public record PresignedPart(String url, OffsetDateTime expiresAt) {}

    private final S3Client s3;
    private final S3Presigner presigner;
    private final ArtifactProperties props;

    public ObjectStorageService(S3Client artifactS3Client, S3Presigner artifactS3Presigner,
                                ArtifactProperties props) {
        this.s3 = artifactS3Client;
        this.presigner = artifactS3Presigner;
        this.props = props;
    }

    /** 幂等初始化 Multipart：先按 objectKey 认领已有 upload，再创建新的（05 §6.1）。 */
    public String initMultipart(String objectKey, String contentType) {
        var list = s3.listMultipartUploads(r -> r.bucket(props.getBucket()).prefix(objectKey));
        for (var upload : list.uploads()) {
            if (objectKey.equals(upload.key())) {
                log.info("认领已有 Multipart: key={} uploadId={}", objectKey, upload.uploadId());
                return upload.uploadId();
            }
        }
        var resp = s3.createMultipartUpload(r -> r.bucket(props.getBucket()).key(objectKey)
                .contentType(contentType == null ? "application/octet-stream" : contentType));
        return resp.uploadId();
    }

    private static final String SIGV4_ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";
    private static final String SIGNED_HEADERS = "host;x-amz-content-sha256";
    private static final DateTimeFormatter SIGV4_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    /**
     * 签发分片上传 URL（public base URL，短期有效，05 §11）。
     * UploadPart 强制 x-amz-content-sha256 头参与签名（MinIO 要求，aws cli 同款）。
     * 自实现 SigV4 预签名：AWS SDK 2.40.x 的 Aws4Signer.presign 在 canonical request 计算
     * 之后才追加 X-Amz-* 查询参数（签名未覆盖这些参数），而 MinIO 校验时 canonical query
     * 必须包含全部 X-Amz-*，故 SDK 输出恒被拒（403 SignatureDoesNotMatch，实测验证）；
     * 本实现严格对齐 MinIO doesPresignedSignatureMatch（canonical query 含 X-Amz-*，
     * 按 Go url.Values 排序与编码），探针端到端 PUT 200 验证通过。
     * 注意：X-Amz-Expires 不得超 604800s（SigV4 上限，MinIO 强制）；调用方 ttl 为 15min。
     */
    public PresignedPart presignUploadPart(String objectKey, String uploadId, int partNumber, Duration ttl) {
        URI base = URI.create(props.getPublicBaseUrl());
        String basePath = base.getPath();
        String encodedObjectPath = (basePath == null || basePath.isBlank() || "/".equals(basePath)
                ? "" : basePath.replaceAll("/+$", ""))
                + "/" + props.getBucket() + "/" + encodePath(objectKey);
        String host = base.getHost() + (base.getPort() > 0 ? ":" + base.getPort() : "");
        Instant now = Instant.now();
        String amzDate = SIGV4_DATE_TIME.format(now);
        String scope = amzDate.substring(0, 8) + "/" + props.getRegion() + "/s3/aws4_request";

        TreeMap<String, String> query = new TreeMap<>();
        query.put("partNumber", String.valueOf(partNumber));
        query.put("uploadId", uploadId);
        query.put("X-Amz-Algorithm", SIGV4_ALGORITHM);
        query.put("X-Amz-Credential", props.getAccessKey() + "/" + scope);
        query.put("X-Amz-Date", amzDate);
        query.put("X-Amz-Expires", String.valueOf(ttl.toSeconds()));
        query.put("X-Amz-SignedHeaders", SIGNED_HEADERS);
        String canonicalQuery = query.entrySet().stream()
                .map(e -> goEscape(e.getKey()) + "=" + goEscape(e.getValue()))
                .collect(Collectors.joining("&"))
                .replace("+", "%20");

        String canonicalHeaders = "host:" + host + "\n"
                + "x-amz-content-sha256:" + UNSIGNED_PAYLOAD + "\n";
        String canonicalRequest = "PUT\n" + encodedObjectPath + "\n" + canonicalQuery + "\n"
                + canonicalHeaders + "\n" + SIGNED_HEADERS + "\n" + UNSIGNED_PAYLOAD;
        String stringToSign = SIGV4_ALGORITHM + "\n" + amzDate + "\n" + scope + "\n"
                + sha256Hex(canonicalRequest);
        String signature = HexFormat.of().formatHex(
                hmacSha256(deriveSigningKey(props.getSecretKey(), scope), stringToSign));

        String url = props.getPublicBaseUrl().replaceAll("/+$", "") + encodedObjectPath
                + "?" + canonicalQuery + "&X-Amz-Signature=" + signature;
        return new PresignedPart(url, OffsetDateTime.now(ZoneOffset.UTC).plus(ttl));
    }

    /** Go net/url QueryEscape：unreserved（-_.~）不转义，空格→+，其余 %XX 大写。 */
    private static String goEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append((char) c);
            } else if (c == ' ') {
                sb.append('+');
            } else {
                sb.append('%').append(String.format("%02X", c));
            }
        }
        return sb.toString();
    }

    /** SigV4 签名密钥链：AWS4{secret}→date→region→service→aws4_request。 */
    private static byte[] deriveSigningKey(String secretKey, String scope) {
        String[] p = scope.split("/");
        byte[] k = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), p[0]);
        k = hmacSha256(k, p[1]);
        k = hmacSha256(k, p[2]);
        return hmacSha256(k, "aws4_request");
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 不可用", e);
        }
    }

    private static String sha256Hex(String data) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** S3 对象键路径段百分号编码（段内保留字符与空格均转义，%2F 保持分隔语义）。 */
    private static String encodePath(String key) {
        return Arrays.stream(key.split("/", -1))
                .map(seg -> URLEncoder.encode(seg, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }

    /** 签发对象下载 URL（授权成功后调用，05 §11）。 */
    public String presignGetObject(String objectKey, Duration ttl) {
        GetObjectPresignRequest req = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(b -> b.bucket(props.getBucket()).key(objectKey))
                .build();
        PresignedGetObjectRequest signed = presigner.presignGetObject(req);
        return signed.url().toString();
    }

    /** ListParts 全量拉取：完成前校验真实分片与总大小的唯一依据（05 §6.3）。 */
    public List<ProviderPart> listParts(String objectKey, String uploadId) {
        List<ProviderPart> out = new ArrayList<>();
        Integer marker = null;
        do {
            Integer pageMarker = marker;
            ListPartsResponse resp = s3.listParts(ListPartsRequest.builder()
                    .bucket(props.getBucket()).key(objectKey).uploadId(uploadId)
                    .partNumberMarker(pageMarker)
                    .build());
            for (Part p : resp.parts()) {
                out.add(new ProviderPart(p.partNumber(), p.size(), p.eTag()));
            }
            marker = resp.isTruncated() ? resp.nextPartNumberMarker() : null;
        } while (marker != null);
        return out;
    }

    /** 完成 Multipart：parts 必须按 partNumber 升序（S3 协议要求）。 */
    public void completeMultipart(String objectKey, String uploadId, List<ProviderPart> parts) {
        List<CompletedPart> completed = parts.stream()
                .sorted((a, b) -> Integer.compare(a.partNumber(), b.partNumber()))
                .map(p -> CompletedPart.builder().partNumber(p.partNumber()).eTag(p.etag()).build())
                .toList();
        s3.completeMultipartUpload(r -> r.bucket(props.getBucket()).key(objectKey).uploadId(uploadId)
                .multipartUpload(m -> m.parts(completed)));
    }

    /** 放弃 Multipart：upload 不存在视为已清理（幂等）。 */
    public void abortMultipart(String objectKey, String uploadId) {
        try {
            s3.abortMultipartUpload(r -> r.bucket(props.getBucket()).key(objectKey).uploadId(uploadId));
        } catch (NoSuchUploadException e) {
            log.info("Multipart 已不存在，跳过 abort: key={}", objectKey);
        }
    }

    /** 流式计算对象完整 SHA-256（05 §6.3：禁止 Multipart ETag 当 SHA-256）。 */
    public String computeSha256(String objectKey) {
        try (var in = s3.getObject(b -> b.bucket(props.getBucket()).key(objectKey))) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 20];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
                total += n;
            }
            log.debug("对象哈希完成 key={} bytes={}", objectKey, total);
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败: " + objectKey, e);
        }
    }

    /** 读取整对象字节（仅限 git source 小文件，上限由调用方保证）。 */
    public byte[] getObjectBytes(String objectKey) {
        return s3.getObjectAsBytes(b -> b.bucket(props.getBucket()).key(objectKey)).asByteArray();
    }

    /** 删除对象（幂等：不存在不报错）。 */
    public void deleteObject(String objectKey) {
        try {
            s3.deleteObject(b -> b.bucket(props.getBucket()).key(objectKey));
        } catch (Exception e) {
            log.warn("删除对象失败 key={}: {}", objectKey, e.getMessage());
        }
    }
}
