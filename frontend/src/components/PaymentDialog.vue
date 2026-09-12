<script setup>
import { onMounted, ref } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
const props = defineProps({ order: { type: Object, required: true } })
const emit = defineEmits(['close', 'submitted'])
const settings = ref(null)
const reference = ref('')
const proof = ref(null)
const busy = ref(false)
const error = ref('')
onMounted(async () => { try { settings.value = (await axios.get('/payment-settings')).data } catch { error.value = '暂时无法加载收款方式，请稍后重试' } })
function choose(event) { proof.value = event.target.files[0] || null }
async function submit() {
  if (busy.value) return
  if (!reference.value.trim() || !proof.value) { error.value = '请填写付款单号并上传付款截图'; return }
  if (!['image/jpeg', 'image/png'].includes(proof.value.type) || proof.value.size > 5 * 1024 * 1024) { error.value = '请选择5MB以内的JPG或PNG图片'; return }
  busy.value = true
  try {
    const data = new FormData()
    data.append('reference', reference.value.trim())
    data.append('file', proof.value)
    await axios.post(`/orders/${props.order.id}/payment-proof`, data)
    ElMessage.success('已提交凭证，请等待人工核实')
    emit('submitted')
    emit('close')
  } catch (failure) { error.value = failure.response?.data?.message || '提交失败，请稍后重试' }
  finally { busy.value = false }
}
</script>
<template>
  <el-dialog :model-value="true" title="支付平台服务费" width="min(480px, calc(100vw - 24px))" :close-on-click-modal="!busy" :show-close="!busy" @close="emit('close')">
    <div class="fee-amount">¥{{ Number(order.service_fee || 0).toFixed(2) }}</div>
    <p>本次只向平台支付服务费。商品货款 ¥{{ Number(order.price).toFixed(2) }} 由你与卖家另行结算，请勿把商品货款付到此收款码。</p>
    <template v-if="settings?.qr_url"><img class="payment-qr" :src="settings.qr_url" alt="平台服务费收款二维码" /><p class="payee">收款人：{{ settings.payee_name }}</p><p>{{ settings.instructions || '扫码后请核对收款人和金额，并备注订单编号。' }}</p><p>备注：校园集市订单 {{ order.id }}</p><el-form label-position="top"><el-form-item label="付款交易单号"><el-input v-model="reference" maxlength="100" placeholder="打开微信或支付宝付款详情，复制交易单号" :disabled="busy" /></el-form-item><el-form-item label="付款截图"><input type="file" accept="image/png,image/jpeg" :disabled="busy" @change="choose" /></el-form-item></el-form><p class="privacy-note">截图只供你和审核员查看。请遮盖无关的余额、聊天和个人信息。提交截图不代表已核实到账。</p></template>
    <el-alert v-else-if="settings" title="平台正在准备收款方式，请稍后再来。请勿向陌生账户转账。" type="warning" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template #footer><el-button :disabled="busy" @click="emit('close')">稍后支付</el-button><el-button type="primary" :loading="busy" :disabled="!settings?.qr_url" @click="submit">提交凭证，等待核实</el-button></template>
  </el-dialog>
</template>
<style scoped>
.fee-amount{text-align:center;font-size:36px;font-weight:700;color:#087e73}.payment-qr{display:block;max-width:240px;width:100%;max-height:300px;object-fit:contain;margin:18px auto}.payee{text-align:center;font-weight:600}p{font-size:13px;line-height:1.8;color:#56665f}.privacy-note{font-size:12px;color:#78877f}
</style>
