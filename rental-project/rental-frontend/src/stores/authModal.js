// Compatibility entry for existing business screens and request interceptors.
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { openAuthPage, safeAuthRedirect } from './authNavigation'
export const useAuthModal = defineStore('authModal', () => {
  const visible = ref(false), redirectTo = ref(null)
  function open(to) { redirectTo.value = safeAuthRedirect(to || '/'); return openAuthPage(to) }
  function close() { visible.value = false; redirectTo.value = null }
  return { visible, redirectTo, open, close }
})
