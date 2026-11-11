import axios from 'axios'
import { useAuthModal } from '../stores/authModal'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// ===== 请求拦截器 —— 自动注入 JWT Token =====
request.interceptors.request.use(
  config => {
    const user = localStorage.getItem('user')
    if (user) {
      try {
        const userData = JSON.parse(user)
        if (userData.token) {
          config.headers['Authorization'] = 'Bearer ' + userData.token
        }
      } catch (_) {
        /* ignore parse error */
      }
    }
    return config
  },
  error => Promise.reject(error)
)

// ===== 响应拦截器 —— 统一错误处理 =====
request.interceptors.response.use(
  response => {
    const body = response.data
    // 业务层错误（code != 200）
    if (body && typeof body.code !== 'undefined' && body.code !== 200) {
      const err = new Error(body.message || '请求失败')
      err.code = body.code
      return Promise.reject(err)
    }
    return body
  },
  error => {
    if (!error.response) error.message = error.code === 'ECONNABORTED' ? '连接超时，请稍后重试' : '暂时无法连接服务，请稍后重试'
    else if (error.response.status >= 500) error.message = '服务暂时不可用，请稍后重试'
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        const keys = ['rental_token', 'rental_userId', 'rental_username', 'rental_role',
          'rental_avatar', 'rental_points', 'rental_level', 'user', 'token']
        keys.forEach(k => localStorage.removeItem(k))
        useAuthModal().open()
      } else if (status === 500) {
        console.error('服务器错误:', error.response.data)
      }
    } else if (error.code === 'ECONNABORTED') {
      console.error('请求超时')
    } else {
      console.error('网络错误:', error.message)
    }
    return Promise.reject(error)
  }
)

export default request
