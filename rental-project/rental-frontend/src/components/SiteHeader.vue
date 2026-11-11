<template>
  <header ref="header" class="site-header" :inert="disabled || undefined" :class="{ 'is-scrolled': scrolled, 'has-menu': menu, 'is-full': menu === 'full' }" @keydown="onKeydown">
    <a href="#page-content" class="skip-link" @click="closeMenu">跳到页面内容</a>
    <div class="header-inner">
      <RouterLink class="brand-link" to="/" aria-label="易租宝首页" @click="goHome"><BrandMark /></RouterLink>
      <CityPicker ref="cityPicker" @open="closeMenu" />
      <nav ref="nav" class="primary-nav" aria-label="主要导航" @pointerleave="hovered = null">
        <RouterLink ref="homeLink" to="/" class="nav-item" :class="{ active: activeGroup === 'home' && !menu }" @click="goHome" @pointerenter="hovered = 'home'" @focus="hovered = 'home'">首页</RouterLink>
        <button v-for="item in navigation" :key="item.id" :ref="el => navButtons[item.id] = el" class="nav-item" :class="{ active: menu === item.id || (!menu && activeGroup === item.id) }" :aria-expanded="menu === item.id" aria-controls="site-menu" @pointerenter="hovered = item.id" @focus="hovered = item.id" @click="toggle(item.id)">{{ item.label }}</button>
        <span class="nav-line" :style="lineStyle" aria-hidden="true"></span>
      </nav>
      <div class="header-actions"><MessageInboxLink v-if="user.isLogin" />
        <RouterLink to="/app/publish-house" class="publish-link" @click="closeMenu">发布房源 <span aria-hidden="true">↗</span></RouterLink>
        <button v-if="!user.isLogin" class="account-link" @click="login">登录 <span>/ 注册</span></button>
        <button v-else class="account-link signed-in" :aria-expanded="menu === 'account'" aria-controls="site-menu" @click="toggle('account')"><span>{{ user.username || '个人中心' }}</span><span class="account-chevron" aria-hidden="true">⌄</span></button>
        <button ref="menuButton" class="menu-toggle" :class="{ open: !!menu }" :aria-label="menu ? '关闭菜单' : '打开全部菜单'" :aria-expanded="!!menu" aria-controls="site-menu" @click="menu ? closeMenu() : toggle('full')"><span></span><span></span></button>
      </div>
    </div>

    <Transition name="menu-sheet" @after-leave="restoreFocus">
      <section v-if="menu" id="site-menu" ref="panel" class="menu-sheet" :class="{ 'full-menu': menu === 'full', 'account-menu': menu === 'account' }" :aria-label="menuLabel">
        <div v-if="menu === 'full'" class="full-inner">
          <div class="full-intro"><span class="eyebrow">把生活，住成喜欢的样子。</span><nav class="full-index" aria-label="菜单分类"><RouterLink to="/" @click="closeMenu">首页 <ArrowIcon /></RouterLink><button v-for="item in navigation" :key="item.id" @click="focusFullGroup(item.id)">{{ item.label }} <ArrowIcon /></button></nav><h2>从这里，走向你的新生活。</h2></div>
          <div class="full-groups"><section v-for="group in navigation" :id="'full-' + group.id" :key="group.id"><h3>{{ group.label }}</h3><RouterLink v-for="link in group.links" :key="link.to" :to="link.to" @click="closeMenu">{{ link.name }}<ArrowIcon /></RouterLink></section><section><h3>我的生活</h3><button v-if="!user.isLogin" class="full-login" @click="login">登录 / 注册 <ArrowIcon /></button><RouterLink to="/app/messages" @click="closeMenu">我的消息 <ArrowIcon /></RouterLink><RouterLink to="/app/workflow" @click="closeMenu">平台通知与办理进度 <ArrowIcon /></RouterLink><RouterLink to="/app/profile" @click="closeMenu">个人中心与收藏 <ArrowIcon /></RouterLink><RouterLink to="/app/membership" @click="closeMenu">会员服务 <ArrowIcon /></RouterLink><template v-if="user.isAdmin"><RouterLink to="/app/kb-admin" @click="closeMenu">知识库管理 <ArrowIcon /></RouterLink><RouterLink to="/admin" @click="closeMenu">管理后台 <ArrowIcon /></RouterLink></template></section></div>
          <div class="full-bottom"><span>易租宝 · 好好找房，好好生活。</span><MotionSwitch /><RouterLink to="/app/publish-house" @click="closeMenu">我有房源要发布 <ArrowIcon /></RouterLink></div>
        </div>
        <div v-else-if="menu === 'account'" class="account-inner"><p>你好，{{ user.username }}</p><RouterLink to="/app/messages" @click="closeMenu">我的消息 <ArrowIcon /></RouterLink><RouterLink to="/app/workflow" @click="closeMenu">平台通知与办理进度 <ArrowIcon /></RouterLink><RouterLink to="/app/profile" @click="closeMenu">个人中心与我的收藏 <ArrowIcon /></RouterLink><RouterLink to="/app/membership" @click="closeMenu">会员服务 <ArrowIcon /></RouterLink><template v-if="user.isAdmin"><RouterLink to="/app/kb-admin" @click="closeMenu">知识库管理 <ArrowIcon /></RouterLink><RouterLink to="/admin" @click="closeMenu">管理后台 <ArrowIcon /></RouterLink></template></div>
        <div v-else class="mega-shell">
          <Transition name="menu-copy" mode="out-in">
            <div :key="group.id" class="mega-content">
              <div class="mega-intro"><span class="eyebrow">租房的事，一起解决</span><h2>{{ group.title }}</h2><p>{{ group.intro }}</p><RouterLink :to="group.links[0].to" class="text-link" @click="closeMenu">{{ group.id === 'ai' ? '开始一次对话' : group.id === 'find' ? '看看全部房源' : '去读租房攻略' }} <ArrowIcon /></RouterLink></div>
              <div class="mega-links"><RouterLink v-for="(link, index) in group.links" :key="link.to" :to="link.to" :style="{ '--item-index': index }" @click="closeMenu" @pointerenter="highlight = index" @focus="highlight = index" :class="{ highlighted: highlight === index }"><span><strong>{{ link.label }}</strong><small>{{ link.name }}</small></span><ArrowIcon /></RouterLink></div>
              <figure class="mega-photo"><div><Transition name="menu-photo"><img :key="menuPhoto.image" :src="menuPhoto.image" :alt="menuPhoto.caption" @error="fallbackImage"></Transition></div><figcaption>{{ menuPhoto.caption }}</figcaption><p>从真实的居住需求出发。</p></figure>
            </div>
          </Transition>
          <div class="menu-quick"><span>常用入口</span><RouterLink to="/houses" @click="closeMenu">全部房源</RouterLink><RouterLink to="/app/subway" @click="closeMenu">地铁找房</RouterLink><RouterLink to="/app/workflow" @click="closeMenu">平台通知与办理进度 <ArrowIcon /></RouterLink><RouterLink to="/app/profile" @click="closeMenu">我的收藏</RouterLink><span class="menu-hint">按 Esc 关闭</span></div>
        </div>
      </section>
    </Transition>
  </header>
  <Transition name="menu-backdrop"><div v-if="menu" class="nav-backdrop" :class="{ 'account-backdrop': menu === 'account' }" aria-hidden="true" @click="closeMenu"></div></Transition>
