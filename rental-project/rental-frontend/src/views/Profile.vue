<template>
  <div class="ke-profile">
    <PageHero title="个人中心" subtitle="收藏喜欢的家，照顾自己的租住日常。" />
    <div class="ke-p-inner">
      <!-- 基本信息卡 -->
      <div class="ke-p-card">
        <div class="ke-p-ava">{{ (userInfo.username || '用')[0] }}</div>
        <div class="ke-p-info">
          <h2>{{ userInfo.username || '用户' }} <span class="ke-p-level">{{ userInfo.level || '青铜' }}</span></h2>
          <p>{{ userInfo.role==='LANDLORD'?'房东':(userInfo.role==='ADMIN'?'管理员':'租客') }} ·
            <span v-if="userInfo.phone">{{ userInfo.phone }}</span>
            <a v-else class="ke-p-bind" @click="showBind=true">⚠ 未绑定手机号 · 立即绑定</a>
            <span v-if="userInfo.email"> · {{ userInfo.email }}</span>
            <a v-else class="ke-p-bind" @click="showBind=true"> · 绑定邮箱</a>
            · 积分 {{ userInfo.points || 0 }}
          </p>
        </div>
        <button class="ke-p-logout" @click="logout">退出登录</button>
      </div>

      <p style="margin:20px 0"><RouterLink class="jade-link" to="/app/workflow">平台通知 · 房源审核进度 · 我的举报与内容 →</RouterLink></p>
      <!-- Tab -->
      <div class="ke-p-tabs">
        <button :class="{on:tab==='fav'}" :aria-pressed="tab==='fav'" @click="tab='fav'">我的收藏</button>
        <button :class="{on:tab==='house'}" :aria-pressed="tab==='house'" @click="tab='house'">我的房源</button>
        <button :class="{on:tab==='contract'}" :aria-pressed="tab==='contract'" @click="tab='contract'">合同审核</button>
        <button :class="{on:tab==='setting'}" :aria-pressed="tab==='setting'" @click="tab='setting'">账号设置</button>
      </div>

      <!-- 收藏列表 -->
      <div v-if="tab==='fav'">
        <div v-if="favs.length===0" class="ke-p-empty">暂无收藏，去首页逛逛吧</div>
        <div class="ke-p-list">
          <div v-for="h in favs" :key="h.id" class="ke-p-item" @click="$router.push('/detail/'+h.id)">
            <img v-if="h.coverImage" :src="h.coverImage" class="ke-p-img" />
            <div class="ke-p-item-info">
              <h4>{{ h.title }}</h4>
              <p>{{ h.city }}{{ h.district }} · {{ h.area }}㎡ · {{ h.bedrooms }}室{{ h.livingRooms }}厅</p>
              <p class="ke-p-price"><b>{{ h.price }}</b> 元/月</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 我的房源 -->
      <div v-if="tab==='house'">
        <div v-if="myHouses.length===0" class="ke-p-empty">
          <p>{{ userInfo.role==='LANDLORD'?'暂无发布的房源':'仅房东可发布房源' }}</p>
          <a class="ke-p-link" href="/app/publish-house" v-if="userInfo.role==='LANDLORD'">去发布</a>
        </div>
        <div class="ke-p-list">
          <div v-for="h in myHouses" :key="h.id" class="ke-p-item">
            <img v-if="h.coverImage" :src="h.coverImage" class="ke-p-img" />
            <div class="ke-p-item-info">
              <h4>{{ h.title }} <span :class="'ke-status-'+h.status">{{ h.status }}</span></h4>
              <p>{{ h.viewCount }}浏览 · ¥{{ h.price }}/月</p><RouterLink to="/app/workflow">查看审核意见、补交材料或下架 →</RouterLink>
            </div>
          </div>
        </div>
      </div>

      <!-- 合同审核 -->
      <div v-if="tab==='contract'">
        <div v-if="contracts.length===0" class="ke-p-empty">暂无合同审核记录</div>
        <div class="ke-p-list">
          <div v-for="c in contracts" :key="c.id" class="ke-p-item">
            <div class="ke-p-item-info">
              <h4>{{ c.title || c.fileName || '合同文件' }}</h4>
              <p>风险等级：<b :class="'risk-'+c.riskLevel">{{ c.overallRisk || c.riskLevel }}</b> · {{ c.createdAt }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 账号设置 -->
      <div v-if="tab==='setting'" class="ke-p-setting">
        <div class="ke-p-setting-row">
          <div class="ke-psr-label">用户ID</div>
          <div class="ke-psr-value">{{ userInfo.id }}</div>
        </div>
        <div class="ke-p-setting-row">
          <div class="ke-psr-label">用户名</div>
          <div class="ke-psr-value">{{ userInfo.username }}</div>
        </div>
        <div class="ke-p-setting-row">
          <div class="ke-psr-label">手机号</div>
          <div class="ke-psr-value">
            <span v-if="userInfo.phone">{{ userInfo.phone }}</span>
            <span v-else style="color:#f15b5b">未绑定</span>
            <button class="ke-psr-btn" @click="showBind=true">{{ userInfo.phone ? '更换' : '绑定' }}</button>
          </div>
        </div>
        <div class="ke-p-setting-row">
          <div class="ke-psr-label">邮箱</div>
          <div class="ke-psr-value">
            <span v-if="userInfo.email">{{ userInfo.email }}</span>
            <span v-else style="color:#999">未绑定</span>
            <button class="ke-psr-btn" @click="showEmailBind=true">{{ userInfo.email ? '更换' : '绑定' }}</button>
          </div>
        </div>
        <div class="ke-p-setting-row">
          <div class="ke-psr-label">会员等级</div>
          <div class="ke-psr-value">{{ userInfo.level || '青铜会员' }} · 积分 {{ userInfo.points || 0 }}</div>
        </div>
        <div class="ke-p-setting-row">
          <div class="ke-psr-label">注册时间</div>
          <div class="ke-psr-value">{{ userInfo.createdAt || '-' }}</div>
        </div>
      </div>
    </div>

    <!-- 绑定手机号弹窗 -->
    <div class="ke-p-modal" v-if="showBind" @click.self="showBind=false">
      <div class="ke-p-modal-inner">
        <button class="ke-p-modal-close" @click="showBind=false">✕</button>
        <h3>绑定手机号</h3>
        <p class="ke-p-modal-sub">手机号用于订单通知、密码找回等</p>
        <input v-model="bindPhone" placeholder="请输入11位手机号" class="ke-p-modal-input" />
        <SmsVerification :target="bindPhone" @target-change="bindCode=''"><input v-model="bindCode" aria-label="手机验证码" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="6位手机验证码" /></SmsVerification>
        <button class="ke-p-modal-btn" @click="doBindPhone" :disabled="!bindPhone || !bindCode || bindLoading">{{ bindLoading ? '绑定中...' : '确认绑定' }}</button>
        <p class="ke-p-modal-error" v-if="bindError">{{ bindError }}</p>
      </div>
    </div>

    <!-- 绑定邮箱弹窗 -->
    <div class="ke-p-modal" v-if="showEmailBind" @click.self="showEmailBind=false">
      <div class="ke-p-modal-inner">
        <button class="ke-p-modal-close" @click="showEmailBind=false">✕</button>
        <h3>绑定邮箱</h3>
        <p class="ke-p-modal-sub">用于接收合同、订单等通知</p>
        <input v-model="bindEmail" placeholder="请输入邮箱" class="ke-p-modal-input" />
        <button class="ke-p-modal-btn" @click="doBindEmail" :disabled="!bindEmail || bindEmailLoading">{{ bindEmailLoading ? '绑定中...' : '确认绑定' }}</button>
        <p class="ke-p-modal-error" v-if="bindEmailError">{{ bindEmailError }}</p>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, reactive, onMounted } from 'vue'
