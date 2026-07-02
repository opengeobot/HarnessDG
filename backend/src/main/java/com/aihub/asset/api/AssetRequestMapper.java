/*
 * 功能: 资产 REST 请求与应用层命令/查询之间的映射。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.api;

import com.aihub.asset.application.AssetSearchQuery;
import com.aihub.asset.application.CreateAssetCommand;
import com.aihub.asset.application.UpdateAssetCommand;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.DatasetProfile;
import com.aihub.asset.domain.ModelProfile;

/**
 * 资产请求映射器。
 *
 * <p>将 REST 请求体转换为应用层命令/查询，隔离传输模型与应用模型。
 */
final class AssetRequestMapper {

    private AssetRequestMapper() {
    }

    static CreateAssetCommand toCreateCommand(CreateAssetRequest request, String principalId) {
        return new CreateAssetCommand(
                request.type(),
                request.organizationId(),
                request.projectId(),
                request.namespace(),
                request.name(),
                request.displayName(),
                request.description(),
                request.visibility(),
                request.owners(),
                request.tags(),
                request.tagIds(),
                request.license(),
                toModelProfile(request.model()),
                toDatasetProfile(request.dataset()),
                principalId);
    }

    static UpdateAssetCommand toUpdateCommand(UpdateAssetRequest request, String principalId) {
        return new UpdateAssetCommand(
                request.organizationId(),
                request.projectId(),
                request.displayName(),
                request.description(),
                request.visibility(),
                request.owners(),
                request.tags(),
                request.tagIds(),
                request.license(),
                toModelProfile(request.model()),
                toDatasetProfile(request.dataset()),
                principalId);
    }

    static AssetSearchQuery toSearchQuery(String keyword,
                                          AssetType type,
                                          String namespace,
                                          String organizationId,
                                          String framework,
                                          String task,
                                          String format,
                                          String modality,
                                          String tagId,
                                          String owner,
                                          boolean includeArchived,
                                          String cursor,
                                          int limit,
                                          String principalId) {
        return new AssetSearchQuery(keyword, type, namespace, organizationId, framework, task,
                format, modality, tagId, owner, includeArchived, cursor, limit, principalId);
    }

    private static ModelProfile toModelProfile(CreateAssetRequest.ModelInput input) {
        return input == null ? null : new ModelProfile(input.framework(), input.task(), input.architecture());
    }

    private static DatasetProfile toDatasetProfile(CreateAssetRequest.DatasetInput input) {
        return input == null ? null : new DatasetProfile(input.format(), input.modality());
    }
}
