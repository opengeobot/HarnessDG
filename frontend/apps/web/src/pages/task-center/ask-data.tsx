/**
 * 功能：AI问数页面 - 完整聊天界面
 * 时间：2026-05-09
 * 作者：AxeXie
 */
import { useState, useRef, useEffect } from 'react';
import { Input, Button, Card, Typography, Space, message, Tag, Tooltip, Popconfirm } from 'antd';
import {
  SendOutlined,
  DeleteOutlined,
  CopyOutlined,
  DatabaseOutlined,
  CodeOutlined,
  MessageOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { agentApi } from '../../services';
import { useDictionary } from '../../hooks/useDictionary';

const { TextArea } = Input;
const { Text } = Typography;

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: number;
  intent?: string;
  sql?: string;
  data?: Record<string, any>;
}

function AskData() {
  const { t } = useTranslation('query');
  const { getLabel } = useDictionary('task_type');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [sessionId, setSessionId] = useState<string>('');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<any>(null);

  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // 初始化 session
  useEffect(() => {
    setSessionId(`session_${Date.now()}`);
  }, []);

  // 发送消息
  const handleSend = async () => {
    if (!inputValue.trim() || loading) return;

    const userMessage: ChatMessage = {
      id: `user_${Date.now()}`,
      role: 'user',
      content: inputValue.trim(),
      timestamp: Date.now(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInputValue('');
    setLoading(true);

    try {
      const res = await agentApi.chat(sessionId, userMessage.content, {});
      const assistantMessage: ChatMessage = {
        id: `assistant_${Date.now()}`,
        role: 'assistant',
        content: res.data.reply || t('empty_reply'),
        timestamp: Date.now(),
        intent: res.data.intent,
        sql: res.data.sql,
        data: res.data.data,
      };
      setMessages((prev) => [...prev, assistantMessage]);
    } catch (error: any) {
      message.error(t('error'));
      const errorMessage: ChatMessage = {
        id: `error_${Date.now()}`,
        role: 'assistant',
        content: t('error'),
        timestamp: Date.now(),
      };
      setMessages((prev) => [...prev, errorMessage]);
    } finally {
      setLoading(false);
      inputRef.current?.focus();
    }
  };

  // 清空对话
  const handleClear = () => {
    setMessages([]);
    setSessionId(`session_${Date.now()}`);
    message.success(t('clear_success'));
  };

  // 复制 SQL
  const handleCopySql = (sql: string) => {
    navigator.clipboard.writeText(sql);
    message.success(t('copy_success'));
  };

  // 快捷提问
  const handleQuickAsk = (question: string) => {
    setInputValue(question);
    inputRef.current?.focus();
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 120px)' }}>
      {/* 顶部标题栏 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('query.title')}
        </Typography.Title>
        <Popconfirm
          title={t('clear_confirm')}
          onConfirm={handleClear}
          okText={t('common:action.confirm')}
          cancelText={t('common:action.cancel')}
        >
          <Button icon={<DeleteOutlined />} size="small">
            {t('clear_chat')}
          </Button>
        </Popconfirm>
      </div>

      {/* 消息列表 */}
      <Card
        style={{
          flex: 1,
          overflowY: 'auto',
          marginBottom: 16,
        }}
        bodyStyle={{ padding: '16px 24px' }}
      >
        {messages.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '60px 0', color: '#8c8c8c' }}>
            <MessageOutlined style={{ fontSize: 48, marginBottom: 16 }} />
            <div style={{ marginBottom: 8 }}>{t('no_history')}</div>
            <div style={{ marginBottom: 16 }}>{t('start_hint')}</div>
            <Space wrap>
              <Tag
                color="blue"
                style={{ cursor: 'pointer', padding: '4px 12px' }}
                onClick={() => handleQuickAsk(t('example_metric'))}
              >
                {t('example_metric')}
              </Tag>
              <Tag
                color="green"
                style={{ cursor: 'pointer', padding: '4px 12px' }}
                onClick={() => handleQuickAsk(t('example_compare'))}
              >
                {t('example_compare')}
              </Tag>
              <Tag
                color="orange"
                style={{ cursor: 'pointer', padding: '4px 12px' }}
                onClick={() => handleQuickAsk(t('example_top'))}
              >
                {t('example_top')}
              </Tag>
            </Space>
          </div>
        ) : (
          messages.map((msg) => (
            <div
              key={msg.id}
              style={{
                marginBottom: 24,
                display: 'flex',
                justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start',
              }}
            >
              <div
                style={{
                  maxWidth: '80%',
                  padding: '12px 16px',
                  borderRadius: msg.role === 'user' ? '16px 16px 0 16px' : '16px 16px 16px 0',
                  background: msg.role === 'user' ? '#1677ff' : '#f5f5f5',
                  color: msg.role === 'user' ? '#fff' : '#000',
                }}
              >
                <div style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{msg.content}</div>

                {/* AI 消息附加信息 */}
                {msg.role === 'assistant' && (
                  <div style={{ marginTop: 12 }}>
                    {msg.intent && (
                      <Tag color="blue" style={{ marginBottom: 8 }}>
                        {getLabel(msg.intent) || msg.intent}
                      </Tag>
                    )}

                    {msg.sql && (
                      <div style={{ marginTop: 8 }}>
                        <Space>
                          <CodeOutlined />
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            {t('sql')}
                          </Text>
                          <Tooltip title={t('copy_sql')}>
                            <CopyOutlined
                              style={{ cursor: 'pointer', color: '#1677ff' }}
                              onClick={() => handleCopySql(msg.sql!)}
                            />
                          </Tooltip>
                        </Space>
                        <pre
                          style={{
                            marginTop: 8,
                            padding: 12,
                            background: '#fafafa',
                            borderRadius: 4,
                            fontSize: 12,
                            overflowX: 'auto',
                          }}
                        >
                          {msg.sql}
                        </pre>
                      </div>
                    )}

                    {msg.data && (
                      <div style={{ marginTop: 8 }}>
                        <Space>
                          <DatabaseOutlined />
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            {t('sources')}
                          </Text>
                        </Space>
                        <pre
                          style={{
                            marginTop: 8,
                            padding: 12,
                            background: '#fafafa',
                            borderRadius: 4,
                            fontSize: 12,
                            maxHeight: 200,
                            overflow: 'auto',
                          }}
                        >
                          {JSON.stringify(msg.data, null, 2)}
                        </pre>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          ))
        )}

        {loading && (
          <div style={{ display: 'flex', justifyContent: 'flex-start', marginBottom: 24 }}>
            <div
              style={{
                padding: '12px 16px',
                borderRadius: '16px 16px 16px 0',
                background: '#f5f5f5',
              }}
            >
              <Text type="secondary">{t('thinking')}</Text>
            </div>
          </div>
        )}
        <div ref={messagesEndRef} />
      </Card>

      {/* 输入区域 */}
      <div style={{ display: 'flex', gap: 8 }}>
        <TextArea
          ref={inputRef}
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          placeholder={t('placeholder')}
          autoSize={{ minRows: 1, maxRows: 4 }}
          onPressEnter={(e) => {
            if (!e.shiftKey) {
              e.preventDefault();
              handleSend();
            }
          }}
          disabled={loading}
        />
        <Button
          type="primary"
          icon={<SendOutlined />}
          onClick={handleSend}
          loading={loading}
          disabled={!inputValue.trim()}
          style={{ alignSelf: 'flex-end' }}
        >
          {t('send')}
        </Button>
      </div>
    </div>
  );
}

export default AskData;
