import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { userApi } from '../api/user'

/**
 * 用户状态 Store —— 统一管理登录态、token、用户信息
 * 
 * 使用方式：
 *   import { useUserStore } from '@/stores/user'
 *   const userStore = useUserStore()
 *   userStore.login(account, password)  // 登录
 *   userStore.isLogin                   // 是否已登录
 *   userStore.username                  // 当前用户名
 *   userStore.logout()                  // 退出
 */
export const useUserStore = defineStore('user', () => {
  // ===== 状态 =====
  const token = ref(localStorage.getItem('rental_token') || '')
  const userId = ref(Number(localStorage.getItem('rental_userId')) || null)
  const username = ref(localStorage.getItem('rental_username') || '')
  const role = ref(localStorage.getItem('rental_role') || '')
  const avatar = ref(localStorage.getItem('rental_avatar') || '')
  const points = ref(Number(localStorage.getItem('rental_points')) || 0)
  const level = ref(localStorage.getItem('rental_level') || '')

  // ===== 计算属性 =====
  const isLogin = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'ADMIN')

  // ===== 持久化到 localStorage =====
  function persist(data) {
    if (data.token) {
      token.value = data.token
      userId.value = data.id
      username.value = data.username
      role.value = data.role
      avatar.value = data.avatar || ''
      points.value = data.points || 0
      level.value = data.level || ''

      localStorage.setItem('rental_token', data.token)
      localStorage.setItem('rental_userId', String(data.id))
      localStorage.setItem('rental_username', data.username)
      localStorage.setItem('rental_role', data.role)
      if (data.avatar) localStorage.setItem('rental_avatar', data.avatar)
      if (data.points) localStorage.setItem('rental_points', String(data.points))
      if (data.level) localStorage.setItem('rental_level', data.level)

      // 兼容旧代码（保留 user 对象给 main.js 的 fetch 拦截器用）
      localStorage.setItem('user', JSON.stringify(data))
      localStorage.setItem('token', data.token)
    }
  }

  // ===== 操作 =====
  async function login(account, password) {
    const res = await userApi.login({ account, password })
    if (res.code !== 200) throw new Error(res.message)
    persist(res.data)
    return res.data
  }

  async function loginByCode(target, code) {
    const res = await userApi.loginByCode({ target, code })
    if (res.code !== 200) throw new Error(res.message)
    persist(res.data)
    return res.data
  }

  async function register(params) {
    const res = await userApi.register(params)
    if (res.code !== 200) throw new Error(res.message)
    return res.data
  }

  function logout() {
    token.value = ''
    userId.value = null
    username.value = ''
    role.value = ''
    avatar.value = ''
    points.value = 0
    level.value = ''

    const keys = ['rental_token', 'rental_userId', 'rental_username', 'rental_role',
      'rental_avatar', 'rental_points', 'rental_level', 'user', 'token']
    keys.forEach(k => localStorage.removeItem(k))

    // 跳转到首页
    window.location.href = '/'
  }

  return {
    // 状态
    token, userId, username, role, avatar, points, level,
    // 计算属性
    isLogin, isAdmin,
    // 操作
    login, loginByCode, register, logout
  }
})
