<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import axios from 'axios'
import OrderCenter from './components/OrderCenter.vue'
import WalletPanel from './components/WalletPanel.vue'
import AdminPayments from './components/AdminPayments.vue'
import GoodsCard from './components/GoodsCard.vue'
import MessageCenter from './components/MessageCenter.vue'
import ParticleField from './components/ParticleField.vue'
import { contact, categoryIcons } from './site'
import './styles.css'
import { Search, ChatDotRound, ArrowDown, Plus, Message, Phone, Location } from '@element-plus/icons-vue'

const current = ref('home')
const transitionsEnabled = import.meta.env.MODE !== 'test'
const navRef = ref(null)
const navIndicator = ref({ left: '0px', width: '0px', opacity: 0 })
const scrolled = ref(false)
const goods = ref([])
const lost = ref([])
const audits = ref([])
const categories = ref([])
const conversations = ref([])
const personalGoods = ref([])
const chatId = ref(null)
const viewed = new Set()
const unread = computed(() => conversations.value.reduce((sum, item) => sum + Number(item.unread_count || 0), 0))
const selected = ref(null)
const keyword = ref('')
const lostKeyword = ref('')
const lostResults = ref([])
const uploadBusy = ref(false)
const submitBusy = ref(false)
const online = ref(true)
const revenue = ref(null)
const feeRate = ref(null)
let syncTimer
let searchTimer
let searchSequence = 0

function updateNavIndicator() {
  const nav = navRef.value
  const activeButton = nav?.querySelector('button.active')
  if (!nav || !activeButton) {
    navIndicator.value = { left: '0px', width: '0px', opacity: 0 }
    return
  }
  const navRect = nav.getBoundingClientRect()
  const buttonRect = activeButton.getBoundingClientRect()
  navIndicator.value = { left: `${buttonRect.left - navRect.left}px`, width: `${buttonRect.width}px`, opacity: 1 }
}

function handleScroll() { scrolled.value = window.scrollY > 24 }
const category = ref('全部分类')
const message = ref('')
const authVisible = ref(false)
const authMode = ref('login')
const loginForm = ref({ username: '', password: '' })
function savedUser() { try { return JSON.parse(localStorage.getItem('campus_user') || 'null') } catch { localStorage.removeItem('campus_user'); localStorage.removeItem('campus_token'); return null } }
const user = ref(savedUser())
const preview = new URLSearchParams(location.search).get('preview') === '1'
const loading = ref(false)
const form = ref({ title: '', categoryId: 1, description: '', price: 0, originalPrice: null, tradeLocation: '', condition: '9成新', deliveryMode: 'DORM_DELIVERY', deliveryNote: '', bargainingAllowed: true, images: [] })
const lostForm = ref({ type: 'LOST', title: '', location: '', description: '' })

const demoGoods = [
  { id: 1, title: '计算机网络教材全套', category: '教材资料', price: 35, original_price: 68, view_count: 128, condition: '9成新', emoji: '📖', description: '软件技术专业常用教材，笔记整洁。', delivery_mode: 'DORM_DELIVERY', delivery_note: '可送至宿舍楼下', bargaining_allowed: true, seller_nickname: '林同学', seller_credit: null, completed_trades: 0 },
  { id: 2, title: '蓝牙降噪耳机', category: '数码产品', price: 99, original_price: 299, view_count: 96, condition: '8成新', emoji: '🎧', description: '功能正常，续航稳定，适合自习和通勤。', delivery_mode: 'SELF_PICKUP', delivery_note: '教学楼门口自提', bargaining_allowed: false, seller_nickname: '陈同学', seller_credit: null, completed_trades: 0 },
  { id: 3, title: '机械键盘办公自用', category: '数码产品', price: 120, original_price: 189, view_count: 73, condition: '9成新', emoji: '⌨️', description: '手感清脆，无明显划痕。', delivery_mode: 'DORM_DELIVERY', delivery_note: '可送到 3 号宿舍楼', bargaining_allowed: true, seller_nickname: '周同学', seller_credit: null, completed_trades: 0 },
  { id: 4, title: '宿舍折叠椅', category: '生活用品', price: 25, original_price: 49, view_count: 52, condition: '近全新', emoji: '🪑', description: '折叠方便，宿舍学习休息都能用。', delivery_mode: 'SELF_PICKUP', delivery_note: '南区驿站自提', bargaining_allowed: true, seller_nickname: '王同学', seller_credit: null, completed_trades: 0 }
]
const demoLost = [
  { id: 1, title: '在图书馆三楼捡到一串钥匙', location: '图书馆三楼', emoji: '🔑', type: 'FOUND', status: '未认领' },
  { id: 2, title: '寻找校园卡，姓名：李同学', location: '食堂一楼', emoji: '🎫', type: 'LOST', status: '寻找中' }
]

