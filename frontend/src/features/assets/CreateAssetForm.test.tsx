/**
 * CreateAssetForm 组件测试——验证创建资产表单字段渲染与 payload 构建。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { Form } from 'antd';
import { CreateAssetFormFields, buildCreateAssetPayload, type CreateAssetFormValues } from './CreateAssetForm';
import { renderWithProviders } from '@/test/test-utils';

vi.mock('@/shared/components/ControlledSelect', () => ({
  ControlledSelect: (props: { placeholder?: string; value?: string; onChange?: (v: string) => void }) => (
    <select
      data-testid="controlled-select"
      value={props.value ?? ''}
      onChange={(e) => props.onChange?.(e.target.value)}
    >
      <option value="">{props.placeholder ?? ''}</option>
    </select>
  ),
}));

vi.mock('./api', () => ({
  listDictionaryItems: vi.fn().mockResolvedValue([]),
  listTags: vi.fn().mockResolvedValue([]),
}));

function TestFormWrapper() {
  const [form] = Form.useForm<CreateAssetFormValues>();
  return <CreateAssetFormFields form={form} />;
}

describe('CreateAssetFormFields', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('渲染类型选择器', () => {
    renderWithProviders(<TestFormWrapper />);
    expect(screen.getByText('类型')).toBeInTheDocument();
  });

  it('渲染命名空间和名称字段', () => {
    renderWithProviders(<TestFormWrapper />);
    expect(screen.getByText('命名空间')).toBeInTheDocument();
    // 名称字段存在（可能多个“名称”相关元素，用 getAllByText）
    expect(screen.getAllByText(/名称/).length).toBeGreaterThan(0);
  });

  it('默认为 MODEL 类型', () => {
    renderWithProviders(<TestFormWrapper />);
    // 默认初始值为 MODEL，显示“模型”选项
    expect(screen.getByText('模型')).toBeInTheDocument();
  });
});

describe('buildCreateAssetPayload', () => {
  it('MODEL 类型构建正确 payload', () => {
    const values: CreateAssetFormValues = {
      type: 'MODEL',
      namespace: 'org1',
      name: 'model1',
      displayName: 'Model 1',
      visibility: 'INTERNAL',
      ownerTeamId: 'team_001',
      framework: 'pytorch',
      task: 'text-generation',
    };

    const payload = buildCreateAssetPayload(values);

    expect(payload.type).toBe('MODEL');
    expect(payload.namespace).toBe('org1');
    expect(payload.name).toBe('model1');
    expect(payload.model).toBeDefined();
    expect(payload.model?.framework).toBe('pytorch');
    expect(payload.dataset).toBeUndefined();
  });

  it('DATASET 类型构建正确 payload', () => {
    const values: CreateAssetFormValues = {
      type: 'DATASET',
      namespace: 'org1',
      name: 'dataset1',
      visibility: 'INTERNAL',
      ownerTeamId: 'team_001',
      format: 'parquet',
      sampleCount: 1000,
    };

    const payload = buildCreateAssetPayload(values);

    expect(payload.type).toBe('DATASET');
    expect(payload.dataset).toBeDefined();
    expect(payload.dataset?.format).toBe('parquet');
    expect(payload.dataset?.sampleCount).toBe(1000);
    expect(payload.model).toBeUndefined();
  });

  it('可选字段未填时不包含在 payload', () => {
    const values: CreateAssetFormValues = {
      type: 'MODEL',
      namespace: 'org1',
      name: 'model1',
      visibility: 'INTERNAL',
      ownerTeamId: 'team_001',
    };

    const payload = buildCreateAssetPayload(values);

    expect(payload.organizationId).toBeUndefined();
    expect(payload.projectId).toBeUndefined();
    expect(payload.tagIds).toBeUndefined();
  });
});
