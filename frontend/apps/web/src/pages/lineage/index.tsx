/**
 * 功能：血缘可视化页 - 查询并展示实体/指标上下游血缘关系
 * 时间：2026-05-08
 * 作者：AxeXie
 */
import { useState } from 'react';
import {
  Card, Input, Button, Space, Tree, List, Tag, Typography, Spin, Empty,
} from 'antd';
import { SearchOutlined, ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { lineageApi } from '@/services/lineageApi';

const { Title, Text } = Typography;

interface LineageNode {
  id: number;
  name: string;
  type: string;
  level: number;
}

interface LineageEdge {
  sourceId: number;
  targetId: number;
  relationType: string;
}

export default function LineagePage() {
  const { t } = useTranslation(['lineage', 'common']);
  const [queryType, setQueryType] = useState<'metric' | 'entity'>('metric');
  const [queryId, setQueryId] = useState('');
  const [loading, setLoading] = useState(false);
  const [upstream, setUpstream] = useState<LineageNode[]>([]);
  const [downstream, setDownstream] = useState<LineageNode[]>([]);
  const [edges, setEdges] = useState<LineageEdge[]>([]);

  const handleQuery = async () => {
    if (!queryId) return;
    setLoading(true);
    try {
      if (queryType === 'metric') {
        const res: any = await lineageApi.getMetricLineage(Number(queryId));
        const data = res.data || {};
        setUpstream(data.upstream || []);
        setDownstream(data.downstream || []);
        setEdges(data.edges || []);
      } else {
        const res: any = await lineageApi.getImpact(Number(queryId));
        const data = res.data || {};
        setUpstream(data.upstream || []);
        setDownstream(data.downstream || []);
        setEdges(data.edges || []);
      }
    } finally {
      setLoading(false);
    }
  };

  const buildTreeData = (nodes: LineageNode[]) => {
    return nodes.map(n => ({
      key: n.id,
      title: `${n.name} (${n.type})`,
      icon: <Tag color={n.type === 'table' ? 'blue' : n.type === 'metric' ? 'green' : 'orange'}>{n.type}</Tag>,
    }));
  };

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={16}>
      <Card title={t('lineage.title', '血缘可视化')}>
        <Space>
          <Input.Group compact>
            <Button onClick={() => setQueryType('metric')}
              type={queryType === 'metric' ? 'primary' : 'default'}>
              {t('lineage.query_by_metric', '按指标查询')}
            </Button>
            <Button onClick={() => setQueryType('entity')}
              type={queryType === 'entity' ? 'primary' : 'default'}>
              {t('lineage.query_by_entity', '按实体查询')}
            </Button>
          </Input.Group>
          <Input placeholder={queryType === 'metric' ? t('lineage.metric_id', '指标ID') : t('lineage.entity_id', '实体ID')}
            value={queryId} onChange={e => setQueryId(e.target.value)}
            onPressEnter={handleQuery} style={{ width: 200 }} />
          <Button type="primary" icon={<SearchOutlined />} onClick={handleQuery}>
            {t('common:action.query', '查询')}
          </Button>
        </Space>
      </Card>

      <Spin spinning={loading}>
        {upstream.length === 0 && downstream.length === 0 && !loading ? (
          <Card><Empty description={t('lineage.no_data', '请输入ID并点击查询')} /></Card>
        ) : (
          <>
            <Card title={<><ArrowUpOutlined /> {t('lineage.upstream', '上游血缘')} ({upstream.length})</>}>
              {upstream.length > 0 ? (
                <Tree treeData={buildTreeData(upstream)} defaultExpandAll />
              ) : (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('lineage.no_upstream', '无上游依赖')} />
              )}
            </Card>
            <Card title={<><ArrowDownOutlined /> {t('lineage.downstream', '下游血缘')} ({downstream.length})</>}>
              {downstream.length > 0 ? (
                <Tree treeData={buildTreeData(downstream)} defaultExpandAll />
              ) : (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('lineage.no_downstream', '无下游依赖')} />
              )}
            </Card>
            {edges.length > 0 && (
              <Card title={t('lineage.edges', '血缘关系')}>
                <List<LineageEdge>
                  size="small"
                  dataSource={edges}
                  renderItem={item => (
                    <List.Item>
                      <Text strong>{item.sourceId}</Text>
                      <Tag color="blue" style={{ margin: '0 8px' }}>{item.relationType}</Tag>
                      <Text strong>{item.targetId}</Text>
                    </List.Item>
                  )}
                />
              </Card>
            )}
          </>
        )}
      </Spin>
    </Space>
  );
}
