<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
const rows = ref([])
const settings = ref({ qrUrl: '', payeeName: '', instructions: '' })
const selected = ref(null)
const proofUrl = ref('')
const busy = ref(false)
const error = ref('')
const action = ref('CONFIRM')
const actualAmount = ref(null)
const reference = ref('')
const note = ref('')
const acknowledged = ref(false)
let loading = false
let proofSequence = 0
async function load() {
  if (loading || busy.value) return
  loading = true
  try { rows.value = (await axios.get('/admin/payments')).data; error.value = '' } catch { error.value = '收款核实列表暂时无法加载' }
  finally { loading = false }
}
async function upload(event) {
  const file = event.target.files[0]
  event.target.value = ''
  if (!file || busy.value) return
  if (!['image/jpeg','image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) { ElMessage.error('请选择5MB以内的JPG或PNG收款码'); return }
  busy.value = true
  try { const data = new FormData(); data.append('file', file); settings.value.qrUrl = (await axios.post('/media', data)).data.url }
  catch (failure) { ElMessage.error(failure.response?.data?.message || '上传失败') }
  finally { busy.value = false }
}
async function save() {
  if (busy.value) return
  busy.value = true
  try { await axios.put('/admin/payment-settings', settings.value); ElMessage.success('收款方式已保存') }
  catch (failure) { ElMessage.error(failure.response?.data?.message || '保存失败') }
  finally { busy.value = false }
}
function close() { proofSequence++; selected.value = null; if (proofUrl.value) URL.revokeObjectURL(proofUrl.value); proofUrl.value = '' }
async function inspect(item) {
  close(); selected.value = item; action.value = item.status === 'PENDING' ? 'CONFIRM' : 'REFUND'; actualAmount.value = null; reference.value = ''; note.value = ''; acknowledged.value = false
  const sequence = proofSequence
  try { const response = await axios.get(`/orders/${item.order_id}/payment-proof`, { responseType: 'blob' }); if (sequence === proofSequence) proofUrl.value = URL.createObjectURL(response.data) }
  catch { ElMessage.warning('暂无可查看凭证，请以实际收款记录为准') }
}
async function verify() {
  if (busy.value || !selected.value) return
  if (!acknowledged.value) { ElMessage.warning('请先核对实际收款或退款记录'); return }
  busy.value = true
  try {
    await ElMessageBox.confirm('此操作会改变交易状态。请确认已在微信或支付宝的实际账单中核实，不能只依据付款截图。', '确认保存核实结果', { confirmButtonText: '已核实，保存', cancelButtonText: '返回检查' })
    await axios.put('/admin/payments/' + selected.value.order_id, { action: action.value, reference: reference.value, actualAmount: actualAmount.value, note: note.value })
    close(); ElMessage.success('核实结果已保存'); window.dispatchEvent(new Event('campus-wallet-updated'))
  } catch (failure) { if (failure !== 'cancel' && failure !== 'close') ElMessage.error(failure.response?.data?.message || '保存失败') }
  finally { busy.value = false; await load() }
}
onMounted(async () => {
  await load()
  try { const data = (await axios.get('/payment-settings')).data; settings.value = { qrUrl: data.qr_url || '', payeeName: data.payee_name || '', instructions: data.instructions || '' } } catch {}
  window.addEventListener('campus-sync', load)
})
onUnmounted(() => { window.removeEventListener('campus-sync', load); close() })
</script>
<template>
  <el-card class="payment-admin"><template #header><h2>服务费收款与核实</h2></template>
    <el-collapse><el-collapse-item title="设置平台收款二维码" name="settings"><el-form label-position="top"><el-form-item label="收款二维码"><input type="file" accept="image/png,image/jpeg" :disabled="busy" @change="upload" /><img v-if="settings.qrUrl" :src="settings.qrUrl" alt="当前收款码" class="configured-qr" /></el-form-item><el-form-item label="收款人名称"><el-input v-model="settings.payeeName" maxlength="80" placeholder="填写扫码付款时显示的收款人" /></el-form-item><el-form-item label="付款说明"><el-input v-model="settings.instructions" maxlength="255" type="textarea" placeholder="例如：请备注订单编号，请勿支付商品货款" /></el-form-item><el-button type="primary" :loading="busy" @click="save">保存收款方式</el-button></el-form></el-collapse-item></el-collapse>
    <p>个人收款码不能自动通知到账。请打开收款账户实际账单，逐笔核对单号与金额，截图仅作辅助。退款需要你先在原收款账户实际退回款项，再确认退款。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-empty v-if="!rows.length" description="暂无待核实收款或退款" />
    <article v-for="item in rows" :key="item.order_id" class="payment-row"><div><b>{{ item.title }}</b><p>订单 {{ item.order_id }} · {{ item.buyer_name }} · 服务费 ¥{{ Number(item.fee).toFixed(2) }}</p><el-tag :type="item.status === 'PENDING' ? 'warning' : 'danger'">{{ item.status === 'PENDING' ? '等待核实到账' : '取消交易，待核实退款' }}</el-tag></div><el-button type="primary" @click="inspect(item)">核实处理</el-button></article>
    <el-dialog :model-value="!!selected" title="人工核实记录" width="min(580px, calc(100vw - 24px))" :close-on-click-modal="!busy" :show-close="!busy" @close="close"><template v-if="selected"><p>订单 {{ selected.order_id }} · 服务费 ¥{{ Number(selected.fee).toFixed(2) }}</p><p>用户提供单号：{{ selected.payer_reference || '未提供' }}</p><img v-if="proofUrl" :src="proofUrl" class="payment-proof" alt="买家提交的付款凭证，需核对实际到账" /><el-form label-position="top"><el-form-item label="处理结果"><el-radio-group v-model="action"><template v-if="selected.status === 'PENDING'"><el-radio value="CONFIRM">已核实到账</el-radio><el-radio value="REJECT">凭证未通过</el-radio></template><template v-else><el-radio value="REFUND">已实际退款</el-radio><el-radio v-if="!selected.verified_reference" value="NO_PAYMENT">核实未收到款项</el-radio></template></el-radio-group></el-form-item><template v-if="['CONFIRM','REFUND'].includes(action)"><el-form-item :label="action === 'REFUND' ? '实际退款交易单号' : '实际到账交易单号'"><el-input v-model="reference" maxlength="100" placeholder="从你的收款账户账单核对后填写" /></el-form-item><el-form-item label="实际金额（元）"><el-input-number v-model="actualAmount" :min="0" :precision="2" /></el-form-item></template><el-form-item label="处理说明"><el-input v-model="note" type="textarea" maxlength="255" placeholder="未通过或未收款时必填，买家可查看" /></el-form-item></el-form><el-checkbox v-model="acknowledged">我已核对实际账单，而非仅查看用户截图</el-checkbox></template><template #footer><el-button type="primary" :loading="busy" @click="verify">保存核实结果</el-button></template></el-dialog>
  </el-card>
</template>
<style scoped>
.payment-admin{margin:20px 0}.payment-admin h2{margin:0;font-size:20px}.payment-admin p{font-size:13px;color:#69786f;line-height:1.8}.configured-qr{display:block;width:160px;max-height:200px;object-fit:contain;margin-top:12px}.payment-row{display:flex;align-items:center;justify-content:space-between;gap:14px;padding:16px 0;border-bottom:1px solid #e4ebe7}.payment-proof{max-width:100%;max-height:320px;object-fit:contain;display:block;margin:auto}
</style>
