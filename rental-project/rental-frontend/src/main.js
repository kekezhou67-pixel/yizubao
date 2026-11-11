import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { configureAuthNavigation } from './stores/authNavigation'
import { useAuthModal } from './stores/authModal'

// 引入 Vant 样式
import 'vant/lib/index.css'

// 引入全局设计系统
import './assets/styles/variables.css'
import './assets/styles/global.css'
import './assets/styles/jade.css'
import './assets/styles/business.css'

// 全局 fetch 拦截：自动注入 JWT Token（兼容旧代码中仍用 fetch 的地方）
const originalFetch = window.fetch
window.fetch = async function(url, options = {}) {
  const token = localStorage.getItem('rental_token')
  if (token) {
    options.headers = {
      ...options.headers,
      'Authorization': 'Bearer ' + token
    }
  }
  const response = await originalFetch(url, options)
  // 401：登录失效，清除信息并跳转登录页（仅非登录/注册相关页）
  if (response.status === 401 && !url.includes('/api/user')) {
    const keys = ['rental_token', 'rental_userId', 'rental_username', 'rental_role',
      'rental_avatar', 'rental_points', 'rental_level', 'user', 'token']
    keys.forEach(k => localStorage.removeItem(k))
    useAuthModal().open()
  }
  return response
}

const app = createApp(App)

// 注册 Pinia 状态管理
const pinia = createPinia()
app.use(pinia)

configureAuthNavigation(router)
app.use(router)
router.isReady().then(() => app.mount('#app'))

