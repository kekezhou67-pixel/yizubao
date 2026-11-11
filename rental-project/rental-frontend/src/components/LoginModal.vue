<template>
  <div class="login-overlay" v-if="visible" @keydown="dialogKeys" @click.self="$emit('close')">
    <div class="login-card" ref="dialogCard" role="dialog" aria-modal="true" aria-label="登录或注册易租宝" tabindex="-1">
      <button class="login-close" @click="$emit('close')" aria-label="关闭">
        <svg viewBox="0 0 24 24" width="14" height="14"><path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/></svg>
      </button>

      <!-- 品牌头部 -->
      <div class="lc-header">
        <svg viewBox="0 0 32 32" width="34" height="34" class="lc-logo">
          <circle cx="16" cy="16" r="14" fill="#fff" />
          <path d="M8 16c0 0 3-6 8-6s8 6 8 6" stroke="#1d7dff" stroke-width="2.6" fill="none" stroke-linecap="round" />
          <circle cx="16" cy="16" r="2.6" fill="#1d7dff" />
        </svg>
        <div class="lc-hd-txt">
          <span class="lc-hd-name">易租宝</span>
          <span class="lc-hd-sub">好好找房，好好生活</span>
        </div>
      </div>

      <div class="lc-body">
        <!-- ===== Step 0: 输入手机号 ===== -->
        <template v-if="step === 'input'">
          <h3 class="lc-title">欢迎登录</h3>
          <p class="lc-sub">输入手机号，登录或注册</p>

          <div class="field" :class="{ 'has-err': error }">
            <span class="field-ic">
              <svg viewBox="0 0 24 24" width="17" height="17"><path d="M7 3h10a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z" fill="none" stroke="currentColor" stroke-width="1.6"/><path d="M11 18h2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
            </span>
            <input
              v-model.trim="target"
              @input="clearError"
              @keyup.enter="checkAccount"
              maxlength="11"
              inputmode="numeric"
              placeholder="请输入手机号"
              class="lc-input"
            />
          </div>
          <p class="login-error" v-if="error">{{ error }}</p>

          <button class="lc-btn" @click="checkAccount" :disabled="!target || loading">
            <span v-if="loading" class="lc-spin"></span>
            {{ loading ? '查询中' : '继续' }}
          </button>

          <div class="lc-divider"><span>其他登录方式</span></div>
          <div class="lc-social">
            <button class="lc-social-btn" @click="error = '暂未开通第三方登录，请使用手机号验证码登录'">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="#07c160"><path d="M8.5 4C4.9 4 2 6.5 2 9.6c0 1.8 1 3.4 2.6 4.4l-.6 2 2.3-1.2c.7.2 1.4.3 2.2.3h.6c-.1-.4-.2-.9-.2-1.3 0-2.8 2.7-5.1 6-5.1h.6C15.2 5.9 12.2 4 8.5 4zm-2 3.2c.5 0 .9.4.9.9s-.4.9-.9.9-.9-.4-.9-.9.4-.9.9-.9zm4 0c.5 0 .9.4.9.9s-.4.9-.9.9-.9-.4-.9-.9.4-.9.9-.9zm5.5 2.1c-3 0-5.5 2.1-5.5 4.7s2.5 4.7 5.5 4.7c.6 0 1.2-.1 1.8-.3l1.9 1-.5-1.7c1.3-.9 2.2-2.2 2.2-3.7 0-2.6-2.4-4.7-5.4-4.7zm-1.8 2.2c.3 0 .6.2.6.6s-.3.6-.6.6-.6-.3-.6-.6.3-.6.6-.6zm3.6 0c.3 0 .6.2.6.6s-.3.6-.6.6-.6-.3-.6-.6.3-.6.6-.6z"/></svg>
              <span>微信登录</span>
            </button>
          </div>
          <button class="account-password-entry" @click="step = 'account'; target = ''; password = ''; clearError()">使用账号密码登录 →</button>
          <p class="lc-tip">未注册手机号可继续创建账号</p>
        </template>

        <template v-if="step === 'account'">
          <h3 class="lc-title">欢迎回来</h3>
          <p class="lc-sub">使用已有账号，继续你的租住日常。</p>
          <div class="field"><input v-model.trim="target" class="lc-input no-ic" placeholder="用户名 / 手机号 / 邮箱" aria-label="登录账号" autocomplete="username" @input="clearError" @keyup.enter="doLoginPwd" /></div>
          <div class="field"><input v-model="password" class="lc-input no-ic" type="password" placeholder="请输入密码" aria-label="登录密码" autocomplete="current-password" @input="clearError" @keyup.enter="doLoginPwd" /></div>
          <p v-if="error" class="login-error" role="alert">{{ error }}</p>
          <button class="lc-btn" :disabled="!target || !password || loading" @click="doLoginPwd">{{ loading ? '登录中…' : '登录' }}</button>
          <button class="account-password-entry" @click="reset">使用手机号登录或注册 →</button>
        </template>

        <!-- ===== Step 1: 用户存在 → 选择登录方式 ===== -->
        <template v-if="step === 'login'">
          <div class="login-found">
            <div class="lf-avatar">{{ (foundUser.username || 'U')[0] }}</div>
            <h3 class="lf-name">{{ foundUser.username }}</h3>
            <p class="lf-role">{{ foundUser.role === 'ADMIN' ? '管理员' : foundUser.role === 'LANDLORD' ? '房东' : '租客' }}</p>
          </div>

          <div class="login-tabs">
            <button :class="{ on: loginMethod === 'code' }" @click="loginMethod = 'code'; clearError()">验证码</button>
            <button :class="{ on: loginMethod === 'password' }" @click="loginMethod = 'password'; clearError()">密码</button>
          </div>

          <template v-if="loginMethod === 'code'">
            <div class="code-row" :class="{ 'has-err': error }">
              <input
                v-model.trim="code"
                @input="clearError"
                maxlength="6"
                inputmode="numeric"
                placeholder="6 位验证码"
                class="lc-input no-ic"
              />
              <button class="send-btn" @click="sendCode" :disabled="cd > 0 || loading">
                {{ cd > 0 ? cd + 's' : '获取' }}
              </button>
            </div>
            <p class="login-error" v-if="error">{{ error }}</p>
            <button class="lc-btn" @click="doLoginCode" :disabled="!code || loading">
              <span v-if="loading" class="lc-spin"></span>
              {{ loading ? '登录中' : '登录' }}
            </button>
          </template>

          <template v-else>
            <div class="field" :class="{ 'has-err': error }">
              <span class="field-ic">
                <svg viewBox="0 0 24 24" width="17" height="17"><rect x="5" y="11" width="14" height="9" rx="2" fill="none" stroke="currentColor" stroke-width="1.6"/><path d="M8 11V8a4 4 0 0 1 8 0v3" fill="none" stroke="currentColor" stroke-width="1.6"/></svg>
              </span>
              <input
                v-model="password"
                @input="clearError"
                @keyup.enter="doLoginPwd"
                type="password"
                placeholder="请输入密码"
                class="lc-input"
              />
            </div>
            <p class="login-error" v-if="error">{{ error }}</p>
            <button class="lc-btn" @click="doLoginPwd" :disabled="!password || loading">
              <span v-if="loading" class="lc-spin"></span>
              {{ loading ? '登录中' : '登录' }}
            </button>
          </template>

          <p class="login-switch">不是你的账号？<a @click="reset">重新输入</a></p>
        </template>

        <!-- ===== Step 2: 用户不存在 → 注册 ===== -->
        <template v-if="step === 'register'">
          <h3 class="lc-title">创建账号</h3>
          <p class="lc-sub">一步完成注册</p>

          <div class="reg-role">
            <span :class="{ on: regRole === 'TENANT' }" @click="regRole = 'TENANT'; clearError()">租客</span>
            <span :class="{ on: regRole === 'LANDLORD' }" @click="regRole = 'LANDLORD'; clearError()">房东</span>
          </div>

          <div class="field" :class="{ 'has-err': error }">
            <input v-model.trim="regForm.nickname" @input="clearError" maxlength="20" placeholder="昵称" class="lc-input no-ic" />
          </div>
          <div class="field" :class="{ 'has-err': error }">
            <span class="field-ic">
              <svg viewBox="0 0 24 24" width="17" height="17"><rect x="5" y="11" width="14" height="9" rx="2" fill="none" stroke="currentColor" stroke-width="1.6"/><path d="M8 11V8a4 4 0 0 1 8 0v3" fill="none" stroke="currentColor" stroke-width="1.6"/></svg>
            </span>
            <input v-model="regForm.password" @input="clearError" type="password" maxlength="20" placeholder="密码（至少 6 位）" class="lc-input" />
          </div>
          <div class="field" v-if="regRole === 'LANDLORD'" :class="{ 'has-err': error }">
            <input v-model.trim="regForm.realName" @input="clearError" maxlength="20" placeholder="真实姓名" class="lc-input no-ic" />
          </div>
          <div class="code-row" :class="{ 'has-err': error }">
            <input v-model.trim="code" @input="clearError" maxlength="6" inputmode="numeric" placeholder="验证码" class="lc-input no-ic" />
            <button class="send-btn" @click="sendCode" :disabled="cd > 0 || loading">
              {{ cd > 0 ? cd + 's' : '获取' }}
            </button>
          </div>
          <p class="login-error" v-if="error">{{ error }}</p>
          <button class="lc-btn" @click="doRegister" :disabled="!canRegister || loading">
            <span v-if="loading" class="lc-spin"></span>
            {{ loading ? '注册中' : '注册并登录' }}
          </button>
          <label class="agree">
            <input type="checkbox" v-model="regForm.agree" />
            <span>同意 <a @click.prevent>《用户协议》</a> 与 <a @click.prevent>《隐私政策》</a></span>
          </label>
        </template>
      </div>
    </div>
  </div>
