// AIHub CLI Dataset Search 命令
// 作者: AxeXie
// 创建时间: 2026-07-30

import { Command } from 'commander';
import { createApiClient, apiGet } from '../api/client';
import { searchAssetsPath } from '../api/endpoints';
import { CommandError, formatApiError, ExitCode } from '../shared/errors';
import { printTable, isJsonOutput, outputJson, successMessage, errorMessage } from '../shared/output';

export interface AssetSummary {
  assetId: string;
  type: 'MODEL' | 'DATASET';
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
  framework: string;
  task: string;
  format: string;
  modality: string;
  updatedAt: string;
}

export interface CursorPageAssetSummary {
  items: AssetSummary[];
  nextCursor: string | null;
  hasMore: boolean;
}

export function createDatasetSearchCommand(): Command {
  return new Command('search')
    .argument('<keyword>', 'Search keyword')
    .option('--type <type>', 'Asset type filter', 'DATASET')
    .option('--namespace <namespace>', 'Namespace filter')
    .option('--tag-id <tagId>', 'Tag ID filter')
    .option('--limit <limit>', 'Max results', '20')
    .description('Search assets by keyword')
    .action(async (keyword: string, opts: { type?: string; namespace?: string; tagId?: string; limit?: string }) => {
      try {
        const client = await createApiClient();
        const params: Record<string, string> = { keyword, limit: opts.limit || '20' };
        if (opts.type) params.type = opts.type;
        if (opts.namespace) params.namespace = opts.namespace;
        if (opts.tagId) params.tagId = opts.tagId;

        const result = await apiGet<CursorPageAssetSummary>(client, searchAssetsPath(), params);

        if (isJsonOutput()) {
          console.log(outputJson(result));
        } else {
          if (result.items.length === 0) {
            console.log(successMessage('No assets found'));
            return;
          }

          const columns = ['assetId', 'type', 'namespace', 'name', 'visibility', 'status'];
          const rows = result.items.map(item => ({
            assetId: item.assetId,
            type: item.type,
            namespace: item.namespace,
            name: item.displayName || item.name,
            visibility: item.visibility,
            status: item.status,
          }));

          printTable(rows, columns);

          if (result.hasMore) {
            console.log(`\n  ... and more (use --limit to adjust)`);
          }
        }
      } catch (err) {
        if (err instanceof CommandError || err instanceof Error) {
          process.exitCode = ExitCode.Error;
          console.error(errorMessage(formatApiError(err)));
          return;
        }
        process.exitCode = ExitCode.Error;
        console.error(errorMessage(formatApiError(err)));
      }
    });
}