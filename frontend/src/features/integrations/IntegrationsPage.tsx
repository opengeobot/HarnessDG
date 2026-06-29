/**
 * 功能: Agent 接入占位页面 (OpenClaw/QwenPaw 接入向导)。骨架阶段无业务逻辑。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { PlaceholderPage } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';

export function IntegrationsPage() {
  useDocumentTitle('Agent 接入');
  return (
    <PlaceholderPage
      title="Agent 接入"
      description="OpenClaw / QwenPaw 接入向导将在 P1 实现。"
    />
  );
}
