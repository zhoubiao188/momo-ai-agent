#!/usr/bin/env bash
# 端到端冒烟：登录 -> 订单查询 -> Agent（SSE）
set -euo pipefail

GW="${GW:-http://localhost:8080}"

echo "==> 1. 登录"
TOKEN=$(curl -s -X POST "$GW/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["token"])')
echo "token 长度: ${#TOKEN}"

echo "==> 2. 无 token 访问应返回 401"
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$GW/api/orders/MO20260101001")
[ "$CODE" = "401" ] && echo "OK (401)" || { echo "期望 401，实际 $CODE"; exit 1; }

echo "==> 3. 带 token 查订单"
curl -s -H "Authorization: Bearer $TOKEN" "$GW/api/orders/MO20260101001"
echo

echo "==> 4. Agent 同步问答"
curl -s -X POST "$GW/api/agent/chat" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"sessionId":"smoke","question":"订单 MO20260101001 为什么一直没发货？帮我看看 tr-9f2a41 的日志"}' \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); print("toolCalls:", d["toolCalls"]); print(d["answer"][:400])'

echo "==> 5. Agent 流式问答（只打印事件名）"
curl -sN -X POST "$GW/api/agent/chat/stream" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{"sessionId":"smoke-sse","question":"库存锁定失败怎么排查？"}' \
  | grep '^event:' | uniq

echo "==> 冒烟通过"
