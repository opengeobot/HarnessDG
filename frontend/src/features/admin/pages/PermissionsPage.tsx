/**
 * 功能: 权限清单页面（只读）。展示系统权限定义 resource:action。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Flex, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { QueryBoundary } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';
import { listPermissions } from '../api';
import type { PermissionView } from '../types';

export function PermissionsPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('admin.permissions.title'));
  const query = useQuery({ queryKey: ['admin', 'permissions'], queryFn: listPermissions });

  const columns: ColumnsType<PermissionView> = [
    {
      title: t('admin.permissions.permissionCode'),
      dataIndex: 'permissionCode',
      key: 'permissionCode',
      render: (code: string) => <Tag color="blue">{code}</Tag>,
    },
    { title: t('admin.permissions.resource'), dataIndex: 'resource', key: 'resource' },
    { title: t('admin.permissions.action'), dataIndex: 'action', key: 'action' },
    { title: t('admin.permissions.i18nKey'), dataIndex: 'i18nKey', key: 'i18nKey' },
  ];

  return (
    <Flex vertical gap={16}>
      <Space>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('admin.permissions.title')}
        </Typography.Title>
        <Typography.Text type="secondary">{t('admin.permissions.readonly')}</Typography.Text>
      </Space>

      <QueryBoundary
        isLoading={query.isLoading}
        isError={query.isError}
        error={query.error}
        onRetry={() => query.refetch()}
      >
        <Table<PermissionView>
          rowKey="permissionCode"
          columns={columns}
          dataSource={query.data ?? []}
          pagination={false}
        />
      </QueryBoundary>
    </Flex>
  );
}
