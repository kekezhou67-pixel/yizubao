<template><footer class="site-footer"><div class="footer-main"><div class="footer-brand"><BrandMark /><h2>好好找房，<br>好好生活。</h2><p>从一个房间开始，安放自己的日常。</p></div><div class="footer-links"><section v-for="group in navigation" :key="group.id"><h3>{{ group.label }}</h3><RouterLink v-for="link in group.links" :key="link.to" :to="link.to">{{ link.name }}</RouterLink></section></div></div><div class="footer-bottom"><span>© {{ new Date().getFullYear() }} 易租宝 · 智能租房平台</span><RouterLink to="/app/publish-house">我有房源要发布 <ArrowIcon /></RouterLink><MotionSwitch /><a href="#page-content" @click.prevent="backToTop">回到顶部 ↑</a></div></footer></template>
<script setup>
import BrandMark from './BrandMark.vue'
import MotionSwitch from './MotionSwitch.vue'
import { isReducedMotion } from '../composables/motionPreference'
import ArrowIcon from './ArrowIcon.vue'
import { navigation } from '../data/navigation'
function backToTop(){ window.scrollTo({ top:0, behavior:isReducedMotion() ? 'instant' : 'smooth' }) }
</script>
<style scoped>
.site-footer{background:var(--jade-900);color:#fff;padding:55px var(--page-gutter) 0}.footer-main,.footer-bottom{max-width:1440px;margin:auto}.footer-main{display:grid;grid-template-columns:1fr 1.5fr;gap:60px;padding-bottom:40px}.footer-brand :deep(.brand-mark){color:#fff;font-size:23px}.footer-brand :deep(svg){color:#71b6a4}.footer-brand h2{font-family:var(--font-display);font-weight:400;font-size:28px;line-height:1.65;margin:25px 0 15px}.footer-brand p{font-size:12px;color:#a7bfb4}.footer-links{display:grid;grid-template-columns:repeat(3,1fr);gap:35px}.footer-links h3{font-size:15px;font-weight:500;margin-bottom:20px}.footer-links a{display:block;font-size:12px;color:#a7bfb4;padding:6px 0}.footer-links a:hover{color:#fff}.footer-bottom{border-top:1px solid #ffffff22;display:flex;align-items:center;justify-content:space-between;gap:20px;padding:23px 0;font-size:11px;color:#94b0a3}.footer-bottom a{display:flex;gap:15px;align-items:center;color:#c1d6cb}.footer-bottom svg{width:17px}@media(max-width:800px){.footer-main{grid-template-columns:1fr;gap:40px}.footer-brand h2 br{display:none}.footer-brand h2{font-size:25px;margin:20px 0 12px}.footer-links{gap:20px}.footer-links h3{font-size:14px}.footer-links a{font-size:11px}.footer-bottom{flex-wrap:wrap;font-size:10px}.footer-bottom>span{width:100%}}
</style>
