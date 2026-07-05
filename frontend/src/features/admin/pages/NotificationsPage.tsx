/**
 * 功能: 通知管理页面。当前主体站内通知游标列表 + 仅未读过滤 + 标记已读。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { App, Button, Flex, Space, Switch, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { isApiError } from '@/shared/api';
import { QueryBoundary } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';
import { listNotifications, markNotificationRead } from '../api';
import type { NotificationView } from '../types';

export function NotificationsPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('admin.notifications.title'));
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
    onError: (error) => message.error(isApiError(error) ? error.message : t('common.operationFailed')),
  });

  const items = useMemo(
    () => query.data?.pages.flatMap((page) => page.items) ?? [],
    [query.data],
  );

  const columns: ColumnsType<NotificationView> = [
    { title: t('admin.notifications.eventType'), dataIndex: 'eventType', key: 'eventType' },
    { title: t('admin.notifications.i18nKey'), dataIndex: 'i18nKey', key: 'i18nKey' },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Tag color={status === 'UNREAD' ? 'blue' : 'default'}>{status}</Tag>
      ),
    },
    { title: t('admin.notifications.time'), dataIndex: 'createdAt', key: 'createdAt' },
    {
      title: t('common.action'),
      key: 'action',
      render: (_, record) =>
        record.status === 'UNREAD' ? (
          <Button
            type="link"
            size="small"
            loading={readMutation.isPending}
            onClick={() => readMutation.mutate(record.notificationId)}
          >
            {t('admin.notifications.markRead')}
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
          {t('admin.notifications.title')}
        </Typography.Title>
        <Space>
          <span>{t('admin.notifications.unreadOnly')}</span>
          <Switch checked={unreadOnly} onChange={setUnreadOnly} />
          <Button onClick={() => query.refetch()}>{t('common.refresh')}</Button>
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
              {t('assets.loadMore')}
            </Button>
          </Flex>
        ) : null}
      </QueryBoundary>
    </Flex>
  );
}
