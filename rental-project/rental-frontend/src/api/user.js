/**
 * 用户模块 API
 */
import request from './index'

export const userApi = {
  /** 密码登录 */
  login(data) {
    return request.post('/user/login', data)
  },

  /** 验证码登录 */
  loginByCode(data) {
    return request.post('/user/login-by-code', data)
  },

  /** 注册 */
  register(data) {
    return request.post('/user/register', data)
  },

  /** 发送验证码 */
  sendCode(target, captchaTicket) {
    return request.post('/user/send-code', { target, captchaTicket })
  },

  captcha() { return request.get('/user/captcha') },
  verifyCaptcha(data) { return request.post('/user/captcha/verify', data) },

  /** 获取当前用户信息 */
  getMe() {
    return request.get('/user/me')
  },

  /** 获取用户信息 */
  getUserInfo(id) {
    return request.get(`/user/info/${id}`)
  },

  /** 检查用户是否存在 */
  checkUser(keyword) {
    return request.post('/user/check', { target: keyword })
  }
}
