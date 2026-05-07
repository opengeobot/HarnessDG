/**
 * 功能：本体建模页面
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { Typography, Card, Empty } from 'antd';
import { DeploymentUnitOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';

export default function Ontology() {
  const { t } = useTranslation('navigation');

  return (
    <div>
      <Typography.Title level={2}>{t('menu.ontology')}</Typography.Title>
      <Card>
        <Empty
          image={<DeploymentUnitOutlined style={{ fontSize: 64, color: '#bfbfbf' }} />}
          description={t('menu.ontology') + ' - 功能建设中'}
        />
      </Card>
    </div>
  );
}
