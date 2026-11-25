import { useEffect, useRef, useState } from 'react'
import { Button, Input, Tag, App as AntApp } from 'antd'
import { chatStream, login as loginApi } from './api'
import { renderMarkdown } from './markdown'
import ToolTimeline from './components/ToolTimeline'

const SAMPLES = [
  '订单 MO20260101001 为什么一直没发货？帮我看看 tr-9f2a41 的日志',
  '订单 MO20260101003 我想退款，符合条件吗？',
  '库存锁定失败一般怎么排查？'
]

export default function App() {
  const { message: toast } = AntApp.useApp()
  const [token, setToken] = useState(() => localStorage.getItem('momo_token_react') || '')
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('admin123')
  const [sessionId, setSessionId] = useState('')
  const [question, setQuestion] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [messages, setMessages] = useState([])
  const [tools, setTools] = useState([])
  const [step, setStep] = useState(0)
  const abortRef = useRef(null)
  const scrollRef = useRef(null)

  useEffect(() => {
    if (token && messages.length === 0) {
      setMessages([{ id: 'welcome', role: 'agent', content: WELCOME }])
    }
  }, [token])

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' })
  }, [messages, tools])

  async function doLogin() {
    try {
      const data = await loginApi(username, password)
      localStorage.setItem('momo_token_react', data.token)
      setToken(data.token)
    } catch (e) {
      toast.error(e.message)
    }
  }

  function logout() {
    localStorage.removeItem('momo_token_react')
    setToken('')
    setMessages([])
    setTools([])
    setSessionId('')
  }

  async function send() {
    const q = question.trim()
    if (!q || streaming) return

    setQuestion('')
    setStreaming(true)
    setStep(0)
    setTools([])

    const replyId = `a-${Date.now()}`
    setMessages((prev) => [
      ...prev,
      { id: `u-${Date.now()}`, role: 'user', content: q },
      { id: replyId, role: 'agent', content: '' }
    ])

    const patchReply = (fn) =>
      setMessages((prev) => prev.map((m) => (m.id === replyId ? { ...m, content: fn(m.content) } : m)))

    const controller = new AbortController()
    abortRef.current = controller

    try {
      for await (const data of chatStream({ sessionId, question: q, token, signal: controller.signal })) {
        if (data.sessionId) setSessionId(data.sessionId)

        switch (data.type) {
          case 'delta':
            patchReply((c) => c + (data.content || ''))
            break
          case 'step':
            setStep(data.step)
            break
          case 'tool_call':
            setTools((prev) => [
              ...prev,
              {
                id: data.toolCallId,
                name: data.toolName,
                step: data.step,
                args: data.arguments,
                status: 'running'
              }
            ])
            break
          case 'tool_result':
            setTools((prev) =>
              prev.map((t) =>
                t.id === data.toolCallId
                  ? {
                      ...t,
                      result: data.content,
                      elapsedMs: data.elapsedMs,
                      status: data.success ? 'ok' : 'fail'
                    }
                  : t
              )
            )
            break
          case 'final':
            patchReply(() => data.content || '')
            break
          case 'error':
            patchReply((c) => `${c}\n\n> ⚠️ ${data.error}`)
            break
          default:
            break
        }
      }
    } catch (e) {
      if (e.name !== 'AbortError') {
        toast.error(e.message)
        patchReply((c) => `${c}\n\n> ⚠️ ${e.message}`)
      }
    } finally {
      setStreaming(false)
      abortRef.current = null
    }
  }

  function stop() {
    abortRef.current?.abort()
    setStreaming(false)
  }

  if (!token) {
    return (
      <div className="login-card">
        <h1>MoMo AIOps · React 18</h1>
        <p className="subtitle">Spring Cloud Gateway + Spring Boot Agent + SSE</p>
        <Input value={username} onChange={(e) => setUsername(e.target.value)} placeholder="用户名" />
        <Input.Password
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="密码"
          onPressEnter={doLogin}
        />
        <Button type="primary" block onClick={doLogin}>
          登录
        </Button>
        <p className="subtitle">演示账号：admin / admin123</p>
      </div>
    )
  }

  return (
    <div className="layout">
      <section className="panel">
        <header className="panel-head">
          <div>
            <div className="title">运维对话</div>
            <div className="subtitle">
              session {sessionId || '—'} · 共 {step} 步推理
            </div>
          </div>
          <div className="head-actions">
            <Tag color={streaming ? 'processing' : 'default'}>{streaming ? '推理中' : '空闲'}</Tag>
            <Button size="small" type="text" onClick={logout}>
              退出
            </Button>
          </div>
        </header>

        <div className="messages" ref={scrollRef}>
          {messages.map((m, i) => (
            <div className={`msg ${m.role}`} key={m.id}>
              <div className="avatar">{m.role === 'user' ? '我' : 'Agent'}</div>
              <div
                className={`bubble ${
                  streaming && i === messages.length - 1 && m.role === 'agent' ? 'cursor' : ''
                }`}
              >
                {m.content ? <div dangerouslySetInnerHTML={renderMarkdown(m.content)} /> : '思考中…'}
              </div>
            </div>
          ))}
        </div>

        <div className="chips">
          {SAMPLES.map((s) => (
            <button key={s} className="chip" onClick={() => setQuestion(s)}>
              {s}
            </button>
          ))}
        </div>

        <footer className="composer">
          <Input.TextArea
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            autoSize={{ minRows: 2, maxRows: 4 }}
            placeholder="描述问题，例如：订单 MO20260101001 支付成功但一直没发货"
            onPressEnter={(e) => {
              if (!e.shiftKey) {
                e.preventDefault()
                send()
              }
            }}
          />
          {streaming ? (
            <Button danger onClick={stop}>
              停止
            </Button>
          ) : (
            <Button type="primary" onClick={send}>
              发送
            </Button>
          )}
        </footer>
      </section>

      <aside className="panel">
        <header className="panel-head">
          <div className="title">工具链</div>
          <div className="subtitle">{tools.length} 次调用</div>
        </header>
        <ToolTimeline items={tools} />
      </aside>
    </div>
  )
}

const WELCOME =
  '你好，我是 MoMo 运维 Agent。给我一个订单号、TraceId，或者直接描述现象，我会自己去查订单、日志和运行手册。'
