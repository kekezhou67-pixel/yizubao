<template>
  <div class="community">
    <PageHero :title="isDetailView ? '攻略详情' : '租房攻略'" :subtitle="isDetailView ? '实战经验，让你的租房之旅更轻松' : '从看房到入住，分享认真生活的小经验。'" />
      <template v-if="isListView">
        <div class="page-content">
          <ContentComposer kind="guide"/><div class="cm-tabs">
            <button v-for="cat in categories" :key="cat.value" :class="['cm-tab', { active: activeCategory === cat.value }]" @click="switchCategory(cat.value)">{{ cat.label }}</button>
          </div>
          <div v-if="loading" class="cm-loading">加载中...</div>
          <div v-else-if="guides.length === 0" class="cm-empty">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="empty-icon"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
            <p>暂无攻略</p>
          </div>
          <div v-else class="cm-list">
            <div v-for="guide in guides" :key="guide.id" class="cm-card" role="link" tabindex="0" @keydown.enter="$event.currentTarget.click()" @click="$router.push('/app/guide-detail/'+guide.id)">
              <div class="cm-card-body">
                <div class="cm-card-top">
                  <span class="cm-cat-badge">{{ guide.category || '租房攻略' }}</span>
                  <h3 class="cm-card-title">{{ guide.title }}</h3>
                </div>
                <p class="cm-card-desc">{{ truncate(guide.content, 100) }}</p>
                <div class="cm-card-meta">
                  <span>{{ guide.authorName || '匿名' }}</span><span class="cm-dot">·</span>
                  <span>{{ guide.viewCount }} 阅读</span><span class="cm-dot">·</span>
                  <span>{{ guide.likeCount }} 点赞</span><span class="cm-dot">·</span>
                  <span>{{ guide.commentCount }} 评论</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>

      <template v-else-if="isDetailView">
        <div class="page-content" v-if="detail">
          <div class="dt-card">
            <span class="dt-cat">{{ detail.category || '租房攻略' }}</span>
            <h1 class="dt-title">{{ detail.title }}</h1>
            <div class="dt-meta"><span>{{ detail.authorName || '匿名' }} · {{ detail.viewCount }} 阅读</span></div>
            <div class="dt-body">{{ detail.content }}</div>
            <div class="dt-actions">
              <button class="dt-like-btn" @click="likeGuide"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3H14z"/></svg>{{ detail.likeCount }} 赞</button>
            </div>
          </div>
          <div class="dt-comments">
            <h3>评论 ({{ detail.commentCount }})</h3>
            <div class="cmt-input-row">
              <input v-model="commentText" placeholder="写下你的评论..." @keyup.enter="postComment" class="cmt-input" />
              <button class="cmt-btn" @click="postComment">发布</button>
            </div>
            <div v-if="comments.length" class="cmt-list">
              <div v-for="c in comments" :key="c.id" class="cmt-item">
                <div class="cmt-avatar">{{ (c.userName || '?')[0] }}</div>
                <div class="cmt-body"><span class="cmt-name">{{ c.userName || '匿名' }}</span><p class="cmt-text">{{ c.content }}</p></div>
              </div>
            </div>
          </div>
        </div>
      </template>

  </div>
</template>

<script>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { guideApi } from '../api/modules'
import ContentComposer from '../components/ContentComposer.vue'
import PageHero from '../components/PageHero.vue'

export default {
  name: 'Community',
  components: { PageHero, ContentComposer },
  setup() {
    const route = useRoute()
    const isListView = computed(() => route.name !== 'GuideDetail')
    const isDetailView = computed(() => route.name === 'GuideDetail')

    const categories = [
      { label: '全部', value: '' }, { label: '看房技巧', value: '看房技巧' },
      { label: '合同知识', value: '合同知识' }, { label: '搬家经验', value: '搬家经验' },
      { label: '区域推荐', value: '区域推荐' }
    ]
    const activeCategory = ref(''), loading = ref(false), guides = ref([])

    const switchCategory = (cat) => { activeCategory.value = cat; loadGuides() }
    const loadGuides = async () => {
      loading.value = true
      try {
        const params = { page: 1, size: 50 }
        if (activeCategory.value) params.category = activeCategory.value
        const r = await guideApi.list(params)
        guides.value = r.code === 200 ? (r.data?.list || r.data || []) : []
      } catch (e) { console.error('攻略列表加载失败:', e) } finally { loading.value = false }
    }

    const detail = ref(null), comments = ref([])
    const loadDetail = async () => {
      const id = route.params.id; if (!id) return
      try {
        const r = await guideApi.detail(Number(id))
        if (r.code === 200) detail.value = r.data
        const cr = await fetch('/api/guide/comments/' + id).then(r => r.json())
        if (cr.code === 200) comments.value = cr.data || []
      } catch (e) { console.error('攻略详情加载失败:', e) }
    }

    const commentText = ref('')
    const postComment = async () => {
      if (!commentText.value.trim() || !detail.value) return
      try {
        const r = await fetch('/api/guide/comment', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ guideId: detail.value.id, content: commentText.value }) }).then(r => r.json())
        if (r.code === 200) { comments.value.push({ id: Date.now(), userName: '我', content: commentText.value }); detail.value.commentCount++; commentText.value = '' }
      } catch (e) { console.error('评论失败:', e) }
    }
    const likeGuide = async () => {
      if (!detail.value) return
      try { const r = await guideApi.like(detail.value.id); if (r.code === 200) detail.value.likeCount++ } catch (e) { console.error('点赞失败:', e) }
    }
    const truncate = (t, n) => t ? (t.length > n ? t.slice(0, n) + '...' : t) : ''

    onMounted(() => { isDetailView.value ? loadDetail() : loadGuides() })
    watch(() => route.fullPath, () => { if (isDetailView.value) { detail.value = null; comments.value = []; loadDetail() } else loadGuides() })

    return { isListView, isDetailView, categories, activeCategory, loading, guides, switchCategory, detail, comments, commentText, postComment, likeGuide, truncate }
  }
}
</script>