import PageHero from '../components/PageHero.vue'
import SmsVerification from '../components/SmsVerification.vue'
export default {
  name: 'KeProfile',
  components: { PageHero, SmsVerification },
  setup(){
    const tab = ref('fav')
    const userInfo = reactive({ id:'', username:'', role:'TENANT', phone:'', email:'', points:0, level:'青铜', avatar:'', realName:'', createdAt:'' })
    const favs = ref([])
    const myHouses = ref([])
    const contracts = ref([])

    // 绑定手机号
    const showBind = ref(false)
    const bindPhone = ref('')
    const bindCode = ref('')
    const bindError = ref('')
    const bindLoading = ref(false)
    const bindSending = ref(false)
    const bindCd = ref(0)

    // 绑定邮箱
    const showEmailBind = ref(false)
    const bindEmail = ref('')
    const bindEmailError = ref('')
    const bindEmailLoading = ref(false)

    const authHeaders = () => {
      const u = localStorage.getItem('user')
      if (u) { try { const d = JSON.parse(u); if (d.token) return { 'Authorization':'Bearer '+d.token, 'Content-Type':'application/json' } } catch(_){} }
      return { 'Content-Type':'application/json' }
    }
    const fetchMe = async () => {
      try {
        const r = await fetch('/api/user/me', { headers: authHeaders() }).then(r=>r.json())
        if(r.code===200 && r.data){
          Object.assign(userInfo, r.data)
          localStorage.setItem('username', r.data.username || '')
          localStorage.setItem('userRole', r.data.role || 'TENANT')
        }
      } catch(e){}
    }

    const doBindPhone = async () => {
      if(!/^1[3-9]\d{9}$/.test(bindPhone.value)){ bindError.value='手机号格式错误'; return }
      bindLoading.value=true; bindError.value=''
      try {
        const r = await fetch('/api/user/bind-phone',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({phone:bindPhone.value,code:bindCode.value})}).then(r=>r.json())
        if(r.code===200){
          userInfo.phone = bindPhone.value
          showBind.value = false
          bindPhone.value=''; bindCode.value=''
        } else { bindError.value = r.message || '绑定失败' }
      } catch(e){ bindError.value='网络错误' } finally { bindLoading.value=false }
    }

    const doBindEmail = async () => {
      bindEmailLoading.value=true; bindEmailError.value=''
      try {
        const r = await fetch('/api/user/bind-email',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:bindEmail.value})}).then(r=>r.json())
        if(r.code===200){
          userInfo.email = bindEmail.value
          showEmailBind.value = false
          bindEmail.value=''
        } else { bindEmailError.value = r.message || '绑定失败' }
      } catch(e){ bindEmailError.value='网络错误' } finally { bindEmailLoading.value=false }
    }

    const fetchAll = async () => {
      try {
        const h = authHeaders()
        const [fr, hr, cr] = await Promise.all([
          fetch('/api/favorite/list', { headers: h }).then(r=>r.json()),
          fetch('/api/house/list?status=ALL&landlordId=' + (userInfo.id || 0), { headers: h }).then(r=>r.json()),
          fetch('/api/contract/list', { headers: h }).then(r=>r.json())
        ])
        favs.value = fr.code===200 ? (fr.data||[]) : []
        myHouses.value = hr.code===200 ? (hr.data?.list||[]) : []
        contracts.value = cr.code===200 ? (cr.data||[]) : []
      } catch(e){}
    }
    const logout = () => {
      localStorage.clear(); location.href = '/'
    }
    onMounted(async ()=>{ await fetchMe(); await fetchAll() })
    return {
      tab, userInfo, favs, myHouses, contracts, logout,
      showBind, bindPhone, bindCode, bindError, bindLoading, bindSending, bindCd, doBindPhone,
      showEmailBind, bindEmail, bindEmailError, bindEmailLoading, doBindEmail
    }
  }
}
</script>

