<template>
  <div class="app-root">
    <SiteHeader v-if="!route.meta.authPage" :disabled="false" @menu-change="menuOpen = $event" />
    <main id="page-content" class="app-content" tabindex="-1" :inert="menuOpen || undefined">
      <RouterView v-slot="{ Component, route }"><component :is="Component" :key="route.path.startsWith('/app/') ? 'app-layout' : route.path" /></RouterView>
    </main>
    <RouteCurtain />
  </div>
</template>
<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import SiteHeader from './components/SiteHeader.vue'
import RouteCurtain from './components/RouteCurtain.vue'
const menuOpen = ref(false), router = useRouter(), route = useRoute()
watch(() => route.path, () => { menuOpen.value = false })
function internalLink(event) {
  if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
  const link = event.target.closest?.('a[href]')
  if (!link || link.target || link.hasAttribute('download')) return
  const url = new URL(link.href, window.location.href)
  if (url.origin !== window.location.origin || !['http:', 'https:'].includes(url.protocol) || url.pathname.startsWith('/api/') || url.pathname === window.location.pathname && url.hash) return
  const target = url.pathname + url.search + url.hash
  if (!router.resolve(target).matched.length) return
  event.preventDefault(); router.push(target)
}
onMounted(() => document.addEventListener('click', internalLink))
onBeforeUnmount(() => document.removeEventListener('click', internalLink))
</script>
