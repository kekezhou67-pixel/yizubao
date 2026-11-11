<template>
  <div class="ke-vip">
    <PageHero title="会员中心" subtitle="为不同的租住需要，选择适合自己的服务。" />
    <div class="ke-vip-inner">

      <div class="ke-vip-grid">
        <!-- 免费 -->
        <div class="ke-vip-card">
          <h3>免费版</h3>
          <div class="ke-vip-price"><b>¥0</b>/月</div>
          <ul>
            <li>发布 2 套房源</li>
            <li>基础排序</li>
            <li>浏览/搜索不限</li>
            <li>AI咨询 5次/天</li>
          </ul>
          <button class="ke-vip-btn current">当前方案</button>
        </div>
        <!-- 专业 -->
        <div class="ke-vip-card ke-vip-hot">
          <div class="ke-vip-badge">专业方案</div>
          <h3>专业版</h3>
          <div class="ke-vip-price"><b>¥29</b>/月</div>
          <ul>
            <li>发布 20 套房源</li>
            <li>优先展示</li>
            <li>认证房东标识</li>
            <li>数据看板</li>
            <li>AI写房源描述</li>
            <li>直拨电话</li>
          </ul>
          <button class="ke-vip-btn upgrade" @click="upgrade('pro')">立即升级</button>
        </div>
        <!-- 企业 -->
        <div class="ke-vip-card">
          <h3>企业版</h3>
          <div class="ke-vip-price"><b>¥99</b>/月</div>
          <ul>
            <li>不限房源发布</li>
            <li>置顶推荐</li>
            <li>品牌房东标识</li>
            <li>全维度分析报告</li>
            <li>AI写+优化+多语言</li>
            <li>专属客服热线</li>
          </ul>
          <button class="ke-vip-btn upgrade" @click="upgrade('enterprise')">立即升级</button>
        </div>
      </div>

      <div class="ke-vip-note">
        <h3>租客会员 · ¥9.9/月</h3>
        <div class="ke-vip-tenant">
          <span>不限收藏</span><span>不限AI咨询</span><span>10套合同模板</span><span>看房日程管理</span><span>搬家9折优惠</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import PageHero from '../components/PageHero.vue'
export default {
  name: 'KeVip',
  components: { PageHero },
  setup(){
    const upgrade = (plan) => { alert(`已选择 ${plan==='pro'?'专业版':'企业版'}，支付功能开发中...`) }
    return { upgrade }
  }
}
</script>

<style scoped>
.ke-vip { background:var(--jade-paper); min-height:100vh; font-family:-apple-system,BlinkMacSystemFont,'PingFang SC','Microsoft YaHei',sans-serif; }
.ke-vip-inner {   padding:40px 24px; text-align:center; }
.ke-vip-inner h1 { font-size:28px; font-weight:700; color:var(--jade-ink); margin:0 0 8px; }
.ke-vip-sub { font-size:14px; color:#999; margin-bottom:40px; }
.ke-vip-grid { display:grid; grid-template-columns:repeat(3,1fr); gap:20px; margin-bottom:40px; }
.ke-vip-card { background:#fff; border:1px solid #f0f0f0; border-radius:12px; padding:28px 20px; text-align:center; position:relative; }
.ke-vip-hot { border-color:#f15b5b; box-shadow:0 4px 20px rgba(241,91,91,.15); transform:scale(1.03); }
.ke-vip-badge { position:absolute; top:-12px; left:50%; transform:translateX(-50%); padding:4px 16px; background:#f15b5b; color:#fff; border-radius:12px; font-size:12px; }
.ke-vip-card h3 { font-size:18px; color:var(--jade-ink); margin:0 0 12px; }
.ke-vip-price { font-size:14px; color:#999; margin-bottom:16px; }
.ke-vip-price b { font-size:36px; color:#f15b5b; font-weight:700; }
.ke-vip-card ul { list-style:none; padding:0; text-align:left; margin:0 0 20px; }
.ke-vip-card li { padding:6px 0; font-size:13px; color:#666; border-bottom:1px solid var(--jade-paper); }
.ke-vip-card li:before { content:'✓ '; color:#52c41a; }
.ke-vip-btn { width:100%; padding:10px; border-radius:6px; font-size:14px; font-weight:500; cursor:pointer; border:none; }
.ke-vip-btn.current { background:var(--jade-paper); color:#999; cursor:default; }
.ke-vip-btn.upgrade { background:#f15b5b; color:#fff; }
.ke-vip-btn.upgrade:hover { background:#e04a4a; }
.ke-vip-note { background:#fff; border-radius:12px; padding:24px; text-align:left; }
.ke-vip-note h3 { font-size:16px; color:var(--jade-ink); margin:0 0 12px; }
.ke-vip-tenant { display:flex; gap:12px; flex-wrap:wrap; }
.ke-vip-tenant span { padding:6px 14px; background:var(--jade-pale); color:var(--jade-600); border-radius:20px; font-size:13px; }
</style>
