<template><div ref="curtain" class="route-curtain" aria-hidden="true"><span>易租宝<span class="curtain-rule"></span><small>好好找房，好好生活。</small></span></div><span class="sr-only" role="status" aria-live="polite">{{ announcement }}</span></template>
<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { isReducedMotion } from '../composables/motionPreference'
const router = useRouter(), curtain = ref(null), announcement = ref('')
let disposeBefore, disposeAfter, disposeError, animation, pendingPath = '', timer
const titles = { Messages:'我的消息', Login:'登录', Register:'注册', Home:'易租宝 · 好房，让生活从容开始', HouseList:'全部房源', HouseDetail:'房源详情', SubwaySearch:'地铁找房', Tools:'租房工具', Community:'租房指南', GuideDetail:'攻略详情', AIChat:'小易工作台', RagQa:'政策问答', ImageSearch:'以图找房', KbAdmin:'知识库管理', PublishHouse:'发布房源', Profile:'个人中心', Membership:'会员中心', Admin:'管理后台' }
function reset() { animation?.cancel(); clearTimeout(timer); pendingPath = ''; if (curtain.value) { curtain.value.style.visibility = 'hidden'; curtain.value.style.transform = 'translateY(100%)' } }
async function reveal() {
  clearTimeout(timer)
  if (!curtain.value) return
  animation?.cancel()
  animation = curtain.value.animate([{ transform:'translateY(0)' }, { transform:'translateY(-100%)' }], { duration:360, easing:'cubic-bezier(.65,0,.2,1)', fill:'forwards' })
  try { await animation.finished } catch { return }
  reset()
}
function title(to) { document.title = to.name === 'Home' ? titles.Home : `${titles[to.name] || '易租宝'} · 易租宝`; announcement.value = `${titles[to.name] || '页面'}已打开` }
onMounted(() => {
  title(router.currentRoute.value)
  disposeBefore = router.beforeResolve(async (to, from) => {
    if (to.path === from.path || to.path === '/login' || isReducedMotion()) return
    pendingPath = to.fullPath; animation?.cancel(); clearTimeout(timer)
    curtain.value.style.visibility = 'visible'
    animation = curtain.value.animate([{ transform:'translateY(100%)' }, { transform:'translateY(0)' }], { duration:230, easing:'cubic-bezier(.65,0,.2,1)', fill:'forwards' })
    try { await animation.finished } catch { /* A newer navigation takes over. */ }
    timer = setTimeout(reveal, 1600)
  })
  disposeAfter = router.afterEach(async (to, from, failure) => {
    if (failure) { if (pendingPath === to.fullPath) reveal(); return }
    title(to); await nextTick()
    if (pendingPath === to.fullPath) requestAnimationFrame(reveal)
    if (to.path !== from.path) document.getElementById('page-content')?.focus({ preventScroll:true })
  })
  disposeError = router.onError(reset)
})
onBeforeUnmount(() => { disposeBefore?.(); disposeAfter?.(); disposeError?.(); reset() })
</script>
<style scoped>
.route-curtain{position:fixed;inset:0;z-index:500;background:var(--jade-600);color:#fff;visibility:hidden;transform:translateY(100%);display:grid;place-items:center;pointer-events:none;will-change:transform}.route-curtain>span{display:flex;align-items:center;gap:25px;font-size:27px;letter-spacing:3px}.curtain-rule{width:40px;height:1px;background:#ffffff70}.route-curtain small{font-family:var(--font-display);font-size:16px;letter-spacing:2px}.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}@media(max-width:600px){.route-curtain>span{flex-direction:column;gap:16px}.curtain-rule{display:none}}
</style>
