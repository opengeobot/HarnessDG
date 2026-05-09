# HarnessDG Frontend Dockerfile
# 功能：React 前端开发服务器容器化
# 时间：2026-05-09
# 作者：AxeXie
# 说明：pnpm monorepo 结构，使用国内镜像源

# 使用 Node 20 Alpine
FROM node:20-alpine

WORKDIR /app

# 配置 npm 使用国内镜像
RUN npm config set registry https://registry.npmmirror.com

# 安装 pnpm
RUN npm install -g pnpm@9.7.0

# 复制完整项目（包括 workspace 所有包）
COPY . .

# 安装依赖
RUN pnpm install

# 暴露端口
EXPOSE 5173

# 启动 vite 开发服务器
CMD ["pnpm", "run", "dev"]