goods.value = preview ? demoGoods.map(item => ({ ...item, seller_credit: null, completed_trades: 0 })) : []
lost.value = preview ? demoLost : []
categories.value = preview ? ['教材资料', '数码产品', '生活用品', '运动器材', '服饰鞋包', '美妆个护', '家用电器', '出行工具', '乐器文娱', '手作周边', '其他'].map((name, index) => ({ id: index + 1, name })) : []

const pageTitle = computed(() => ({ home: '首页', goods: '市场', messages: '消息', orders: '我的订单', mine: '我的商品', favorites: '我的收藏', detail: '商品详情', publish: '发布信息', lost: '失物招领', profile: '我的', admin: '管理后台' }[current.value] || '首页'))
const filteredGoods = computed(() => goods.value.filter(item => {
  const text = keyword.value.trim()
  const matchText = !text || [item.title, item.category, item.description, item.seller_nickname].some(v => String(v || '').includes(text))
  const matchCategory = category.value === '全部分类' || item.category === category.value
  return matchText && matchCategory
}))
const userInitial = computed(() => user.value?.nickname?.slice(0, 1) || user.value?.username?.slice(0, 1) || '我')

const shownLost = computed(() => lostKeyword.value.trim() ? lostResults.value : lost.value)
function lostStatus(item) { return { OPEN: item.type === 'FOUND' ? '等待认领' : '寻找中', RESOLVED: '已找回', CLOSED: '已结束' }[item.status] || (preview ? item.status : '待确认') }
function typeText(type) { return { GOODS: '二手商品', LOST_FOUND: '失物招领' }[type] || '信息' }
function auditText(status) { return { PENDING: '等待审核', APPROVED: '审核通过', REJECTED: '未通过审核' }[status] || '待确认' }
async function searchLost() {
  const sequence = ++searchSequence
  if (preview) { lostResults.value = lost.value.filter(item => [item.title, item.location].some(text => text?.includes(lostKeyword.value.trim()))); return }
  try {
    const { data } = await axios.get('/lost-found', { params: { keyword: lostKeyword.value.trim() } })
    if (sequence === searchSequence) lostResults.value = data
  } catch { if (sequence === searchSequence) { lostResults.value = []; message.value = '搜索暂时不可用，请稍后重试' } }
}
watch(lostKeyword, () => { clearTimeout(searchTimer); ++searchSequence; searchTimer = setTimeout(searchLost, 300) })
watch(current, () => nextTick(updateNavIndicator))
async function uploadPhotos(event) {
  const files = [...event.target.files]
  event.target.value = ''
  if (!requireLogin() || uploadBusy.value) return
  if (files.length + form.value.images.length > 6) { message.value = '每件商品最多上传6张照片'; return }
  uploadBusy.value = true
  try {
    for (const file of files) {
      if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) throw new Error('请选择5MB以内的JPG或PNG图片')
      const data = new FormData()
      data.append('file', file)
      const response = await axios.post('/media', data)
      form.value.images.push(response.data.url)
    }
    message.value = '照片上传完成，可继续完善商品信息'
  } catch (failure) { message.value = failure.response?.data?.message || failure.message || '上传失败，请重试' }
  finally { uploadBusy.value = false }
}
function creditText(item) { return item.seller_credit == null ? '暂无评价' : String(item.seller_credit) }
function boolValue(value) { return value === true || value === 1 || value === '1' || value === 'true' }
function normalizeGood(item) {
  return { ...item, images: item.images?.length ? item.images : (item.image_url ? [item.image_url] : []), category: item.category || '校园好物', condition: item.condition || item.condition_level || '成色良好', delivery_mode: item.delivery_mode || 'SELF_PICKUP', delivery_note: item.delivery_note || (item.delivery_mode === 'DORM_DELIVERY' ? '可送至宿舍楼下' : '校内自提'), bargaining_allowed: boolValue(item.bargaining_allowed), seller_nickname: item.seller_nickname || '校园同学', seller_credit: item.seller_credit == null ? null : Number(item.seller_credit), completed_trades: Number(item.completed_trades || 0) }
}
function deliveryText(item) { return item.delivery_mode === 'DORM_DELIVERY' ? '可配送到楼下' : '需自提' }
function bargainText(item) { return item.bargaining_allowed ? '可小刀' : '不议价' }
function go(page) {
  if (preview && page === 'admin') { message.value = '预览模式无管理权限'; return }
  if (['publish', 'profile', 'orders', 'mine', 'favorites'].includes(page) && !user.value) { authVisible.value = true; message.value = '请先登录后继续'; return }
  if (page === 'admin' && (!user.value || user.value.role !== 'ADMIN')) { authVisible.value = true; message.value = '此页面需要管理员权限'; return }
  current.value = page
  if (page === 'admin') loadAdmin()
  if (['mine', 'favorites'].includes(page)) loadPersonal()
  if (page === 'messages') { chatId.value = null; loadConversations() }
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
async function openDetail(item) {
  selected.value = normalizeGood(item)
  go('detail')
  if (preview || viewed.has(item.id) || (item.audit_status && item.audit_status !== 'APPROVED') || (item.trade_status && item.trade_status !== 'ON_SALE')) return
  viewed.add(item.id)
  try { const { data } = await axios.post('/goods/' + item.id + '/views'); item.view_count = data.view_count; if (selected.value?.id === item.id) selected.value.view_count = data.view_count }
  catch { viewed.delete(item.id) }
}
function showAuth(mode = 'login') { authMode.value = mode; authVisible.value = true }
async function loadConversations() {
  if (!user.value || preview) { conversations.value = []; return }
  const owner = user.value.id
  try { const { data } = await axios.get('/conversations'); if (user.value?.id === owner) conversations.value = data }
  catch { }
}
async function loadPersonal() {
  personalGoods.value = []
  const page = current.value
  try { const { data } = await axios.get(page === 'mine' ? '/me/goods' : '/me/favorites'); if (current.value === page) personalGoods.value = data.map(normalizeGood) }
  catch (failure) { message.value = failure.response?.data?.message || '暂时无法加载，请稍后重试' }
}
async function chatWithSeller() {
  if (!requireLogin() || !selected.value) return
  try { const { data } = await axios.post('/conversations', { goodsId: selected.value.id }); await loadConversations(); go('messages'); chatId.value = data.id }
  catch (failure) { message.value = failure.response?.data?.message || '暂时无法联系卖家' }
}
async function saveFavorite() {
  if (!requireLogin() || !selected.value) return
  try { await axios.put('/me/favorites/' + selected.value.id); message.value = '已加入我的收藏' }
  catch (failure) { message.value = failure.response?.data?.message || '收藏失败，请重试' }
}
async function removeFavorite(item) {
  try { await axios.delete('/me/favorites/' + item.id); personalGoods.value = personalGoods.value.filter(good => good.id !== item.id) }
  catch { message.value = '取消收藏失败，请重试' }
}
function tradeText(item) { return item.audit_status !== 'APPROVED' ? auditText(item.audit_status) : ({ ON_SALE: '在售', RESERVED: '已预订', SOLD: '已售出', OFF_SHELF: '已下架' }[item.trade_status] || '在售') }
function requireLogin() { if (preview) { message.value = '预览模式不能提交交易'; return false } if (!user.value) { authVisible.value = true; message.value = '请先登录后继续'; return false } return true }
async function load(silent = false) {
  if (preview || loading.value) return
  if (!silent) loading.value = true
  try {
    const [goodsResponse, lostResponse, paymentResponse, categoryResponse] = await Promise.all([axios.get('/goods'), axios.get('/lost-found'), axios.get('/payment-settings'), axios.get('/categories')])
    categories.value = categoryResponse.data
    feeRate.value = Number(paymentResponse.data.serviceFeeRate)
    goods.value = goodsResponse.data.map(normalizeGood)
    lost.value = lostResponse.data
    online.value = true
    if (selected.value) { const fresh = goods.value.find(item => item.id === selected.value.id); if (fresh) selected.value = fresh; else if (current.value === 'detail' && selected.value.audit_status === 'APPROVED' && selected.value.trade_status === 'ON_SALE') { selected.value = null; current.value = 'goods'; message.value = '该商品已被预订或下架，请看看其他商品' } }
  } catch (error) { online.value = false; if (!silent) message.value = error.response?.data?.message || '暂时无法连接，请稍后重试' } finally { loading.value = false }
}
async function loadAdmin() {
  try { const [auditResponse, revenueResponse] = await Promise.all([axios.get('/admin/audits'), axios.get('/admin/revenue')]); audits.value = auditResponse.data; revenue.value = revenueResponse.data } catch (error) { message.value = error.response?.data?.message || '审核列表加载失败' }
}
async function authenticate() {
  if (preview) { message.value = '请退出预览模式后登录'; return }
  try {
    const { data } = await axios.post('/auth/' + authMode.value, loginForm.value)
    if (authMode.value === 'register') { authMode.value = 'login'; message.value = '注册成功，请登录'; return }
    localStorage.setItem('campus_token', data.token)
    localStorage.setItem('campus_user', JSON.stringify(data.user))
    user.value = data.user
    authVisible.value = false
    message.value = '登录成功'
    loadConversations()
  } catch (error) { message.value = error.response?.data?.message || '操作失败，请检查账号密码' }
}
function logout() { conversations.value = []; personalGoods.value = []; chatId.value = null; audits.value = []; selected.value = null; localStorage.removeItem('campus_token'); localStorage.removeItem('campus_user'); user.value = null; current.value = 'home'; message.value = '已退出登录' }
async function submitGoods() {
  if (!requireLogin() || submitBusy.value || uploadBusy.value) return
  if (!form.value.images.length) { message.value = '请至少上传1张商品实拍照片'; return }
  submitBusy.value = true
  try { await axios.post('/goods', form.value); form.value = { title: '', categoryId: 1, description: '', price: 0, originalPrice: null, tradeLocation: '', condition: '9成新', deliveryMode: 'DORM_DELIVERY', deliveryNote: '', bargainingAllowed: true, images: [] }; message.value = '商品已提交，等待管理员审核'; go('profile') } catch (error) { message.value = error.response?.data?.message || '发布失败' } finally { submitBusy.value = false }
}
async function submitLost() {
  if (!requireLogin()) return
  try { await axios.post('/lost-found', lostForm.value); message.value = '失物信息已提交，等待管理员审核'; go('profile') } catch (error) { message.value = error.response?.data?.message || '发布失败' }
}
async function buy() {
  if (!requireLogin() || !selected.value) return
  try { await axios.post('/orders/intentions', { goodsId: selected.value.id, message: '我对该商品感兴趣，请联系我' }); message.value = '购买意向已提交，可在“我的”查看交易进度' } catch (error) { message.value = error.response?.data?.message || '提交失败' }
}
async function audit(item, status) {
  try { await axios.put('/admin/audits/' + item.type + '/' + item.id + '?status=' + status); message.value = status === 'APPROVED' ? '审核已通过' : '内容已驳回'; await loadAdmin(); await load(true) } catch (error) { message.value = error.response?.data?.message || '审核失败' }
}
function authExpired() {
  user.value = null
  conversations.value = []
  personalGoods.value = []
  audits.value = []
  if (['profile', 'publish', 'admin', 'orders', 'mine', 'favorites', 'messages'].includes(current.value)) current.value = 'home'
  authVisible.value = true
  message.value = '登录已失效，请重新登录'
}
async function sync() {
  if (!document.hidden && !preview) {
    await load(true)
    await loadConversations()
    if (current.value === 'admin' && user.value?.role === 'ADMIN') await loadAdmin()
    if (current.value === 'lost' && lostKeyword.value.trim()) await searchLost()
    window.dispatchEvent(new Event('campus-sync'))
  }
}
async function scheduleSync() { await sync(); syncTimer = setTimeout(scheduleSync, 5000) }
onMounted(() => { window.addEventListener('campus-auth-expired', authExpired); document.addEventListener('visibilitychange', sync); window.addEventListener('scroll', handleScroll, { passive: true }); window.addEventListener('resize', updateNavIndicator, { passive: true }); handleScroll(); load(); loadConversations(); syncTimer = setTimeout(scheduleSync, 5000); nextTick(updateNavIndicator) })
onUnmounted(() => { window.removeEventListener('campus-auth-expired', authExpired); document.removeEventListener('visibilitychange', sync); window.removeEventListener('scroll', handleScroll); window.removeEventListener('resize', updateNavIndicator); clearTimeout(syncTimer); clearTimeout(searchTimer) })
</script>


<template>
  <el-container class="page-shell">
    <ParticleField />
    <div class="fluid-ribbons" aria-hidden="true"><span class="ribbon ribbon-one"></span><span class="ribbon ribbon-two"></span><span class="ribbon ribbon-three"></span></div>
    <el-header class="topbar" :class="{ compact: scrolled }"><div class="topbar-inner">
      <button class="brand" @click="go('home')"><span class="brand-mark">享</span><span class="brand-name">享购<small>校园闲置</small></span></button>
      <nav ref="navRef" class="primary-nav" aria-label="主导航"><button v-for="nav in [{id:'home',label:'首页'},{id:'goods',label:'市场'},{id:'lost',label:'失物'}]" :key="nav.id" :class="{ active: current === nav.id }" @click="go(nav.id)">{{ nav.label }}</button><span class="nav-indicator" :style="{ left: navIndicator.left, width: navIndicator.width, opacity: navIndicator.opacity }" aria-hidden="true"></span></nav>
      <div class="header-actions">
        <el-badge :value="unread" :hidden="!unread" :max="99"><button class="nav-button" :class="{ active: current === 'messages' }" @click="go('messages')"><ChatDotRound />消息</button></el-badge>
        <el-popover placement="bottom-end" :width="300" trigger="click" popper-class="contact-popover"><template #reference><button class="nav-button">联系我们</button></template><h3>很高兴与你联系</h3><p class="muted">你的建议，让享购更好一点。</p><div class="contact-item"><Message /><div><small>开发者联系邮箱</small><a v-if="contact.email" :href="'mailto:' + contact.email">{{ contact.email }}</a><span v-else>暂未公布</span></div></div><div class="contact-item"><Phone /><div><small>电话热线</small><a v-if="contact.phone" :href="'tel:' + contact.phone">{{ contact.phone }}</a><span v-else>暂未公布</span></div></div><div class="contact-item"><Location /><div><small>办公地址</small><span>{{ contact.address || '暂未公布' }}</span></div></div></el-popover>
        <template v-if="!user"><button class="nav-button" @click="showAuth('login')">登录</button><button class="register-button" @click="showAuth('register')">注册</button></template>
        <el-dropdown v-else trigger="click" placement="bottom-end"><button class="avatar-button" aria-label="用户菜单"><el-avatar :size="32">{{ userInitial }}</el-avatar><span>{{ user.nickname || user.username }}</span><ArrowDown /></button><template #dropdown><el-dropdown-menu><el-dropdown-item @click="go('orders')">我的订单</el-dropdown-item><el-dropdown-item @click="go('profile')">我的主页</el-dropdown-item><el-dropdown-item @click="go('mine')">我的商品</el-dropdown-item><el-dropdown-item @click="go('favorites')">我的收藏</el-dropdown-item><el-dropdown-item v-if="user.role === 'ADMIN'" @click="go('admin')">管理后台</el-dropdown-item><el-dropdown-item divided @click="logout">退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
      </div>
    </div></el-header>
    <el-main class="main-content" v-loading="loading">
      <el-alert v-if="preview" title="只读预览 · 示例商品，不参与交易" type="warning" :closable="false" />
      <div v-if="current !== 'home'" class="crumb"><button class="text-button" @click="go('home')">享购</button><span>/ {{ pageTitle }}</span></div>
      <el-alert v-if="!online" title="连接暂时中断，正在尝试重新连接" type="warning" :closable="false" />
      <el-alert v-if="message" class="message" :title="message" type="info" show-icon closable @close="message = ''" />
      <Transition name="page" mode="out-in" appear :css="transitionsEnabled">
      <section v-if="current === 'home'" key="home" class="home-page">
        <div class="home-hero"><div class="hero-copy"><span class="eyebrow"><i></i>同一个校园，让好物再次被喜欢</span><h1>闲置有新归处，<br />生活有小惊喜<span>。</span></h1><p>从一套教材到一辆单车，把用不到的交给刚好需要的人。</p><div class="hero-actions"><button class="solid-button" @click="go('goods')">发现校园好物 <span>↗</span></button><button class="soft-button" @click="go('publish')"><Plus />发布闲置</button></div><span class="hero-note">校内见面 · 轻松交流 · 循环使用</span></div><div class="hero-art" aria-hidden="true"><span class="orbit orbit-one"></span><span class="orbit orbit-two"></span><div class="art-card art-book"><span>BOOKS & MORE</span><b>翻开<br />下一段故事</b><div>📚</div></div><div class="art-card art-headphone"><span>FIND YOUR FAVORITE</span><div>🎧</div><b>好物，值得再相遇</b></div><span class="art-sticker">给闲置<br /><b>第二次心动 ✦</b></span><span class="art-spark">✳</span></div></div>
        <div class="category-section"><div class="section-title"><div><h2>按兴趣，逛一逛</h2><p>校园生活的每一面，都有值得发现的好物</p></div><button class="text-button" @click="category='全部分类';go('goods')">全部分类 ↗</button></div><div class="category-grid"><button v-for="item in categories" :key="item.id" @click="category=item.name;go('goods')"><span>{{ categoryIcons[item.name] || '✨' }}</span><b>{{ item.name }}</b></button></div></div>
        <div class="section-title"><div><span class="eyebrow">JUST ARRIVED</span><h2>最新发布</h2></div><button class="text-button" @click="category='全部分类';go('goods')">逛逛整个市场 ↗</button></div>
         <el-empty v-if="!loading && !goods.length" description="好物正在路上，发布你的第一件闲置吧"><el-button type="primary" @click="go('publish')">发布闲置</el-button></el-empty><TransitionGroup name="goods-list" tag="div" class="goods-grid" appear><GoodsCard v-for="(item, index) in goods.slice(0,12)" :key="item.id" :item="item" :style="{ '--card-delay': `${index * 70}ms` }" @open="openDetail" /></TransitionGroup>
      </section>
       <section v-else-if="current === 'goods'" key="goods"><div class="section-title"><div><span class="eyebrow">CAMPUS MARKET</span><h1>校园市场</h1><p>用心挑选，刚好是你需要的。</p></div><el-button type="primary" :icon="Plus" @click="go('publish')">发布闲置</el-button></div><div class="toolbar"><el-input v-model="keyword" placeholder="搜索商品、分类或卖家" :prefix-icon="Search" clearable size="large" /><el-select v-model="category" size="large"><el-option label="全部分类" value="全部分类" /><el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.name" /></el-select></div><el-empty v-if="!loading && !filteredGoods.length" description="暂无符合条件的商品，换个关键词试试" /><TransitionGroup name="goods-list" tag="div" class="goods-grid" appear><GoodsCard v-for="(item, index) in filteredGoods" :key="item.id" :item="item" :style="{ '--card-delay': `${index * 70}ms` }" @open="openDetail" /></TransitionGroup></section>
       <section v-else-if="current === 'detail' && selected" key="detail" class="detail-card"><div class="detail-gallery"><el-image v-if="selected.images?.length" :src="selected.images[0]" :preview-src-list="selected.images" fit="contain" class="detail-photo" /><div v-else class="detail-cover">暂无实拍图</div><div class="photo-thumbs"><el-image v-for="(photo, index) in selected.images" :key="photo" :src="photo" :preview-src-list="selected.images" :initial-index="index" fit="cover" /></div></div><div class="detail-info"><el-tag type="success">{{ deliveryText(selected) }}</el-tag><el-tag :type="selected.bargaining_allowed ? 'warning' : 'info'">{{ bargainText(selected) }}</el-tag><h1>{{ selected.title }}</h1><p class="muted">{{ selected.category }} · {{ selected.condition }}</p><div class="price-row"><strong class="detail-price">¥{{ Number(selected.price).toFixed(2) }}</strong><del v-if="selected.original_price != null">¥{{ Number(selected.original_price).toFixed(2) }}</del></div><p class="muted">交易地点：{{ selected.trade_location || selected.delivery_note || '与卖家协商' }} · {{ selected.view_count || 0 }} 次浏览</p><p v-if="feeRate != null" class="field-help">买家另付 {{ (feeRate * 100).toFixed(0) }}% 平台服务费（¥{{ (Number(selected.price) * feeRate).toFixed(2) }}），人工核实后交易。商品货款由双方另行结算。</p><p>{{ selected.description }}</p><div class="seller-box"><b>卖家信用</b><span>{{ selected.seller_nickname }} · ⭐ {{ creditText(selected) }}</span><small>已完成 {{ selected.completed_trades }} 笔交易 · 信用分满分 5 分</small><small>{{ selected.delivery_note }}</small></div><div v-if="(!selected.audit_status || selected.audit_status === 'APPROVED') && (!selected.trade_status || selected.trade_status === 'ON_SALE') && selected.seller_id !== user?.id" class="detail-actions"><el-button type="primary" @click="buy">提交购买意向</el-button><el-button @click="chatWithSeller">联系卖家</el-button><el-button @click="saveFavorite">收藏商品</el-button></div><p v-else class="muted">{{ tradeText(selected) }}</p><el-button @click="go('goods')">返回列表</el-button></div></section>
       <section v-else-if="current === 'publish'" key="publish" class="publish-card"><el-tabs type="border-card"><el-tab-pane label="发布二手商品"><el-form label-width="96px"><el-form-item label="商品照片"><div><label class="photo-upload" :class="{ disabled: uploadBusy }"><input type="file" accept="image/jpeg,image/png" multiple :disabled="uploadBusy || submitBusy" @change="uploadPhotos" />{{ uploadBusy ? '正在上传…' : '＋ 上传实拍照片' }}</label><p class="field-help">最多6张，每张不超过5MB。建议拍摄整体、细节和瑕疵，第一张作为封面。请勿上传他人隐私。</p><div class="uploaded-photos"><div v-for="(photo, index) in form.images" :key="photo"><el-image :src="photo" :preview-src-list="form.images" fit="cover" /><button type="button" :disabled="submitBusy" @click="form.images.splice(index, 1)">移除</button><small v-if="index === 0">封面</small></div></div></div></el-form-item><el-form-item label="商品标题"><el-input v-model="form.title" /></el-form-item><el-form-item label="商品分类"><el-select v-model="form.categoryId"><el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item label="售价（元）"><el-input-number v-model="form.price" :min="0" :max="99999999.99" :precision="2" /></el-form-item><el-form-item label="原价（元）"><el-input-number v-model="form.originalPrice" :min="0" :max="99999999.99" :precision="2" placeholder="选填，填写真实购入价" /></el-form-item><el-form-item label="成色"><el-select v-model="form.condition"><el-option label="全新未用" value="全新未用" /><el-option label="7成新及以下" value="7成新及以下" /><el-option label="近全新" value="近全新" /><el-option label="9成新" value="9成新" /><el-option label="8成新" value="8成新" /></el-select></el-form-item><el-form-item label="交易地点"><el-input v-model="form.tradeLocation" maxlength="100" placeholder="如：图书馆正门、3号宿舍楼下" /></el-form-item><el-form-item label="配送方式"><el-radio-group v-model="form.deliveryMode"><el-radio value="DORM_DELIVERY">可配送到楼下</el-radio><el-radio value="SELF_PICKUP">需自提</el-radio></el-radio-group></el-form-item><el-form-item label="配送说明"><el-input v-model="form.deliveryNote" placeholder="如：可送 3 号宿舍楼下 / 图书馆门口自提" /></el-form-item><el-form-item label="是否议价"><el-switch v-model="form.bargainingAllowed" active-text="可小刀" inactive-text="不议价" /></el-form-item><el-form-item label="商品描述"><el-input v-model="form.description" type="textarea" :rows="5" /></el-form-item><el-button type="primary" :loading="submitBusy" :disabled="uploadBusy" @click="submitGoods">提交审核</el-button></el-form></el-tab-pane><el-tab-pane label="发布失物信息"><el-form label-width="96px"><el-form-item label="类型"><el-radio-group v-model="lostForm.type"><el-radio value="LOST">寻物</el-radio><el-radio value="FOUND">拾物</el-radio></el-radio-group></el-form-item><el-form-item label="标题"><el-input v-model="lostForm.title" /></el-form-item><el-form-item label="地点"><el-input v-model="lostForm.location" /></el-form-item><el-form-item label="描述"><el-input v-model="lostForm.description" type="textarea" :rows="5" /></el-form-item><el-button type="primary" @click="submitLost">提交审核</el-button></el-form></el-tab-pane></el-tabs></section>
       <section v-else-if="current === 'lost'" key="lost"><el-input v-model="lostKeyword" placeholder="搜索物品名称、特征或丢失地点" maxlength="100" clearable size="large" :prefix-icon="Search" /><el-empty v-if="!shownLost.length" description="暂时没有找到相关信息，换个关键词试试" /><div class="section-title"><h2>失物招领</h2><el-button type="primary" @click="go('publish')">发布信息</el-button></div><div class="lost-grid"><article v-for="item in shownLost" :key="item.id" class="lost-card"><span>{{ item.emoji || '🔎' }}</span><b>{{ item.title }}</b><small>{{ item.location }} · {{ lostStatus(item) }}</small></article></div></section>
      <MessageCenter v-else-if="current === 'messages'" :key="user?.id || 'guest'" :user="user" :conversations="conversations" :initial-id="chatId" @market="go('goods')" @login="showAuth()" @refresh="loadConversations" />
      <section v-else-if="current === 'profile'"><div class="profile-card"><el-avatar :size="72">{{ userInitial }}</el-avatar><div><span class="eyebrow">MY CAMPUS LIFE</span><h1>{{ user?.nickname || user?.username }}的主页</h1><p>让每一件闲置，都遇到新的喜欢。</p></div></div><div class="profile-shortcuts"><button class="soft-button" @click="go('orders')">我的订单 ↗</button><button class="soft-button" @click="go('mine')">我的商品 ↗</button><button class="soft-button" @click="go('favorites')">我的收藏 ↗</button><button class="solid-button" @click="go('publish')">发布闲置 ＋</button><el-button v-if="user?.role === 'ADMIN'" @click="go('admin')">审核管理</el-button></div><WalletPanel v-if="user" :preview="preview" /></section>
      <section v-else-if="current === 'orders'"><div class="section-title"><h1>我的订单</h1></div><OrderCenter v-if="user" :key="user.id" :user="user" :preview="preview" @updated="load" /></section>
      <section v-else-if="current === 'mine' || current === 'favorites'"><div class="section-title"><div><h1>{{ current === 'mine' ? '我的商品' : '我的收藏' }}</h1><p>{{ current === 'mine' ? '发布、审核与出售进度，都在这里。' : '把喜欢留住，慢慢挑选。' }}</p></div><el-button type="primary" @click="go(current === 'mine' ? 'publish' : 'goods')">{{ current === 'mine' ? '发布闲置' : '逛市场' }}</el-button></div><el-empty v-if="!personalGoods.length" :description="current === 'mine' ? '还没有发布商品，分享你的第一件好物吧' : '还没有收藏，去市场发现喜欢的好物吧'" /><div class="goods-grid"><div v-for="item in personalGoods" :key="item.id"><GoodsCard :item="item" @open="openDetail" /><div class="personal-card-status"><el-tag>{{ tradeText(item) }}</el-tag><el-button v-if="current === 'favorites'" link @click="removeFavorite(item)">取消收藏</el-button></div></div></div></section>
      <section v-else><div v-if="revenue" class="revenue-card"><h2>平台收益</h2><p>已结算服务费 <strong>¥{{ Number(revenue.settledFees).toFixed(2) }}</strong> · 待结算 ¥{{ Number(revenue.pendingFees).toFixed(2) }}</p><p>成交服务费 {{ Number(revenue.serviceFeeRate) * 100 }}%，由买家单独支付。待核实退款 ¥{{ Number(revenue.pendingRefunds).toFixed(2) }}。</p><p>收款进入你设置的二维码账户。本站记录核实结果，不自动收款、转账或提现。</p></div><AdminPayments /><div class="stats"><el-card><b>待审核</b><strong>{{ audits.length }}</strong></el-card><el-card><b>商品管理</b><strong>{{ goods.length }}</strong></el-card><el-card><b>失物信息</b><strong>{{ lost.length }}</strong></el-card></div><el-card class="table-card"><template #header>待审核内容</template><el-table :data="audits"><el-table-column label="类型"><template #default="scope">{{ typeText(scope.row.type) }}</template></el-table-column><el-table-column prop="title" label="标题" /><el-table-column label="状态"><template #default="scope"><el-tag type="warning">{{ auditText(scope.row.audit_status) }}</el-tag></template></el-table-column><el-table-column label="实拍与描述" min-width="200"><template #default="scope"><div class="audit-photos"><el-image v-for="(photo,index) in scope.row.images" :key="photo" :src="photo" :preview-src-list="scope.row.images" :initial-index="index" preview-teleported fit="cover" /></div><p>{{ scope.row.description }}</p></template></el-table-column><el-table-column label="操作"><template #default="scope"><el-button size="small" type="success" @click="audit(scope.row, 'APPROVED')">通过</el-button><el-button size="small" type="danger" @click="audit(scope.row, 'REJECTED')">驳回</el-button></template></el-table-column></el-table></el-card></section>
      </Transition>
    </el-main>
    <footer class="site-footer"><div><button class="brand" @click="go('home')"><span class="brand-mark">享</span><b>享购-校园闲置交易平台</b></button><p>让好物流转，让校园生活轻盈一点。</p></div><div class="footer-credit"><strong>Xiang Gou</strong><p>© 2026 Campus-platform</p></div></footer>
    <el-dialog v-model="authVisible" :title="authMode === 'login' ? '登录享购' : '注册享购账号'" width="min(400px, calc(100vw - 32px))"><p class="muted">欢迎加入，发现身边的校园好物。</p><el-form label-position="top" @submit.prevent="authenticate"><el-form-item label="账号"><el-input v-model="loginForm.username" autocomplete="username" /></el-form-item><el-form-item label="密码"><el-input v-model="loginForm.password" type="password" :autocomplete="authMode === 'login' ? 'current-password' : 'new-password'" show-password @keyup.enter="authenticate" /></el-form-item></el-form><template #footer><el-button link @click="authMode = authMode === 'login' ? 'register' : 'login'">{{ authMode === 'login' ? '没有账号？去注册' : '已有账号？去登录' }}</el-button><el-button type="primary" @click="authenticate">{{ authMode === 'login' ? '登录' : '注册' }}</el-button></template></el-dialog>
  </el-container>
</template>
