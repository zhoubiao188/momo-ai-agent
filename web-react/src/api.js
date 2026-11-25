const BASE = import.meta.env.VITE_API_BASE || ''

export async function login(username, password) {
  const res = await fetch(`${BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  const data = await res.json()
  if (data.code !== 0) throw new Error(data.message || '登录失败')
  return data.data
}

/** 把 fetch 的字节流解析成事件序列：分片不按帧边界切，必须自己缓冲。 */
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
  const dataLines = []
  for (const line of frame.split('\n')) {
    if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
  }
  if (!dataLines.length) return null
  const raw = dataLines.join('\n')
  try {
    return JSON.parse(raw)
  } catch {
    return { type: 'delta', content: raw }
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
