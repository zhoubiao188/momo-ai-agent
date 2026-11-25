<script setup>
import { computed } from 'vue'
import { renderMarkdown } from '../markdown'

const props = defineProps({
  message: { type: Object, required: true },
  streaming: { type: Boolean, default: false }
})

const html = computed(() => renderMarkdown(props.message.content))
const roleLabel = computed(() => (props.message.role === 'user' ? '我' : 'Agent'))
</script>

<template>
  <div class="msg" :class="message.role">
    <div class="avatar">{{ roleLabel }}</div>
    <div class="bubble" :class="{ cursor: streaming }">
      <span v-if="!message.content" class="subtitle">思考中…</span>
      <!-- eslint-disable-next-line vue/no-v-html -->
      <div v-else v-html="html"></div>
    </div>
  </div>
</template>