<style scoped>
.ke-profile { background:#e8eaed; min-height:100vh; font-family:-apple-system,BlinkMacSystemFont,'PingFang SC','Microsoft YaHei',sans-serif; }
.ke-p-inner { padding:24px; }
.ke-p-card { background:#fff; border-radius:8px; padding:24px; display:flex; align-items:center; gap:16px; margin-bottom:20px; box-shadow:0 1px 3px rgba(0,0,0,.04); }
.ke-p-ava { width:56px; height:56px; border-radius:50%; background:var(--jade-600); color:#fff; display:flex; align-items:center; justify-content:center; font-size:24px; font-weight:700; flex-shrink:0; }
.ke-p-info { flex:1; min-width:0; }
.ke-p-info h2 { font-size:18px; color:var(--jade-ink); margin:0 0 6px; display:flex; align-items:center; gap:8px; }
.ke-p-level { padding:1px 6px; background:#fff7e6; color:#fa8c16; font-size:11px; border-radius:3px; font-weight:500; }
.ke-p-info p { font-size:13px; color:#666; margin:0; line-height:1.6; }
.ke-p-bind { color:var(--jade-600); cursor:pointer; }
.ke-p-bind:hover { text-decoration:underline; }
.ke-p-logout { padding:8px 16px; border:1px solid #e0e0e0; border-radius:4px; background:#fff; color:#999; cursor:pointer; }
.ke-p-logout:hover { border-color:#f15b5b; color:#f15b5b; }

.ke-p-tabs { display:flex; gap:0; background:#fff; border-radius:8px; margin-bottom:20px; overflow:hidden; box-shadow:0 1px 3px rgba(0,0,0,.04); }
.ke-p-tabs button { flex:1; text-align:center; padding:14px; font-size:14px; color:#666; cursor:pointer; border-bottom:2px solid transparent; transition:.2s; }
.ke-p-tabs button:hover { color:var(--jade-600); }
.ke-p-tabs button.on { color:var(--jade-600); border-bottom-color:var(--jade-600); font-weight:500; }

.ke-p-empty { text-align:center; padding:60px 20px; color:#999; font-size:14px; background:#fff; border-radius:8px; }
.ke-p-link { display:inline-block; margin-top:12px; padding:8px 20px; background:var(--jade-600); color:#fff; border-radius:4px; font-size:13px; text-decoration:none; }
.ke-p-list { display:flex; flex-direction:column; gap:10px; }
.ke-p-item { display:flex; gap:14px; background:#fff; border-radius:8px; padding:14px; cursor:pointer; transition:.2s; }
.ke-p-item:hover { box-shadow:0 2px 8px rgba(0,0,0,.06); }
.ke-p-img { width:120px; height:90px; object-fit:cover; border-radius:4px; flex-shrink:0; background:var(--jade-paper); }
.ke-p-item-info { flex:1; min-width:0; }
.ke-p-item-info h4 { font-size:15px; color:var(--jade-ink); margin:0 0 4px; }
.ke-p-item-info p { font-size:12px; color:#999; margin:0 0 4px; }
.ke-p-price { font-size:12px; color:#999; }
.ke-p-price b { font-size:16px; color:#f15b5b; }
.ke-status-APPROVED { padding:1px 6px; background:#f6ffed; color:#52c41a; font-size:11px; border-radius:2px; }
.ke-status-PENDING { padding:1px 6px; background:#fff7e6; color:#fa8c16; font-size:11px; border-radius:2px; }
.risk-HIGH { color:#f15b5b; }
.risk-MEDIUM { color:#fa8c16; }
.risk-LOW { color:#52c41a; }

/* 账号设置 */
.ke-p-setting { background:#fff; border-radius:8px; padding:8px 24px; }
.ke-p-setting-row { display:flex; align-items:center; padding:18px 0; border-bottom:1px solid #f0f0f0; }
.ke-p-setting-row:last-child { border-bottom:none; }
.ke-psr-label { width:120px; color:#999; font-size:14px; }
.ke-psr-value { flex:1; color:var(--jade-ink); font-size:14px; display:flex; align-items:center; gap:12px; }
.ke-psr-btn { padding:4px 12px; background:#fff; border:1px solid var(--jade-600); color:var(--jade-600); border-radius:4px; cursor:pointer; font-size:12px; margin-left:auto; }
.ke-psr-btn:hover { background:var(--jade-pale); }

/* 绑定弹窗 */
.ke-p-modal { position:fixed; inset:0; background:rgba(0,0,0,.5); z-index:9999; display:flex; align-items:center; justify-content:center; }
.ke-p-modal-inner { background:#fff; border-radius:8px; padding:28px; width:380px; position:relative; }
.ke-p-modal-close { position:absolute; top:12px; right:16px; background:none; border:none; font-size:18px; color:#999; cursor:pointer; }
.ke-p-modal-inner h3 { font-size:18px; color:var(--jade-ink); margin:0 0 4px; }
.ke-p-modal-sub { font-size:13px; color:#999; margin:0 0 20px; }
.ke-p-modal-input { width:100%; padding:10px 12px; border:1px solid #e0e0e0; border-radius:4px; font-size:14px; outline:none; margin-bottom:12px; box-sizing:border-box; }
.ke-p-modal-input:focus { border-color:var(--jade-600); }
.ke-p-modal-code { display:flex; gap:8px; }
.ke-p-modal-send { padding:0 16px; background:var(--jade-600); color:#fff; border:none; border-radius:4px; font-size:13px; cursor:pointer; white-space:nowrap; }
.ke-p-modal-send:disabled { background:#ccc; cursor:default; }
.ke-p-modal-btn { width:100%; padding:12px; background:var(--jade-600); color:#fff; border:none; border-radius:4px; font-size:15px; font-weight:600; cursor:pointer; margin-top:8px; }
.ke-p-modal-btn:disabled { background:#ccc; cursor:default; }
.ke-p-modal-error { color:#f15b5b; font-size:13px; margin:8px 0 0; }
</style>
