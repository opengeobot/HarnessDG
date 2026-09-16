// AIHub CLI 输出格式化模块
// 作者: AxeXie
// 创建时间: 2026-07-30

/** 是否输出 JSON 格式 */
export function isJsonOutput(): boolean {
  return process.argv.includes('--json');
}

/** 格式化对象为 JSON 输出 */
export function outputJson(data: unknown): string {
  return JSON.stringify(data, null, 2);
}

/** 通用成功消息 */
export function successMessage(message: string): string {
  if (isJsonOutput()) {
    return JSON.stringify({ success: true, message });
  }
  return `✓ ${message}`;
}

/** 通用错误消息 */
export function errorMessage(message: string): string {
  if (isJsonOutput()) {
    return JSON.stringify({ success: false, error: message });
  }
  return `✗ ${message}`;
}

/** 打印 ASCII 表格 */
export function printTable(rows: Record<string, unknown>[], columns: string[]): void {
  if (rows.length === 0) {
    console.log('(no results)');
    return;
  }

  // 计算每列宽度
  const widths = new Map<string, number>();
  for (const col of columns) {
    widths.set(col, col.length);
  }
  for (const row of rows) {
    for (const col of columns) {
      const val = String(row[col] ?? '');
      if (val.length > widths.get(col)!) {
        widths.set(col, val.length);
      }
    }
  }

  // 打印表头
  const header = columns.map(col => col.padEnd(widths.get(col)!)).join('  ');
  console.log(header);
  console.log(columns.map(() => '-'.repeat(widths.get('') || 1)).map((_, i) => '-'.repeat(widths.get(columns[i])!)).join('  '));

  // 打印数据行
  for (const row of rows) {
    const line = columns.map(col => String(row[col] ?? '').padEnd(widths.get(col)!)).join('  ');
    console.log(line);
  }
}

/** 格式化文件大小 */
export function formatFileSize(bytes: number | null | undefined): string {
  if (bytes == null || bytes < 0) return '-';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

/** 打印进度条 */
export function printProgress(current: number, total: number, label: string): void {
  const pct = total > 0 ? Math.min((current / total) * 100, 100) : 0;
  const barLen = 30;
  const filled = Math.round((pct / 100) * barLen);
  const bar = '█'.repeat(filled) + '░'.repeat(barLen - filled);
  const sizeCurr = formatFileSize(current);
  const sizeTotal = formatFileSize(total);
  process.stdout.write(`\r  ${label} [${bar}] ${pct.toFixed(1)}% ${sizeCurr}/${sizeTotal}`);
  if (current >= total) {
    process.stdout.write('\n');
  }
}