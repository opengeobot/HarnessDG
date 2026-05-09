# HarnessDG Agent Service Dockerfile
# 功能：Python FastAPI Agent 服务容器化
# 时间：2026-05-09
# 作者：AxeXie
# 说明：使用已有的本地镜像避免网络问题

# 使用已有的本地 Python 3.10 镜像
FROM python:3.10

WORKDIR /app

# 设置国内 pip 镜像源（阿里云）
RUN pip config set global.index-url https://mirrors.aliyun.com/pypi/simple/ \
    && pip config set global.trusted-host mirrors.aliyun.com

# 复制依赖文件
COPY requirements.txt .

# 安装依赖
RUN pip install --no-cache-dir -r requirements.txt

# 复制源代码
COPY app app

# 暴露端口
EXPOSE 8000

# 启动服务
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]