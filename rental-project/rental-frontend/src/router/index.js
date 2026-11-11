import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../components/MainLayout.vue'

const routes = [
  // 大门户首页 — 全宽
  { path: '/', name: 'Home', component: () => import('../views/Home.vue') },
  // 全部房源列表 — 全宽
  { path: '/houses', name: 'HouseList', component: () => import('../views/HouseList.vue') },
  // 房源详情 — 全宽
  { path: '/detail/:id', name: 'HouseDetail', component: () => import('../views/HouseDetail.vue') },
  // 独立登录与注册页
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue'), meta:{ authPage:true } },
  { path: '/register', name: 'Register', component: () => import('../views/Login.vue'), meta:{ authPage:true } },
  // App 内页（侧边栏布局，管理功能用）
  {
    path: '/app',
    component: MainLayout,
    redirect: '/app/subway',
    children: [
      { path: 'home', redirect: '/' },
      { path: 'subway', name: 'SubwaySearch', component: () => import('../views/SubwaySearch.vue') },
      { path: 'tools', name: 'Tools', component: () => import('../views/Tools.vue'), beforeEnter:to=>to.query.tool==='contract'?{path:'/app/chat',query:{task:'contract'}}:true },
      { path: 'community', name: 'Community', component: () => import('../views/Community.vue') },
      { path: 'guide-detail/:id', name: 'GuideDetail', component: () => import('../views/Community.vue') },
      { path: 'chat', name: 'AIChat', component: () => import('../views/AIChat.vue') },
      { path: 'rag-qa', redirect:{path:'/app/chat',query:{task:'policy'}} },
      { path: 'kb-admin', name: 'KbAdmin', component: () => import('../views/KbAdmin.vue') },
      { path: 'image-search', redirect:{path:'/app/chat',query:{task:'image'}} },
      { path: 'publish-house', name: 'PublishHouse', component: () => import('../views/PublishHouse.vue') },
      { path: 'messages', name:'Messages', component:()=>import('../views/Messages.vue') },
      { path:'workflow',name:'Workflow',component:()=>import('../views/Workflow.vue') },
      { path: 'profile', name: 'Profile', component: () => import('../views/Profile.vue') },
      { path: 'membership', name: 'Membership', component: () => import('../views/Membership.vue') }
    ]
  },
  // 后台
  { path: '/admin', name: 'Admin', component: () => import('../views/Admin.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.path === from.path) return false
    return { top: 0 }
  }
})

// ===== 路由守卫 —— 需要登录的页面 =====
const authRequired = ['/app/workflow','/app/messages','/admin', '/app/publish-house', '/app/profile', '/app/membership', '/app/kb-admin']
router.beforeEach((to) => {
  // 登录与注册入口允许游客访问
  if (to.meta.authPage) return
  if (authRequired.includes(to.path)) {
    const user = localStorage.getItem('user')
    if (!user) { return { path:'/login', query:{ redirect:to.fullPath } } }
    try {
      const d = JSON.parse(user)
      if (!d.token) { return { path:'/login', query:{ redirect:to.fullPath } } }
      if (['/admin','/app/kb-admin'].includes(to.path) && d.role !== 'ADMIN') return '/'
    } catch (_) { return { path:'/login', query:{ redirect:to.fullPath } } }
  }
})

export default router
