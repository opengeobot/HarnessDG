/**
 * 功能: 通知管理页面。Tab 1：当前主体站内通知游标列表 + 仅未读过滤 + 标记已读。
 *       Tab 2（system:observe）：Outbox 事件 + Webhook 投递状态 + 手动重试。
 * 时间: 2026-07-06
 * 作者: AxeXie
 */
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Badge, Button, Flex, Space, Switch, Table, Tabs, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { isApiError } from '@/shared/api';
import { QueryBoundary } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';
import {
  listNotifications,
  listOutboxEvents,
  listWebhookDeliveries,
  getOutboxPendingCount,
  markNotificationRead,
  retryWebhookDelivery,
} from '../api';
import type { NotificationView, OutboxEventView, WebhookDeliveryView } from '../types';

export function NotificationsPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('admin.notifications.title'));

  return (
    <Flex vertical gap={16}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        {t('admin.notifications.title')}
      </Typography.Title>
      <Tabs
        defaultActiveKey="user"
        items={[
          {
            key: 'user',
            label: t('admin.notifications.userTab'),
            children: <UserNotificationsTab />,
          },
          {
            key: 'admin',
            label: t('admin.notifications.adminTab'),
            children: <NotificationAdminTab />,
          },
        ]}
      />
    </Flex>
  );
}

/* ---------------- Tab 1: 用户通知 ---------------- */

function UserNotificationsTab() {
  const { t } = useTranslation();
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
    <Flex vertical gap={12}>
      <Flex justify="flex-end" align="center" wrap gap={12}>
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

/* ---------------- Tab 2: 通知管理（Admin） ---------------- */

function NotificationAdminTab() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const outboxQuery = useQuery({
    queryKey: ['admin', 'notifications', 'outbox'],
    queryFn: () => listOutboxEvents(50),
  });

  const deliveryQuery = useQuery({
    queryKey: ['admin', 'notifications', 'deliveries'],
    queryFn: () => listWebhookDeliveries(50),
  });

  const pendingQuery = useQuery({
    queryKey: ['admin', 'notifications', 'outbox', 'pending'],
    queryFn: () => getOutboxPendingCount(),
  });

  const retryMutation = useMutation({
    mutationFn: (deliveryId: string) => retryWebhookDelivery(deliveryId),
    onSuccess: () => {
      message.success(t('admin.notifications.retrySuccess'));
      void queryClient.invalidateQueries({ queryKey: ['admin', 'notifications'] });
    },
    onError: (error) => message.error(isApiError(error) ? error.message : t('common.operationFailed')),
  });

  const outboxColumns: ColumnsType<OutboxEventView> = [
    { title: t('admin.notifications.eventId'), dataIndex: 'eventId', key: 'eventId', ellipsis: true },
    { title: t('admin.notifications.aggregateType'), dataIndex: 'aggregateType', key: 'aggregateType' },
    { title: t('admin.notifications.eventType'), dataIndex: 'eventType', key: 'eventType' },
    {
      title: t('common.status'),
      dataIndex: 'processed',
      key: 'processed',
      render: (processed: boolean) => (
        <Tag color={processed ? 'green' : 'orange'}>
          {processed ? t('admin.notifications.processed') : t('admin.notifications.pending')}
        </Tag>
      ),
    },
    { title: t('admin.notifications.time'), dataIndex: 'occurredAt', key: 'occurredAt' },
  ];

  const deliveryColumns: ColumnsType<WebhookDeliveryView> = [
    { title: t('admin.notifications.deliveryId'), dataIndex: 'deliveryId', key: 'deliveryId', ellipsis: true },
    { title: t('admin.notifications.targetUrl'), dataIndex: 'targetUrl', key: 'targetUrl', ellipsis: true },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => {
        const color = status === 'DELIVERED' ? 'green' : status === 'FAILED' ? 'red' : status === 'DEAD' ? 'volcano' : 'blue';
        return <Tag color={color}>{status}</Tag>;
      },
    },
    { title: t('admin.notifications.attempts'), dataIndex: 'attempts', key: 'attempts' },
    {
      title: t('admin.notifications.lastResponse'),
      key: 'lastResponse',
      render: (_, record) => record.lastResponseCode ?? record.lastError ?? '-',
    },
    { title: t('admin.notifications.time'), dataIndex: 'createdAt', key: 'createdAt' },
    {
      title: t('common.action'),
      key: 'action',
      render: (_, record) =>
        record.status === 'FAILED' || record.status === 'DEAD' ? (
          <Button
            type="link"
            size="small"
            danger
            loading={retryMutation.isPending}
            onClick={() => retryMutation.mutate(record.deliveryId)}
          >
            {t('admin.notifications.retry')}
          </Button>
        ) : (
          '-'
        ),
    },
  ];

  return (
    <Flex vertical gap={24}>
      {/* Outbox 事件 */}
      <Flex vertical gap={8}>
        <Flex justify="space-between" align="center">
          <Typography.Title level={5} style={{ margin: 0 }}>
            {t('admin.notifications.outboxTitle')}
            {pendingQuery.data && pendingQuery.data.count > 0 && (
              <Badge
                count={pendingQuery.data.count}
                style={{ marginLeft: 8 }}
                overflowCount={999}
              />
            )}
          </Typography.Title>
          <Button
            size="small"
            onClick={() => outboxQuery.refetch()}
            loading={outboxQuery.isFetching}
          >
            {t('common.refresh')}
          </Button>
        </Flex>
        <QueryBoundary
          isLoading={outboxQuery.isLoading}
          isError={outboxQuery.isError}
          error={outboxQuery.error}
          onRetry={() => outboxQuery.refetch()}
        >
          <Table<OutboxEventView>
            rowKey="eventId"
            size="small"
            columns={outboxColumns}
            dataSource={outboxQuery.data ?? []}
            pagination={false}
          />
        </QueryBoundary>
      </Flex>

      {/* Webhook 投递 */}
      <Flex vertical gap={8}>
        <Flex justify="space-between" align="center">
          <Typography.Title level={5} style={{ margin: 0 }}>
            {t('admin.notifications.deliveryTitle')}
          </Typography.Title>
          <Button
            size="small"
            onClick={() => deliveryQuery.refetch()}
            loading={deliveryQuery.isFetching}
          >
            {t('common.refresh')}
          </Button>
        </Flex>
        <QueryBoundary
          isLoading={deliveryQuery.isLoading}
          isError={deliveryQuery.isError}
          error={deliveryQuery.error}
          onRetry={() => deliveryQuery.refetch()}
        >
          <Table<WebhookDeliveryView>
            rowKey="deliveryId"
            size="small"
            columns={deliveryColumns}
            dataSource={deliveryQuery.data ?? []}
            pagination={false}
          />
        </QueryBoundary>
      </Flex>
    </Flex>
  );
}
