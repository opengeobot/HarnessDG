/**
 * 功能: 通知管理页面。当前主体站内通知游标列表 + 仅未读过滤 + 标记已读。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useMemo, useState } from 'react';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { App, Button, Flex, Space, Switch, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { isApiError } from '@/shared/api';
import { QueryBoundary } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';
import { listNotifications, markNotificationRead } from '../api';
import type { NotificationView } from '../types';

export function NotificationsPage() {
  useDocumentTitle('通知中心');
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [unreadOnly, setUnreadOnly] = useState(false);

  const query = useInfiniteQuery({
    queryKey: ['admin', 'notifications', { unreadOnly }],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) => listNotifications({ unreadOnly, cursor: pageParam, limit: 20 }),
    getNextPageParam: (last) => (last.hasMore ? last.nextCursor ?? undefined : undefined),
  });

  const readMutation = useMutation({
    mutationFn: (notificationId: string) => markNotificationRead(notificationId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'notifications'] });
    },
    onError: (error) => message.error(isApiError(error) ? error.message : '操作失败'),
  });

  const items = useMemo(
    () => query.data?.pages.flatMap((page) => page.items) ?? [],
    [query.data],
  );

  const columns: ColumnsType<NotificationView> = [
    { title: '事件类型', dataIndex: 'eventType', key: 'eventType' },
    { title: '文案 Key', dataIndex: 'i18nKey', key: 'i18nKey' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Tag color={status === 'UNREAD' ? 'blue' : 'default'}>{status}</Tag>
      ),
    },
    { title: '时间', dataIndex: 'createdAt', key: 'createdAt' },
    {
      title: '操作',
      key: 'action',
      render: (_, record) =>
        record.status === 'UNREAD' ? (
          <Button
            type="link"
            size="small"
            loading={readMutation.isPending}
            onClick={() => readMutation.mutate(record.notificationId)}
          >
            标记已读
          </Button>
        ) : (
          '-'
        ),
    },
  ];

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          通知中心
        </Typography.Title>
        <Space>
          <span>仅未读</span>
          <Switch checked={unreadOnly} onChange={setUnreadOnly} />
          <Button onClick={() => query.refetch()}>刷新</Button>
        </Space>
      </Flex>

      <QueryBoundary
        isLoading={query.isLoading}
        isError={query.isError}
        error={query.error}
        onRetry={() => query.refetch()}
      >
        <Table<NotificationView>
          rowKey="notificationId"
          columns={columns}
          dataSource={items}
          pagination={false}
        />
        {query.hasNextPage ? (
          <Flex justify="center" style={{ marginTop: 16 }}>
            <Button onClick={() => query.fetchNextPage()} loading={query.isFetchingNextPage}>
              加载更多
            </Button>
          </Flex>
        ) : null}
      </QueryBoundary>
    </Flex>
  );
}
