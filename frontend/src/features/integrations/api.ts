/**
 * 功能: Agent 接入配置包 API。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
import { apiClient } from '@/shared/api';

export interface SkillTemplateView {
  name: string;
  description: string;
  path: string;
}

export interface ExampleCommandView {
  label: string;
  command: string;
}

export interface AgentBundleView {
  mcpEndpointUrl: string;
  openApiUrl: string;
  skillTemplates: SkillTemplateView[];
  exampleCommands: ExampleCommandView[];
}

/** 获取 Agent 接入配置包 */
export function getAgentBundle(): Promise<AgentBundleView> {
  return apiClient.get<AgentBundleView>('/integrations/agent-bundle');
}
