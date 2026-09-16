// AIHub CLI Dataset Status 命令
// 作者: AxeXie
// 创建时间: 2026-07-30

import { Command } from 'commander';
import { createApiClient, apiGet } from '../api/client';
import { getAssetPath } from '../api/endpoints';
import { CommandError, formatApiError, ExitCode } from '../shared/errors';
import { printTable, isJsonOutput, outputJson, errorMessage } from '../shared/output';

export interface AssetView {
  assetId: string;
  type: string;
  namespace: string;
  name: string;
  displayName: string;
  description: string;
  visibility: string;
  status: string;
  owners: string[];
  tags: string[];
  tagIds: string[];
  license: string;
  model: Record<string, unknown> | null;
  dataset: Record<string, unknown> | null;
  repository: { fullName: string; htmlUrl: string; cloneUrl: string } | null;
  provisioningStatus: string;
  createdAt: string;
  updatedAt: string;
  organizationId: string | null;
  projectId: string | null;
}

export function createDatasetStatusCommand(): Command {
  return new Command('status')
    .argument('<asset-id>', 'Asset ID')
    .description('Get asset and version status')
    .action(async (assetId: string) => {
      try {
        const client = await createApiClient();
        const asset = await apiGet<AssetView>(client, getAssetPath(assetId));

        if (isJsonOutput()) {
          console.log(outputJson(asset));
        } else {
          const columns = ['Field', 'Value'];
          const rows = [
            { Field: 'assetId', Value: asset.assetId },
            { Field: 'type', Value: asset.type },
            { Field: 'namespace', Value: asset.namespace },
            { Field: 'name', Value: asset.name },
            { Field: 'displayName', Value: asset.displayName },
            { Field: 'status', Value: asset.status },
            { Field: 'visibility', Value: asset.visibility },
            { Field: 'license', Value: asset.license },
            { Field: 'provisioningStatus', Value: asset.provisioningStatus },
            { Field: 'createdAt', Value: asset.createdAt },
            { Field: 'updatedAt', Value: asset.updatedAt },
          ];
          if (asset.organizationId) {
            rows.push({ Field: 'organizationId', Value: asset.organizationId });
          }
          if (asset.repository) {
            rows.push({ Field: 'repository', Value: asset.repository.htmlUrl });
          }

          printTable(rows, columns);
        }
      } catch (err) {
        process.exitCode = ExitCode.Error;
        console.error(errorMessage(formatApiError(err)));
      }
    });
}