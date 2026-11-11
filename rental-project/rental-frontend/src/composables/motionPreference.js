import { ref } from 'vue'

const saved = localStorage.getItem('yzb-motion')
export const motionMode = ref(saved === 'full' ? 'full' : 'system')
document.documentElement.dataset.motion = motionMode.value
export function isReducedMotion() {
  return motionMode.value !== 'full' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}
export function toggleMotion() {
  motionMode.value = motionMode.value === 'full' ? 'system' : 'full'
  document.documentElement.dataset.motion = motionMode.value
  localStorage.setItem('yzb-motion', motionMode.value)
  window.dispatchEvent(new Event('rental-motion-change'))
}
