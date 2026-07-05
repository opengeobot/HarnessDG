/*
 * 功能: 建仓 Saga 幂等处理器——领取 REPOSITORY_PROVISION 任务，调用 Provisioner 开通仓库，
 *       更新 provisioning_status 并发布 Outbox 事件。
 * 时间: 2026-07-04
 * 作者: AxeXie
 */
package com.aihub.asset.infrastructure;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetCard;
import com.aihub.asset.domain.AssetRepository;
import com.aihub.asset.domain.AssetRepositoryProvisioner;
import com.aihub.asset.domain.AssetRepositoryRef;
import com.aihub.asset.domain.ProvisioningStatus;
import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.aihub.notification.domain.OutboxEvent;
import com.aihub.notification.domain.OutboxRepository;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 建仓 Saga 幂等处理器。
 *
 * <p>Worker 领取 {@code REPOSITORY_PROVISION} 类型任务后调用本 Handler。流程：
 * <ol>
 *   <li>从 payload 解析 assetId，加载资产聚合</li>
 *   <li>幂等检查：已完成/失败时直接返回</li>
 *   <li>推进状态至 IN_PROGRESS，渲染初始卡片</li>
 *   <li>调用 {@link AssetRepositoryProvisioner} 开通仓库</li>
 *   <li>绑定仓库引用，推进状态至 COMPLETED</li>
 *   <li>发布 ASSET_PROVISIONED Outbox 事件</li>
 * </ol>
 *
 * <p>失败时标记 FAILED 并抛出异常，由 Worker 按退避策略重试。
 */
@Component
public class RepositoryProvisionJobHandler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryProvisionJobHandler.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AssetRepository assetRepository;
    private final AssetRepositoryProvisioner repositoryProvisioner;
    private final OutboxRepository outboxRepository;
    private final IdGenerator idGenerator;

    public RepositoryProvisionJobHandler(AssetRepository assetRepository,
                                         AssetRepositoryProvisioner repositoryProvisioner,
                                         OutboxRepository outboxRepository,
                                         IdGenerator idGenerator) {
        this.assetRepository = assetRepository;
        this.repositoryProvisioner = repositoryProvisioner;
        this.outboxRepository = outboxRepository;
        this.idGenerator = idGenerator;
    }

    @Override
    public String type() {
        return "REPOSITORY_PROVISION";
    }

    @Override
    public void handle(JobContext context) throws Exception {
        String assetId = extractAssetId(context.payload());
        Asset asset = assetRepository.findByAssetId(assetId).orElse(null);
        if (asset == null) {
            LOG.warn("asset not found for provisioning job, likely replayed or orphaned: assetId={}", assetId);
            return;
        }
        // 幂等：已完成或失败（非 PENDING/IN_PROGRESS）时跳过
        if (asset.provisioningStatus() == ProvisioningStatus.COMPLETED) {
            LOG.info("asset already provisioned, skipping: assetId={}", assetId);
            return;
        }

        try {
            asset.advanceProvisioning(ProvisioningStatus.IN_PROGRESS);

            AssetCard.CardFiles card = AssetCard.render(asset);
            AssetRepositoryRef ref = repositoryProvisioner.provision(
                    new AssetRepositoryProvisioner.ProvisionRequest(
                            asset.namespace(), asset.name(), asset.type(), asset.description(),
                            asset.visibility(), card.readme(), card.assetYaml()));
            asset.attachRepository(ref);
            asset.advanceProvisioning(ProvisioningStatus.COMPLETED);
            assetRepository.update(asset);

            publishProvisionedEvent(asset, context);
            LOG.info("repository provisioned successfully: assetId={} repo={}", assetId, ref.fullName());
        } catch (Exception ex) {
            LOG.error("repository provisioning failed: assetId={}", assetId, ex);
            asset.advanceProvisioning(ProvisioningStatus.FAILED);
            assetRepository.update(asset);
            throw ex;
        }
    }

    private String extractAssetId(String payload) throws Exception {
        JsonNode node = OBJECT_MAPPER.readTree(payload);
        return node.get("assetId").asText();
    }

    private void publishProvisionedEvent(Asset asset, JobContext context) {
        Instant now = Instant.now();
        String payload = String.format(
                "{\"assetId\":\"%s\",\"namespace\":\"%s\",\"name\":\"%s\",\"type\":\"%s\"}",
                asset.assetId(), asset.namespace(), asset.name(), asset.type());
        OutboxEvent event = new OutboxEvent(
                null,
                idGenerator.generate(IdPrefix.REQUEST),
                "ASSET", asset.assetId(), "ASSET_PROVISIONED",
                payload, null, now, null,
                context.traceId(), context.principalId());
        outboxRepository.append(event);
    }
}
