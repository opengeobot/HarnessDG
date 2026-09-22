/*
 * 功能: 资产 Card 同步 Saga 幂等处理器——领取 ASSET_CARD_SYNC 任务，在 Gitea 启用时刷新投影。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.asset.infrastructure;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetCardProjectionPort;
import com.aihub.asset.domain.AssetRepository;
import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 资产 Card 同步 Saga 幂等处理器（stub）。
 *
 * <p>重命名或元数据变更后异步刷新 Gitea 仓库中的 asset.yaml/README 投影。
 * Gitea 未启用时直接跳过，不阻塞主事务。
 */
@Component
@ConditionalOnProperty(name = "aihub.gitea.enabled", havingValue = "true")
public class AssetCardSyncJobHandler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AssetCardSyncJobHandler.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AssetRepository assetRepository;
    private final ObjectProvider<AssetCardProjectionPort> cardProjectionPortProvider;

    public AssetCardSyncJobHandler(AssetRepository assetRepository,
                                   ObjectProvider<AssetCardProjectionPort> cardProjectionPortProvider) {
        this.assetRepository = assetRepository;
        this.cardProjectionPortProvider = cardProjectionPortProvider;
    }

    @Override
    public String type() {
        return "ASSET_CARD_SYNC";
    }

    @Override
    public void handle(JobContext context) throws Exception {
        String assetId = extractAssetId(context.payload());
        Asset asset = assetRepository.findByAssetId(assetId).orElse(null);
        if (asset == null) {
            LOG.warn("asset not found for card sync job, skipping: assetId={}", assetId);
            return;
        }
        AssetCardProjectionPort port = cardProjectionPortProvider.getIfAvailable();
        if (port == null || asset.repository() == null) {
            LOG.info("gitea card projection unavailable, skipping card sync: assetId={}", assetId);
            return;
        }
        AssetCardProjectionPort.CardProjection projection =
                port.fetchCard(asset.repository().fullName(), asset.sourceCommit());
        if (projection.sourceCommit() == null) {
            LOG.info("no card projection returned from gitea, skipping: assetId={}", assetId);
            return;
        }
        Asset updated = new Asset.Builder()
                .assetId(asset.assetId())
                .type(asset.type())
                .organizationId(asset.organizationId())
                .projectId(asset.projectId())
                .namespace(asset.namespace())
                .name(asset.name())
                .displayName(asset.displayName())
                .description(asset.description())
                .visibility(asset.visibility())
                .status(asset.status())
                .owners(asset.owners())
                .tags(asset.tags())
                .tagIds(asset.tagIds())
                .license(asset.license())
                .ownerTeamId(asset.ownerTeamId())
                .aliases(asset.aliases())
                .modelProfile(asset.modelProfile())
                .datasetProfile(asset.datasetProfile())
                .repository(asset.repository())
                .provisioningStatus(asset.provisioningStatus())
                .deprecationReason(asset.deprecationReason())
                .deprecationNote(asset.deprecationNote())
                .replacementAssetId(asset.replacementAssetId())
                .rowVersion(asset.rowVersion())
                .createdBy(asset.createdBy())
                .updatedBy(context.principalId())
                .createdAt(asset.createdAt())
                .updatedAt(Instant.now())
                .sourceCommit(projection.sourceCommit())
                .cardReadme(projection.readme())
                .cardAssetYaml(projection.assetYaml())
                .build();
        assetRepository.update(updated);
        LOG.info("asset card projection synced: assetId={} commit={}", assetId, projection.sourceCommit());
    }

    private String extractAssetId(String payload) throws Exception {
        JsonNode node = OBJECT_MAPPER.readTree(payload);
        return node.get("assetId").asText();
    }
}
