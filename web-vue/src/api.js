const BASE = import.meta.env.VITE_API_BASE || ''

async function postJson(path, body, token) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body)
  })
  return res.json()
}

export async function login(username, password) {
  const data = await postJson('/api/auth/login', { username, password })
  if (data.code !== 0) throw new Error(data.message || '登录失败')
  return data.data
}

/**
 * 逐帧解析 SSE。
 * 关键点：网络分片不会按事件边界切，必须自己维护缓冲区；\r\n 也要归一化。
 */
async function* readSse(response) {
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')

    let index
    while ((index = buffer.indexOf('\n\n')) >= 0) {
      const frame = buffer.slice(0, index)
      buffer = buffer.slice(index + 2)
      const event = parseFrame(frame)
      if (event) yield event
    }
  }
}

function parseFrame(frame) {
  let eventName = 'message'
  const dataLines = []
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) eventName = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
  }
  if (!dataLines.length) return null
  const raw = dataLines.join('\n')
  try {
    return { name: eventName, data: JSON.parse(raw) }
  } catch {
    return { name: eventName, data: { content: raw } }
  }
}

export async function* chatStream({ sessionId, question, token, signal }) {
  const res = await fetch(`${BASE}/api/agent/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify({ sessionId, question }),
    signal
  })

  if (!res.ok) {
    throw new Error(res.status === 401 ? '登录已过期，请重新登录' : `请求失败：HTTP ${res.status}`)
  }
  for await (const event of readSse(res)) {
    yield event
  }
}
