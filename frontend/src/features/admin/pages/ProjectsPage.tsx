/**
 * 功能: 项目管理页面。先选择组织，再查看/创建该组织下的项目。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Flex,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { isApiError } from '@/shared/api';
import { QueryBoundary } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';
import { createProject, listOrganizationProjects, listOrganizations } from '../api';
import type { CreateProjectRequest, ProjectView } from '../types';

export function ProjectsPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('admin.projects.title'));
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [organizationId, setOrganizationId] = useState<string | undefined>();
  const [createOpen, setCreateOpen] = useState(false);
  const [createForm] = Form.useForm<CreateProjectRequest>();

  const orgsQuery = useQuery({ queryKey: ['admin', 'organizations'], queryFn: listOrganizations });

  const projectsQuery = useQuery({
    queryKey: ['admin', 'projects', organizationId],
    queryFn: () => listOrganizationProjects(organizationId!),
    enabled: Boolean(organizationId),
  });

  const createMutation = useMutation({
    mutationFn: (payload: CreateProjectRequest) => createProject(organizationId!, payload),
    onSuccess: () => {
      message.success(t('admin.projects.projectCreated'));
      setCreateOpen(false);
      createForm.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['admin', 'projects', organizationId] });
    },
    onError: (error) => message.error(isApiError(error) ? error.message : t('common.operationFailed')),
  });

  const columns: ColumnsType<ProjectView> = [
    {
      title: t('admin.projects.project'),
      key: 'project',
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{record.name}</Typography.Text>
          <Typography.Text type="secondary" code>
            {record.code}
          </Typography.Text>
        </Space>
      ),
    },
    { title: t('admin.projects.projectId'), dataIndex: 'projectId', key: 'projectId' },
    { title: t('common.status'), dataIndex: 'status', key: 'status', render: (s: string) => <Tag>{s}</Tag> },
    { title: t('admin.projects.createdAt'), dataIndex: 'createdAt', key: 'createdAt' },
  ];

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('admin.projects.title')}
        </Typography.Title>
        <Space wrap>
          <Select
            style={{ width: 260 }}
            placeholder={t('admin.projects.selectOrg')}
            loading={orgsQuery.isLoading}
            value={organizationId}
            onChange={setOrganizationId}
            options={(orgsQuery.data ?? []).map((org) => ({
              value: org.organizationId,
              label: `${org.name} (${org.code})`,
            }))}
          />
          <Button
            type="primary"
            disabled={!organizationId}
            onClick={() => setCreateOpen(true)}
          >
            {t('admin.projects.createProject')}
          </Button>
        </Space>
      </Flex>

      {organizationId ? (
        <QueryBoundary
          isLoading={projectsQuery.isLoading}
          isError={projectsQuery.isError}
          error={projectsQuery.error}
          onRetry={() => projectsQuery.refetch()}
        >
          <Table<ProjectView>
            rowKey="projectId"
            columns={columns}
            dataSource={projectsQuery.data ?? []}
          />
        </QueryBoundary>
      ) : (
        <Typography.Text type="secondary">{t('admin.projects.selectOrgHint')}</Typography.Text>
      )}

      <Modal
        title={t('admin.projects.createProject')}
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
        confirmLoading={createMutation.isPending}
        destroyOnClose
      >
        <Form
          form={createForm}
          layout="vertical"
          preserve={false}
          onFinish={(values) => createMutation.mutate(values)}
        >
          <Form.Item name="code" label={t('admin.projects.projectCode')} rules={[{ required: true }]}>
            <Input placeholder={t('admin.projects.codePlaceholder')} />
          </Form.Item>
          <Form.Item name="name" label={t('admin.projects.projectName')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </Flex>
  );
}
