// AIHub CLI Dataset Create 命令
// 作者: AxeXie
// 创建时间: 2026-07-30

import { Command } from 'commander';
import { createApiClient, apiPost } from '../api/client';
import { createAssetPath } from '../api/endpoints';
import { CommandError, formatApiError, ExitCode } from '../shared/errors';
import { printTable, isJsonOutput, outputJson, successMessage, errorMessage } from '../shared/output';

export interface CreateAssetRequest {
  type: 'MODEL' | 'DATASET';
  organizationId?: string;
  projectId?: string;
  namespace: string;
  name: string;
  displayName?: string;
  description?: string;
  visibility: 'PRIVATE' | 'INTERNAL' | 'PUBLIC';
  owners?: string[];
  tagIds?: string[];
  license?: string;
  model?: { framework?: string; task?: string; architecture?: string };
  dataset?: { format?: string; modality?: string };
}

export function createDatasetCreateCommand(): Command {
  return new Command('create')
    .argument('<name>', 'Asset name (lowercase slug)')
    .option('--type <type>', 'Asset type (DATASET or MODEL)', 'DATASET')
    .option('--namespace <namespace>', 'Namespace (lowercase slug)')
    .option('--organization <organizationId>', 'Organization ID')
    .option('--project <projectId>', 'Project ID')
    .option('--display-name <displayName>', 'Display name')
    .option('--description <description>', 'Description')
    .option('--visibility <visibility>', 'Visibility (PRIVATE, INTERNAL, PUBLIC)', 'PRIVATE')
    .option('--license <license>', 'License (e.g. Apache-2.0)')
    .option('--format <format>', 'Dataset format (e.g. parquet)')
    .option('--modality <modality>', 'Dataset modality (e.g. text)')
    .description('Create a new asset draft')
    .action(async (name: string, opts: {
      type?: string;
      namespace?: string;
      organization?: string;
      project?: string;
      displayName?: string;
      description?: string;
      visibility?: string;
      license?: string;
      format?: string;
      modality?: string;
    }) => {
      try {
        const client = await createApiClient();

        if (!opts.namespace) {
          console.error(errorMessage('Namespace is required. Use --namespace'));
          process.exitCode = ExitCode.Error;
          return;
        }

        const body: CreateAssetRequest = {
          type: (opts.type || 'DATASET') as 'MODEL' | 'DATASET',
          namespace: opts.namespace,
          name,
          visibility: (opts.visibility || 'PRIVATE') as 'PRIVATE' | 'INTERNAL' | 'PUBLIC',
        };

        if (opts.organization) body.organizationId = opts.organization;
        if (opts.project) body.projectId = opts.project;
        if (opts.displayName) body.displayName = opts.displayName;
        if (opts.description) body.description = opts.description;
        if (opts.license) body.license = opts.license;

        if (opts.type === 'DATASET' && (opts.format || opts.modality)) {
          body.dataset = {};
          if (opts.format) body.dataset.format = opts.format;
          if (opts.modality) body.dataset.modality = opts.modality;
        }

        const result = await apiPost<typeof body>(client, createAssetPath(), body);

        if (isJsonOutput()) {
          console.log(outputJson(result));
        } else {
          console.log(successMessage(`Asset created: ${name}`));
          const columns = ['Field', 'Value'];
          const rows = [
            { Field: 'assetId', Value: result.assetId },
            { Field: 'name', Value: result.name },
            { Field: 'type', Value: result.type },
            { Field: 'namespace', Value: result.namespace },
            { Field: 'status', Value: result.status },
            { Field: 'visibility', Value: result.visibility },
          ];
          printTable(rows, columns);
        }
      } catch (err) {
        process.exitCode = ExitCode.Error;
        console.error(errorMessage(formatApiError(err)));
      }
    });
}