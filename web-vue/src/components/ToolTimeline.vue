<script setup>
import { computed } from 'vue'
import { prettyJson } from '../markdown'

const props = defineProps({
  items: { type: Array, required: true }
})

const visible = computed(() => props.items)

function statusOf(item) {
  if (item.status) return item.status
  return item.success ? 'ok' : 'fail'
}
</script>

<template>
  <div class="timeline">
    <div v-if="!visible.length" class="subtitle" style="padding: 8px">
      工具调用会实时出现在这里：查订单 → 查日志 → 查手册 → （可选）退款。
    </div>

    <div v-for="item in visible" :key="item.id" class="tool">
      <div class="tool-top">
        <span class="dot" :class="statusOf(item)"></span>
        <span class="tool-name">{{ item.name }}</span>
        <span v-if="item.step" class="tool-meta">step {{ item.step }}</span>
        <span v-if="item.elapsedMs != null" class="tool-meta">{{ item.elapsedMs }}ms</span>
      </div>

      <pre v-if="item.args">{{ prettyJson(item.args) }}</pre>
      <pre v-if="item.result">{{ item.result }}</pre>
      <div v-if="item.note" class="subtitle" style="margin-top: 6px">{{ item.note }}</div>
    </div>
  </div>
</template>
