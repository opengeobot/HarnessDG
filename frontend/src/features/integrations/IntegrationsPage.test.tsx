/**
 * IntegrationsPage 组件测试——MCP 连接信息、只读/写 Tool 列表。
 */
import { describe, it, expect } from 'vitest';
import { screen } from '@testing-library/react';
import { IntegrationsPage } from './IntegrationsPage';
import { renderWithProviders } from '@/test/test-utils';

describe('IntegrationsPage', () => {
  it('渲染页面标题', () => {
    renderWithProviders(<IntegrationsPage />);
    expect(screen.getByText('Agent 接入')).toBeInTheDocument();
  });

  it('渲染 MCP 连接信息卡片', () => {
    renderWithProviders(<IntegrationsPage />);
    expect(screen.getByText('连接信息')).toBeInTheDocument();
    expect(screen.getByText('/api/v1/mcp')).toBeInTheDocument();
  });

  it('显示 MCP 协议 Tag', () => {
    renderWithProviders(<IntegrationsPage />);
    // MCP 出现在 Tag 内
    const tags = screen.getAllByText(/MCP/);
    expect(tags.length).toBeGreaterThanOrEqual(1);
  });

  it('渲染只读 Tool 列表', () => {
    renderWithProviders(<IntegrationsPage />);
    expect(screen.getByText('asset_search')).toBeInTheDocument();
    expect(screen.getByText('asset_get')).toBeInTheDocument();
    expect(screen.getByText('asset_list_versions')).toBeInTheDocument();
  });

  it('渲染写 Tool 列表', () => {
    renderWithProviders(<IntegrationsPage />);
    expect(screen.getByText('asset_create_draft')).toBeInTheDocument();
    expect(screen.getByText('asset_publish_version')).toBeInTheDocument();
    expect(screen.getByText('asset_delete')).toBeInTheDocument();
  });

  it('写 Tool 默认关闭标签', () => {
    renderWithProviders(<IntegrationsPage />);
    expect(screen.getByText('默认关闭')).toBeInTheDocument();
  });
});
