/**
 * 功能: 访问与凭据页面。展示当前主体信息、Scope 列表、PAT 管理。
 *       Scope 来源于认证会话（/me），安全判断以后端为准。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Empty,
  Flex,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { useState } from 'react';
import { useAuth } from '@/app/auth';
import { useDocumentTitle } from '@/shared/hooks';
import { useTranslation } from 'react-i18next';
import { isApiError } from '@/shared/api';
import { createPat, listPats, revokePat } from './api';

const SCOPE_COLOR: Record<string, string> = {
  'asset:read': 'blue',
  'asset:manage': 'geekblue',
  'admin:all': 'red',
};

export function AccessPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('access.title'));
  const { principal, scopes } = useAuth();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [createOpen, setCreateOpen] = useState(false);
  const [createdToken, setCreatedToken] = useState<string | null>(null);
  const [form] = Form.useForm<{ name: string }>();

  const scopeList = Array.from(scopes).sort();
  const canManageTokens = scopes.has('token:create');

  const tokensQuery = useQuery({
    queryKey: ['pats'],
    queryFn: listPats,
    enabled: canManageTokens,
  });

  const createMutation = useMutation({
    mutationFn: createPat,
    onSuccess: (result) => {
      setCreatedToken(result.token);
      void queryClient.invalidateQueries({ queryKey: ['pats'] });
      form.resetFields();
      message.success(t('access.tokenCreated'));
    },
    onError: (error) => {
      message.error(isApiError(error) ? error.message : t('access.tokenCreateFailed'));
    },
  });

  const revokeMutation = useMutation({
    mutationFn: revokePat,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['pats'] });
      message.success(t('access.tokenRevoked'));
    },
    onError: (error) => {
      message.error(isApiError(error) ? error.message : t('access.tokenRevokeFailed'));
    },
  });

  const handleCreate = () => {
    form.validateFields().then((values) => {
      createMutation.mutate({ name: values.name });
    }).catch(() => undefined);
  };

  return (
    <Flex vertical gap={16}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        {t('access.title')}
      </Typography.Title>

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

      <Card
        title={t('access.apiTokens')}
        size="small"
        extra={
          canManageTokens ? (
            <Button type="primary" size="small" onClick={() => setCreateOpen(true)}>
              {t('access.createToken')}
            </Button>
          ) : null
        }
      >
        {!canManageTokens ? (
          <Empty description={t('access.noTokenPermission')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Table
            rowKey="tokenId"
            size="small"
            loading={tokensQuery.isLoading}
            dataSource={tokensQuery.data ?? []}
            pagination={{ pageSize: 10, showSizeChanger: false }}
            locale={{ emptyText: <Empty description={t('access.noTokens')} /> }}
            columns={[
              { title: t('common.name'), dataIndex: 'name' },
              {
                title: t('common.status'),
                dataIndex: 'status',
                render: (status: string) => (
                  <Tag color={status === 'ACTIVE' ? 'green' : 'default'}>{status}</Tag>
                ),
              },
              {
                title: t('access.expiresAt'),
                dataIndex: 'expiresAt',
                render: (v: string) => new Date(v).toLocaleString(),
              },
              {
                title: t('common.actions'),
                render: (_, record) => (
                  record.status === 'ACTIVE' ? (
                    <Popconfirm
                      title={t('access.confirmRevoke')}
                      onConfirm={() => revokeMutation.mutate(record.tokenId)}
                    >
                      <Button type="link" size="small" danger loading={revokeMutation.isPending}>
                        {t('access.revokeToken')}
                      </Button>
                    </Popconfirm>
                  ) : null
                ),
              },
            ]}
          />
        )}
      </Card>

      <Modal
        title={t('access.createToken')}
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={handleCreate}
        confirmLoading={createMutation.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label={t('common.name')}
            rules={[{ required: true, message: t('access.tokenNameRequired') }]}
          >
            <Input placeholder={t('access.tokenNamePlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t('access.tokenCreatedTitle')}
        open={!!createdToken}
        onCancel={() => setCreatedToken(null)}
        footer={[
          <Button key="close" type="primary" onClick={() => setCreatedToken(null)}>
            {t('access.tokenCopiedAck')}
          </Button>,
        ]}
      >
        <Alert
          type="warning"
          showIcon
          message={t('access.tokenShownOnce')}
          style={{ marginBottom: 12 }}
        />
        <Typography.Paragraph copyable={{ text: createdToken ?? '' }}>
          <Typography.Text code>{createdToken}</Typography.Text>
        </Typography.Paragraph>
      </Modal>
    </Flex>
  );
}
