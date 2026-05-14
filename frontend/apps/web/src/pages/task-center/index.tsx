/**
 * 功能：业务任务中心首页 - 六大任务卡片入口
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { Row, Col, Card, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  DatabaseOutlined,
  LineChartOutlined,
  MessageOutlined,
  FileTextOutlined,
  BugOutlined,
  KeyOutlined,
} from '@ant-design/icons';

const { Title, Text } = Typography;

const taskIcons: Record<string, React.ReactNode> = {
  data_ingestion: <DatabaseOutlined style={{ fontSize: 32 }} />,
  build_metric: <LineChartOutlined style={{ fontSize: 32 }} />,
  ask_data: <MessageOutlined style={{ fontSize: 32 }} />,
  generate_report: <FileTextOutlined style={{ fontSize: 32 }} />,
  diagnose_exception: <BugOutlined style={{ fontSize: 32 }} />,
  request_permission: <KeyOutlined style={{ fontSize: 32 }} />,
};

const taskRoutes: Record<string, string> = {
  data_ingestion: '/data-ingestion',
  build_metric: '/tasks/build-metric',
  ask_data: '/tasks/ask-data',
  generate_report: '/weekly-report',
  diagnose_exception: '/diagnosis',
  request_permission: '/approval',
};

const taskCodes = [
  'data_ingestion',
  'build_metric',
  'ask_data',
  'generate_report',
  'diagnose_exception',
  'request_permission',
];

function TaskCenter() {
  const { t } = useTranslation('task');
  const navigate = useNavigate();

  return (
    <div>
      <Title level={2}>{t('center.title')}</Title>
      <Text type="secondary">{t('center.subtitle')}</Text>

      <Row gutter={[24, 24]} style={{ marginTop: 32 }}>
        {taskCodes.map((code) => (
          <Col xs={24} sm={12} lg={8} key={code}>
            <Card
              hoverable
              onClick={() => navigate(taskRoutes[code] || '/tasks')}
              style={{
                textAlign: 'center',
                borderRadius: 'var(--radius-lg)',
                transition: 'var(--transition-base)',
              }}
              styles={{
                body: { padding: '32px 24px' },
              }}
            >
              <div style={{ color: 'var(--color-brand-primary)', marginBottom: 16 }}>
                {taskIcons[code]}
              </div>
              <Title level={4} style={{ marginBottom: 8 }}>
                {t(`card.${code}.title`)}
              </Title>
              <Text type="secondary">{t(`card.${code}.description`)}</Text>
            </Card>
          </Col>
        ))}
      </Row>
    </div>
  );
}

export default TaskCenter;
