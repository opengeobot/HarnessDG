# 功能: 前端多阶段镜像 (node 构建 + nginx 提供静态资源)
# 时间: 2026-06-29
# 作者: AxeXie

# ---- 构建阶段 ----
FROM node:20.19.2-bookworm-slim AS builder
WORKDIR /app

RUN npm config set registry https://registry.npmmirror.com/ && npm install -g pnpm@10.33.4

# 使用国内镜像源（https）安装依赖
COPY .npmrc package.json pnpm-lock.yaml* ./
RUN pnpm install --frozen-lockfile || pnpm install

# 复制源码并构建
COPY . .
RUN pnpm build

# ---- 运行阶段 ----
FROM nginx:1.27.3-alpine AS runtime

# 提供 SPA 友好的静态资源配置
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
