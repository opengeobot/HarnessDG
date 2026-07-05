/**
 * 功能: 讨论面板——资产详情页内嵌的讨论线程/评论组件。
 *       增强：嵌套回复、编辑修订标记、删除后 Tombstone、游标分页。
 * 时间: 2026-07-06
 * 作者: AxeXie
 */
import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Card,
  Empty,
  Flex,
  Input,
  List,
  Popconfirm,
  Space,
  Tag,
  Typography,
} from 'antd';
import { useTranslation } from 'react-i18next';
import { isApiError } from '@/shared/api';
import {
  createComment,
  createThread,
  editComment,
  listComments,
  listThreads,
  retractComment,
} from './api';
import type { CommentView, ThreadView } from './types';

interface DiscussionPanelProps {
  assetId: string;
}

// ---------------------------------------------------------------------------
// CommentItem: 单条评论（含嵌套回复渲染）
// ---------------------------------------------------------------------------

interface CommentItemProps {
  comment: CommentView;
  threadId: string;
  allComments: CommentView[];
  depth: number;
}

function CommentItem({ comment, threadId, allComments, depth }: CommentItemProps) {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [editBody, setEditBody] = useState(comment.body);
  const [replying, setReplying] = useState(false);
  const [replyBody, setReplyBody] = useState('');
  const [showReplies, setShowReplies] = useState(false);

  const replies = useMemo(
    () => allComments.filter((c) => c.parentId === comment.commentId),
    [allComments, comment.commentId],
  );

  const editMut = useMutation({
    mutationFn: () => editComment(threadId, comment.commentId, editBody),
    onSuccess: () => {
      message.success(t('discussion.commentPosted'));
      setEditing(false);
      void queryClient.invalidateQueries({ queryKey: ['comments', threadId] });
    },
    onError: (err: unknown) => message.error(isApiError(err) ? err.message : t('discussion.commentFailed')),
  });

  const retractMut = useMutation({
    mutationFn: () => retractComment(threadId, comment.commentId),
    onSuccess: () => {
      message.success(t('discussion.retracted'));
      void queryClient.invalidateQueries({ queryKey: ['comments', threadId] });
    },
    onError: (err: unknown) => message.error(isApiError(err) ? err.message : t('discussion.commentFailed')),
  });

  const replyMut = useMutation({
    mutationFn: () => createComment(threadId, replyBody, comment.commentId),
    onSuccess: () => {
      message.success(t('discussion.commentPosted'));
      setReplyBody('');
      setReplying(false);
      setShowReplies(true);
      void queryClient.invalidateQueries({ queryKey: ['comments', threadId] });
    },
    onError: (err: unknown) => message.error(isApiError(err) ? err.message : t('discussion.commentFailed')),
  });

  const isTombstone = comment.status === 'HIDDEN' || comment.status === 'RETRACTED';

  return (
    <div style={{ marginLeft: depth > 0 ? 24 : 0, marginTop: 8 }}>
      <List.Item style={{ padding: '8px 0', borderBottom: 'none' }}>
        <Flex vertical gap={4} style={{ width: '100%' }}>
          {/* Header row */}
          <Flex justify="space-between" align="center">
            <Space size={4}>
              <Typography.Text strong>{comment.createdBy}</Typography.Text>
              {comment.revisionCount > 0 && (
                <Tag color="orange" style={{ fontSize: 10 }}>{t('discussion.edited')}</Tag>
              )}
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {new Date(comment.createdAt).toLocaleString()}
              </Typography.Text>
            </Space>
            <Space size={4}>
              {isTombstone && (
                <Tag color={comment.status === 'HIDDEN' ? 'red' : 'default'}>
                  {comment.status === 'HIDDEN' ? t('discussion.hidden') : t('discussion.retracted')}
                </Tag>
              )}
              {!isTombstone && (
                <>
                  <Button size="small" type="text" onClick={() => setReplying(!replying)}>
                    {t('discussion.reply')}
                  </Button>
                  <Button size="small" type="text" onClick={() => { setEditing(!editing); setEditBody(comment.body); }}>
                    {t('discussion.edit')}
                  </Button>
                  <Popconfirm title={t('discussion.confirmRetract')} onConfirm={() => retractMut.mutate()}>
                    <Button size="small" type="text" danger>{t('discussion.retract')}</Button>
                  </Popconfirm>
                </>
              )}
            </Space>
          </Flex>

          {/* Body */}
          {isTombstone ? (
            <Typography.Text type="secondary" italic>
              {t('discussion.tombstone')}
            </Typography.Text>
          ) : editing ? (
            <Flex gap={8}>
              <Input.TextArea
                rows={2}
                value={editBody}
                onChange={(e) => setEditBody(e.target.value)}
                maxLength={10000}
                showCount
              />
              <Flex vertical>
                <Button
                  type="primary"
                  size="small"
                  loading={editMut.isPending}
                  disabled={!editBody.trim()}
                  onClick={() => editMut.mutate()}
                >
                  {t('discussion.save')}
                </Button>
                <Button size="small" onClick={() => setEditing(false)}>{t('discussion.cancel')}</Button>
              </Flex>
            </Flex>
          ) : (
            <Typography.Paragraph style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
              {comment.body}
            </Typography.Paragraph>
          )}

          {/* Reply input */}
          {replying && (
            <Flex gap={8} style={{ marginTop: 4 }}>
              <Input.TextArea
                rows={2}
                placeholder={t('discussion.replyPlaceholder', { author: comment.createdBy })}
                value={replyBody}
                onChange={(e) => setReplyBody(e.target.value)}
                maxLength={10000}
                showCount
              />
              <Button
                type="primary"
                size="small"
                disabled={!replyBody.trim()}
                loading={replyMut.isPending}
                onClick={() => replyMut.mutate()}
              >
                {t('discussion.publish')}
              </Button>
            </Flex>
          )}

          {/* Nested replies toggle */}
          {replies.length > 0 && (
            <Button
              type="link"
              size="small"
              onClick={() => setShowReplies(!showReplies)}
              style={{ padding: 0, width: 'fit-content' }}
            >
              {showReplies
                ? t('discussion.hideReplies')
                : t('discussion.showReplies', { count: replies.length })}
            </Button>
          )}
        </Flex>
      </List.Item>

      {/* Render nested replies */}
      {showReplies && replies.map((reply) => (
        <CommentItem
          key={reply.commentId}
          comment={reply}
          threadId={threadId}
          allComments={allComments}
          depth={depth + 1}
        />
      ))}
    </div>
  );
}

