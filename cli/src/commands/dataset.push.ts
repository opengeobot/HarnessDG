// AIHub CLI Dataset Push 命令
// 作者: AxeXie
// 创建时间: 2026-07-30
// 创建上传会话 -> 分片上传 -> 完成上传

import { Command } from 'commander';
import * as fs from 'node:fs';
import * as crypto from 'node:crypto';
import { createApiClient, apiPost, apiGet } from '../api/client';
import { createUploadSessionPath, presignPartUploadPath, completeUploadSessionPath } from '../api/endpoints';
import { formatApiError, ExitCode } from '../shared/errors';
import { printProgress, printTable, isJsonOutput, outputJson, successMessage, errorMessage, formatFileSize } from '../shared/output';

export interface UploadSessionView {
  sessionId: string;
  assetId: string;
  versionId: string;
  principalId: string;
  status: 'OPEN' | 'COMMITTING' | 'COMPLETED' | 'CANCELLED' | 'EXPIRED';
  totalBytes: number;
  fileCount: number;
  expiresAt: string;
  createdAt: string;
}

/** 默认分片大小: 5MB */
const DEFAULT_PART_SIZE = 5 * 1024 * 1024;

/** 分片 ETag 信息 */
interface PartInfo {
  partNumber: number;
  etag: string;
}

export function createDatasetPushCommand(): Command {
  return new Command('push')
    .argument('<asset-id>', 'Asset ID')
    .option('--version <version>', 'Version ID (required)')
    .option('--file <path>', 'Local file path to upload (required)')
    .option('--part-size <bytes>', 'Part size in bytes', String(DEFAULT_PART_SIZE))
    .description('Upload file to asset version via multipart upload')
    .action(async (assetId: string, opts: { version?: string; file?: string; partSize?: string }) => {
      try {
        if (!opts.version) {
          console.error(errorMessage('--version is required'));
          process.exitCode = ExitCode.Error;
          return;
        }
        if (!opts.file) {
          console.error(errorMessage('--file is required'));
          process.exitCode = ExitCode.Error;
          return;
        }

        const versionId = opts.version;
        const filePath = opts.file;

        if (!fs.existsSync(filePath)) {
          console.error(errorMessage(`File not found: ${filePath}`));
          process.exitCode = ExitCode.Error;
          return;
        }

        const client = await createApiClient();
        const stats = fs.statSync(filePath);
        const totalBytes = stats.size;
        const partSize = parseInt(opts.partSize || String(DEFAULT_PART_SIZE), 10);
        const fileCount = 1;

        if (isJsonOutput()) {
          console.log(outputJson({ status: 'starting', assetId, versionId, file: filePath, size: totalBytes }));
        } else {
          console.log(`Uploading ${filePath} (${formatFileSize(totalBytes)})...`);
        }

        // Step 1: 创建上传会话
        const session = await apiPost<UploadSessionView>(client, createUploadSessionPath(assetId, versionId), {
          totalBytes,
          fileCount,
        });

        if (session.status !== 'OPEN') {
          console.error(errorMessage(`Upload session not open: ${session.status}`));
          process.exitCode = ExitCode.Error;
          return;
        }

        // Step 2: 计算分片数量并上传
        const partCount = Math.ceil(totalBytes / partSize);
        const parts: PartInfo[] = [];
        const fileStream = fs.createReadStream(filePath);

        for (let i = 1; i <= partCount; i++) {
          // 读取分片数据
          const start = (i - 1) * partSize;
          const end = Math.min(start + partSize, totalBytes);
          const chunk = fs.readFileSync(filePath, { start, end });

          // 获取预签名 URL
          const presign = await apiGet<{ url: string }>(client, presignPartUploadPath(session.sessionId, i));

          // 上传分片到预签名 URL
          const axios = await import('axios');
          const uploadResp = await axios.default.put(presign.url, chunk, {
            timeout: 120_000,
            headers: { 'Content-Type': 'application/octet-stream' },
            validateStatus: (status) => status < 500,
          });

          // 提取 ETag（S3 通常在 ETag 头中返回）
          const etag = uploadResp.headers['etag'] || crypto.createHash('md5').update(chunk).digest('hex');
          parts.push({ partNumber: i, etag });

          printProgress(i, partCount, 'Upload');
        }

        // Step 3: 完成上传
        const completeResult = await apiPost<UploadSessionView>(
          client,
          completeUploadSessionPath(session.sessionId),
          { parts },
        );

        if (isJsonOutput()) {
          console.log(outputJson({ status: 'complete', session: completeResult }));
        } else {
          console.log(''); // 换行
          console.log(successMessage('Upload completed'));
          const columns = ['Field', 'Value'];
          const rows = [
            { Field: 'sessionId', Value: completeResult.sessionId },
            { Field: 'status', Value: completeResult.status },
            { Field: 'totalBytes', Value: formatFileSize(completeResult.totalBytes) },
          ];
          printTable(rows, columns);
        }
      } catch (err) {
        process.exitCode = ExitCode.NetworkError;
        console.error(errorMessage(formatApiError(err)));
      }
    });
}