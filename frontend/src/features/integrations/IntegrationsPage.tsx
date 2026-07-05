/**
 * 功能: Agent 接入页面。展示 MCP 接入状态概览与接入引导。
 *       Agent 注册/管理在管理中心操作，本页为接入视角。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { Alert, Card, Descriptions, Flex, List, Space, Tag, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import { useDocumentTitle } from '@/shared/hooks';

interface McpToolInfo {
  name: string;
  description: string;
  write: boolean;
  permission: string;
}

/** MCP Tool 清单（与 tools.yaml + McpToolCatalog 对齐） */
const MCP_TOOLS: McpToolInfo[] = [
  { name: 'asset_search', description: 'Search assets by keyword, type, namespace', write: false, permission: 'asset:read' },
  { name: 'asset_get', description: 'Get a single asset by ID', write: false, permission: 'asset:read' },
  { name: 'asset_list_versions', description: 'List versions for a given asset', write: false, permission: 'asset:read' },
  { name: 'asset_get_version', description: 'Get version details by version ID', write: false, permission: 'asset:read' },
  { name: 'asset_request_download', description: 'Request a download ticket for a published version', write: false, permission: 'asset:read' },
  { name: 'asset_create_draft', description: 'Create a new draft version (write tool, disabled by default)', write: true, permission: 'asset:manage' },
  { name: 'asset_publish_version', description: 'Publish a version (high-risk, disabled by default)', write: true, permission: 'asset:publish' },
  { name: 'asset_delete', description: 'Delete an asset (high-risk, disabled by default)', write: true, permission: 'asset:delete' },
];

export function IntegrationsPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('integrations.title'));

  const readOnlyTools = MCP_TOOLS.filter((t) => !t.write);
  const writeTools = MCP_TOOLS.filter((t) => t.write);

  return (
    <Flex vertical gap={16}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        {t('integrations.title')}
      </Typography.Title>

      <Alert
        type="info"
        showIcon
        message={t('integrations.mcpOverview')}
        description={t('integrations.mcpDescription')}
      />

      {/* MCP 连接信息 */}
      <Card title={t('integrations.connectionInfo')} size="small">
        <Descriptions column={1} size="small" bordered>
          <Descriptions.Item label={t('integrations.protocol')}>
            <Tag color="blue">MCP (Model Context Protocol)</Tag>
          </Descriptions.Item>
          <Descriptions.Item label={t('integrations.endpoint')}>
            <Typography.Text code>/api/v1/mcp</Typography.Text>
          </Descriptions.Item>
          <Descriptions.Item label={t('integrations.writeTools')}>
            <Tag color="orange">{t('integrations.disabledByDefault')}</Tag>
            <Typography.Text type="secondary">
              {t('integrations.writeToolsNote')}
            </Typography.Text>
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 只读 Tool 列表 */}
      <Card title={t('integrations.readOnlyTools')} size="small">
        <List
          dataSource={readOnlyTools}
          renderItem={(tool) => (
            <List.Item>
              <List.Item.Meta
                title={
                  <Space>
                    <Typography.Text code>{tool.name}</Typography.Text>
                    <Tag color="green">R</Tag>
                    <Tag>{tool.permission}</Tag>
                  </Space>
                }
                description={tool.description}
              />
            </List.Item>
          )}
        />
      </Card>

      {/* 写 Tool 列表 */}
      <Card title={t('integrations.writeToolsTitle')} size="small">
        <List
          dataSource={writeTools}
          renderItem={(tool) => (
            <List.Item>
              <List.Item.Meta
                title={
                  <Space>
                    <Typography.Text code>{tool.name}</Typography.Text>
                    <Tag color="red">W</Tag>
                    <Tag>{tool.permission}</Tag>
                  </Space>
                }
                description={tool.description}
              />
            </List.Item>
          )}
        />
      </Card>
    </Flex>
  );
}
