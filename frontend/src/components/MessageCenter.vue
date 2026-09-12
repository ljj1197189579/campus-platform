<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import axios from 'axios'
import { ChatDotRound, Promotion } from '@element-plus/icons-vue'
const props = defineProps({ user: Object, conversations: { type: Array, default: () => [] }, initialId: Number })
const emit = defineEmits(['market', 'refresh', 'login'])
const activeId = ref(props.initialId || null)
const active = computed(() => props.conversations.find(item => item.id === activeId.value))
const messages = ref([])
const draft = ref('')
const error = ref('')
const sending = ref(false)
const busy = ref(false)
const more = ref(false)
const history = ref(null)
let sequence = 0
function time(value) { return new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) }
async function load(older = false) {
  if (!props.user || !activeId.value || (older && busy.value)) return
  const id = activeId.value
  const request = ++sequence
  busy.value = true
  try {
    const options = older && messages.value.length ? { params: { before: messages.value[0].id } } : undefined
    const { data } = await axios.get('/conversations/' + id + '/messages', options)
    if (request !== sequence) return
    const nearBottom = !history.value || history.value.scrollHeight - history.value.scrollTop - history.value.clientHeight < 100
    const previousHeight = history.value?.scrollHeight || 0
    const grouped = new Map(messages.value.map(item => [item.id, item]))
    data.forEach(item => grouped.set(item.id, item))
    messages.value = [...grouped.values()].sort((first, second) => first.id - second.id)
    if (older || messages.value.length <= 50) more.value = data.length === 50
    if (!older && data.length && !document.hidden) {
      await axios.put('/conversations/' + id + '/read', { messageId: Math.max(...data.map(item => item.id)) })
      emit('refresh')
    }
    await nextTick()
    if (history.value) {
      if (older) history.value.scrollTop += history.value.scrollHeight - previousHeight
      else if (nearBottom) history.value.scrollTop = history.value.scrollHeight
    }
    error.value = ''
  } catch (failure) { if (request === sequence) error.value = failure.response?.data?.message || '消息暂时未能加载，请重试' }
  finally { if (request === sequence) busy.value = false }
}
function select(item) { ++sequence; activeId.value = item.id; messages.value = []; draft.value = ''; error.value = ''; more.value = false; load() }
async function send() {
  if (!draft.value.trim() || sending.value || !activeId.value) return
  const id = activeId.value
  const content = draft.value.trim()
  sending.value = true
  try {
    await axios.post('/conversations/' + id + '/messages', { content })
    if (id === activeId.value) { draft.value = ''; await load(); await nextTick(); if (history.value) history.value.scrollTop = history.value.scrollHeight }
    emit('refresh')
  } catch (failure) { error.value = failure.response?.data?.message || '发送失败，内容已保留，请重试' }
  finally { sending.value = false }
}
function sync() { if (!busy.value && !document.hidden) load() }
watch(() => props.initialId, id => { if (id && id !== activeId.value) select({ id }) })
onMounted(() => { load(); window.addEventListener('campus-sync', sync) })
onUnmounted(() => { ++sequence; window.removeEventListener('campus-sync', sync) })
</script>

<template>
  <div class="messages-page">
    <div class="section-title"><div><span class="eyebrow">保持联系，好物不擦肩</span><h1>我的消息</h1></div></div>
    <div class="message-center">
    <aside class="conversation-panel"><div class="panel-heading">全部会话 <span>{{ conversations.length }}</span></div>
      <div v-if="!conversations.length" class="chat-empty"><ChatDotRound /><h3>暂无消息，去逛逛市场吧</h3><button class="soft-button" @click="emit('market')">逛市场</button><button v-if="!user" class="text-button" @click="emit('login')">登录查看消息</button></div>
      <button v-for="item in conversations" :key="item.id" class="conversation-item" :class="{ selected: activeId === item.id }" @click="select(item)"><el-badge :value="Number(item.unread_count)" :hidden="!Number(item.unread_count)" :max="99"><el-avatar :size="42">{{ item.peer_name?.slice(0,1) }}</el-avatar></el-badge><span><b>{{ item.peer_name }}</b><small>{{ item.last_message || '还没有消息，打个招呼吧' }}</small></span></button>
    </aside>
    <section class="dialogue-panel">
      <div v-if="!activeId" class="chat-empty dialogue-empty"><ChatDotRound /><h3>选择一个对话，开始聊天吧</h3><p>聊聊成色、价格，或约个见面地点。</p></div>
      <template v-else><header class="dialogue-heading"><el-avatar :size="36">{{ active?.peer_name?.slice(0,1) || '同' }}</el-avatar><b>{{ active?.peer_name || '校园同学' }}</b><span>校园交易，请确认实物后结算货款</span></header>
        <el-alert v-if="error" :title="error" type="warning" :closable="false" /><div ref="history" class="chat-history" role="log" aria-label="聊天记录"><button v-if="more" class="text-button history-more" :disabled="busy" @click="load(true)">查看更早消息</button><p v-if="!messages.length && !busy" class="chat-start">还没有消息，打个招呼吧</p><div v-for="item in messages" :key="item.id" class="chat-row" :class="{ own: item.sender_id === user?.id }"><div><p class="chat-bubble">{{ item.content }}</p><time>{{ time(item.created_at) }}</time></div></div></div>
        <form class="chat-composer" @submit.prevent="send"><textarea v-model="draft" aria-label="消息内容" placeholder="输入消息，和同学聊一聊…" maxlength="2000" :disabled="sending" @keydown.ctrl.enter.prevent="send" /><div><small>{{ draft.length }}/2000 · Ctrl + Enter 发送</small><el-button type="primary" native-type="submit" :icon="Promotion" :loading="sending" :disabled="!draft.trim()">发送</el-button></div></form>
      </template>
    </section>
    </div>
  </div>
</template>
