<template>
  <div class="tools-page">
    <PageHero title="租房工具" subtitle="算清搬家的花费，看懂签字前的条款。" />
    <div class="page-content">
      <!-- 工具卡片网格 -->
      <div class="tool-cards">
        <div class="tool-card" :class="{ expanded: activeTool === 'calculator' }">
          <div class="tool-card-header" role="button" tabindex="0" @keydown.enter="$event.currentTarget.click()" @keydown.space.prevent="$event.currentTarget.click()" @click="activeTool = activeTool === 'calculator' ? null : 'calculator'">
            <div class="tool-icon-wrap">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="18" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
            </div>
            <div class="tool-info">
              <h3>费用计算器</h3>
              <p>房租、押金、中介费，一键算出总费用</p>
            </div>
            <svg class="tool-arrow" :class="{ open: activeTool === 'calculator' }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m6 9 6 6 6-6"/></svg>
          </div>
          <div v-if="activeTool === 'calculator'" class="tool-card-body">
            <div class="calc-grid">
              <div class="calc-field">
                <label>月租金</label>
                <div class="calc-input-wrap"><span class="prefix">¥</span><input v-model.number="calc.monthlyRent" type="number" min="0" placeholder="2000" /></div>
              </div>
              <div class="calc-field">
                <label>押金</label>
                <div class="calc-input-wrap"><span class="prefix">¥</span><input v-model.number="calc.deposit" type="number" min="0" placeholder="2000" /></div>
              </div>
              <div class="calc-field">
                <label>中介费</label>
                <div class="calc-input-wrap"><span class="prefix">¥</span><input v-model.number="calc.agentFee" type="number" min="0" placeholder="00" /></div>
              </div>
              <div class="calc-field">
                <label>搬家费</label>
                <div class="calc-input-wrap"><span class="prefix">¥</span><input v-model.number="calc.moveFee" type="number" min="0" placeholder="300" /></div>
              </div>
              <div class="calc-field">
                <label>其他费用</label>
                <div class="calc-input-wrap"><span class="prefix">¥</span><input v-model.number="calc.otherFee" type="number" min="0" placeholder="0" /></div>
              </div>
            </div>
            <div class="calc-total">
              <span>合计费用</span>
              <strong>¥{{ calcTotal.toLocaleString() }}</strong>
            </div>
          </div>
        </div>

        <div class="tool-card" :class="{ expanded: activeTool === 'contract' }">
          <div class="tool-card-header" role="button" tabindex="0" @keydown.enter="$event.currentTarget.click()" @keydown.space.prevent="$event.currentTarget.click()" @click="activeTool = activeTool === 'contract' ? null : 'contract'">
            <div class="tool-icon-wrap purple">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
            </div>
            <div class="tool-info">
              <h3>合同审查</h3>
              <p>AI 提取关键条款，自动标注风险点</p>
            </div>
            <svg class="tool-arrow" :class="{ open: activeTool === 'contract' }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m6 9 6 6 6-6"/></svg>
          </div>
          <div v-if="activeTool === 'contract'" class="tool-card-body">
            <textarea v-model="contract.text" rows="8" placeholder="粘贴合同文本内容，或描述合同条款..." class="ct-textarea"></textarea>
            <button class="ct-btn" :disabled="contract.loading || contract.imageLoading" @click="reviewContract">
              <svg v-if="contract.loading" class="spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
              {{ contract.loading ? 'AI 分析中...' : '提交审查' }}
            </button>

            <div v-if="contract.error" class="ct-error">{{ contract.error }}</div>

            <div v-if="!contract.result && !contract.error && !contract.loading && !contract.imageLoading" class="ct-hint">
              输入合同文本或上传合同照片，AI 将逐条审查条款并标注风险点
            </div>

            <div v-if="contract.result" class="ct-result">
              <div class="ct-result-top">
                <span class="ct-risk" :class="riskClass(contract.result.overallRisk)">总体风险：{{ riskLabel(contract.result.overallRisk) }}</span>
              </div>
              <pre v-if="contract.result.summary" class="ct-ai-text">{{ contract.result.summary }}</pre>
              <div class="ct-stats">共审查 {{ contract.result.clauseCount || 0 }} 条条款，发现 {{ (contract.result.riskPoints || []).length }} 处风险</div>

              <!-- 风险点卡片列表（兼容旧版字符串数组） -->
              <div v-if="contract.result.riskPoints && contract.result.riskPoints.length" class="ct-risk-items">
                <div v-for="(p, i) in contract.result.riskPoints" :key="i" class="risk-item">
                  <template v-if="typeof p === 'string'">
                    <div class="risk-item-head"><p class="risk-point-text">{{ p }}</p></div>
                  </template>
                  <template v-else>
                    <div class="risk-item-head">
                      <span v-if="p.severity" class="sev-badge" :class="riskClass(p.severity)">{{ p.severity }}</span>
                      <p class="risk-point-text">{{ p.point }}</p>
                    </div>
                    <div v-if="p.suggestion" class="risk-suggestion">💡 {{ p.suggestion }}</div>
                    <div v-if="p.law" class="risk-law">⚖️ {{ p.law }}</div>
                    <div v-if="p.source" class="risk-source">来源：{{ p.source }}</div>
                  </template>
                </div>
              </div>

              <!-- 条款明细（默认折叠） -->
              <div v-if="contract.result.keyClauses && contract.result.keyClauses.length" class="ct-fold">
                <div class="ct-fold-toggle" @click="showClauses = !showClauses">
                  <span class="fold-arrow" :class="{ open: showClauses }">▾</span>
                  {{ showClauses ? '收起条款明细' : '查看条款明细' }}（{{ contract.result.keyClauses.length }} 条）
                </div>
                <div v-if="showClauses" class="clause-table">
                  <div v-for="(c, i) in contract.result.keyClauses" :key="i" class="clause-row">
                    <div class="clause-line">
                      <span class="clause-name">{{ c.clause }}</span>
                      <span class="lv-badge" :class="levelClass(c.level)">{{ c.level || '正常' }}</span>
                    </div>
                    <p class="clause-content">{{ c.content }}</p>
                  </div>
                </div>
              </div>

              <!-- 知识库引用来源（默认折叠） -->
              <div v-if="contract.result.sources && contract.result.sources.length" class="ct-fold">
                <div class="ct-fold-toggle" @click="showSources = !showSources">
                  <span class="fold-arrow" :class="{ open: showSources }">▾</span>
                  引用了 {{ contract.result.sources.length }} 条知识库资料
                </div>
                <div v-if="showSources" class="source-list">
                  <div v-for="(s, i) in contract.result.sources" :key="i" class="source-item">
                    <div class="source-head">
                      <span class="source-title">📄 {{ s.docTitle || '知识库文档' }}</span>
                      <span v-if="s.score != null" class="source-score">{{ scorePct(s.score) }}</span>
                    </div>
                    <p class="source-text">{{ (s.text || '').slice(0, 100) }}</p>
                  </div>
                </div>
              </div>

              <!-- OCR 识别原文（图片审查时显示，默认折叠） -->
              <div v-if="contract.ocrText" class="ct-fold">
                <div class="ct-fold-toggle" @click="showOcr = !showOcr">
                  <span class="fold-arrow" :class="{ open: showOcr }">▾</span>
                  {{ showOcr ? '收起识别的原文' : '查看识别的原文' }}
                </div>
                <pre v-if="showOcr" class="ct-ocr-text">{{ contract.ocrText }}</pre>
              </div>
            </div>

            <!-- 📷 拍照审查合同 -->
            <div class="ct-divider"></div>
            <div class="ct-image-section">
              <h4 class="ct-section-title">📷 拍照审查合同</h4>
              <input id="ct-image-input" type="file" accept="image/*" class="ct-file-input" @change="onImagePick" />
              <label for="ct-image-input" class="ct-file-label">选择合同照片</label>
              <img v-if="contract.imagePreview" :src="contract.imagePreview" class="ct-preview" alt="合同照片预览" />
              <button class="ct-btn ct-btn-img" :disabled="!contract.imageFile || contract.imageLoading || contract.loading" @click="reviewImage">
                <svg v-if="contract.imageLoading" class="spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
                {{ contract.imageLoading ? 'OCR 识别 + 逐条审查中，约需 30-90 秒…' : '开始识别审查' }}
              </button>
            </div>
          </div>
        </div>

        <div class="tool-card" :class="{ expanded: activeTool === 'landlord' }">
          <div class="tool-card-header" role="button" tabindex="0" @keydown.enter="$event.currentTarget.click()" @keydown.space.prevent="$event.currentTarget.click()" @click="activeTool = activeTool === 'landlord' ? null : 'landlord'">
            <div class="tool-icon-wrap green">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
            </div>
            <div class="tool-info">
              <h3>房东评价</h3>
              <p>查看社区真实评价，避开黑心房东</p>
            </div>
            <svg class="tool-arrow" :class="{ open: activeTool === 'landlord' }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m6 9 6 6 6-6"/></svg>
          </div>
          <div v-if="activeTool === 'landlord'" class="tool-card-body">
            <div class="ld-search">
              <input v-model="landlord.id" placeholder="输入房东ID查询评价" @keyup.enter="searchLandlord" class="ld-input" />
              <button class="ld-btn" :disabled="landlord.loading" @click="searchLandlord">查询</button>
            </div>

            <div v-if="landlord.avgScore !== null" class="ld-result">
              <div class="ld-score-box">
                <div class="ld-score-num">{{ landlord.avgScore.toFixed(1) }}</div>
                <div class="ld-score-stars">
                  <svg v-for="s in 5" :key="s" :class="['ld-star', { on: s <= Math.round(landlord.avgScore) }]" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
                </div>
                <span class="ld-score-count">{{ landlord.reviews.length }} 条评价</span>
              </div>
              <div v-if="landlord.reviews.length" class="ld-reviews">
                <div v-for="r in landlord.reviews" :key="r.id" class="ld-review-item">
                  <div class="ld-review-top">
                    <div class="ld-review-stars">
                      <svg v-for="s in 5" :key="s" :class="['ld-s-star', { on: s <= r.score }]" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
                    </div>
                  </div>
                  <p class="ld-review-text">{{ r.content }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, reactive, computed, watch, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import PageHero from '../components/PageHero.vue'

export default {
  name: 'Tools',
  components: { PageHero },
  setup() {
    const route = useRoute()
    const activeTool = ref(['contract','calculator','landlord'].includes(route.query.tool) ? route.query.tool : 'contract')
    watch(() => route.query.tool, value => { if (['contract','calculator','landlord'].includes(value)) activeTool.value = value })

    const calc = reactive({ monthlyRent: null, deposit: null, agentFee: null, moveFee: null, otherFee: null })
    watch(() => calc.monthlyRent, (v) => { if (calc.deposit === null && v !== null) calc.deposit = v })
    const calcTotal = computed(() => (Number(calc.monthlyRent)||0)+(Number(calc.deposit)||0)+(Number(calc.agentFee)||0)+(Number(calc.moveFee)||0)+(Number(calc.otherFee)||0))

    const contract = reactive({ text: '', loading: false, result: null, history: [], error: '', ocrText: '', imageFile: null, imagePreview: '', imageLoading: false })
    const showClauses = ref(false)
    const showSources = ref(false)
    const showOcr = ref(false)

    // 风险等级 → 样式类名（兼容新版中文"高/中/低"与旧版英文 HIGH/MEDIUM/LOW）
    const riskClass = (v) => {
      const s = String(v || '')
      if (s === '高' || s.toUpperCase() === 'HIGH') return 'high'
      if (s === '中' || s.toUpperCase() === 'MEDIUM') return 'medium'
      return 'low'
    }
    const riskLabel = (v) => {
      const s = String(v || '')
      return { HIGH: '高', MEDIUM: '中', LOW: '低' }[s.toUpperCase()] || s || '低'
    }
    // 条款级别徽章：正常绿 / 注意橙 / 风险红
    const levelClass = (l) => (l === '风险' ? 'lv-danger' : l === '注意' ? 'lv-warn' : 'lv-ok')
    // 知识库引用相关度 → 百分比（score 可能是 0~1 或 0~100）
    const scorePct = (s) => (s == null ? '' : (Number(s) <= 1 ? Math.round(Number(s) * 100) : Math.round(Number(s))) + '%')

    const resetFolds = () => { showClauses.value = false; showSources.value = false; showOcr.value = false }

    const reviewContract = async () => {
      if (!contract.text.trim()) return
      contract.loading = true; contract.result = null; contract.error = ''; contract.ocrText = ''; resetFolds()
      try {
        const resp = await fetch('/api/contract/review', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({contentText:contract.text}) })
        if (!resp.ok) { contract.error = '服务异常(HTTP ' + resp.status + ')'; return }
        const r = await resp.json()
        if (r.code === 200) contract.result = r.data
        else contract.error = r.message || '合同审查失败，请稍后重试'
      } catch(e){ contract.error = (e && e.message) || '合同审查失败，请稍后重试' } finally { contract.loading=false }
    }

    const onImagePick = (e) => {
      const f = e.target.files && e.target.files[0]
      if (!f) return
      if (contract.imagePreview) URL.revokeObjectURL(contract.imagePreview)
      contract.imageFile = f
      contract.imagePreview = URL.createObjectURL(f)
    }

    // 组件卸载时释放预览用的 Blob URL，避免内存泄漏
    onUnmounted(() => {
      if (contract.imagePreview) URL.revokeObjectURL(contract.imagePreview)
    })

    const reviewImage = async () => {
      if (!contract.imageFile || contract.imageLoading) return
      contract.imageLoading = true; contract.result = null; contract.error = ''; contract.ocrText = ''; resetFolds()
      try {
        const fd = new FormData()
        fd.append('file', contract.imageFile)
        // 不手动设置 Content-Type，浏览器自动带 multipart boundary；main.js fetch 补丁注入 token
        const resp = await fetch('/api/contract/review-image', { method: 'POST', body: fd })
        if (!resp.ok) { contract.error = '服务异常(HTTP ' + resp.status + ')'; return }
        const r = await resp.json()
        if (r.code === 200 && r.data) {
          const d = r.data
          // 图片审查响应用 risks 字段（与 riskPoints 同构），统一为 riskPoints 复用同一套渲染
          contract.result = Object.assign({}, d, { riskPoints: d.riskPoints || d.risks || [] })
          contract.ocrText = d.ocrText || ''
        } else {
          contract.error = r.message || '图片审查失败，请稍后重试'
        }
      } catch (e) {
        contract.error = (e && e.message) || '图片审查失败，请稍后重试'
      } finally {
        contract.imageLoading = false
      }
    }

    const landlord = reactive({ id:'', loading: false, avgScore: null, reviews: [] })
    const searchLandlord = async () => {
      if (!landlord.id.trim()) return
      landlord.loading = true; landlord.avgScore = null; landlord.reviews = []
      try {
        const r = await fetch('/api/landlord-review/landlord/' + encodeURIComponent(landlord.id)).then(r => r.json())
        if (r.code === 200 && r.data) {
          landlord.avgScore = r.data.avgScore || 0
          landlord.reviews = r.data.reviews || []
        }
      } catch (e) {} finally { landlord.loading = false }
    }

    return { activeTool, calc, calcTotal, contract, reviewContract, onImagePick, reviewImage, showClauses, showSources, showOcr, riskClass, riskLabel, levelClass, scorePct, landlord, searchLandlord }
  }
}
</script>

