/**
 * 功能: 项目管理页面。先选择组织，再查看/创建该组织下的项目。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useState } from 'react';
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
  useDocumentTitle('项目管理');
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
      message.success('项目已创建');
      setCreateOpen(false);
      createForm.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['admin', 'projects', organizationId] });
    },
    onError: (error) => message.error(isApiError(error) ? error.message : '操作失败'),
  });

  const columns: ColumnsType<ProjectView> = [
    {
      title: '项目',
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
    { title: '项目 ID', dataIndex: 'projectId', key: 'projectId' },
    { title: '状态', dataIndex: 'status', key: 'status', render: (s: string) => <Tag>{s}</Tag> },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt' },
  ];

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          项目管理
        </Typography.Title>
        <Space wrap>
          <Select
            style={{ width: 260 }}
            placeholder="选择组织"
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
            创建项目
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
        <Typography.Text type="secondary">请先选择组织以查看项目。</Typography.Text>
      )}

      <Modal
        title="创建项目"
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
          <Form.Item name="code" label="项目代码" rules={[{ required: true }]}>
            <Input placeholder="小写字母数字与连字符" />
          </Form.Item>
          <Form.Item name="name" label="项目名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </Flex>
  );
}
