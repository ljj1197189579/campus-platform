import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import axios from 'axios'
import App from '../App.vue'
import OrderCenter from '../components/OrderCenter.vue'
import PaymentDialog from '../components/PaymentDialog.vue'

vi.mock('axios', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }))
const user = { id: 10, nickname: '买家小林', role: 'USER' }
let wrappers = []
let publicGoods
let publicLost
function render(component, props = {}) { const wrapper = mount(component, { props, attachTo: document.body, global: { plugins: [ElementPlus], stubs: { teleport: component === PaymentDialog } } }); wrappers.push(wrapper); return wrapper }
async function clickButton(wrapper, name) { const button = wrapper.findAll('button').find(item => item.text().trim() === name); expect(button, name).toBeTruthy(); await button.trigger('click'); await flushPromises() }

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  publicGoods = []
  publicLost = [{ id: 1, title: '图书馆钥匙', location: '图书馆', type: 'FOUND', status: 'OPEN' }]
  axios.get.mockImplementation(async (url, options) => {
    if (url === '/goods') return { data: publicGoods }
    if (url === '/lost-found') return { data: options?.params?.keyword ? publicLost.filter(item => item.title.includes(options.params.keyword)) : publicLost }
    if (url === '/payment-settings') return { data: { qr_url: '/api/media/test', payee_name: '校园集市', serviceFeeRate: 0.02 } }
    if (url === '/me/credit') return { data: { seller_credit: null, completed_trades: 0, review_count: 0 } }
    if (url === '/wallet') return { data: { payments: [], paidFees: 0, pendingFees: 0, pendingRefunds: 0, serviceFeeRate: 0.02 } }
    if (url === '/admin/revenue') return { data: { settledFees: 0, pendingFees: 0, pendingRefunds: 0, serviceFeeRate: 0.02 } }
    return { data: [] }
  })
  axios.post.mockResolvedValue({ data: {} })
  axios.put.mockResolvedValue({ data: {} })
})
afterEach(() => { wrappers.forEach(wrapper => wrapper.unmount()); wrappers = []; vi.useRealTimers(); document.body.innerHTML = '' })

