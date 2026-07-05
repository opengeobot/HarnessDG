/**
 * 功能: 讨论面板——资产详情页内嵌的讨论线程/评论组件。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Card,
  Empty,
  Flex,
  Input,
  List,
  Space,
  Tag,
  Typography,
} from 'antd';
import { isApiError } from '@/shared/api';
import {
  createComment,
  createThread,
  listComments,
  listThreads,
} from './api';
import type { CommentView, ThreadView } from './types';

interface DiscussionPanelProps {
  assetId: string;
}

export function DiscussionPanel({ assetId }: DiscussionPanelProps) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [newTitle, setNewTitle] = useState('');
  const [selectedThread, setSelectedThread] = useState<string | null>(null);
  const [newComment, setNewComment] = useState('');

  const threadsQuery = useQuery({
    queryKey: ['threads', assetId],
    queryFn: () => listThreads(assetId),
  });

  const commentsQuery = useQuery({
    queryKey: ['comments', selectedThread],
    queryFn: () => listComments(selectedThread!),
    enabled: !!selectedThread,
  });

  const createThreadMut = useMutation({
    mutationFn: () => createThread(assetId, newTitle),
    onSuccess: () => {
      message.success('线程已创建');
      setNewTitle('');
      void queryClient.invalidateQueries({ queryKey: ['threads', assetId] });
    },
    onError: (err: unknown) => message.error(isApiError(err) ? err.message : '创建失败'),
  });

  const createCommentMut = useMutation({
    mutationFn: () => createComment(selectedThread!, newComment),
    onSuccess: () => {
      message.success('评论已发表');
      setNewComment('');
      void queryClient.invalidateQueries({ queryKey: ['comments', selectedThread] });
      void queryClient.invalidateQueries({ queryKey: ['threads', assetId] });
    },
    onError: (err: unknown) => message.error(isApiError(err) ? err.message : '评论失败'),
  });

  const threads = threadsQuery.data?.items ?? [];

  return (
    <Card title="讨论">
      <Flex vertical gap={12}>
        <Flex gap={8}>
          <Input
            placeholder="新建讨论标题"
            value={newTitle}
            onChange={(e) => setNewTitle(e.target.value)}
            onPressEnter={() => newTitle.trim() && createThreadMut.mutate()}
          />
          <Button
            type="primary"
            disabled={!newTitle.trim()}
            loading={createThreadMut.isPending}
            onClick={() => createThreadMut.mutate()}
          >
            新建
          </Button>
        </Flex>

        <List
          dataSource={threads}
          loading={threadsQuery.isLoading}
          locale={{ emptyText: <Empty description="暂无讨论" /> }}
          renderItem={(thread: ThreadView) => (
            <List.Item
              style={{
                cursor: 'pointer',
                background: selectedThread === thread.threadId ? '#e6f4ff' : undefined,
                padding: '8px 12px',
              }}
              onClick={() => setSelectedThread(thread.threadId)}
            >
              <Flex justify="space-between" style={{ width: '100%' }}>
                <Space>
                  <Typography.Text strong>{thread.title}</Typography.Text>
                  <Tag color={thread.status === 'OPEN' ? 'green' : thread.status === 'LOCKED' ? 'red' : 'default'}>
                    {thread.status}
                  </Tag>
                </Space>
                <Space>
                  <Typography.Text type="secondary">{thread.commentCount} 评论</Typography.Text>
                  <Typography.Text type="secondary">{thread.createdBy}</Typography.Text>
                </Space>
              </Flex>
            </List.Item>
          )}
        />

        {selectedThread && (
          <Card
            size="small"
            title={`评论 - ${threads.find((t) => t.threadId === selectedThread)?.title}`}
            extra={<Button size="small" onClick={() => setSelectedThread(null)}>关闭</Button>}
          >
            <Flex vertical gap={8}>
              <List
                dataSource={commentsQuery.data ?? []}
                loading={commentsQuery.isLoading}
                locale={{ emptyText: <Empty description="暂无评论" /> }}
                renderItem={(comment: CommentView) => (
                  <List.Item>
                    <Flex vertical gap={4} style={{ width: '100%' }}>
                      <Flex justify="space-between">
                        <Typography.Text strong>{comment.createdBy}</Typography.Text>
                        <Space>
                          {comment.status !== 'VISIBLE' && (
                            <Tag color={comment.status === 'HIDDEN' ? 'red' : 'default'}>
                              {comment.status}
                            </Tag>
                          )}
                          <Typography.Text type="secondary">
                            {new Date(comment.createdAt).toLocaleString()}
                          </Typography.Text>
                        </Space>
                      </Flex>
                      {comment.status === 'VISIBLE' ? (
                        <Typography.Paragraph style={{ margin: 0 }}>
                          {comment.body}
                        </Typography.Paragraph>
                      ) : (
                        <Typography.Text type="secondary">
                          [{comment.status === 'RETRACTED' ? '已撤回' : '已隐藏'}]
                        </Typography.Text>
                      )}
                    </Flex>
                  </List.Item>
                )}
              />
              <Flex gap={8}>
                <Input.TextArea
                  rows={2}
                  placeholder="输入评论..."
                  value={newComment}
                  onChange={(e) => setNewComment(e.target.value)}
                  maxLength={10000}
                  showCount
                />
                <Button
                  type="primary"
                  disabled={!newComment.trim()}
                  loading={createCommentMut.isPending}
                  onClick={() => createCommentMut.mutate()}
                >
                  发表
                </Button>
              </Flex>
            </Flex>
          </Card>
        )}
      </Flex>
    </Card>
  );
}
