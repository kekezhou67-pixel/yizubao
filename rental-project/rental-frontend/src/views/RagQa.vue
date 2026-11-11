<template>
  <div class="ragqa-page">
    <PageHero title="政策问答" subtitle="押金、维修、退租……把疑问说清楚，把依据看明白。" />

    <div class="page-content">
      <!-- 提问卡片 -->
      <div class="card ask-card">
        <textarea
          v-model="question"
          class="ask-textarea" aria-label="你的租房政策问题"
          rows="2"
          placeholder="请输入租房政策相关问题，Ctrl + Enter 快速发送…"
          :disabled="loading"
          @keydown.ctrl.enter="ask"
          @keydown.meta.enter="ask"
        ></textarea>
        <div class="ask-actions">
          <span class="ask-tip">支持 Ctrl + Enter 发送</span>
          <button class="ask-btn" :disabled="loading || !question.trim()" @click="ask">
            <svg v-if="loading" class="spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
            {{ loading ? '正在查找相关依据…' : '提问' }}
          </button>
        </div>

        <!-- 预置问题 chips -->
        <div class="chips">
          <button type="button" :disabled="loading"
            v-for="q in presetQuestions"
            :key="q"
            class="chip"
            :class="{ disabled: loading }"
            @click="askPreset(q)"
          >{{ q }}</button>
        </div>
      </div>

      <div v-if="loading" class="answer-pending" role="status"><span>正在为你查找相关依据，整理回答……</span><div></div><div></div><div></div></div>
      <!-- 回答区（最新一条） -->
      <template v-if="latest">
        <div v-if="latest.error" class="error-bar">⚠ {{ latest.error }}</div>

        <div v-if="latest.answer" class="card answer-card">
          <div class="answer-head">
            <span class="answer-badge">AI 回答</span>
            <span class="answer-question">{{ latest.question }}</span>
          </div>

          <div v-if="latest.noKnowledge" class="notice-bar">
            现有资料还不足以回答这个问题。
          </div>

          <div class="answer-text" v-html="latest.answerHtml"></div>

          <!-- 引用来源 -->
          <div v-if="latest.sources.length" class="sources">
            <div class="sources-title">📚 引用来源（{{ latest.sources.length }}）</div>
            <div v-for="(s, i) in latest.sources" :key="i" class="source-card">
              <div class="source-head">
                <span class="doc-badge">📄 {{ s.docTitle }}</span>
                <div class="score-wrap">
                  <div class="score-bar"><div class="score-bar-inner" :style="{ width: scorePct(s.score) }"></div></div>
                  <span class="score-num">相关度 {{ scorePct(s.score) }}</span>
                </div>
              </div>
              <p class="source-text" :class="{ clamped: !expandedSet.has('n' + i) }">{{ s.text }}</p>
              <button class="expand-btn" @click="toggleExpand('n' + i)">
                {{ expandedSet.has('n' + i) ? '收起' : '展开' }}
              </button>
            </div>
          </div>
        </div>
      </template>

      <!-- 历史问答（本次会话） -->
      <div v-if="history.length > 1" class="card history-card">
        <div class="history-title">历史问答（本次会话）</div>
        <div v-for="item in history.slice(1)" :key="item.id" class="history-item">
          <div class="history-q" @click="toggleExpand('h' + item.id)">
            <span class="history-q-icon">💬</span>
            <span class="history-q-text">{{ item.question }}</span>
            <svg class="history-arrow" :class="{ open: expandedSet.has('h' + item.id) }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m6 9 6 6 6-6"/></svg>
          </div>
          <div v-if="expandedSet.has('h' + item.id)" class="history-body">
            <div v-if="item.error" class="error-bar">⚠ {{ item.error }}</div>
            <div v-if="item.answer" class="history-answer">
              <div v-if="item.noKnowledge" class="notice-bar">现有资料还不足以回答这个问题。</div>
              <div class="answer-text" v-html="item.answerHtml"></div>
              <div v-if="item.sources.length" class="history-sources">
                <div v-for="(s, i) in item.sources" :key="i" class="history-source">
                  <span class="doc-badge small">📄 {{ s.docTitle }}</span>
                  <span class="score-num">相关度 {{ scorePct(s.score) }}</span>
                  <p class="source-text">{{ s.text }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 空态 -->
      <div v-if="!latest && !loading" class="card empty-card">
        <div class="empty-icon">⚖️</div>
        <p>还没有提问，输入问题或点击上方预置问题开始咨询</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import request from '../api/index'
import PageHero from '../components/PageHero.vue'

const presetQuestions = [
  '租房押金一般收几个月',
  '房东不修水管怎么办',
  '提前退租押金能退吗',
  '租着租着房东把房子卖了怎么办',
  '转租需要什么条件'
]

const question = ref('')
const loading = ref(false)
const history = ref([]) // 新问答追加到列表顶部
const expandedSet = reactive(new Set()) // 展开状态 key 集合
let seq = 0

const latest = computed(() => history.value[0] || null)

const escapeHtml = (str) => String(str)
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;')

// 先 HTML 转义，再把【资料N】高亮成 span.cite
const renderAnswer = (answer) =>
  escapeHtml(answer).replace(/【资料(\d+)】/g, '<span class="cite">【资料$1】</span>')

const scorePct = (score) => Math.max(0, Math.min(100, (Number(score) || 0) * 100)).toFixed(1) + '%'

const toggleExpand = (key) => {
  if (expandedSet.has(key)) expandedSet.delete(key)
  else expandedSet.add(key)
}

const ask = async () => {
  const q = question.value.trim()
  if (!q || loading.value) return
  loading.value = true
  const record = reactive({
    id: ++seq,
    question: q,
    answer: '',
    answerHtml: '',
    sources: [],
    noKnowledge: false,
    error: ''
  })
  history.value.unshift(record)
  question.value = ''
  try {
    const res = await request.post('/ai/qa', { question: q }, { timeout: 60000 })
    // 响应拦截器成功时直接返回整个 body {code,message,data}
    const data = res.data || {}
    const answer = data.answer || ''
    const sources = Array.isArray(data.sources) ? data.sources : []
    record.answer = answer
    record.answerHtml = renderAnswer(answer)
    record.sources = sources
    record.noKnowledge = sources.length === 0 && answer.includes('资料中没有')
  } catch (e) {
    record.error = e.message || '请求失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const askPreset = (q) => {
  if (loading.value) return
  question.value = q
  ask()
}
</script>

<style scoped>
.ragqa-page { min-height: 100vh; background: var(--jade-paper); font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', sans-serif; }
.page-content { max-width: 1000px; margin: 0 auto; padding: 24px 16px; }

.card { background: #fff; border-radius: 14px; border: 1px solid #f0f0f0; padding: 20px; margin-bottom: 16px; }

/* 提问卡片 */
.ask-textarea { width: 100%; box-sizing: border-box; padding: 12px; border: 1px solid #e5e7eb; border-radius: 10px; font-size: 14px; color: var(--jade-ink); outline: none; resize: vertical; font-family: inherit; background: #fafafa; line-height: 1.6; }
.ask-textarea:focus { border-color: var(--jade-600); background: #fff; }
.ask-textarea:disabled { opacity: 0.6; cursor: not-allowed; }
.ask-actions { display: flex; align-items: center; justify-content: space-between; margin-top: 12px; gap: 12px; }
.ask-tip { font-size: 12px; color: #999; }
.ask-btn { display: inline-flex; align-items: center; gap: 6px; justify-content: center; background: var(--jade-600); color: #fff; border: none; border-radius: 10px; padding: 10px 20px; font-size: 14px; font-weight: 600; cursor: pointer; transition: background 0.2s; }
.ask-btn:hover:not(:disabled) { background: var(--jade-700); }
.ask-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.ask-btn .spin { width: 16px; height: 16px; animation: spin 2s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* 预置问题 chips */
.chips { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }
.chip { padding: 6px 14px; border-radius: 20px; background: var(--jade-pale); color: var(--jade-600); font-size: 13px; cursor: pointer; border: 1px solid transparent; transition: all 0.2s; user-select: none; }
.chip:hover { border-color: var(--jade-600); background: #dceade; }
.chip.disabled { opacity: 0.5; cursor: not-allowed; }

/* 错误条 */
.error-bar { background: #fef2f2; color: #ef4444; border: 1px solid #fecaca; border-radius: 10px; padding: 10px 14px; font-size: 13px; margin-bottom: 16px; line-height: 1.6; }

/* 提示条（诚实拒答） */
.notice-bar { background: #fffbeb; color: #f59e0b; border: 1px solid #fde68a; border-radius: 10px; padding: 10px 14px; font-size: 13px; margin-bottom: 14px; line-height: 1.6; }

/* 回答卡片 */
.answer-head { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.answer-badge { display: inline-block; padding: 3px 10px; border-radius: 20px; background: var(--jade-pale); color: var(--jade-600); font-size: 12px; font-weight: 600; flex-shrink: 0; }
.answer-question { font-size: 14px; color: #666; }
.answer-text { white-space: pre-wrap; font-size: 14px; color: var(--jade-ink); line-height: 1.8; word-break: break-word; }
.answer-text :deep(.cite) { display: inline-block; padding: 0 6px; margin: 0 2px; border-radius: 6px; background: var(--jade-pale); color: var(--jade-600); font-size: 12px; font-weight: 600; line-height: 20px; vertical-align: baseline; }

/* 引用来源 */
.sources { margin-top: 18px; border-top: 1px solid #f0f0f0; padding-top: 16px; }
.sources-title { font-size: 14px; font-weight: 600; color: var(--jade-ink); margin-bottom: 12px; }
.source-card { border: 1px solid #f0f0f0; border-radius: 12px; padding: 14px 16px; margin-bottom: 10px; background: #fafafa; }
.source-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 10px; }
.doc-badge { display: inline-block; padding: 3px 10px; border-radius: 8px; background: var(--jade-pale); color: var(--jade-700); font-size: 12px; font-weight: 600; }
.doc-badge.small { font-size: 11px; padding: 2px 8px; }
.score-wrap { display: flex; align-items: center; gap: 8px; }
.score-bar { width: 80px; height: 6px; border-radius: 3px; background: #e5e7eb; overflow: hidden; }
.score-bar-inner { height: 100%; border-radius: 3px; background: #10b981; }
.score-num { font-size: 12px; color: #666; white-space: nowrap; }
.source-text { margin: 0; font-size: 13px; color: #666; line-height: 1.7; word-break: break-word; }
.source-text.clamped { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 3; line-clamp: 3; overflow: hidden; }
.expand-btn { margin-top: 8px; background: none; border: none; color: var(--jade-600); font-size: 12px; cursor: pointer; padding: 2px 0; }
.expand-btn:hover { color: var(--jade-700); text-decoration: underline; }

/* 历史问答 */
.history-title { font-size: 15px; font-weight: 600; color: var(--jade-ink); margin-bottom: 12px; }
.history-item { border: 1px solid #f0f0f0; border-radius: 10px; margin-bottom: 8px; overflow: hidden; }
.history-q { display: flex; align-items: center; gap: 8px; padding: 12px 14px; cursor: pointer; }
.history-q:hover { background: #fafafa; }
.history-q-icon { flex-shrink: 0; font-size: 14px; }
.history-q-text { flex: 1; font-size: 13px; color: var(--jade-ink); font-weight: 500; }
.history-arrow { width: 16px; height: 16px; color: #d1d5db; transition: transform 0.2s; flex-shrink: 0; }
.history-arrow.open { transform: rotate(180deg); color: var(--jade-600); }
.history-body { padding: 0 14px 14px; border-top: 1px solid var(--jade-paper); }
.history-answer { padding-top: 12px; }
.history-answer .answer-text { font-size: 13px; color: #4b5563; }
.history-sources { margin-top: 12px; display: flex; flex-direction: column; gap: 8px; }
.history-source { background: #fafafa; border: 1px solid #f0f0f0; border-radius: 10px; padding: 10px 12px; }
.history-source .score-num { margin-left: 8px; }
.history-source .source-text { margin-top: 6px; font-size: 12px; }

/* 空态 */
.empty-card { text-align: center; padding: 48px 20px; }
.empty-icon { font-size: 40px; margin-bottom: 12px; }
.empty-card p { margin: 0; font-size: 14px; color: #999; }

@media (max-width: 640px) {
  .ask-actions { flex-direction: column-reverse; align-items: stretch; }
  .ask-btn { width: 100%; }
  .ask-tip { text-align: center; }
}
</style>
