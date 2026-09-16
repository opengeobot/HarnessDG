// AIHub CLI Auth 命令 (login/logout)
// 作者: AxeXie
// 创建时间: 2026-07-30

import { Command } from 'commander';
import axios from 'axios';
import { getCredentials, saveCredentials, clearCredentials, type StoredCredentials } from './credentialStore';
import { AuthError, NetworkError, formatApiError, ExitCode } from '../shared/errors';
import { successMessage, errorMessage, outputJson, isJsonOutput } from '../shared/output';

/** 从环境变量或默认值获取 API 服务器地址 */
function getServerUrl(): string {
  return process.env.AIHub_API_URL || 'http://localhost:8080';
}

/** 构建凭据对象 */
function buildCredentials(
  tokenType: string,
  accessToken: string,
  refreshToken: string,
  expiresIn: number,
  principalId: string,
  username: string,
  serverUrl: string,
): StoredCredentials {
  const now = Date.now() / 1000;
  return {
    accessToken,
    refreshToken,
    expiresAt: now + expiresIn,
    principalId,
    username,
    serverUrl,
  };
}

/** 登录命令 */
export function createAuthCommand(): Command {
  const cmd = new Command('auth');
  cmd.description('Manage authentication');

  cmd
    .command('login')
    .argument('<username>', 'Login username')
    .argument('<password>', 'Login password')
    .option('--server <url>', 'API server URL', getServerUrl())
    .description('Login and store JWT credentials')
    .action(async (username: string, password: string, opts: { server?: string }) => {
      try {
        const serverUrl = (opts.server || getServerUrl()).replace(/\/+$/, '');
        const loginUrl = `${serverUrl}/api/v1/auth/login`;

        const response = await axios.post(loginUrl, { username, password }, {
          timeout: 15_000,
          validateStatus: (status) => status < 500,
        });

        if (response.status !== 200) {
          const body = response.data;
          const code = body?.error?.code || body?.code || 'UNKNOWN';
          const msg = body?.error?.message || body?.message || 'Login failed';
          throw new AuthError(msg);
        }

        const data = response.data?.data;
        if (!data?.accessToken || !data?.refreshToken) {
          throw new AuthError('Invalid login response: missing tokens');
        }

        const creds = buildCredentials(
          data.tokenType,
          data.accessToken,
          data.refreshToken,
          data.expiresIn,
          data.principal?.principalId || 'unknown',
          username,
          serverUrl,
        );

        saveCredentials(creds);

        if (isJsonOutput()) {
          console.log(outputJson({
            success: true,
            principalId: creds.principalId,
            expiresAt: creds.expiresAt,
          }));
        } else {
          console.log(successMessage(`Logged in as ${username} (principal: ${creds.principalId})`));
          console.log(`  Token expires at: ${new Date(creds.expiresAt * 1000).toISOString()}`);
        }
      } catch (err) {
        if (err instanceof AuthError) {
          process.exitCode = err.exitCode;
          console.error(errorMessage(err.message));
          return;
        }
        if (axios.isAxiosError(err)) {
          process.exitCode = ExitCode.NetworkError;
          console.error(errorMessage(`Network error: ${formatApiError(err)}`));
          return;
        }
        process.exitCode = ExitCode.Error;
        console.error(errorMessage(formatApiError(err)));
      }
    });

  cmd
    .command('logout')
    .description('Clear stored credentials')
    .action(async () => {
      try {
        const creds = getCredentials();
        clearCredentials();

        if (isJsonOutput()) {
          console.log(outputJson({ success: true, message: 'Logged out' }));
        } else {
          if (creds) {
            console.log(successMessage(`Logged out ${creds.username}`));
          } else {
            console.log(successMessage('No active session found'));
          }
        }
      } catch (err) {
        process.exitCode = ExitCode.Error;
        console.error(errorMessage(formatApiError(err)));
      }
    });

  return cmd;
}