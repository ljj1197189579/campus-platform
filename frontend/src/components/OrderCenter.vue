<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import axios from 'axios'
import PaymentDialog from './PaymentDialog.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({ user: { type: Object, required: true }, preview: Boolean })
const emit = defineEmits(['updated'])
const orders = ref([])
const credit = ref(null)
const tab = ref('buying')
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const reviewOrder = ref(null)
const paymentOrder = ref(null)
const paymentLabels = { UNPAID: '待支付服务费', PENDING: '等待核实到账', CONFIRMED: '服务费已核实，可安排交付', REJECTED: '付款凭证未通过' }
const reviewVisible = ref(false)
const rating = ref(5)
const content = ref('')
const statuses = { PENDING: '等待卖家确认', ACCEPTED: '等待付款或收货', COMPLETED: '交易完成', REJECTED: '意向未接受', CANCELLED: '已取消' }
const visibleOrders = computed(() => orders.value.filter(order => tab.value === 'buying' ? isBuyer(order) : !isBuyer(order)))
function isBuyer(order) { return Number(order.buyer_id) === Number(props.user.id) }

async function load() {
  if (props.preview) return
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    const [orderResponse, creditResponse] = await Promise.all([axios.get('/orders'), axios.get('/me/credit')])
    orders.value = orderResponse.data
    credit.value = creditResponse.data
  } catch (failure) {
    orders.value = []
    credit.value = null
    error.value = failure.response?.data?.message || '订单加载失败，请重试'
  } finally { loading.value = false }
}

async function act(order, action) {
  if (busy.value || props.preview) return
  busy.value = true
  try {
    if (action === 'COMPLETE') await ElMessageBox.confirm('请确认已收到商品，并已与卖家结清商品货款。平台仅收取服务费，确认后本次交易完成。', '确认收货', { confirmButtonText: '确认收货', cancelButtonText: '暂不确认' })
    if (action === 'CANCEL') await ElMessageBox.confirm('确认取消这笔交易？已预留的商品会重新上架。', '取消交易', { confirmButtonText: '确认取消', cancelButtonText: '返回' })
    await axios.put(`/orders/${order.id}/${action}`)
    ElMessage.success('订单已更新')
    window.dispatchEvent(new Event('campus-wallet-updated'))
    await load()
    emit('updated')
  } catch (failure) {
    if (failure !== 'cancel' && failure !== 'close') ElMessage.error(failure.response?.data?.message || '操作失败，请刷新重试')
  } finally { busy.value = false }
}

function startReview(order) {
  reviewOrder.value = order
  rating.value = 5
  content.value = ''
  reviewVisible.value = true
}

async function submitReview() {
  if (busy.value || props.preview || !reviewOrder.value || !rating.value) return
  busy.value = true
  try {
    await axios.post(`/orders/${reviewOrder.value.id}/review`, { rating: rating.value, content: content.value })
    reviewVisible.value = false
    ElMessage.success('评价已提交，卖家信用已更新')
    await load()
    emit('updated')
  } catch (failure) { ElMessage.error(failure.response?.data?.message || '评价失败，请重试') }
  finally { busy.value = false }
}
function refresh() { if (!busy.value) load() }
onMounted(() => { load(); window.addEventListener('campus-sync', refresh) })
onUnmounted(() => window.removeEventListener('campus-sync', refresh))
</script>

