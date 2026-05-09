@echo off
REM HarnessDG 服务启动脚本
REM 功能：统一启动所有 HarnessDG 服务
REM 时间：2026-05-09
REM 作者：AxeXie
REM 使用说明：双击运行或在命令行执行 start-services.bat

setlocal enabledelayedexpansion

echo ========================================
echo    HarnessDG 服务启动
echo ========================================
echo.

REM 1. 启动 PostgreSQL 通过 Docker Compose
echo [1/4] 启动 PostgreSQL 数据库...
docker compose up -d
if errorlevel 1 (
    echo [错误] PostgreSQL 启动失败
    exit /b 1
)
echo [成功] PostgreSQL 已启动
echo.

REM 等待 PostgreSQL 就绪
echo [等待] 等待 PostgreSQL 就绪...
:wait_for_db
docker ps --filter "name=harnessdg-db" --filter "status=healthy" --format "{{.Status}}" >nul 2>&1
if errorlevel 1 (
    timeout /t 2 /nobreak >nul
    goto wait_for_db
)
echo [成功] PostgreSQL 已就绪
echo.

REM 2. 启动 Backend (Java Spring Boot)
echo [2/4] 启动 Backend 服务...
set "JAVA_HOME=D:\tools\jdks\temurin-21.0.9"
set "JAVA=%JAVA_HOME%\bin\java.exe"
if not exist "%JAVA%" (
    echo [警告] 未找到 Java 21，尝试使用系统默认 Java
    set "JAVA=java"
)

cd backend
start "HarnessDG Backend" cmd /k "%JAVA% -Xmx512m -Xms256m -XX:+UseG1GC -jar harness-app\target\harness-app-1.0.0-SNAPSHOT.jar"
cd ..
echo [成功] Backend 已启动
echo.

REM 3. 启动 Agent (Python FastAPI)
echo [3/4] 启动 Agent 服务...
cd agent
start "HarnessDG Agent" cmd /k "uvicorn app.main:app --reload --port 8000 --host 0.0.0.0"
cd ..
echo [成功] Agent 已启动
echo.

REM 4. 启动 Frontend (React + Vite)
echo [4/4] 启动 Frontend 服务...
cd frontend
start "HarnessDG Frontend" cmd /k "pnpm dev"
cd ..
echo [成功] Frontend 已启动
echo.

echo ========================================
echo    所有服务已启动
echo ========================================
echo.
echo 服务地址：
echo   - PostgreSQL:  localhost:5432
echo   - Backend API: http://localhost:8080
echo   - Agent API:   http://localhost:8000
echo   - Frontend:    http://localhost:5173
echo.
echo Docker Compose 分组: harnessdg
echo.
echo 查看服务状态:
echo   docker ps --filter "name=harnessdg"
echo.
echo 停止所有服务:
echo   docker compose down
echo.

pause
