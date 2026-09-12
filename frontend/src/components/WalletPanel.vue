<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import axios from 'axios'
const props = defineProps({ preview: Boolean })
const account = ref(null)
const error = ref('')
let loading = false
const labels = { UNPAID: '待支付服务费', PENDING: '等待核实到账', CONFIRMED: '服务费已核实', SETTLED: '交易完成', REJECTED: '凭证未通过，请重新提交', REFUND_PENDING: '等待核实退款', REFUNDED: '已确认退款', CANCELLED: '已取消' }
function money(value) { return Number(value || 0).toFixed(2) }
async function load() {
  if (props.preview || loading) return
  loading = true
  try { account.value = (await axios.get('/wallet')).data; error.value = '' }
  catch { error.value = '付款记录暂时无法加载，请稍后重试' }
  finally { loading = false }
}
onMounted(() => { load(); window.addEventListener('campus-sync', load); window.addEventListener('campus-wallet-updated', load) })
onUnmounted(() => { window.removeEventListener('campus-sync', load); window.removeEventListener('campus-wallet-updated', load) })
</script>
<template>
  <section class="wallet-panel">
    <h2>我的付款</h2>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div class="wallet-grid"><div><small>已付服务费（元）</small><strong>{{ account ? money(account.paidFees) : '—' }}</strong></div><div><small>待核实（元）</small><strong>{{ account ? money(account.pendingFees) : '—' }}</strong></div><div><small>待退款核实（元）</small><strong>{{ account ? money(account.pendingRefunds) : '—' }}</strong></div></div>
    <p>平台按成交金额收取 {{ account ? Number(account.serviceFeeRate) * 100 : '—' }}% 服务费，由买家单独扫码支付。商品货款由买卖双方另行结算，不进入平台账户，因此这里没有商品余额或提现。</p>
    <p>取消交易后，已提交的付款记录将交由平台核实退款。退款退回原付款渠道，请以微信或支付宝的实际到账记录为准。</p>
    <el-empty v-if="!account?.payments?.length" description="暂无服务费付款记录" />
    <article v-for="item in account?.payments" :key="item.order_id" class="bill"><div><b>{{ item.title }}</b><small>订单 {{ item.order_id }} · {{ labels[item.status] || '待确认' }}</small><p v-if="item.note">{{ item.note }}</p></div><strong>¥{{ money(item.fee) }}</strong></article>
  </section>
</template>
<style scoped>
.wallet-panel{background:#fff;padding:24px;border-radius:14px;margin-top:22px;border:1px solid #e3e7e7}.wallet-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:20px;padding-bottom:18px}.wallet-grid small{display:block;color:#65746f;font-size:12px}.wallet-grid strong{display:block;font-size:28px;margin-top:12px;color:#087e73;overflow-wrap:anywhere}.wallet-panel p{color:#65746f;font-size:13px;line-height:1.8}.bill{padding:14px 0;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #edf0ef;gap:12px}.bill small{display:block;font-size:12px;color:#7a8781;margin-top:6px}.bill strong{color:#087e73;white-space:nowrap}@media(max-width:650px){.wallet-panel{padding:16px}.wallet-grid{gap:10px}.wallet-grid strong{font-size:22px}}
</style>
