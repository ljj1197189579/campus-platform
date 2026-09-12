<script setup>
import { onMounted, onUnmounted, ref } from 'vue'

const canvas = ref(null)
let frame = 0
let resizeObserver
let context
let wordParticles = []
let grid = { columns: 0, rows: 0, spacing: 100, points: [] }
const pointer = { x: -1000, y: -1000, active: false }
let lastTime = 0

function makeParticles(width, height) {
  const spacing = Math.max(76, Math.min(116, Math.round(width / 13)))
  const margin = spacing * 2.4
  const columns = Math.ceil((width + margin * 2) / spacing) + 1
  const rows = Math.ceil((height + margin * 2) / spacing) + 1
  const startX = -margin
  const startY = -margin
  const points = []
  for (let row = 0; row < rows; row += 1) {
    for (let column = 0; column < columns; column += 1) {
      const baseX = startX + column * spacing
      const baseY = startY + row * spacing
      points.push({ baseX, baseY, x: baseX, y: baseY, vx: 0, vy: 0, phase: Math.random() * Math.PI * 2 })
    }
  }
  grid = { columns, rows, spacing, points }

  if (typeof document === 'undefined') return
  const sample = document.createElement('canvas')
  sample.width = 760
  sample.height = 250
  const sampleContext = sample.getContext('2d')
  if (!sampleContext) return
  sampleContext.font = '800 188px Microsoft YaHei, sans-serif'
  sampleContext.textAlign = 'center'
  sampleContext.textBaseline = 'middle'
  sampleContext.fillStyle = '#fff'
  sampleContext.fillText('享购', sample.width / 2, sample.height / 2 + 8)
  const image = sampleContext.getImageData(0, 0, sample.width, sample.height).data
  wordParticles = []
  for (let y = 0; y < sample.height; y += 6) {
    for (let x = 0; x < sample.width; x += 6) {
      if (image[(y * sample.width + x) * 4 + 3] > 80) wordParticles.push({ x, y, phase: Math.random() * Math.PI * 2 })
    }
  }
}

function resize() {
  if (!canvas.value) return
  const ratio = Math.min(window.devicePixelRatio || 1, 2)
  canvas.value.width = Math.floor(window.innerWidth * ratio)
  canvas.value.height = Math.floor(window.innerHeight * ratio)
  canvas.value.style.width = `${window.innerWidth}px`
  canvas.value.style.height = `${window.innerHeight}px`
  try { context = canvas.value.getContext('2d') } catch { context = null }
  if (!context) return
  context.setTransform(ratio, 0, 0, ratio, 0, 0)
  makeParticles(window.innerWidth, window.innerHeight)
  lastTime = 0
}

function draw(time) {
  if (!context) return
  const width = window.innerWidth
  const height = window.innerHeight
  const step = lastTime ? Math.min(2, Math.max(.5, (time - lastTime) / 16.67)) : 1
  lastTime = time
  context.clearRect(0, 0, width, height)
  const wordScale = Math.min(width / 1000, 1)
  const wordOriginX = width * 0.7 - 380 * wordScale
  const wordOriginY = Math.min(height * 0.34, 270)
  wordParticles.forEach((point) => {
    const baseX = wordOriginX + point.x * wordScale
    const baseY = wordOriginY + point.y * wordScale
    const distance = Math.hypot(pointer.x - baseX, pointer.y - baseY)
    const influence = pointer.active && distance < 190 ? (190 - distance) / 190 : 0
    const drift = Math.sin(time * 0.0012 + point.phase) * 1.5
    context.beginPath()
    context.fillStyle = `rgba(177, 222, 255, ${0.08 + influence * 0.72})`
    context.arc(baseX + drift - influence * (pointer.x - baseX) * 0.08, baseY + drift - influence * (pointer.y - baseY) * 0.08, 1.15 + influence * 1.8, 0, Math.PI * 2)
    context.fill()
  })
  const rendered = grid.points.map((point) => {
    const spring = .055
    point.vx += (point.baseX - point.x) * spring * step
    point.vy += (point.baseY - point.y) * spring * step
    let glow = 0
    if (pointer.active) {
      const dx = point.x - pointer.x
      const dy = point.y - pointer.y
      const distance = Math.hypot(dx, dy)
      const radius = grid.spacing * 3.2
      if (distance > 0 && distance < radius) {
        glow = (1 - distance / radius) ** 2
        const wave = Math.sin(time * .0042 - distance * .055 + point.phase) * glow
        const push = glow * (2.8 + wave * .85)
        const tangent = wave * glow * 1.1
        point.vx += ((dx / distance) * push - (dy / distance) * tangent) * step
        point.vy += ((dy / distance) * push + (dx / distance) * tangent) * step
      }
    }
    const damping = Math.pow(.88, step)
    point.vx *= damping
    point.vy *= damping
    point.x += point.vx * step
    point.y += point.vy * step
    return { x: point.x, y: point.y, glow }
  })
  grid.points.forEach((point, index) => {
    const current = rendered[index]
    const pulse = (Math.sin(time * 0.001 + point.phase) + 1) * 0.5
    context.beginPath()
    context.fillStyle = `rgba(178, 220, 255, ${0.12 + pulse * 0.16 + current.glow * 0.42})`
    context.arc(current.x, current.y, 1.1 + current.glow * 1.8, 0, Math.PI * 2)
    context.fill()
  })
  for (let row = 0; row < grid.rows; row += 1) {
    for (let column = 0; column < grid.columns; column += 1) {
      const index = row * grid.columns + column
      const current = rendered[index]
      const neighbors = []
      if (column + 1 < grid.columns) neighbors.push(rendered[index + 1])
      if (row + 1 < grid.rows) neighbors.push(rendered[index + grid.columns])
      neighbors.forEach((neighbor) => {
        const nearPointer = current.glow > 0 || neighbor.glow > 0
        context.beginPath()
        context.strokeStyle = `rgba(126, 177, 224, ${nearPointer ? 0.2 : 0.08})`
        context.lineWidth = nearPointer ? 1.15 : 1
        context.moveTo(current.x, current.y)
        context.lineTo(neighbor.x, neighbor.y)
        context.stroke()
      })
    }
  }
  frame = window.requestAnimationFrame(draw)
}

function move(event) { pointer.x = event.clientX; pointer.y = event.clientY; pointer.active = true }
function leave() { pointer.active = false }

onMounted(() => {
  resize()
  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(document.documentElement)
  window.addEventListener('pointermove', move, { passive: true })
  window.addEventListener('pointerleave', leave, { passive: true })
  if (typeof window.requestAnimationFrame === 'function') frame = window.requestAnimationFrame(draw)
})

onUnmounted(() => {
  if (typeof window.cancelAnimationFrame === 'function') window.cancelAnimationFrame(frame)
  resizeObserver?.disconnect()
  window.removeEventListener('pointermove', move)
  window.removeEventListener('pointerleave', leave)
})
</script>

<template><canvas ref="canvas" class="particle-field" aria-hidden="true" /></template>
