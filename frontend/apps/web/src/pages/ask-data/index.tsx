import { useState, useRef, useEffect } from 'react';
import { Card, Input, Button, List, Typography, Spin, Tag } from 'antd';
import { SendOutlined, RobotOutlined, UserOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { agentApi } from '@/services';

const { Text, Paragraph } = Typography;

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
  intent?: string;
}

export default function AskData() {
  const { t } = useTranslation('task');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [sessionId] = useState(() => `session_${Date.now()}`);
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight;
    }
  }, [messages]);

  const handleSend = async () => {
    if (!input.trim() || loading) return;

    const userMessage: ChatMessage = {
      id: `msg_${Date.now()}`,
      role: 'user',
      content: input.trim(),
      timestamp: new Date(),
    };
    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    setLoading(true);

    try {
      const res = await agentApi.chat(sessionId, userMessage.content, {});
      const data = res.data;

      const assistantMessage: ChatMessage = {
        id: `msg_${Date.now()}_reply`,
        role: 'assistant',
        content: data.reply || JSON.stringify(data),
        timestamp: new Date(),
        intent: data.intent,
      };
      setMessages((prev) => [...prev, assistantMessage]);
    } catch (err: any) {
      const errorMessage: ChatMessage = {
        id: `msg_${Date.now()}_error`,
        role: 'assistant',
        content: t('askData.error', 'Agent 服务暂时不可用，请稍后再试。'),
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, errorMessage]);
    } finally {
      setLoading(false);
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div style={{ padding: 24, height: 'calc(100vh - 120px)', display: 'flex', flexDirection: 'column' }}>
      <Card
        title={t('askData.title', 'Ask Data - AI 数据问答')}
        style={{ flex: 1, display: 'flex', flexDirection: 'column' }}
        bodyStyle={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}
      >
        <div ref={listRef} style={{ flex: 1, overflow: 'auto', marginBottom: 16 }}>
          {messages.length === 0 && (
            <div style={{ textAlign: 'center', padding: '60px 0', color: '#999' }}>
              <RobotOutlined style={{ fontSize: 48, marginBottom: 16 }} />
              <Paragraph type="secondary">
                {t('askData.placeholder', '你好！我是 AODO 数据助手，可以帮你查询指标、分析数据。试试问我：')}
              </Paragraph>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, justifyContent: 'center', marginTop: 16 }}>
                <Tag color="blue" style={{ cursor: 'pointer' }} onClick={() => setInput('上月总营收是多少？')}>
                  上月总营收是多少？
                </Tag>
                <Tag color="blue" style={{ cursor: 'pointer' }} onClick={() => setInput('按渠道分析订单量趋势')}>
                  按渠道分析订单量趋势
                </Tag>
                <Tag color="blue" style={{ cursor: 'pointer' }} onClick={() => setInput('客单价环比变化')}>
                  客单价环比变化
                </Tag>
              </div>
            </div>
          )}

          <List
            dataSource={messages}
            renderItem={(msg) => (
              <List.Item
                style={{
                  justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start',
                  border: 'none',
                  padding: '8px 0',
                }}
              >
                <div
                  style={{
                    maxWidth: '70%',
                    padding: '12px 16px',
                    borderRadius: 12,
                    background: msg.role === 'user' ? '#1A365D' : '#f5f5f5',
                    color: msg.role === 'user' ? '#fff' : '#333',
                  }}
                >
                  <div style={{ marginBottom: 4 }}>
                    {msg.role === 'user' ? <UserOutlined /> : <RobotOutlined />}
                    <Text style={{ marginLeft: 8, color: msg.role === 'user' ? '#ccc' : '#999', fontSize: 12 }}>
                      {msg.timestamp.toLocaleTimeString()}
                    </Text>
                    {msg.intent && (
                      <Tag color="green" style={{ marginLeft: 8, fontSize: 10 }}>{msg.intent}</Tag>
                    )}
                  </div>
                  <Paragraph style={{ margin: 0, color: 'inherit', whiteSpace: 'pre-wrap' }}>
                    {msg.content}
                  </Paragraph>
                </div>
              </List.Item>
            )}
          />
          {loading && (
            <div style={{ padding: '8px 16px' }}>
              <Spin size="small" /> <Text type="secondary">{t('askData.thinking', '正在思考...')}</Text>
            </div>
          )}
        </div>

        <div style={{ display: 'flex', gap: 8 }}>
          <Input.TextArea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyPress}
            placeholder={t('askData.inputPlaceholder', '输入你的数据问题...')}
            autoSize={{ minRows: 1, maxRows: 4 }}
            style={{ flex: 1 }}
          />
          <Button
            type="primary"
            icon={<SendOutlined />}
            onClick={handleSend}
            loading={loading}
            style={{ height: 'auto' }}
          >
            {t('common.send', '发送')}
          </Button>
        </div>
      </Card>
    </div>
  );
}
