#!/bin/bash
# 功能：在容器启动时安装 pgvector 扩展
# 时间：2026-05-07
# 作者：AxeXie

set -e

echo "=== Installing pgvector extension ==="

# 检查是否是 Debian/Ubuntu 基础镜像
if command -v apt-get &> /dev/null; then
    echo "Installing pgvector for PostgreSQL 16..."
    apt-get update -qq
    apt-get install -y -qq postgresql-16-pgvector 2>/dev/null || echo "pgvector package not found, will try CREATE EXTENSION"
fi

echo "=== pgvector installation complete ==="
