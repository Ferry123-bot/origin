<script setup lang="ts">
import { computed, ref } from 'vue'

const props = defineProps<{ src: string; mime: string; busy?: boolean }>()
const emit = defineEmits<{ confirm: [file: File]; cancel: [] }>()

const VIEW = 280
const OUTPUT = 400
const imgEl = ref<HTMLImageElement | null>(null)
const zoom = ref(1)
const offset = ref({ x: 0, y: 0 })
const ready = ref(false)
const saving = ref(false)
let natural = { w: 1, h: 1 }
let dragging = false
let start = { x: 0, y: 0, ox: 0, oy: 0 }

const imageStyle = computed(() => {
  const scale = coverScale() * zoom.value
  return {
    width: `${natural.w * scale}px`,
    height: `${natural.h * scale}px`,
    transform: `translate(${offset.value.x}px, ${offset.value.y}px)`,
  }
})

function coverScale() {
  return Math.max(VIEW / natural.w, VIEW / natural.h)
}

function clamp(x: number, y: number, nextZoom = zoom.value) {
  const scale = coverScale() * nextZoom
  const minX = VIEW - natural.w * scale
  const minY = VIEW - natural.h * scale
  return {
    x: Math.min(0, Math.max(minX, x)),
    y: Math.min(0, Math.max(minY, y)),
  }
}

function onLoad() {
  const image = imgEl.value
  if (!image) return
  natural = { w: image.naturalWidth, h: image.naturalHeight }
  const scale = coverScale()
  offset.value = clamp((VIEW - natural.w * scale) / 2, (VIEW - natural.h * scale) / 2, 1)
  ready.value = true
}

function setZoom(value: number) {
  const previous = coverScale() * zoom.value
  const next = coverScale() * value
  const centerX = (VIEW / 2 - offset.value.x) / previous
  const centerY = (VIEW / 2 - offset.value.y) / previous
  zoom.value = value
  offset.value = clamp(VIEW / 2 - centerX * next, VIEW / 2 - centerY * next, value)
}

function onPointerDown(event: PointerEvent) {
  if (!ready.value) return
  dragging = true
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
  start = { x: event.clientX, y: event.clientY, ox: offset.value.x, oy: offset.value.y }
}

function onPointerMove(event: PointerEvent) {
  if (!dragging) return
  offset.value = clamp(start.ox + event.clientX - start.x, start.oy + event.clientY - start.y)
}

function onPointerUp() {
  dragging = false
}

function onWheel(event: WheelEvent) {
  const next = Math.min(3, Math.max(1, zoom.value + (event.deltaY < 0 ? 0.08 : -0.08)))
  setZoom(Number(next.toFixed(2)))
}

async function confirm() {
  const image = imgEl.value
  if (!image || !ready.value || saving.value || props.busy) return
  saving.value = true
  const scale = coverScale() * zoom.value
  const canvas = document.createElement('canvas')
  canvas.width = OUTPUT
  canvas.height = OUTPUT
  const context = canvas.getContext('2d')
  if (!context) {
    saving.value = false
    return
  }
  context.drawImage(
    image,
    -offset.value.x / scale,
    -offset.value.y / scale,
    VIEW / scale,
    VIEW / scale,
    0,
    0,
    OUTPUT,
    OUTPUT,
  )
  const type = props.mime === 'image/png' ? 'image/png' : 'image/jpeg'
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, type, 0.92))
  saving.value = false
  if (!blob) return
  emit('confirm', new File([blob], type === 'image/png' ? 'avatar.png' : 'avatar.jpg', { type }))
}
</script>

<template>
  <div class="mask" @click.self="emit('cancel')">
    <div class="dialog">
      <h3>裁剪头像</h3>
      <p>拖动图片调整位置，滚轮或滑杆可以缩放</p>
      <div
        class="viewport"
        @pointerdown="onPointerDown"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
        @pointercancel="onPointerUp"
        @wheel.prevent="onWheel"
      >
        <img ref="imgEl" :src="src" alt="" draggable="false" :style="imageStyle" @load="onLoad" />
      </div>
      <label class="zoom">
        <span>缩放</span>
        <input :value="zoom" type="range" min="1" max="3" step="0.01" @input="setZoom(Number(($event.target as HTMLInputElement).value))" />
      </label>
      <div class="actions">
        <button type="button" @click="emit('cancel')">取消</button>
        <button type="button" class="primary" :disabled="!ready || saving || busy" @click="confirm">{{ saving || busy ? '处理中...' : '保存头像' }}</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mask { position: fixed; inset: 0; z-index: 2000; display: grid; place-items: center; padding: 24px; background: rgba(0, 0, 0, 0.45); }
.dialog { width: min(420px, 100%); padding: 24px; border-radius: 12px; background: #fff; box-shadow: 0 8px 24px rgba(31, 45, 61, 0.16); }
h3 { margin: 0 0 8px; font-size: 18px; }
p { margin: 0 0 16px; color: #909399; font-size: 13px; }
.viewport { position: relative; width: 280px; height: 280px; margin: 0 auto; overflow: hidden; border-radius: 50%; background: #f5f7fa; cursor: grab; touch-action: none; }
.viewport:active { cursor: grabbing; }
img { position: absolute; top: 0; left: 0; max-width: none; user-select: none; }
.zoom { display: flex; align-items: center; gap: 12px; margin-top: 16px; color: #606266; font-size: 13px; }
.zoom input { flex: 1; }
.actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; }
button { height: 34px; padding: 0 14px; border: 1px solid #dcdfe6; border-radius: 6px; background: #fff; cursor: pointer; }
.primary { border: 0; color: #fff; background: linear-gradient(135deg, #409eff, #337ecc); }
.primary:disabled { opacity: 0.7; cursor: not-allowed; }
</style>
