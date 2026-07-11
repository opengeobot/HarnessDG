/**
 * 功能: Agent 接入页面。从 agent-bundle API 展示 MCP/OpenAPI 端点、Skill 模板与示例命令。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { Alert, Card, Descriptions, Flex, List, Skeleton, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { QueryBoundary } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';
import { getAgentBundle } from './api';

export function IntegrationsPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('integrations.title'));

  const query = useQuery({
    queryKey: ['integrations', 'agent-bundle'],
    queryFn: getAgentBundle,
  });

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

      <QueryBoundary
        isLoading={query.isLoading}
        isError={query.isError}
        error={query.error}
        onRetry={() => query.refetch()}
      >
        {query.isLoading && !query.data ? (
          <Skeleton active paragraph={{ rows: 8 }} />
        ) : (
          <>
            <Card title={t('integrations.connectionInfo')} size="small">
              <Descriptions column={1} size="small" bordered>
                <Descriptions.Item label={t('integrations.mcpEndpoint')}>
                  <Typography.Text code copyable>
                    {query.data?.mcpEndpointUrl ?? '-'}
                  </Typography.Text>
                </Descriptions.Item>
                <Descriptions.Item label={t('integrations.openApiUrl')}>
                  <Typography.Text code copyable>
                    {query.data?.openApiUrl ?? '-'}
                  </Typography.Text>
                </Descriptions.Item>
              </Descriptions>
            </Card>

            <Card title={t('integrations.skillTemplates')} size="small">
              <List
                dataSource={query.data?.skillTemplates ?? []}
                locale={{ emptyText: t('integrations.noSkills') }}
                renderItem={(skill) => (
                  <List.Item>
                    <List.Item.Meta
                      title={<Typography.Text code>{skill.name}</Typography.Text>}
                      description={
                        <>
                          <Typography.Paragraph style={{ marginBottom: 4 }}>
                            {skill.description}
                          </Typography.Paragraph>
                          <Typography.Text type="secondary" code>
                            {skill.path}
                          </Typography.Text>
                        </>
                      }
                    />
                  </List.Item>
                )}
              />
            </Card>

            <Card title={t('integrations.exampleCommands')} size="small">
              <List
                dataSource={query.data?.exampleCommands ?? []}
                locale={{ emptyText: t('integrations.noCommands') }}
                renderItem={(item) => (
                  <List.Item>
                    <List.Item.Meta
                      title={item.label}
                      description={<Typography.Text code copyable>{item.command}</Typography.Text>}
                    />
                  </List.Item>
                )}
              />
            </Card>
          </>
        )}
      </QueryBoundary>
    </Flex>
  );
}