</template>

<script>
import { nextTick } from 'vue'
import { userApi } from '../api/user'
import { useUserStore } from '../stores/user'

const PHONE_RE = /^1[3-9]\d{9}$/
const CODE_RE = /^\d{6}$/

export default {
  name: 'LoginModal',
  props: ['visible'],
  emits: ['close', 'login'],
  data() {
    return {
      previousFocus: null,
      previousOverflow: '',
      step: 'input',
      target: '',
      code: '',
      password: '',
      loginMethod: 'code',
      foundUser: null,
      error: '',
      loading: false,
      cd: 0,
      timer: null,
      regRole: 'TENANT',
      regForm: { nickname: '', password: '', realName: '', agree: false }
    }
  },
  computed: {
    canRegister() {
      return (
        PHONE_RE.test(this.target) &&
        this.regForm.nickname &&
        this.regForm.password.length >= 6 &&
        CODE_RE.test(this.code)
      )
    }
  },
  watch: {
    visible: {
      immediate: true,
      async handler(value, old) {
        if (value && !old) {
          this.previousFocus = document.activeElement
          this.previousOverflow = document.body.style.overflow
          document.body.style.overflow = 'hidden'
          await nextTick()
          this.$refs.dialogCard?.querySelector('input')?.focus()
        } else if (!value && old) {
          document.body.style.overflow = this.previousOverflow
          await nextTick()
          if (this.previousFocus?.isConnected) this.previousFocus.focus({preventScroll:true})
        }
      }
    }
  },
  beforeUnmount() {
    if (this.visible) document.body.style.overflow = this.previousOverflow
    if (this.timer) clearInterval(this.timer)
  },
  methods: {
    dialogKeys(event) {
      if (event.key === 'Escape') { event.preventDefault(); this.$emit('close'); return }
      if (event.key !== 'Tab') return
      const items = [...this.$refs.dialogCard.querySelectorAll('input,button,a[href],select,textarea')].filter(el => !el.disabled && el.getClientRects().length)
      if (event.shiftKey && document.activeElement === items[0]) {event.preventDefault(); items.at(-1)?.focus()}
      else if (!event.shiftKey && document.activeElement === items.at(-1)) {event.preventDefault(); items[0]?.focus()}
    },
    clearError() { this.error = '' },

    reset() {
      this.step = 'input'
      this.code = ''
      this.password = ''
      this.error = ''
      this.foundUser = null
    },

    validPhone() {
      if (!this.target) { this.error = '请输入手机号'; return false }
      if (!PHONE_RE.test(this.target)) { this.error = '手机号格式不正确'; return false }
      return true
    },

    async checkAccount() {
      if (!this.validPhone()) return
      this.loading = true
      this.error = ''
      try {
        const r = await userApi.checkUser(this.target)
        if (r.code === 200 && r.data && r.data.exists) {
          this.foundUser = r.data
          this.step = 'login'
        } else {
          this.step = 'register'
        }
      } catch (e) {
        this.error = e.message || '网络错误'
      } finally {
        this.loading = false
      }
    },

    async sendCode() {
      if (this.cd > 0 || this.loading) return
      if (!this.validPhone()) return
      this.loading = true
      try {
        const r = await userApi.sendCode(this.target)
        if (r.code === 200) {
          this.cd = 60
          this.timer = setInterval(() => {
            if (--this.cd <= 0) { clearInterval(this.timer); this.timer = null }
          }, 1000)
          this.error = ''
        } else {
          this.error = r.message
        }
      } catch (e) {
        this.error = e.message || '发送失败'
      } finally {
        this.loading = false
      }
    },

    async doLoginCode() {
      if (!CODE_RE.test(this.code)) { this.error = '请输入 6 位验证码'; return }
      this.loading = true
      this.error = ''
      try {
        const userStore = useUserStore()
        await userStore.loginByCode(this.target, this.code)
        this.$emit('login', { username: userStore.username })
      } catch (e) {
        this.error = e.message || '验证失败'
      } finally {
        this.loading = false
      }
    },

    async doLoginPwd() {
      if (!this.password) { this.error = '请输入密码'; return }
      this.loading = true
      this.error = ''
      try {
        const userStore = useUserStore()
        await userStore.login(this.target, this.password)
        this.$emit('login', { username: userStore.username })
      } catch (e) {
        this.error = e.message || '账号或密码错误'
      } finally {
        this.loading = false
      }
    },

    async doRegister() {
      if (!this.canRegister) {
        if (!PHONE_RE.test(this.target)) this.error = '手机号格式不正确'
        else if (!this.regForm.nickname) this.error = '请填写昵称'
        else if (this.regForm.password.length < 6) this.error = '密码至少 6 位'
        else if (!CODE_RE.test(this.code)) this.error = '请输入 6 位验证码'
        else if (!this.regForm.agree) this.error = '请先同意用户协议'
        return
      }
      this.loading = true
      this.error = ''
      try {
        const r = await userApi.register({
          username: this.regForm.nickname,
          password: this.regForm.password,
          phone: this.target,
          email: null,
          role: this.regRole,
          realName: this.regForm.realName || null
        })
        if (r.code === 200) {
          const userStore = useUserStore()
          await userStore.login(this.target, this.regForm.password)
          this.$emit('login', { username: userStore.username })
        } else {
          this.error = r.message
        }
      } catch (e) {
        this.error = e.message || '网络错误'
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
/* 透明遮罩：仅拦截点击做"点外部关闭"，不遮挡原页面 */
.login-overlay {
  position: fixed; inset: 0;
  background: transparent;
  z-index: 9999;
  display: flex; align-items: center; justify-content: center;
  padding: 20px;
}

/* 玻璃感悬浮卡片：正中间，背景透过卡片模糊可见，绝不会全白 */
.login-card {
  position: relative;
  width: 380px; max-width: 100%;
  border-radius: 20px;
  overflow: hidden;
  background: rgba(255, 255, 255, .72);
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, .55);
  box-shadow: 0 24px 70px rgba(0, 0, 0, .28), 0 8px 24px rgba(0, 0, 0, .12), inset 0 1px 0 rgba(255, 255, 255, .9);
  animation: card-in .28s cubic-bezier(.2, .8, .25, 1);
}
@keyframes card-in { from { opacity: 0; transform: translateY(12px) scale(.97) } to { opacity: 1; transform: none } }

.login-close {
  position: absolute; top: 12px; right: 12px;
  width: 28px; height: 28px; border-radius: 50%;
  border: none; background: rgba(255, 255, 255, .25); color: #fff;
  display: flex; align-items: center; justify-content: center; cursor: pointer;
  transition: background .15s;
  z-index: 2;
}
.login-close:hover { background: rgba(255, 255, 255, .4); }

/* 品牌头部 */
.lc-header {
  padding: 22px 24px 18px;
  background: linear-gradient(135deg, var(--jade-900), var(--jade-700));
  color: #fff;
  display: flex; align-items: center; gap: 12px;
}
.lc-hd-txt { display: flex; flex-direction: column; line-height: 1.2; }
.lc-hd-name { font-size: 17px; font-weight: 800; letter-spacing: .5px; }
.lc-hd-sub { font-size: 11.5px; color: rgba(255, 255, 255, .85); margin-top: 3px; }

.lc-body { padding: 22px 26px 20px; }

.lc-title { font-size: 18px; font-weight: 700; color: var(--jade-ink); margin: 0 0 4px; }
.lc-sub { font-size: 12.5px; color: #9aa0a6; margin: 0 0 16px; }

/* 输入框：不透明白底保证清晰 */
.field { position: relative; margin-bottom: 10px; }
.field-ic {
  position: absolute; left: 12px; top: 50%; transform: translateY(-50%);
  color: #b6bcc4; display: flex; pointer-events: none; z-index: 1;
}
.lc-input {
  width: 100%; padding: 11px 12px 11px 38px;
  background: #fff; border: 1.5px solid #e6e8eb; border-radius: 10px;
  font-size: 14px; color: var(--jade-ink); outline: none;
  transition: border-color .15s, box-shadow .15s;
  box-sizing: border-box;
  position: relative;
}
.lc-input.no-ic { padding-left: 12px; }
.lc-input::placeholder { color: #b6bcc4; }
.lc-input:focus {
  border-color: var(--jade-600);
  box-shadow: 0 0 0 3px rgba(22,139,120, .14);
}
.field.has-err .lc-input,
.code-row.has-err .lc-input { border-color: #f5455c; }
.field.has-err { animation: shake .3s; }
.code-row.has-err { animation: shake .3s; }
@keyframes shake { 0%,100%{transform:translateX(0)} 25%{transform:translateX(-4px)} 75%{transform:translateX(4px)} }

/* 主按钮 */
.lc-btn {
  width: 100%; padding: 11px;
  background: linear-gradient(135deg, var(--jade-600), var(--jade-700));
  color: #fff; border: none; border-radius: 10px;
  font-size: 14.5px; font-weight: 600; cursor: pointer;
  margin: 4px 0 10px;
  display: flex; align-items: center; justify-content: center; gap: 7px;
  transition: transform .12s, box-shadow .15s, opacity .15s;
  box-shadow: 0 6px 16px rgba(29, 111, 240, .3);
}
.lc-btn:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 9px 22px rgba(29, 111, 240, .36); }
.lc-btn:disabled { opacity: .5; cursor: default; box-shadow: none; }

.lc-spin {
  width: 14px; height: 14px; border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, .45); border-top-color: #fff;
  animation: spin .7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg) } }

.lc-tip { font-size: 11.5px; color: #9aa0a6; text-align: center; margin: 8px 0 0; }
.login-error { font-size: 12px; color: #f5455c; margin: -4px 0 10px; }

/* 分隔线 */
.lc-divider { display: flex; align-items: center; gap: 12px; margin: 14px 0 12px; }
.lc-divider::before, .lc-divider::after { content: ''; flex: 1; height: 1px; background: rgba(0, 0, 0, .1); }
.lc-divider span { font-size: 11.5px; color: #9aa0a6; }

/* 第三方登录 */
.lc-social { display: flex; justify-content: center; }
.lc-social-btn {
  display: flex; align-items: center; gap: 8px;
  padding: 9px 18px;
  background: #fff; border: 1.5px solid #e6e8eb; border-radius: 10px;
  font-size: 13px; color: #333; cursor: pointer;
  transition: background .15s, border-color .15s;
}
.lc-social-btn:hover { background: #f5f6f8; border-color: #d2e4ff; }

/* 已存在用户 */
.login-found { text-align: center; margin-bottom: 14px; }
.lf-avatar {
  width: 50px; height: 50px; border-radius: 50%;
  background: linear-gradient(135deg, var(--jade-pale), #d2e4ff); color: var(--jade-600);
  font-size: 20px; font-weight: 800;
  display: flex; align-items: center; justify-content: center; margin: 0 auto 6px;
}
.lf-name { font-size: 16px; font-weight: 700; color: var(--jade-ink); margin: 0 0 1px; }
.lf-role { font-size: 11.5px; color: #9aa0a6; margin: 0; }

/* Tab */
.login-tabs { display: flex; gap: 4px; margin-bottom: 12px; background: rgba(0, 0, 0, .05); padding: 3px; border-radius: 10px; }
.login-tabs button {
  flex: 1; padding: 7px; text-align: center; background: transparent;
  border: none; border-radius: 7px; font-size: 13px; font-weight: 600; color: #7a818c; cursor: pointer;
  transition: background .15s, color .15s;
}
.login-tabs button.on { background: #fff; color: var(--jade-600); box-shadow: 0 1px 3px rgba(0, 0, 0, .1); }

/* 验证码行 */
.code-row { display: flex; gap: 8px; margin-bottom: 10px; }
.code-row .lc-input { flex: 1; }
.send-btn {
  padding: 0 14px; flex-shrink: 0;
  background: #fff; color: var(--jade-600); border: 1.5px solid #d2e4ff;
  border-radius: 10px; font-size: 12.5px; font-weight: 600; cursor: pointer; white-space: nowrap;
  transition: background .15s;
}
.send-btn:hover:not(:disabled) { background: #e9f1ff; }
.send-btn:disabled { background: #f7f8fa; color: #b6bcc4; border-color: #eef0f2; cursor: default; }

.login-switch { text-align: center; font-size: 12px; color: #9aa0a6; margin: 8px 0 0; }
.login-switch a { color: var(--jade-600); cursor: pointer; font-weight: 600; }

/* 注册 */
.reg-role { display: flex; gap: 6px; margin-bottom: 12px; }
.reg-role span {
  flex: 1; text-align: center; padding: 9px; background: #fff; border: 1.5px solid #e6e8eb;
  border-radius: 10px; font-size: 13px; font-weight: 600; color: #7a818c; cursor: pointer;
  transition: all .15s;
}
.reg-role span.on { background: #e9f1ff; border-color: var(--jade-600); color: var(--jade-600); }

.agree { display: flex; align-items: flex-start; gap: 7px; font-size: 11.5px; color: #9aa0a6; margin: 8px 2px 0; line-height: 1.5; cursor: pointer; }
.agree input { margin-top: 1px; accent-color: var(--jade-600); }
.agree a { color: var(--jade-600); }
</style>