</template>

<script setup>
import { computed, ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import BrandMark from './BrandMark.vue'
import CityPicker from './CityPicker.vue'
import MessageInboxLink from './MessageInboxLink.vue'
import MotionSwitch from './MotionSwitch.vue'
import { isReducedMotion } from '../composables/motionPreference'
import ArrowIcon from './ArrowIcon.vue'
import { navigation, navigationGroup } from '../data/navigation'
import { useUserStore } from '../stores/user'
import { useAuthModal } from '../stores/authModal'

const emit = defineEmits(['menu-change'])
const props = defineProps({ disabled:Boolean })
const route = useRoute(), user = useUserStore(), auth = useAuthModal()
const menu = ref(null), hovered = ref(null), highlight = ref(0), scrolled = ref(false)
const header = ref(null), panel = ref(null), menuButton = ref(null), nav = ref(null), homeLink = ref(null)
const navButtons = {}, lineStyle = ref({ opacity: 0 })
let previousFocus, oldOverflow = '', resizeObserver
const cityPicker = ref(null)
const activeGroup = computed(() => navigationGroup(route.path))
const group = computed(() => navigation.find(item => item.id === menu.value) || navigation[1])
const menuPhoto = computed(() => ({ image:group.value.links[highlight.value]?.image || group.value.image, caption:group.value.links[highlight.value]?.caption || group.value.caption }))
const menuLabel = computed(() => menu.value === 'full' ? '全部功能' : menu.value === 'account' ? '个人入口' : group.value.label)
function toggle(id) {
  cityPicker.value?.close()
  if (menu.value === id) return closeMenu()
  if (!menu.value) previousFocus = document.activeElement
  highlight.value = 0
  menu.value = id
}
function focusFullGroup(id) { const el = panel.value?.querySelector('#full-' + id); el?.scrollIntoView({ behavior:isReducedMotion() ? 'instant' : 'smooth', block:'nearest' }); el?.querySelector('a')?.focus({ preventScroll:true }) }
function closeMenu() { menu.value = null; hovered.value = null }
function goHome() { closeMenu(); if (route.path === '/') requestAnimationFrame(() => window.scrollTo({ top:0, behavior:isReducedMotion() ? 'instant' : 'smooth' })) }
function restoreFocus() { if (!menu.value && !props.disabled && previousFocus?.isConnected) previousFocus.focus({ preventScroll: true }) }
function login() { closeMenu(); auth.open() }
function fallbackImage(event) {
  const fallback = '/house-images/uns_1600210492486-724fe5c67fb0.jpg'
  if (!event.target.src.endsWith(fallback)) event.target.src = fallback
}
function updateLine() {
  const id = hovered.value || (navigation.some(item => item.id === menu.value) ? menu.value : activeGroup.value)
  const el = id === 'home' ? homeLink.value?.$el : navButtons[id]
  if (!el || !nav.value) { lineStyle.value = { opacity: 0 }; return }
  lineStyle.value = { width: `${el.offsetWidth - 24}px`, transform: `translateX(${el.offsetLeft + 12}px)`, opacity: 1 }
}
function onScroll() { scrolled.value = window.scrollY > 20 }
function onKeydown(event) {
  if (!menu.value) return
  if (event.key === 'Escape') { event.preventDefault(); closeMenu(); return }
  if (event.key !== 'Tab') return
  const focusable = [...header.value.querySelectorAll('a[href],button:not([disabled])')].filter(el => el.getClientRects().length)
  const first = focusable[0], last = focusable.at(-1)
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
watch(menu, async (value, old) => {
  emit('menu-change', !!value)
  if (value && !old) { oldOverflow = document.body.style.overflow; document.body.style.overflow = 'hidden' }
  if (!value && old) document.body.style.overflow = oldOverflow
  await nextTick(); updateLine()
  if (value === 'full' || value === 'account') panel.value?.querySelector('a')?.focus({ preventScroll: true })
})
watch([hovered, activeGroup], () => nextTick(updateLine))
watch(() => route.fullPath, closeMenu)
onMounted(() => {
  onScroll(); updateLine()
  window.addEventListener('scroll', onScroll, { passive: true })
  resizeObserver = new ResizeObserver(updateLine); resizeObserver.observe(nav.value)
})
onBeforeUnmount(() => { window.removeEventListener('scroll', onScroll); resizeObserver?.disconnect(); if (menu.value) document.body.style.overflow = oldOverflow })
</script>

<style scoped>
.site-header{position:sticky;top:0;z-index:200;background:rgba(250,249,246,.96);border-bottom:1px solid var(--jade-line);transition:box-shadow .4s,background .4s;backdrop-filter:blur(18px)}.is-scrolled{box-shadow:0 4px 28px #103d360a}.header-inner{height:var(--header-height);max-width:1600px;padding:0 var(--page-gutter);margin:auto;display:flex;align-items:center;gap:24px}.brand-link{display:flex;align-items:center}.city-link{border-left:1px solid var(--jade-line);padding-left:20px;color:var(--jade-900);line-height:1.5;white-space:nowrap}.city-link small{display:block;font-size:11px;color:var(--jade-muted)}.primary-nav{position:relative;display:flex;gap:14px;align-items:stretch;align-self:stretch;margin:auto}.nav-item{display:flex;align-items:center;border:0;background:none;font:inherit;font-size:15px;white-space:nowrap;padding:0 12px;color:var(--jade-ink);cursor:pointer}.nav-item.active,.nav-item:hover{color:var(--jade-600)}.nav-line{position:absolute;height:2px;bottom:17px;left:0;background:var(--jade-600);border-radius:2px;transition:transform .4s var(--ease-out),width .4s var(--ease-out),opacity .2s}.header-actions{display:flex;gap:23px;align-items:center}.publish-link{display:flex;align-items:center;gap:16px;background:var(--jade-600);color:#fff;padding:11px 20px;border-radius:4px;font-size:13px;white-space:nowrap;transition:background .25s,transform .25s}.publish-link:hover{background:var(--jade-900);color:#fff;transform:translateY(-1px)}.account-link{border:0;background:none;font:inherit;font-size:13px;white-space:nowrap;cursor:pointer;color:var(--jade-ink);padding:8px 0}.signed-in{display:flex;align-items:center;gap:8px;max-width:140px}.signed-in>span:last-child{max-width:90px;overflow:hidden;text-overflow:ellipsis}.user-initial{display:grid;place-items:center;width:31px;height:31px;border-radius:50%;background:var(--jade-pale);color:var(--jade-600)}.menu-toggle{width:36px;height:40px;border:0;background:none;cursor:pointer;display:flex;flex-direction:column;justify-content:center;gap:7px;padding:6px}.menu-toggle span{display:block;width:24px;height:1.5px;background:var(--jade-900);transition:transform .4s var(--ease-out)}.menu-toggle.open span:first-child{transform:translateY(4.25px) rotate(45deg)}.menu-toggle.open span:last-child{transform:translateY(-4.25px) rotate(-45deg)}
.menu-sheet{position:absolute;top:100%;left:0;right:0;background:var(--jade-paper);box-shadow:0 25px 40px #102d5020;max-height:calc(100dvh - var(--header-height));overflow:auto;overscroll-behavior:contain;transform-origin:top;will-change:clip-path,transform}.mega-shell{max-width:1600px;margin:auto;padding:30px var(--page-gutter) 0}.mega-content{display:grid;grid-template-columns:.8fr 1.25fr 1.15fr;gap:34px;min-height:285px}.mega-intro{padding:14px 28px 10px 0;border-right:1px solid var(--jade-line);display:flex;align-items:flex-start;flex-direction:column}.eyebrow{font-size:12px;letter-spacing:1px;color:var(--jade-muted)}.mega-intro h2{font-family:var(--font-display);font-size:clamp(24px,2.3vw,36px);font-weight:500;line-height:1.45;margin:15px 0 16px;color:var(--jade-ink)}.mega-intro p{font-size:14px;color:var(--jade-muted);line-height:1.9;max-width:270px}.text-link{display:flex;align-items:center;gap:20px;color:var(--jade-600);margin-top:auto;padding-top:24px;font-size:14px}.text-link svg{transition:transform .35s}.text-link:hover svg{transform:translateX(6px)}.mega-links{display:flex;flex-direction:column;justify-content:center}.mega-links>a{padding:15px 18px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid var(--jade-line);border-left:2px solid transparent;transition:background .35s,border-color .35s;color:var(--jade-ink)}.mega-links>a:last-child{border-bottom:0}.mega-links strong{display:block;font-family:var(--font-display);font-size:19px;font-weight:500}.mega-links small{display:block;color:var(--jade-muted);font-size:12px;margin-top:4px}.mega-links>a.highlighted{background:var(--jade-pale);border-left-color:var(--jade-600)}.mega-links svg{width:21px;transition:transform .35s var(--ease-out),color .35s}.mega-links>a:hover svg{transform:translateX(5px);color:var(--jade-600)}.mega-photo{align-self:center;margin:0;border-left:1px solid var(--jade-line);padding-left:26px}.mega-photo>div{overflow:hidden;aspect-ratio:1.9;background:var(--jade-pale);border-radius:3px}.mega-photo img{width:100%;height:100%;object-fit:cover;transition:transform 1s var(--ease-out),object-position 1s var(--ease-out)}.mega-photo:hover img{transform:scale(1.035)}.mega-photo figcaption{font-family:var(--font-display);font-size:17px;margin-top:12px;line-height:1.6;color:var(--jade-ink)}.mega-photo p{font-size:12px;color:var(--jade-muted);margin-top:3px}.menu-quick{display:flex;align-items:center;gap:24px;padding:16px 0;border-top:1px solid var(--jade-line);margin-top:26px;font-size:12px}.menu-quick>span{color:var(--jade-muted)}.menu-quick a{color:var(--jade-600)}.menu-quick .menu-hint{margin-left:auto;font-size:11px}.nav-backdrop{position:fixed;inset:var(--header-height) 0 0;z-index:190;background:rgba(16,35,28,.23);backdrop-filter:blur(3px)}
.full-menu{background:var(--jade-900);color:#fff;min-height:calc(100dvh - var(--header-height));box-shadow:none}.full-inner{max-width:1500px;margin:auto;padding:55px var(--page-gutter) 25px;display:grid;grid-template-columns:.85fr 1.5fr;gap:50px}.full-intro .eyebrow{color:#a9c6bc}.full-intro h2{font-family:var(--font-display);font-size:clamp(30px,3.8vw,56px);line-height:1.55;font-weight:400;margin:30px 0}.full-intro>a{display:flex;align-items:center;gap:24px;color:#d1e5dd}.full-groups{display:grid;grid-template-columns:1fr 1fr;gap:38px 50px}.full-groups h3{font-family:var(--font-display);font-size:26px;font-weight:400;margin-bottom:14px}.full-groups a{display:flex;align-items:center;justify-content:space-between;font-size:14px;color:#c6d9d2;padding:9px 0;transition:color .2s,transform .3s}.full-groups a svg{width:18px;opacity:.4}.full-groups a:hover{color:#fff;transform:translateX(5px)}.full-bottom{grid-column:1/-1;display:flex;justify-content:space-between;border-top:1px solid #ffffff25;padding-top:25px;color:#b3cdc3;font-size:12px}.full-bottom a{display:flex;align-items:center;gap:15px;color:#fff}.account-inner{max-width:400px;margin-left:auto;padding:24px var(--page-gutter) 30px 24px}.account-inner p{color:var(--jade-muted);padding:12px 0}.account-inner a{display:flex;justify-content:space-between;align-items:center;padding:13px 0;border-bottom:1px solid var(--jade-line);color:var(--jade-900)}
.menu-sheet-enter-active{transition:clip-path .55s var(--ease-out),opacity .35s,transform .55s var(--ease-out)}.menu-sheet-leave-active{transition:clip-path .3s var(--ease-in),opacity .25s}.menu-sheet-enter-from{clip-path:inset(0 0 100%);opacity:.5;transform:translateY(-8px)}.menu-sheet-leave-to{clip-path:inset(0 0 100%);opacity:.3}.menu-backdrop-enter-active,.menu-backdrop-leave-active{transition:opacity .35s}.menu-backdrop-enter-from,.menu-backdrop-leave-to{opacity:0}.menu-copy-enter-active,.menu-copy-leave-active{transition:opacity .14s,transform .18s var(--ease-out)}.menu-copy-enter-from{opacity:0;transform:translateY(8px)}.menu-copy-leave-to{opacity:0;transform:translateY(-5px)}
@media(min-width:1600px){.mega-content{min-height:330px}.mega-links>a{padding:20px}}
@media(max-width:1100px){.header-inner{gap:16px}.city-link{display:none}.primary-nav{gap:4px}.header-actions{gap:15px}.nav-item{font-size:14px;padding:0 10px}.mega-content{gap:22px;grid-template-columns:.85fr 1.4fr 1fr}.mega-photo{padding-left:20px}.mega-links strong{font-size:17px}.mega-intro{padding-right:18px}.mega-photo figcaption{font-size:15px}}
@media(max-width:800px){.primary-nav,.city-link,.account-link>span:not(.user-initial){display:none}.header-actions{margin-left:auto;gap:18px}.header-inner{gap:10px}.publish-link{padding:9px 13px;font-size:12px}.publish-link>span{display:none}.account-link{font-size:12px}.full-inner{grid-template-columns:1fr;padding-top:30px;gap:30px}.full-intro h2{font-size:30px;margin:12px 0}.full-intro h2 br{display:none}.full-intro>a{display:none}.full-groups{gap:28px}.full-groups h3{font-size:23px}.full-groups a{font-size:13px}.full-bottom{gap:20px;align-items:center}.mega-content{grid-template-columns:1fr}.mega-photo{display:none}.mega-intro{border:0}.menu-quick{gap:14px;flex-wrap:wrap}.menu-hint{display:none}}
@media(max-width:400px){.header-actions{gap:10px}.account-link{display:none}.full-groups{column-gap:20px}.full-bottom{flex-direction:column;align-items:flex-start}}
.full-index{display:flex;flex-direction:column;gap:20px;margin:32px 0}.full-index a,.full-index button{font-family:var(--font-display);font-size:34px;line-height:1.5;font-weight:400;color:#fff;background:none;border:0;padding:0;display:flex;align-items:center;justify-content:space-between;text-align:left;max-width:310px}.full-index svg{opacity:0;transition:opacity .3s,transform .3s}.full-index a:hover svg,.full-index button:hover svg{opacity:1;transform:translateX(5px)}.full-intro h2{font-size:15px;color:#a9c6bc;margin:30px 0;font-family:var(--font-family-base)}.full-login{display:flex;align-items:center;justify-content:space-between;background:none;border:0;color:#fff;font-size:14px;width:100%;padding:9px 0;margin-bottom:5px}.full-login svg{width:18px}.full-groups section{scroll-margin-top:25px}.menu-sheet-enter-active .full-groups section{animation:fullGroupIn .7s var(--ease-out) both}.menu-sheet-enter-active .full-groups section:nth-child(2){animation-delay:.06s}.menu-sheet-enter-active .full-groups section:nth-child(3){animation-delay:.12s}.menu-sheet-enter-active .full-groups section:nth-child(4){animation-delay:.18s}@keyframes fullGroupIn{from{opacity:0;transform:translateY(15px)}to{opacity:1;transform:none}}@media(max-width:800px){.full-index{display:none}.full-intro h2{font-family:var(--font-display);font-size:28px;color:#fff;margin:16px 0 0}.full-login{font-size:13px}}
.mega-photo>div{position:relative}.mega-photo>div img{position:absolute;inset:0}.menu-photo-enter-active,.menu-photo-leave-active{transition:opacity .45s,transform .6s var(--ease-out)}.menu-photo-enter-from{opacity:0;transform:scale(1.035)}.menu-photo-leave-to{opacity:0}


/* Keep the sliding indicator below the text, independent of inherited nav height. */
.primary-nav{height:44px;align-self:center}.nav-line{bottom:2px;pointer-events:none}
.signed-in{gap:7px}.signed-in>span:first-child{max-width:95px;overflow:hidden;text-overflow:ellipsis}.signed-in .account-chevron{font-size:13px;color:var(--jade-muted);transition:transform .25s}.signed-in[aria-expanded="true"] .account-chevron{transform:rotate(180deg)}
.menu-sheet.account-menu{left:auto;right:var(--page-gutter);width:270px;max-width:calc(100vw - 40px);top:calc(100% + 8px);border:1px solid var(--jade-line);border-radius:6px;box-shadow:0 14px 40px #103d361a;transform-origin:top right}.account-inner{max-width:none;margin:0;padding:12px 20px 20px}.account-inner p{font-size:12px;padding:10px 0;color:var(--jade-muted)}.account-inner a{font-size:13px;padding:12px 0}.account-inner a:last-child{border-bottom:0}.account-inner a svg{width:17px}.nav-backdrop.account-backdrop{background:transparent;backdrop-filter:none}
@media(max-width:800px){.primary-nav{display:none}.signed-in>span:not(.account-chevron){display:inline}.signed-in .account-chevron{display:inline}}

@media(min-width:801px) and (max-width:1100px){.header-inner{gap:12px;padding-left:24px;padding-right:24px}.brand-link :deep(.brand-mark){font-size:20px;gap:7px}.brand-link :deep(svg){width:26px;height:29px}.primary-nav{gap:0}.nav-item{padding:0 7px;font-size:12px}.header-actions{gap:12px}.publish-link{padding:10px 12px;font-size:12px;gap:6px}.publish-link>span{display:none}}
@media(max-width:500px){.publish-link{display:none}.header-actions{gap:12px}}
</style>

