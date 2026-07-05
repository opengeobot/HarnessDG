# aih CLI — AIHub 命令行客户端

最小实现的命令行工具，封装 AIHub REST API，用于数据集资产管理。

## 安装

```bash
chmod +x scripts/aih/aih
ln -sf "$(pwd)/scripts/aih/aih" /usr/local/bin/aih
```

## 认证

```bash
# 方式 1: 登录保存 Token
aih login --username your-user --password your-password

# 方式 2: 环境变量
export AIH_TOKEN="your-jwt-token"

# 方式 3: 手动保存到文件
echo -n "your-jwt-token" > ~/.aih/token
chmod 600 ~/.aih/token
```

## 使用

```bash
# 搜索数据集
aih dataset search --query "图像分类"
aih dataset search --limit 10 --cursor "ast_xxx"

# 查看资产详情
aih dataset inspect --id ast_001

# 查看 provisioning 状态
aih dataset status --id ast_001

# 列出版本
aih dataset versions --id ast_001

# 创建数据集草稿
aih dataset create --name "my-dataset" --namespace "ai-lab" --description "图像分类数据集"

# 请求下载票据
aih dataset pull --version ver_001
aih dataset pull --version ver_001 --artifact art_001
```

## 配置

| 环境变量 | 说明 | 默认值 |
|----------|------|--------|
| `AIH_API_URL` | API 基址 | `http://localhost:8080` |
| `AIH_TOKEN` | JWT 令牌 | `~/.aih/token` |
| `AIH_OUTPUT` | 输出格式 | `json` |
| `AIH_TIMEOUT` | 超时秒数 | `30` |

## 退出码

| 码 | 含义 |
|----|------|
| 0 | 成功 |
| 1 | 通用错误 |
| 2 | 认证失败 |
| 3 | 资源未找到 |
| 4 | 冲突 |
| 5 | 校验失败 |
| 6 | 限流 |
| 7 | 服务端错误 |

## 输出格式

所有命令默认输出 JSON，包含 `operationId`/`requestId`/`traceId`（服务端提供）。

错误响应格式:
```json
{"error":{"code":"EXIT_CODE","message":"description"}}
```