<style scoped>
.tools-page { min-height: 100vh; background: var(--jade-paper); font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', sans-serif; }

.page-content { max-width: 1000px; margin: 0 auto; padding: 24px 24px; }

/* Tool Cards */
.tool-cards { display: flex; flex-direction: column; gap: 12px; }
.tool-card { background: #fff; border-radius: 14px; border: 1px solid #f0f0f0; overflow: hidden; transition: box-shadow 0.2s; }
.tool-card.expanded { box-shadow: 0 2px 12px rgba(0,0,0,0.06); }
.tool-card-header { display: flex; align-items: center; gap: 14px; padding: 18px 20px; cursor: pointer; }
.tool-card-header:hover { background: #fafafa; }
.tool-icon-wrap { width: 42px; height: 42px; border-radius: 12px; display: flex; align-items: center; justify-content: center; background: var(--jade-pale); color: var(--jade-600); flex-shrink: 0; }
.tool-icon-wrap svg { width: 20px; height: 20px; }
.tool-icon-wrap.purple { background: var(--jade-pale); color: var(--jade-700); }
.tool-icon-wrap.green { background: #ecfdf5; color: #059669; }
.tool-info { flex: 1; }
.tool-info h3 { margin: 0; font-size: 15px; font-weight: 600; color: var(--jade-ink); }
.tool-info p { margin: 2px 0 0; font-size: 12px; color: #9ca3af; }
.tool-arrow { width: 20px; height: 20px; color: #d1d5db; transition: transform 0.2s; flex-shrink: 0; }
.tool-arrow.open { transform: rotate(180deg); color: var(--jade-600); }

.tool-card-body { padding: 0 20px 20px; }

/* Calculator */
.calc-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; margin-bottom: 18px; }
.calc-field label { display: block; font-size: 12px; color: #9ca3af; margin-bottom: 4px; }
.calc-input-wrap { display: flex; align-items: center; border: 1px solid #e5e7eb; border-radius: 8px; overflow: hidden; background: #fafafa; }
.calc-input-wrap .prefix { padding: 0 10px; font-size: 14px; color: #9ca3af; background: #f3f4f6; height: 38px; display: flex; align-items: center; }
.calc-input-wrap input { flex: 1; border: none; padding: 8px 12px; font-size: 15px; color: var(--jade-ink); outline: none; background: #fff; width: 100%; }
.calc-input-wrap:focus-within { border-color: var(--jade-600); }
.calc-total { display: flex; align-items: center; justify-content: space-between; padding: 14px 18px; background: linear-gradient(135deg, var(--jade-pale), #dceade); border-radius: 10px; }
.calc-total span { font-size: 14px; color: #4b5563; }
.calc-total strong { font-size: 26px; color: var(--jade-600); }

/* Contract */
.ct-textarea { width: 100%; padding: 12px; border: 1px solid #e5e7eb; border-radius: 10px; font-size: 13px; color: var(--jade-ink); outline: none; resize: vertical; box-sizing: border-box; font-family: inherit; background: #fafafa; margin-bottom: 12px; }
.ct-textarea:focus { border-color: var(--jade-600); background: #fff; }
.ct-btn { display: flex; align-items: center; gap: 6px; justify-content: center; width: 100%; padding: 11px; border: none; border-radius: 10px; background: var(--jade-600); color: #fff; font-size: 14px; font-weight: 600; cursor: pointer; }
.ct-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.ct-btn .spin { width: 16px; height: 16px; animation: spin 2s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.ct-result { margin-top: 16px; padding: 16px; background: var(--jade-paper); border-radius: 10px; }
.ct-risk { display: inline-block; padding: 4px 12px; border-radius: 20px; font-size: 13px; font-weight: 600; }
.ct-risk.high { background: #fef2f2; color: #ef4444; }
.ct-risk.medium { background: #fffbeb; color: #f59e0b; }
.ct-risk.low { background: #f0fdf4; color: #10b981; }
.ct-ai-text { white-space: pre-wrap; font-size: 13px; color: #4b5563; line-height: 1.7; margin: 12px 0; font-family: inherit; }
.ct-stats { margin: 4px 0 0; font-size: 13px; color: #666; }

/* 风险点卡片 */
.ct-risk-items { display: flex; flex-direction: column; gap: 10px; margin-top: 14px; }
.risk-item { background: #fff; border: 1px solid #f0f0f0; border-radius: 10px; padding: 12px 14px; }
.risk-item-head { display: flex; align-items: flex-start; gap: 8px; }
.sev-badge { flex-shrink: 0; padding: 2px 10px; border-radius: 20px; font-size: 12px; font-weight: 600; margin-top: 1px; }
.sev-badge.high { background: #fef2f2; color: #ef4444; }
.sev-badge.medium { background: #fffbeb; color: #f59e0b; }
.sev-badge.low { background: #f0fdf4; color: #10b981; }
.risk-point-text { margin: 0; font-size: 13px; color: var(--jade-ink); line-height: 1.7; font-weight: 500; }
.risk-suggestion { margin-top: 8px; font-size: 13px; color: #4b5563; line-height: 1.6; }
.risk-law { margin-top: 8px; padding: 8px 12px; background: var(--jade-pale); border-radius: 8px; font-size: 12px; color: #6d28d9; line-height: 1.6; }
.risk-source { margin-top: 6px; font-size: 12px; color: #999; }

/* 折叠区 */
.ct-fold { margin-top: 14px; }
.ct-fold-toggle { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 500; color: var(--jade-600); cursor: pointer; user-select: none; }
.ct-fold-toggle:hover { color: var(--jade-700); }
.fold-arrow { display: inline-block; font-size: 12px; transition: transform 0.2s; transform: rotate(-90deg); }
.fold-arrow.open { transform: rotate(0deg); }

/* 条款明细 */
.clause-table { margin-top: 10px; display: flex; flex-direction: column; gap: 8px; }
.clause-row { background: #fff; border: 1px solid #f0f0f0; border-radius: 8px; padding: 10px 12px; }
.clause-line { display: flex; align-items: center; gap: 8px; }
.clause-name { font-size: 13px; font-weight: 600; color: var(--jade-ink); }
.lv-badge { padding: 1px 8px; border-radius: 20px; font-size: 11px; font-weight: 600; }
.lv-badge.lv-ok { background: #f0fdf4; color: #10b981; }
.lv-badge.lv-warn { background: #fffbeb; color: #f59e0b; }
.lv-badge.lv-danger { background: #fef2f2; color: #ef4444; }
.clause-content { margin: 6px 0 0; font-size: 12px; color: #666; line-height: 1.6; }

/* 知识库来源 */
.source-list { margin-top: 10px; display: flex; flex-direction: column; gap: 8px; }
.source-item { background: #fff; border: 1px solid #f0f0f0; border-radius: 8px; padding: 10px 12px; }
.source-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.source-title { font-size: 13px; font-weight: 600; color: var(--jade-ink); }
.source-score { flex-shrink: 0; font-size: 12px; font-weight: 600; color: var(--jade-600); }
.source-text { margin: 6px 0 0; font-size: 12px; color: #999; line-height: 1.6; }

/* OCR 原文 */
.ct-ocr-text { white-space: pre-wrap; font-size: 12px; color: #4b5563; line-height: 1.7; margin: 10px 0 0; font-family: inherit; background: #fff; border: 1px solid #f0f0f0; border-radius: 8px; padding: 10px 12px; max-height: 300px; overflow-y: auto; }

/* 错误 / 空态提示 */
.ct-error { margin-top: 12px; padding: 10px 14px; background: #fef2f2; border: 1px solid #fecaca; border-radius: 10px; color: #ef4444; font-size: 13px; line-height: 1.6; }
.ct-hint { margin-top: 12px; padding: 14px; border: 1px dashed #e5e7eb; border-radius: 10px; background: #fafafa; color: #999; font-size: 13px; text-align: center; }

/* 拍照审查 */
.ct-divider { margin: 20px 0 16px; border-top: 1px dashed #e5e7eb; }
.ct-section-title { margin: 0 0 12px; font-size: 14px; font-weight: 600; color: var(--jade-ink); }
.ct-file-input { display: none; }
.ct-file-label { display: inline-block; padding: 9px 18px; border: 1px solid var(--jade-600); border-radius: 10px; background: var(--jade-pale); color: var(--jade-600); font-size: 13px; font-weight: 500; cursor: pointer; }
.ct-file-label:hover { background: #dceade; }
.ct-preview { display: block; margin-top: 12px; width: 120px; height: auto; max-height: 160px; object-fit: cover; border-radius: 10px; border: 1px solid #f0f0f0; }
.ct-btn-img { margin-top: 12px; font-size: 13px; }

/* Landlord */
.ld-search { display: flex; gap: 8px; }
.ld-input { flex: 1; padding: 9px 12px; border: 1px solid #e5e7eb; border-radius: 8px; font-size: 14px; outline: none; background: #fafafa; color: var(--jade-ink); }
.ld-input:focus { border-color: var(--jade-600); background: #fff; }
.ld-btn { padding: 9px 20px; border: none; border-radius: 8px; background: var(--jade-600); color: #fff; font-size: 14px; cursor: pointer; }
.ld-btn:disabled { opacity: 0.6; }

.ld-result { margin-top: 16px; }
.ld-score-box { display: flex; align-items: center; gap: 10px; padding: 14px; background: #fffbeb; border-radius: 10px; margin-bottom: 14px; }
.ld-score-num { font-size: 32px; font-weight: 700; color: var(--jade-ink); }
.ld-score-stars { display: flex; gap: 2px; }
.ld-star { width: 18px; height: 18px; color: #e5e7eb; }
.ld-star.on { color: #f59e0b; fill: #f59e0b; }
.ld-score-count { font-size: 13px; color: #9ca3af; }
.ld-reviews { display: flex; flex-direction: column; gap: 10px; }
.ld-review-item { padding: 14px; border: 1px solid #f0f0f0; border-radius: 10px; }
.ld-review-top { margin-bottom: 6px; }
.ld-s-star { width: 13px; height: 13px; color: #e5e7eb; }
.ld-s-star.on { color: #f59e0b; fill: #f59e0b; }
.ld-review-text { margin: 0; font-size: 13px; color: #4b5563; line-height: 1.6; }

@media (max-width: 640px) {
  .calc-grid { grid-template-columns: 1fr 1fr; }
}
</style>

