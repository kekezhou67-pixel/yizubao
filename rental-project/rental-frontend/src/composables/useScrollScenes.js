import { onMounted, onBeforeUnmount } from 'vue'
import { isReducedMotion } from './motionPreference'

export function useScrollScenes(root, story, activeScene, sceneScrollLock) {
  let observer, resizeObserver, frame = 0, media
  function paint() {
    frame = 0
    if (!root.value || isReducedMotion()) return
    const hero = root.value.querySelector('.home-hero')
    if (hero) {
      const rect = hero.getBoundingClientRect()
      if (rect.bottom > 0) hero.style.setProperty('--hero-shift', `${Math.max(0, -rect.top) * .17}px`)
    }
    if (story.value) {
      const rect = story.value.getBoundingClientRect()
      const header = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--header-height')) || 84
      const travel = rect.height - window.innerHeight + header
      if (travel > 50 && window.innerWidth > 800) {
        const progress = Math.min(1, Math.max(0, (header - rect.top) / travel))
        if (Date.now() >= (sceneScrollLock?.value || 0)) activeScene.value = Math.min(2, Math.floor(progress * 3))
        story.value.style.setProperty('--scene-progress', progress)
      }
    }
  }
  function schedule() { if (!frame) frame = requestAnimationFrame(paint) }
  function releaseLock() { if (sceneScrollLock) sceneScrollLock.value = 0; schedule() }
  function observe() { root.value?.querySelectorAll('.reveal:not(.is-visible)').forEach(el => observer?.observe(el)) }
  onMounted(() => {
    media = window.matchMedia('(prefers-reduced-motion: reduce)')
    observer = new IntersectionObserver(entries => {
      for (const entry of entries) if (entry.isIntersecting) { entry.target.classList.add('is-visible'); observer.unobserve(entry.target) }
    }, { threshold: .08, rootMargin: '0px 0px -20px 0px' })
    root.value?.classList.add('motion-ready'); observe()
    resizeObserver = new ResizeObserver(() => { observe(); schedule() })
    if (root.value) resizeObserver.observe(root.value)
    window.addEventListener('scroll', schedule, { passive: true }); window.addEventListener('resize', schedule, { passive: true })
    media.addEventListener('change', schedule); schedule()
    window.addEventListener('rental-motion-change', schedule)
    window.addEventListener('wheel', releaseLock, { passive:true })
    window.addEventListener('touchstart', releaseLock, { passive:true })
  })
    onBeforeUnmount(() => { observer?.disconnect(); resizeObserver?.disconnect(); cancelAnimationFrame(frame); window.removeEventListener('scroll', schedule); window.removeEventListener('resize', schedule); window.removeEventListener('rental-motion-change', schedule); window.removeEventListener('wheel', releaseLock); window.removeEventListener('touchstart', releaseLock); media?.removeEventListener('change', schedule) })
}
