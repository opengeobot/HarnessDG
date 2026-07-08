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
import java.util.List;

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
                request.ownerTeamId(),
                toModelProfile(request.model()),
                toDatasetProfile(request.dataset()),
                principalId);
    }

    static UpdateAssetCommand toUpdateCommand(UpdateAssetRequest request, String principalId) {
        return new UpdateAssetCommand(
                request.expectedVersion(),
                request.organizationId(),
                request.projectId(),
                request.displayName(),
                request.description(),
                request.visibility(),
                request.owners(),
                request.tags(),
                request.tagIds(),
                request.license(),
                request.ownerTeamId(),
                toModelProfile(request.model()),
                toDatasetProfile(request.dataset()),
                principalId);
    }

    static AssetSearchQuery toSearchQuery(String keyword,
                                          AssetType type,
                                          String namespace,
                                          String organizationId,
                                          String projectId,
                                          String visibility,
                                          String status,
                                          String teamId,
                                          String framework,
                                          String task,
                                          String format,
                                          String modality,
                                          String tagId,
                                          String owner,
                                          String language,
                                          String sensitivity,
                                          List<String> taskCodes,
                                          List<String> modalityCodes,
                                          List<String> formatCodes,
                                          boolean includeArchived,
                                          String cursor,
                                          int limit,
                                          String principalId) {
        return new AssetSearchQuery(keyword, type, namespace, organizationId, projectId, visibility,
                status, teamId, framework, task, format, modality, tagId, owner, language, sensitivity,
                taskCodes, modalityCodes, formatCodes,
                includeArchived, cursor, limit, principalId);
    }

    private static ModelProfile toModelProfile(CreateAssetRequest.ModelInput input) {
        if (input == null) {
            return null;
        }
        return new ModelProfile(input.framework(), input.task(), input.architecture(),
                input.parameterScale(), input.precision(), input.weightFormat(),
                input.runtime(), input.knownRisks(), input.usageRestrictions(),
                input.sensitivityCode());
    }

    private static DatasetProfile toDatasetProfile(CreateAssetRequest.DatasetInput input) {
        if (input == null) {
            return null;
        }
        return new DatasetProfile(input.format(), input.modality(),
                input.taskCodes(), input.modalityCodes(), input.formatCodes(),
                input.languageCodes(), input.sensitivityCode(),
                input.sampleCount(), input.totalBytes(), input.sizeBucketCode());
    }
}
