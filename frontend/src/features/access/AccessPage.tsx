/**
 * 功能: 访问与凭据页面。展示当前主体信息、Scope 列表、API Token 占位。
 *       Scope 来源于认证会话（/me），安全判断以后端为准。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { Card, Descriptions, Empty, Flex, Space, Tag, Typography } from 'antd';
import { useAuth } from '@/app/auth';
import { useDocumentTitle } from '@/shared/hooks';
import { useTranslation } from 'react-i18next';

const SCOPE_COLOR: Record<string, string> = {
  'asset:read': 'blue',
  'asset:manage': 'geekblue',
  'admin:all': 'red',
};

export function AccessPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('access.title'));
  const { principal, scopes } = useAuth();

  const scopeList = Array.from(scopes).sort();

  return (
    <Flex vertical gap={16}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        {t('access.title')}
      </Typography.Title>

      {/* 当前主体信息 */}
      <Card title={t('access.principalInfo')} size="small">
        {principal ? (
          <Descriptions column={2} size="small" bordered>
            <Descriptions.Item label={t('access.principalId')}>
              <Typography.Text code>{principal.principalId}</Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label={t('access.principalType')}>
              <Tag>{principal.principalType}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('access.subject')}>
              {principal.subject}
            </Descriptions.Item>
            <Descriptions.Item label={t('access.displayName')}>
              {principal.displayName ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('access.organizationId')}>
              {principal.organizationId ? (
                <Typography.Text code>{principal.organizationId}</Typography.Text>
              ) : '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('access.locale')}>
              {principal.locale}
            </Descriptions.Item>
            <Descriptions.Item label={t('access.roles')} span={2}>
              <Space wrap>
                {principal.roles.length > 0 ? (
                  principal.roles.map((role) => (
                    <Tag key={role} color="gold">{role}</Tag>
                  ))
                ) : (
                  <Typography.Text type="secondary">-</Typography.Text>
                )}
              </Space>
            </Descriptions.Item>
          </Descriptions>
        ) : (
          <Empty description={t('access.notAuthenticated')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}
      </Card>

      {/* Scope 列表 */}
      <Card title={t('access.scopes')} size="small">
        {scopeList.length > 0 ? (
          <Space wrap>
            {scopeList.map((scope) => (
              <Tag key={scope} color={SCOPE_COLOR[scope] ?? 'default'}>
                {scope}
              </Tag>
            ))}
          </Space>
        ) : (
          <Empty
            description={t('access.noScopes')}
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        )}
      </Card>

      {/* API Token（占位——后端 API 待实现） */}
      <Card title={t('access.apiTokens')} size="small">
        <Empty
          description={t('access.tokensComingSoon')}
          image={Empty.PRESENTED_IMAGE_SIMPLE}
        />
      </Card>
    </Flex>
  );
}