// ---------------------------------------------------------------------------
// DiscussionPanel
// ---------------------------------------------------------------------------

export function DiscussionPanel({ assetId }: DiscussionPanelProps) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { t } = useTranslation();
  const [newTitle, setNewTitle] = useState('');
  const [selectedThread, setSelectedThread] = useState<string | null>(null);
  const [newComment, setNewComment] = useState('');

  const threadsQuery = useQuery({
    queryKey: ['threads', assetId],
    queryFn: () => listThreads(assetId, undefined, 50),
  });

  const commentsQuery = useQuery({
    queryKey: ['comments', selectedThread],
    queryFn: () => listComments(selectedThread!),
    enabled: !!selectedThread,
  });

  const createThreadMut = useMutation({
    mutationFn: () => createThread(assetId, newTitle),
    onSuccess: () => {
      message.success(t('discussion.threadCreated'));
      setNewTitle('');
      void queryClient.invalidateQueries({ queryKey: ['threads', assetId] });
    },
    onError: (err: unknown) => message.error(isApiError(err) ? err.message : t('discussion.createFailed')),
  });

  const createCommentMut = useMutation({
    mutationFn: () => createComment(selectedThread!, newComment),
    onSuccess: () => {
      message.success(t('discussion.commentPosted'));
      setNewComment('');
      void queryClient.invalidateQueries({ queryKey: ['comments', selectedThread] });
      void queryClient.invalidateQueries({ queryKey: ['threads', assetId] });
    },
    onError: (err: unknown) => message.error(isApiError(err) ? err.message : t('discussion.commentFailed')),
  });

  // Top-level comments (no parent)
  const topLevelComments = useMemo(
    () => (commentsQuery.data ?? []).filter((c) => !c.parentId),
    [commentsQuery.data],
  );

  const threads = threadsQuery.data?.items ?? [];

  return (
    <Card title={t('discussion.title')}>
      <Flex vertical gap={12}>
        {/* New thread input */}
        <Flex gap={8}>
          <Input
            placeholder={t('discussion.newTitlePlaceholder')}
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
            {t('discussion.newThread')}
          </Button>
        </Flex>

        {/* Thread list */}
        <List
          dataSource={threads}
          loading={threadsQuery.isLoading}
          locale={{ emptyText: <Empty description={t('discussion.noDiscussions')} /> }}
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
                  <Typography.Text type="secondary">{thread.commentCount} {t('discussion.comments')}</Typography.Text>
                  <Typography.Text type="secondary">{thread.createdBy}</Typography.Text>
                </Space>
              </Flex>
            </List.Item>
          )}
        />


        {/* Selected thread comments */}
        {selectedThread && (
          <Card
            size="small"
            title={`${t('discussion.commentsTitle')} - ${threads.find((t2: ThreadView) => t2.threadId === selectedThread)?.title}`}
            extra={<Button size="small" onClick={() => setSelectedThread(null)}>{t('common.close')}</Button>}
          >
            <Flex vertical gap={4}>
              {/* Comments list with nested replies */}
              {commentsQuery.isLoading ? (
                <Typography.Text type="secondary">{t('discussion.noComments')}</Typography.Text>
              ) : topLevelComments.length === 0 ? (
                <Empty description={t('discussion.noComments')} />
              ) : (
                <List
                  dataSource={topLevelComments}
                  renderItem={(comment: CommentView) => (
                    <CommentItem
                      key={comment.commentId}
                      comment={comment}
                      threadId={selectedThread}
                      allComments={commentsQuery.data ?? []}
                      depth={0}
                    />
                  )}
                />
              )}

              {/* New comment input */}
              <Flex gap={8} style={{ marginTop: 12 }}>
                <Input.TextArea
                  rows={2}
                  placeholder={t('discussion.commentPlaceholder')}
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
                  {t('discussion.publish')}
                </Button>
              </Flex>
            </Flex>
          </Card>
        )}
      </Flex>
    </Card>
  );
}
