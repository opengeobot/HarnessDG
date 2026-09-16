// AIHub CLI 共享错误处理模块
// 作者: AxeXie
// 创建时间: 2026-07-30

/** CLI 退出码 */
export enum ExitCode {
  Success = 0,
  Error = 1,
  AuthFailed = 2,
  NetworkError = 3,
}

/** 基础 CLI 错误 */
export class CliError extends Error {
  public readonly exitCode: ExitCode;

  constructor(message: string, exitCode: ExitCode) {
    super(message);
    this.name = 'CliError';
    this.exitCode = exitCode;
  }
}

/** 认证失败错误 */
export class AuthError extends CliError {
  constructor(message: string = 'Authentication failed') {
    super(message, ExitCode.AuthFailed);
    this.name = 'AuthError';
  }
}

/** 网络错误 */
export class NetworkError extends CliError {
  constructor(message: string = 'Network request failed') {
    super(message, ExitCode.NetworkError);
    this.name = 'NetworkError';
  }
}

/** 通用业务错误 */
export class CommandError extends CliError {
  constructor(message: string) {
    super(message, ExitCode.Error);
    this.name = 'CommandError';
  }
}

/** 安全地格式化 API 错误，不泄露敏感信息 */
export function formatApiError(error: unknown): string {
  if (error instanceof CliError) {
    return error.message;
  }

  if (error && typeof error === 'object' && 'message' in error) {
    const msg = String(error.message);
    // 过滤可能的敏感信息（令牌、密码等）
    const safeMsg = msg
      .replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, '[REDACTED_TOKEN]')
      .replace(/password["\s:=]+[^"\\s]+/gi, 'password=[REDACTED]')
      .replace(/Authorization[:\s]+[^"\\s]+/gi, 'Authorization=[REDACTED]');
    return safeMsg;
  }

  return 'An unexpected error occurred';
}