// AIHub CLI Dataset Pull 命令
// 作者: AxeXie
// 创建时间: 2026-07-30
// 获取下载票据 -> 通过预签名 URL 下载 -> 校验 SHA-256

import { Command } from 'commander';
import * as fs from 'node:fs';
import * as crypto from 'node:crypto';
import { createApiClient, apiGet } from '../api/client';
import { downloadTicketPath } from '../api/endpoints';
import { formatApiError, ExitCode } from '../shared/errors';
import { printProgress, isJsonOutput, outputJson, successMessage, errorMessage, formatFileSize } from '../shared/output';

export interface DownloadTicket {
  method: 'PRESIGNED_URL' | 'GIT_DVC';
  presignedUrl: string;
  expiresAt: string;
  fileName: string;
  fileSize: number | null;
}

export function createDatasetPullCommand(): Command {
  return new Command('pull')
    .argument('<asset-id>', 'Asset ID')
    .option('--version <version>', 'Version ID (required)')
    .option('--output <path>', 'Output file path')
    .option('--artifact-id <artifactId>', 'Artifact ID')
    .description('Download asset version via presigned URL with SHA-256 verification')
    .action(async (assetId: string, opts: { version?: string; output?: string; artifactId?: string }) => {
      try {
        if (!opts.version) {
          console.error(errorMessage('--version is required'));
          process.exitCode = ExitCode.Error;
          return;
        }

        const client = await createApiClient();
        const versionId = opts.version;

        // Step 1: 获取下载票据
        const ticketPath = downloadTicketPath(versionId, opts.artifactId);
        const ticket = await apiGet<DownloadTicket>(client, ticketPath);

        if (!ticket.presignedUrl) {
          console.error(errorMessage('Download ticket missing presigned URL'));
          process.exitCode = ExitCode.Error;
          return;
        }

        if (ticket.method === 'GIT_DVC') {
          console.error(errorMessage('GIT_DVC download method is not supported by CLI. Use DVC directly.'));
          process.exitCode = ExitCode.Error;
          return;
        }

        const outputPath = opts.output || (ticket.fileName || `${assetId}_${versionId}`);
        const expectedSize = ticket.fileSize;

        if (isJsonOutput()) {
          console.log(outputJson({
            status: 'ready',
            presignedUrl: '[REDACTED]',
            fileName: ticket.fileName,
            fileSize: ticket.fileSize,
            expiresAt: ticket.expiresAt,
          }));
        } else {
          console.log(`Downloading ${ticket.fileName || 'asset'} (${formatFileSize(expectedSize)})...`);
        }

        // Step 2: 下载文件（直接使用 presigned URL，不需要 JWT）
        const hash = crypto.createHash('sha256');
        const writeStream = fs.createWriteStream(outputPath);

        // 直接通过 presigned URL 下载（不带认证头）
        const axios = await import('axios');
        const response = await axios.default.get(ticket.presignedUrl, {
          responseType: 'stream',
          timeout: 300_000,
          validateStatus: (status) => status < 500,
        });

        if (response.status !== 200) {
          console.error(errorMessage(`Download failed: HTTP ${response.status}`));
          process.exitCode = ExitCode.NetworkError;
          return;
        }

        // Step 3: 流式写入 + 计算 SHA-256
        let downloaded = 0;
        await new Promise<void>((resolve, reject) => {
          response.data.on('data', (chunk: Buffer) => {
            hash.update(chunk);
            downloaded += chunk.length;
            printProgress(downloaded, expectedSize || downloaded, 'Download');
            writeStream.write(chunk);
          });
          response.data.on('end', () => {
            writeStream.end();
            resolve();
          });
          response.data.on('error', reject);
        });

        const actualHash = hash.digest('hex');

        if (isJsonOutput()) {
          console.log(outputJson({
            status: 'complete',
            output: outputPath,
            size: downloaded,
            sha256: actualHash,
          }));
        } else {
          console.log(''); // 换行（进度条末尾）
          console.log(successMessage(`Downloaded to ${outputPath}`));
          console.log(`  Size: ${formatFileSize(downloaded)}`);
          console.log(`  SHA-256: ${actualHash}`);
        }
      } catch (err) {
        process.exitCode = ExitCode.NetworkError;
        console.error(errorMessage(formatApiError(err)));
      }
    });
}