describe('面向用户的页面流程', () => {
  it('顶部提供首页、市场、失物、消息和用户入口，页面没有底部导航', async () => {
    const wrapper = render(App)
    await flushPromises()
    expect(wrapper.find('.primary-nav').text()).toMatch(/首页市场失物/)
    expect(wrapper.find('.header-actions').text()).toMatch(/消息联系我们登录注册/)
    expect(wrapper.find('.bottom-nav').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('OPEN')
  })

  it('从消息返回首页时仍能正常显示首页内容', async () => {
    const wrapper = render(App)
    await flushPromises()
    await wrapper.find('.header-actions .nav-button').trigger('click')
    await flushPromises()
    expect(wrapper.find('.message-center').exists()).toBe(true)
    await wrapper.find('.primary-nav button:first-child').trigger('click')
    await flushPromises()
    expect(wrapper.find('.home-page').exists()).toBe(true)
    expect(wrapper.find('.message-center').exists()).toBe(false)
  })

  it('其他用户审核通过的商品在五秒后自动出现，无需刷新', async () => {
    vi.useFakeTimers()
    const wrapper = render(App)
    await flushPromises()
    expect(wrapper.text()).not.toContain('刚发布的相机')
    publicGoods = [{ id: 20, title: '刚发布的相机', price: 100, images: ['/api/media/photo'], category: '数码产品' }]
    await vi.advanceTimersByTimeAsync(5100)
    await flushPromises()
    expect(wrapper.text()).toContain('刚发布的相机')
    expect(wrapper.find('.goods-cover img').attributes('src')).toBe('/api/media/photo')
  })

  it('失物搜索发送关键词并显示中文空结果', async () => {
    vi.useFakeTimers()
    const wrapper = render(App)
    await flushPromises()
    await wrapper.find('.primary-nav button:nth-child(3)').trigger('click')
    const search = wrapper.find('input[placeholder="搜索物品名称、特征或丢失地点"]')
    await search.setValue('不存在的耳机')
    await vi.advanceTimersByTimeAsync(350)
    await flushPromises()
    expect(axios.get).toHaveBeenCalledWith('/lost-found', { params: { keyword: '不存在的耳机' } })
    expect(wrapper.text()).toContain('暂时没有找到相关信息')
  })

  it('商品发布支持多图并阻止无照片发布', async () => {
    localStorage.setItem('campus_user', JSON.stringify(user))
    const wrapper = render(App)
    await flushPromises()
    await wrapper.find('.soft-button').trigger('click')
    await flushPromises()
    const input = wrapper.find('input[type="file"]')
    expect(input.attributes()).toHaveProperty('multiple')
    const submit = wrapper.findAll('button').find(button => button.text() === '提交审核')
    await submit.trigger('click')
    expect(wrapper.text()).toContain('请至少上传1张商品实拍照片')
    expect(axios.post).not.toHaveBeenCalledWith('/goods', expect.anything())
    axios.post.mockImplementation(async url => ({ data: { url: '/api/media/' + (axios.post.mock.calls.length === 1 ? 'one' : 'two') } }))
    Object.defineProperty(input.element, 'files', { value: [new File(['one'], 'one.png', { type: 'image/png' }), new File(['two'], 'two.jpg', { type: 'image/jpeg' })], configurable: true })
    await input.trigger('change')
    await flushPromises()
    expect(wrapper.findAll('.uploaded-photos .el-image')).toHaveLength(2)
    expect(axios.post.mock.calls.filter(([url]) => url === '/media')).toHaveLength(2)
  })

  it('后台审核类型和状态显示中文', async () => {
    localStorage.setItem('campus_user', JSON.stringify({ ...user, role: 'ADMIN' }))
    const fallback = axios.get.getMockImplementation()
    axios.get.mockImplementation((url, options) => url === '/admin/audits' ? Promise.resolve({ data: [{ id: 1, type: 'GOODS', audit_status: 'PENDING', title: '待审核教材', description: '有笔记', images: [] }] }) : fallback(url, options))
    const wrapper = render(App)
    await flushPromises()
    await wrapper.find('.avatar-button').trigger('click')
    await flushPromises()
    const menuText = document.body.textContent
    expect(menuText).toContain('管理后台')
    const adminItem = [...document.body.querySelectorAll('.el-dropdown-menu__item')].find(item => item.textContent.trim() === '管理后台')
    expect(adminItem).toBeTruthy()
    await adminItem.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()
    expect(wrapper.find('.table-card').text()).toContain('二手商品')
    expect(wrapper.find('.table-card').text()).toContain('等待审核')
    expect(wrapper.find('.table-card').text()).not.toMatch(/GOODS|PENDING/)
  })

  it('凭证未核实的订单不能确认收货', async () => {
    const fallback = axios.get.getMockImplementation()
    let state = 'PENDING'
    axios.get.mockImplementation((url, options) => url === '/orders' ? Promise.resolve({ data: [{ id: 5, buyer_id: 10, seller_id: 11, title: '教材', price: 100, service_fee: 2, status: 'ACCEPTED', payment_status: state }] }) : fallback(url, options))
    const wrapper = render(OrderCenter, { user })
    await flushPromises()
    expect(wrapper.text()).toContain('等待核实到账')
    expect(wrapper.findAll('button').some(button => button.text() === '确认收货')).toBe(false)
    state = 'CONFIRMED'
    window.dispatchEvent(new Event('campus-sync'))
    await flushPromises()
    expect(wrapper.findAll('button').some(button => button.text() === '确认收货')).toBe(true)
  })

  it('付款弹窗只展示服务费，不把提交凭证称为付款成功', async () => {
    const wrapper = render(PaymentDialog, { order: { id: 3, price: 100, service_fee: 2 } })
    await flushPromises()
    expect(wrapper.find('.fee-amount').text()).toBe('¥2.00')
    expect(wrapper.text()).toContain('请勿把商品货款付到此收款码')
    await wrapper.find('input[type="text"]').setValue('wx-reference-123')
    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [new File(['proof'], 'proof.png', { type: 'image/png' })], configurable: true })
    await input.trigger('change')
    await clickButton(wrapper, '提交凭证，等待核实')
    expect(axios.post).toHaveBeenCalledWith('/orders/3/payment-proof', expect.any(FormData))
    expect(wrapper.emitted('submitted')).toHaveLength(1)
  })
})