<style scoped>
.community { min-height: 100vh; background: var(--jade-paper); font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif; }
.page-content { padding: 24px 32px; }
.cm-tabs { display: flex; gap: 8px; margin-bottom: 24px; flex-wrap: wrap; }
.cm-tab { padding: 8px 18px; border-radius: 20px; border: 1px solid #e0e0e0; background: #fff; color: #666; font-size: 13px; cursor: pointer; }
.cm-tab.active, .cm-tab:hover { background: var(--jade-ink); color: #fff; border-color: var(--jade-ink); }
.cm-loading, .cm-empty { text-align: center; padding: 80px 20px; color: #888; }
.empty-icon { width: 50px; height: 50px; margin-bottom: 12px; }
.cm-list { display: flex; flex-direction: column; gap: 12px; }
.cm-card { background: #fff; border-radius: 8px; padding: 22px 24px; cursor: pointer; border: 1px solid #f0f0f0; transition: box-shadow .2s; }
.cm-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,.06); }
.cm-card-top { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 8px; }
.cm-cat-badge { padding: 3px 10px; background: var(--jade-pale); color: var(--jade-600); border-radius: 4px; font-size: 12px; white-space: nowrap; }
.cm-card-title { margin: 0; font-size: 16px; font-weight: 600; color: var(--jade-ink); }
.cm-card-desc { margin: 0 0 12px; font-size: 13px; color: #666; line-height: 1.6; }
.cm-card-meta { font-size: 12px; color: #888; }
.cm-dot { margin: 0 6px; }
.dt-card { background: #fff; border-radius: 8px; padding: 28px; border: 1px solid #f0f0f0; margin-bottom: 16px; }
.dt-cat { display: inline-block; padding: 3px 10px; background: var(--jade-pale); color: var(--jade-600); border-radius: 4px; font-size: 12px; margin-bottom: 12px; }
.dt-title { font-size: 24px; font-weight: 700; color: var(--jade-ink); margin: 0 0 10px; }
.dt-meta { font-size: 13px; color: #777; margin-bottom: 20px; }
.dt-body { font-size: 15px; color: #444; line-height: 1.9; white-space: pre-wrap; padding-bottom: 20px; border-bottom: 1px solid #f0f0f0; }
.dt-actions { padding-top: 16px; }
.dt-like-btn { display: inline-flex; align-items: center; gap: 6px; padding: 8px 18px; border: 1px solid #e0e0e0; border-radius: 8px; background: #fff; color: #666; font-size: 13px; cursor: pointer; }
.dt-like-btn:hover { background: #fef2f2; color: #f15b5b; border-color: #fecaca; }
.dt-comments { background: #fff; border-radius: 8px; padding: 24px 28px; border: 1px solid #f0f0f0; }
.dt-comments h3 { margin: 0 0 16px; font-size: 15px; color: var(--jade-ink); }
.cmt-input-row { display: flex; gap: 8px; margin-bottom: 16px; }
.cmt-input { flex: 1; padding: 10px 14px; border: 1px solid #e0e0e0; border-radius: 8px; font-size: 13px; outline: none; }
.cmt-input:focus { border-color: var(--jade-600); }
.cmt-btn { padding: 10px 20px; border: none; border-radius: 8px; background: var(--jade-600); color: #fff; font-size: 13px; cursor: pointer; }
.cmt-list { display: flex; flex-direction: column; gap: 14px; }
.cmt-item { display: flex; gap: 10px; }
.cmt-avatar { width: 32px; height: 32px; border-radius: 50%; background: var(--jade-pale); color: var(--jade-600); display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 600; flex-shrink: 0; }
.cmt-name { font-size: 13px; font-weight: 600; color: var(--jade-ink); }
.cmt-text { margin: 4px 0 0; font-size: 13px; color: #666; line-height: 1.6; }
</style>
