/**
 * 功能: 权限清单页面（只读）。展示系统权限定义 resource:action。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useQuery } from '@tanstack/react-query';
import { Flex, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { QueryBoundary } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';
import { listPermissions } from '../api';
import type { PermissionView } from '../types';

export function PermissionsPage() {
  useDocumentTitle('权限清单');
  const query = useQuery({ queryKey: ['admin', 'permissions'], queryFn: listPermissions });

  const columns: ColumnsType<PermissionView> = [
    {
      title: '权限编码',
      dataIndex: 'permissionCode',
      key: 'permissionCode',
      render: (code: string) => <Tag color="blue">{code}</Tag>,
    },
    { title: '资源', dataIndex: 'resource', key: 'resource' },
    { title: '动作', dataIndex: 'action', key: 'action' },
    { title: '文案 Key', dataIndex: 'i18nKey', key: 'i18nKey' },
  ];

  return (
    <Flex vertical gap={16}>
      <Space>
        <Typography.Title level={4} style={{ margin: 0 }}>
          权限清单
        </Typography.Title>
        <Typography.Text type="secondary">只读，权限由后端定义</Typography.Text>
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
