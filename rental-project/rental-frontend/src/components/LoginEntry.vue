<template>
  <div class="login-entry"></div>
</template>

<script>
import { useRouter } from 'vue-router'
import { useAuthModal } from '../stores/authModal'

/**
 * /login 路由的极简入口：
 * 整页登录已统一为弹窗，这里仅负责“打开弹窗 + 回到首页”，
 * 保留 /login 路径以便可回退（旧 Login.vue 仍在，可随时切回）。
 */
export default {
  name: 'LoginEntry',
  mounted() {
    useAuthModal().open()
    this.$router.replace('/')
  }
}
</script>

<style scoped>
.login-entry { min-height: 100vh; background: #f8f9fb; }
</style>
