<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { chatStream, login as loginApi } from './api'
import MessageBubble from './components/MessageBubble.vue'
import ToolTimeline from './components/ToolTimeline.vue'

const token = ref(localStorage.getItem('momo_token') || '')
const username = ref('admin')
const password = ref('admin123')
const sessionId = ref('')
const question = ref('')
const streaming = ref(false)
const messages = ref([])
const tools = ref([])
const steps = ref(0)
const scrollBox = ref(null)

const samples = [
  '订单 MO20260101001 为什么一直没发货？帮我看看 tr-9f2a41 的日志',
  '订单 MO20260101003 我想退款，符合条件吗？',
  '库存锁定失败一般怎么排查？'
]

onMounted(() => {
  if (token.value) {
    pushAgentWelcome()
  }
})

function pushAgentWelcome() {
  messages.value.push({
    id: Date.now() + '-w',
    role: 'agent',
    content: '你好，我是 MoMo 运维 Agent。给我一个订单号、TraceId，或者直接描述现象，我会自己去查订单、日志和运行手册。'
  })
}

async function doLogin() {
  try {
    const data = await loginApi(username.value, password.value)
    token.value = data.token
    localStorage.setItem('momo_token', data.token)
    pushAgentWelcome()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function logout() {
  token.value = ''
  localStorage.removeItem('momo_token')
  messages.value = []
  tools.value = []
}

async function scrollToBottom() {
  await nextTick()
  if (scrollBox.value) scrollBox.value.scrollTop = scrollBox.value.scrollHeight
}

async function send() {
  const q = question.value.trim()
  if (!q || streaming.value) return

  question.value = ''
  streaming.value = true
  steps.value = 0
  tools.value = []

  messages.value.push({ id: `u-${Date.now()}`, role: 'user', content: q })
  const reply = reactive({ id: `a-${Date.now()}`, role: 'agent', content: '' })
  messages.value.push(reply)
  await scrollToBottom()

  try {
    for await (const { data } of chatStream({
      sessionId: sessionId.value,
      question: q,
      token: token.value
    })) {
      if (data.sessionId) sessionId.value = data.sessionId

      switch (data.type) {
        case 'delta':
          reply.content += data.content || ''
          await scrollToBottom()
          break
        case 'step':
          steps.value = data.step
          break
        case 'tool_call':
          tools.value.push({
            id: data.toolCallId,
            name: data.toolName,
            step: data.step,
            args: data.arguments,
            status: 'running'
          })
          break
        case 'tool_result': {
          const item = tools.value.find((t) => t.id === data.toolCallId)
          if (item) {
            item.result = data.content
            item.success = data.success
            item.elapsedMs = data.elapsedMs
            item.status = data.success ? 'ok' : 'fail'
          }
          break
        }
        case 'final':
          // delta 已经流式写入了正文，final 是权威版本，直接覆盖避免拼接误差
          reply.content = data.content || reply.content
          break
        case 'error':
          reply.content += `\n\n> ⚠️ ${data.error}`
          break
        default:
          break
      }
    }
  } catch (e) {
    if (e.name !== 'AbortError') {
      ElMessage.error(e.message)
      reply.content += `\n\n> ⚠️ ${e.message}`
    }
  } finally {
    streaming.value = false
    await scrollToBottom()
  }
}

function useSample(text) {
  question.value = text
}
</script>

<template>
  <div v-if="!token" class="login card">
    <div class="card-head">
      <div>
        <div class="title">MoMo AIOps · Vue 3</div>
        <div class="subtitle">Spring Cloud Gateway + Spring Boot Agent + SSE</div>
      </div>
    </div>
    <input v-model="username" placeholder="用户名" />
    <input v-model="password" type="password" placeholder="密码" @keyup.enter="doLogin" />
    <el-button type="primary" style="width: 100%" @click="doLogin">登录</el-button>
    <div class="subtitle" style="margin-top: 10px">演示账号：admin / admin123</div>
  </div>

  <div v-else class="layout">
    <section class="card">
      <div class="card-head">
        <div>
          <div class="title">运维对话</div>
          <div class="subtitle">
            session {{ sessionId || '—' }} · 共 {{ steps }} 步推理
          </div>
        </div>
        <el-button size="small" text @click="logout">退出</el-button>
      </div>

      <div ref="scrollBox" class="messages">
        <MessageBubble
          v-for="(m, i) in messages"
          :key="m.id"
          :message="m"
          :streaming="streaming && i === messages.length - 1 && m.role === 'agent'"
        />
      </div>

      <div class="chips">
        <button v-for="s in samples" :key="s" class="chip" @click="useSample(s)">{{ s }}</button>
      </div>

      <div class="composer">
        <el-input
          v-model="question"
          type="textarea"
          :rows="2"
          resize="none"
          placeholder="描述问题，例如：订单 MO20260101001 支付成功但一直没发货"
          @keydown.enter.exact.prevent="send"
        />
        <el-button type="primary" :loading="streaming" @click="send">发送</el-button>
      </div>
    </section>

    <aside class="card">
      <div class="card-head">
        <div class="title">工具链</div>
        <div class="subtitle">{{ tools.length }} 次调用</div>
      </div>
      <ToolTimeline :items="tools" />
    </aside>
  </div>
</template>
