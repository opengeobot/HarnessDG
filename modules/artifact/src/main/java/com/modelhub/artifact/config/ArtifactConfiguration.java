package com.modelhub.artifact.config;

import com.modelhub.artifact.worker.ArtifactFileDeletionWorker;
import com.modelhub.artifact.worker.ArtifactUploadWorker;
import com.modelhub.catalog.worker.OutboxEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * S3 兼容客户端装配（05 §2 双端点）：
 * - {@link S3Client}：服务端内部端点，Worker 校验/完成/清理与流式 SHA-256；
 * - {@link S3Presigner}：public base URL，仅用于签发客户端可达的短期 URL。
 * Path-style 寻址：MinIO 不依赖虚拟主机域名解析。
 */
@Configuration
@EnableConfigurationProperties(ArtifactProperties.class)
public class ArtifactConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ArtifactConfiguration.class);

    @Bean
    public S3Client artifactS3Client(ArtifactProperties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(props.getInternalEndpoint()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .build();
    }

    @Bean
    public S3Presigner artifactS3Presigner(ArtifactProperties props) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.getPublicBaseUrl()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    /** 启动自检（05 §2 部署自检的最小内嵌版）：bucket 不存在则创建，失败仅告警不阻断启动。 */
    @Bean
    public ArtifactBucketInitializer artifactBucketInitializer(S3Client s3, ArtifactProperties props) {
        return new ArtifactBucketInitializer(s3, props);
    }

    // ---------- Outbox 事件处理器注册（05 §10.1，经 catalog 扩展点解耦） ----------

    @Bean
    public OutboxEventHandler uploadInitializationHandler(ArtifactUploadWorker worker) {
        return handler("UploadInitializationRequested", worker::handleInitialization);
    }

    @Bean
    public OutboxEventHandler uploadVerificationHandler(ArtifactUploadWorker worker) {
        return handler("UploadVerificationRequested", worker::handleVerification);
    }

    @Bean
    public OutboxEventHandler uploadCommitHandler(ArtifactUploadWorker worker) {
        return handler("UploadCommitRequested", worker::handleCommit);
    }

    @Bean
    public OutboxEventHandler uploadAbortHandler(ArtifactUploadWorker worker) {
        return handler("UploadAbortRequested", worker::handleAbort);
    }

    @Bean
    public OutboxEventHandler fileDeletionHandler(ArtifactFileDeletionWorker worker) {
        return handler("FileDeletionRequested", worker::handleDeletion);
    }

    private static OutboxEventHandler handler(String eventType,
                                              java.util.function.Consumer<com.fasterxml.jackson.databind.JsonNode> fn) {
        return new OutboxEventHandler() {
            @Override
            public String eventType() {
                return eventType;
            }

            @Override
            public void handle(com.fasterxml.jackson.databind.JsonNode payload) {
                fn.accept(payload);
            }
        };
    }

    static class ArtifactBucketInitializer {
        ArtifactBucketInitializer(S3Client s3, ArtifactProperties props) {
            try {
                if (!s3.listBuckets().buckets().stream()
                        .anyMatch(b -> props.getBucket().equals(b.name()))) {
                    s3.createBucket(r -> r.bucket(props.getBucket()));
                    log.info("已创建对象 bucket: {}", props.getBucket());
                }
            } catch (Exception e) {
                // 启动期 MinIO 未就绪不阻断应用；上传链路会以 503 显式失败并可重试
                log.warn("对象存储自检失败（bucket={}）: {}", props.getBucket(), e.getMessage());
            }
        }
    }
}
