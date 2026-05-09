# HarnessDG Backend Dockerfile
# 功能：Java Spring Boot 后端服务容器化
# 时间：2026-05-09
# 作者：AxeXie
# 说明：使用预构建的 JAR 文件，避免 Docker 内 Maven 构建的网络问题

# =======================================
# 运行阶段 - Java 21 JRE
# =======================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 复制预构建的 JAR 文件
COPY harness-app/target/harness-app-1.0.0-SNAPSHOT.jar app.jar

# 暴露端口
EXPOSE 8080

# JVM 参数配置
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC"

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]