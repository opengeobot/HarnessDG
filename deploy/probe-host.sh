#!/bin/bash
# 探测从容器内可达的 Docker 宿主地址（发布端口 18089 → probe-web:80）
for h in 172.17.0.1 host.docker.internal 192.168.65.254; do
  printf "%s: " "$h"
  if timeout 3 bash -c "</dev/tcp/$h/18089" 2>/dev/null; then echo OPEN; else echo CLOSED; fi
done