<template>
  <section class="order-center" v-loading="loading">
    <div class="credit-summary">
      <div><small>我的卖家信用 · 5分制</small><strong>{{ credit?.seller_credit == null ? '暂无评价' : Number(credit.seller_credit).toFixed(2) }}</strong></div>
      <div><small>完成交易</small><strong>{{ credit?.completed_trades ?? '—' }}</strong></div>
      <div><small>真实评价</small><strong>{{ credit?.review_count ?? '—' }}</strong></div>
    </div>
    <p class="credit-explanation">平台服务费由买家扫码支付，经人工核实后安排交付。商品货款由买卖双方另行结算，确认收货后可评价。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <div class="section-title"><h2>我的交易</h2><el-button :loading="loading" @click="load">刷新</el-button></div>
    <el-tabs v-model="tab"><el-tab-pane label="我买到的" name="buying" /><el-tab-pane label="我卖出的" name="selling" /></el-tabs>
    <el-empty v-if="!loading && !error && !visibleOrders.length" description="暂无交易记录" />
    <article v-for="order in visibleOrders" :key="order.id" class="order-card">
      <header><span>订单 #{{ order.id }}</span><el-tag>{{ order.status === 'ACCEPTED' ? (paymentLabels[order.payment_status] || '请重新提交购买申请') : (statuses[order.status] || '待确认') }}</el-tag></header>
      <h3>{{ order.title }} <span>¥{{ order.price }}</span></h3>
      <p>{{ isBuyer(order) ? '卖家：' + order.seller_nickname : '买家：' + order.buyer_nickname }}</p>
      <p>{{ order.delivery_mode === 'DORM_DELIVERY' ? '可配送到楼下' : '需自提' }} · {{ order.delivery_note || '交付地点请双方确认' }}</p>
      <p v-if="order.service_fee != null">买家另付平台服务费 ¥{{ Number(order.service_fee).toFixed(2) }} · 商品货款另行结算</p><p v-if="order.payment_note">{{ order.payment_note }}</p><p v-if="order.payment_status === 'REFUNDED'">平台已确认服务费退款，请核对原付款账户</p><p v-if="order.message">买家留言：{{ order.message }}</p>
      <div v-if="order.rating != null" class="order-rating"><el-rate :model-value="Number(order.rating)" disabled /><span>{{ order.review_content || '已评分' }}</span></div>
      <div class="order-actions">
        <template v-if="!isBuyer(order) && order.status === 'PENDING'"><el-button type="primary" :disabled="busy" @click="act(order, 'ACCEPT')">接受意向</el-button><el-button :disabled="busy" @click="act(order, 'REJECT')">拒绝</el-button></template>
        <template v-if="isBuyer(order) && order.status === 'ACCEPTED'"><el-button v-if="order.payment_status === 'CONFIRMED'" type="primary" :disabled="busy" @click="act(order, 'COMPLETE')">确认收货</el-button><el-button v-else-if="['UNPAID','REJECTED'].includes(order.payment_status)" type="primary" @click="paymentOrder = order">扫码付服务费 ¥{{ Number(order.service_fee).toFixed(2) }}</el-button><el-tag v-else-if="order.payment_status === 'PENDING'" type="warning">凭证已提交，请等待核实</el-tag></template>
        <el-button v-if="['PENDING', 'ACCEPTED'].includes(order.status)" :disabled="busy" @click="act(order, 'CANCEL')">取消交易</el-button>
        <el-button v-if="isBuyer(order) && order.status === 'COMPLETED' && order.rating == null" type="primary" @click="startReview(order)">评价卖家</el-button>
      </div>
    </article>
    <PaymentDialog v-if="paymentOrder" :order="paymentOrder" @close="paymentOrder = null" @submitted="load(); emit('updated')" /><el-dialog v-model="reviewVisible" title="评价卖家" width="min(440px, calc(100vw - 32px))" :close-on-click-modal="!busy" :show-close="!busy">
      <p>请根据本次交易如实评价，提交后不可重复评价。</p>
      <el-rate v-model="rating" :disabled="busy" />
      <el-input v-model="content" type="textarea" :rows="3" maxlength="255" show-word-limit placeholder="描述商品与沟通体验（选填）" :disabled="busy" />
      <template #footer><el-button type="primary" :loading="busy" :disabled="!rating" @click="submitReview">提交评价</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.order-center{margin-top:24px}.credit-summary{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;background:#fff;padding:20px;border:1px solid #e3e7e7;border-radius:12px}.credit-summary small{display:block;color:#667085}.credit-summary strong{display:block;font-size:24px;margin-top:10px;color:#087e73}.credit-explanation{font-size:13px;color:#667085;line-height:1.7}.order-card{background:#fff;border:1px solid #e3e7e7;border-radius:12px;padding:20px;margin-bottom:14px}.order-card header{display:flex;justify-content:space-between;gap:12px;align-items:center;color:#667085}.order-card h3{display:flex;justify-content:space-between;gap:12px;overflow-wrap:anywhere}.order-card h3 span{color:#c44c39;white-space:nowrap}.order-card p{color:#667085;overflow-wrap:anywhere;font-size:14px}.order-actions{display:flex;flex-wrap:wrap;gap:8px}.order-actions .el-button{margin:0}.order-rating{display:grid;gap:6px;margin:12px 0;overflow-wrap:anywhere}@media(max-width:500px){.credit-summary{gap:8px;padding:14px}.credit-summary strong{font-size:20px}.credit-summary small{font-size:11px}.order-card{padding:14px}}
</style>
