<template>
  <div class="kb-admin-page">
    <PageHero title="知识库管理" subtitle="RAG 知识库：文档入库、分片向量化、混合检索调试与 Token 用量监控" />
    <div class="page-content">
      <!-- 非管理员：整页只显示提示卡片 -->
      <div v-if="!isAdmin" class="card deny-card">
        <div class="deny-icon">⛔</div>
        <h2>仅管理员可访问</h2>
        <p>知识库管理功能仅限管理员使用，请切换管理员账号登录。</p>
      </div>

      <template v-else>
        

        <!-- Tab 切换 -->
        <div class="tab-bar">
          <button
            v-for="t in tabs"
            :key="t.key"
            class="tab-btn"
            :class="{ active: activeTab === t.key }"
            @click="switchTab(t.key)"
          >{{ t.label }}</button>
        </div>

        <!-- ============ Tab1 概览 ============ -->
        <template v-if="activeTab === 'overview'">
          <div class="card">
            <div class="card-head">
              <h3>Token 用量看板</h3>
              <button class="btn-ghost" :disabled="statsLoading" @click="loadStats">
                {{ statsLoading ? '加载中…' : '刷新' }}
              </button>
            </div>
            <div v-if="statsLoading" class="state-tip">加载中…</div>
            <div v-else-if="statsError" class="state-tip error">{{ statsError }}</div>
            <template v-else-if="stats">
              <div class="stat-grid">
                <div class="stat-card">
                  <div class="stat-label">总调用次数</div>
                  <div class="stat-value">{{ fmtNum(stats.calls) }}</div>
                </div>
                <div class="stat-card">
                  <div class="stat-label">输入 Tokens</div>
                  <div class="stat-value">{{ fmtNum(stats.inputTokens) }}</div>
                </div>
                <div class="stat-card">
                  <div class="stat-label">输出 Tokens</div>
                  <div class="stat-value">{{ fmtNum(stats.outputTokens) }}</div>
                </div>
                <div class="stat-card highlight">
                  <div class="stat-label">合计 Tokens</div>
                  <div class="stat-value">{{ fmtNum(stats.totalTokens) }}</div>
                </div>
              </div>
              <div v-if="stats.user && !stats.user.tip" class="user-stat-row">
                <span class="user-stat-title">当前用户持久累计</span>
                <span>调用 <strong>{{ fmtNum(stats.user.calls) }}</strong> 次</span>
                <span>输入 <strong>{{ fmtNum(stats.user.input) }}</strong></span>
                <span>输出 <strong>{{ fmtNum(stats.user.output) }}</strong></span>
              </div>
              <div v-else-if="stats.user && stats.user.tip" class="user-stat-row tip-row">{{ stats.user.tip }}</div>
            </template>
          </div>

          <div class="card">
            <div class="card-head">
              <h3>系统健康</h3>
              <button class="btn-ghost" :disabled="healthLoading" @click="loadHealth">
                {{ healthLoading ? '检测中…' : '重新检测' }}
              </button>
            </div>
            <div v-if="healthLoading" class="state-tip">检测中…</div>
            <div v-else class="health-row">
              <span class="health-dot" :class="healthOk ? 'green' : 'red'"></span>
              <span class="health-text" :class="{ fail: !healthOk }">
                {{ healthOk ? (healthText || '小易服务正常') : (healthError || '小易服务不可用') }}
              </span>
            </div>
          </div>
        </template>

        <!-- ============ Tab2 文档管理 ============ -->
        <template v-else-if="activeTab === 'docs'">
          <div class="card">
            <div class="card-head">
              <h3>知识库文档（{{ docs.length }}）</h3>
              <button class="btn-ghost" :disabled="docsLoading" @click="loadDocs">
                {{ docsLoading ? '加载中…' : '刷新' }}
              </button>
            </div>
            <div v-if="docsLoading" class="state-tip">加载中…</div>
            <div v-else-if="docsError" class="state-tip error">{{ docsError }}</div>
            <div v-else-if="!docs.length" class="state-tip empty">知识库还没有文档，去【入库】tab 添加</div>
            <div v-else class="table-wrap">
              <table class="doc-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>标题</th>
                    <th>类型</th>
                    <th>城市</th>
                    <th>分片数</th>
                    <th>创建时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="d in docs" :key="d.id">
                    <td>{{ d.id }}</td>
                    <td class="td-title" :title="d.title">{{ d.title }}</td>
                    <td>
                      <span class="badge" :class="badgeClass(d.sourceType)">{{ typeLabel(d.sourceType) }}</span>
                    </td>
                    <td>{{ d.city || '全国' }}</td>
                    <td>{{ d.chunkCount }}</td>
                    <td class="td-time">{{ d.createTime }}</td>
                    <td>
                      <button class="btn-danger-sm" :disabled="deletingId === d.id" @click="removeDoc(d)">
                        {{ deletingId === d.id ? '删除中…' : '删除' }}
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </template>

        <!-- ============ Tab3 入库 ============ -->
        <template v-else-if="activeTab === 'ingest'">
          <div class="card">
            <div class="mode-switch">
              <button class="mode-btn" :class="{ active: ingestMode === 'text' }" @click="ingestMode = 'text'">文本入库</button>
              <button class="mode-btn" :class="{ active: ingestMode === 'file' }" @click="ingestMode = 'file'">文件入库</button>
            </div>

            <!-- 文本入库 -->
            <div v-if="ingestMode === 'text'" class="ingest-form">
              <div class="form-row">
                <div class="form-field grow">
                  <label>标题 <em>*</em></label>
                  <input v-model.trim="textForm.title" type="text" placeholder="如：住房租赁条例全文" />
                </div>
                <div class="form-field">
                  <label>类型</label>
                  <select v-model="textForm.sourceType">
                    <option value="LAW">LAW 法规</option>
                    <option value="GUIDE">GUIDE 攻略</option>
                    <option value="POLICY">POLICY 政策</option>
                  </select>
                </div>
                <div class="form-field">
                  <label>城市（可空=全国）</label>
                  <input v-model.trim="textForm.city" type="text" placeholder="如：北京" />
                </div>
              </div>
              <div class="form-field">
                <label>正文内容 <em>*</em></label>
                <textarea
                  v-model="textForm.content"
                  rows="12"
                  placeholder="粘贴法规 / 攻略 / 政策原文，系统会自动分片并向量化…"
                ></textarea>
              </div>
              <button class="btn-primary" :disabled="ingesting" @click="submitText">
                {{ ingesting ? '入库中…' : '提交入库' }}
              </button>
            </div>

            <!-- 文件入库 -->
            <div v-else class="ingest-form">
              <div class="form-field">
                <label>选择文件 <em>*</em></label>
                <input
                  type="file"
                  accept=".pdf,.docx,.xlsx,.txt,.md"
                  @change="onFilePick"
                />
                <p v-if="fileForm.file" class="file-picked">已选择：{{ fileForm.file.name }}（{{ fmtSize(fileForm.file.size) }}）</p>
              </div>
              <div class="form-row">
                <div class="form-field grow">
                  <label>标题（可空，默认文件名）</label>
                  <input v-model.trim="fileForm.title" type="text" placeholder="留空则使用文件名" />
                </div>
                <div class="form-field">
                  <label>类型</label>
                  <select v-model="fileForm.sourceType">
                    <option value="LAW">LAW 法规</option>
                    <option value="GUIDE">GUIDE 攻略</option>
                    <option value="POLICY">POLICY 政策</option>
                  </select>
                </div>
                <div class="form-field">
                  <label>城市（可空=全国）</label>
                  <input v-model.trim="fileForm.city" type="text" placeholder="如：上海" />
                </div>
              </div>
              <button class="btn-primary" :disabled="ingesting" @click="submitFile">
                {{ ingesting ? '入库中…' : '上传并入库' }}
              </button>
            </div>

            <!-- 入库 loading / 结果 / 错误 -->
            <div v-if="ingesting" class="ingest-loading">
              <span class="spinner"></span>
              解析分片向量化中，约需10-60秒…
            </div>
            <div v-if="ingestError" class="ingest-result error">入库失败：{{ ingestError }}</div>
            <div v-if="ingestResult" class="ingest-result success">
              入库成功：分片 {{ ingestResult.chunkCount }} 块
              <template v-if="ingestResult.charCount">（{{ fmtNum(ingestResult.charCount) }} 字符）</template>
              <template v-if="ingestResult.message"> — {{ ingestResult.message }}</template>
            </div>
          </div>
        </template>

        <!-- ============ Tab4 检索调试 ============ -->
        <template v-else-if="activeTab === 'search'">
          <div class="card">
            <div class="card-head">
              <h3>混合检索调试</h3>
            </div>
            <div class="search-bar">
              <input
                v-model.trim="searchForm.query"
                type="text"
                class="search-input"
                placeholder="输入查询，如：租房押金法律规定"
                @keyup.enter="doSearch"
              />
              <div class="topk-wrap">
                <label>topK</label>
                <input v-model.number="searchForm.topK" type="number" min="1" max="20" />
              </div>
              <button class="btn-primary" :disabled="searching" @click="doSearch">
                {{ searching ? '检索中…' : '检索' }}
              </button>
            </div>

            <div v-if="searching" class="state-tip">检索中…</div>
            <div v-else-if="searchError" class="state-tip error">{{ searchError }}</div>

            <template v-else-if="searchResult">
              <!-- 三栏：向量 / BM25 / RRF -->
              <div class="retrieval-cols">
                <div class="retrieval-col">
                  <div class="col-title vec">① 向量路（FAISS 语义）</div>
                  <div v-if="typeof searchResult.faiss === 'string'" class="col-tip warn">{{ searchResult.faiss }}</div>
                  <div v-else-if="!searchResult.faiss || !searchResult.faiss.length" class="col-tip">无命中</div>
                  <ul v-else class="hit-list">
                    <li v-for="(h, i) in searchResult.faiss" :key="'f' + i">
                      <span class="hit-rank">{{ i + 1 }}</span>
                      <span class="hit-vid">{{ h.vectorId }}</span>
                      <span class="hit-score">{{ fmtScore(h.score) }}</span>
                    </li>
                  </ul>
                </div>
                <div class="retrieval-col">
                  <div class="col-title bm25">② BM25 路（关键词）</div>
                  <div v-if="!searchResult.bm25 || !searchResult.bm25.length" class="col-tip">无命中</div>
                  <ul v-else class="hit-list">
                    <li v-for="(vid, i) in searchResult.bm25" :key="'b' + i">
                      <span class="hit-rank">{{ i + 1 }}</span>
                      <span class="hit-vid">{{ vid }}</span>
                    </li>
                  </ul>
                </div>
                <div class="retrieval-col">
                  <div class="col-title rrf">③ RRF 融合</div>
                  <div v-if="!rrfIds.length" class="col-tip">无融合结果</div>
                  <ul v-else class="hit-list">
                    <li v-for="(vid, i) in rrfIds" :key="'r' + i">
                      <span class="hit-rank">{{ i + 1 }}</span>
                      <span class="hit-vid">{{ vid }}</span>
                      <span v-if="rrfDetail && rrfDetail[vid]" class="hit-from">{{ rrfDetail[vid] }}</span>
                      <span v-if="rrfScores && rrfScores[vid] != null" class="hit-score">{{ fmtScore(rrfScores[vid], 4) }}</span>
                    </li>
                  </ul>
                </div>
              </div>

              <!-- 精排最终结果 -->
              <div class="final-section">
                <div class="final-title">🎯 精排最终结果（送入大模型的上下文）</div>
                <div v-if="!searchResult.final || !searchResult.final.length" class="col-tip">无最终结果</div>
                <div v-for="(f, i) in searchResult.final || []" :key="'fin' + i" class="final-card">
                  <div class="final-head">
                    <span class="final-rank">#{{ i + 1 }}</span>
                    <strong class="final-doc">{{ f.docTitle || f.vectorId }}</strong>
                    <span class="badge from-badge" :class="f.from === 'vec' || f.from === 'vector' ? 'badge-blue' : 'badge-green'">{{ f.from }}</span>
                    <span class="final-score">score {{ fmtScore(f.score) }}</span>
                    <span class="final-vid">{{ f.vectorId }}</span>
                  </div>
                  <p class="final-text">{{ f.text }}</p>
                </div>
              </div>
            </template>
          </div>

          <!-- 危险区 -->
          <div class="card danger-zone">
            <div class="card-head">
              <h3>⚠️ 危险区</h3>
            </div>
            <p class="danger-desc">
              CLIP 房源图重建：对所有房源图片重新计算 CLIP 向量并写入索引，耗时约 1-5 分钟，期间请勿重复触发。
            </p>
            <button class="btn-danger" :disabled="clipLoading" @click="clipReindex">
              {{ clipLoading ? '重建中，请耐心等待…' : 'CLIP 房源图重建（limit=300）' }}
            </button>
            <div v-if="clipLoading" class="ingest-loading">
              <span class="spinner red"></span>
              重建中，约需 1-5 分钟…
            </div>
            <div v-if="clipError" class="ingest-result error">重建失败：{{ clipError }}</div>
            <div v-if="clipResult" class="ingest-result success">
              重建完成：房源 {{ clipResult.houses }} 套，去重图片 {{ clipResult.uniqueImages }} 张，跳过 {{ clipResult.skipped }}
            </div>
          </div>
        </template>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import request from '../api/index'
import PageHero from '../components/PageHero.vue'

const isAdmin = ref(false)
const activeTab = ref('overview')
const tabs = [
  { key: 'overview', label: '概览' },
  { key: 'docs', label: '文档管理' },
  { key: 'ingest', label: '入库' },
  { key: 'search', label: '检索调试' }
]

// ===== 概览：Token 统计 =====
const stats = ref(null)
const statsLoading = ref(false)
const statsError = ref('')

async function loadStats() {
  statsLoading.value = true
  statsError.value = ''
  try {
    const u = JSON.parse(localStorage.getItem('user') || '{}')
    const userId = u.id || localStorage.getItem('rental_userId')
    const res = await request.get('/ai/token/stats', { params: { userId } })
    stats.value = res.data
  } catch (e) {
    statsError.value = e.message || 'Token 统计加载失败'
  } finally {
    statsLoading.value = false
  }
}

// ===== 概览：系统健康 =====
const healthLoading = ref(false)
const healthOk = ref(false)
const healthText = ref('')
const healthError = ref('')

async function loadHealth() {
  healthLoading.value = true
  healthError.value = ''
  try {
    const res = await request.get('/ai/health')
    healthOk.value = true
    healthText.value = typeof res.data === 'string' ? res.data : (res.message || '小易服务正常')
  } catch (e) {
    healthOk.value = false
    healthError.value = e.message || '小易服务不可用'
  } finally {
    healthLoading.value = false
  }
}

// ===== 文档管理 =====
const docs = ref([])
const docsLoading = ref(false)
const docsError = ref('')
const deletingId = ref(null)

async function loadDocs() {
  docsLoading.value = true
  docsError.value = ''
  try {
    const res = await request.get('/kb/documents')
    docs.value = res.data || []
  } catch (e) {
    docsError.value = e.message || '文档列表加载失败'
  } finally {
    docsLoading.value = false
  }
}

async function removeDoc(d) {
  if (!window.confirm(`确定删除文档「${d.title}」吗？其所有分片与向量将一并删除。`)) return
  deletingId.value = d.id
  try {
    await request.delete(`/kb/documents/${d.id}`)
    await loadDocs()
  } catch (e) {
    window.alert('删除失败：' + (e.message || '未知错误'))
  } finally {
    deletingId.value = null
  }
}

function typeLabel(t) {
  return t === 'LAW' ? '法规' : t === 'GUIDE' ? '攻略' : t === 'POLICY' ? '政策' : (t || '-')
}
function badgeClass(t) {
  return t === 'LAW' ? 'badge-purple' : t === 'GUIDE' ? 'badge-green' : t === 'POLICY' ? 'badge-blue' : 'badge-gray'
}

// ===== 入库 =====
const ingestMode = ref('text')
const ingesting = ref(false)
const ingestResult = ref(null)
const ingestError = ref('')

const textForm = reactive({ title: '', sourceType: 'LAW', city: '', content: '' })
const fileForm = reactive({ file: null, title: '', sourceType: 'LAW', city: '' })

function onFilePick(e) {
  fileForm.file = e.target.files && e.target.files[0] ? e.target.files[0] : null
}

async function submitText() {
  ingestResult.value = null
  ingestError.value = ''
  if (!textForm.title) { ingestError.value = '请填写标题'; return }
  if (!textForm.content) { ingestError.value = '请填写正文内容'; return }
  ingesting.value = true
  try {
    const res = await request.post('/kb/documents', {
      title: textForm.title,
      sourceType: textForm.sourceType,
      city: textForm.city || null,
      content: textForm.content
    }, { timeout: 300000 }) // 入库含向量化，需 10-60 秒，放宽超时避免默认 15s 掐断
    ingestResult.value = res.data
    textForm.title = ''
    textForm.content = ''
    textForm.city = ''
  } catch (e) {
    ingestError.value = e.message || '入库失败'
  } finally {
    ingesting.value = false
  }
}

async function submitFile() {
  ingestResult.value = null
  ingestError.value = ''
  if (!fileForm.file) { ingestError.value = '请选择文件'; return }
  ingesting.value = true
  try {
    const formData = new FormData()
    formData.append('file', fileForm.file)
    if (fileForm.title) formData.append('title', fileForm.title)
    formData.append('sourceType', fileForm.sourceType)
    if (fileForm.city) formData.append('city', fileForm.city)
    const res = await request.post('/kb/documents/upload', formData, { timeout: 300000 })
    ingestResult.value = res.data
    fileForm.file = null
    fileForm.title = ''
    fileForm.city = ''
  } catch (e) {
    ingestError.value = e.message || '文件入库失败'
  } finally {
    ingesting.value = false
  }
}

// ===== 检索调试 =====
const searchForm = reactive({ query: '', topK: 5 })
const searching = ref(false)
const searchError = ref('')
const searchResult = ref(null)

const rrfIds = computed(() => (searchResult.value && searchResult.value.rrf && searchResult.value.rrf.ids) || [])
const rrfScores = computed(() => (searchResult.value && searchResult.value.rrf && searchResult.value.rrf.scores) || null)
const rrfDetail = computed(() => (searchResult.value && searchResult.value.rrf && searchResult.value.rrf.detail) || null)

async function doSearch() {
  searchError.value = ''
  if (!searchForm.query) { searchError.value = '请输入查询内容'; return }
  searching.value = true
  searchResult.value = null
  try {
    const res = await request.post('/kb/search', {
      query: searchForm.query,
      topK: searchForm.topK || 5
    })
    searchResult.value = res.data
  } catch (e) {
    searchError.value = e.message || '检索失败'
  } finally {
    searching.value = false
  }
}

// ===== CLIP 重建 =====
const clipLoading = ref(false)
const clipResult = ref(null)
const clipError = ref('')

async function clipReindex() {
  if (!window.confirm('CLIP 房源图重建耗时约 1-5 分钟，期间将占用向量服务资源，确定执行？')) return
  clipLoading.value = true
  clipResult.value = null
  clipError.value = ''
  try {
    const res = await request.post('/house/clip/reindex?limit=300', null, { timeout: 1800000 })
    clipResult.value = res.data
  } catch (e) {
    clipError.value = e.message || 'CLIP 重建失败'
  } finally {
    clipLoading.value = false
  }
}

// ===== 工具函数 =====
function fmtNum(n) {
  return (n == null ? 0 : Number(n)).toLocaleString()
}
function fmtScore(s, digits = 3) {
  if (s == null) return '-'
  return Number(s).toFixed(digits)
}
function fmtSize(bytes) {
  if (bytes == null) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function switchTab(key) {
  activeTab.value = key
  if (key === 'docs' && !docs.value.length && !docsLoading.value) loadDocs()
}

onMounted(() => {
  let u = {}
  try { u = JSON.parse(localStorage.getItem('user') || '{}') } catch (e) {}
  // 大小写不敏感 + 双来源（user 对象 / rental_role），避免角色判断失效
  isAdmin.value = ((u.role || localStorage.getItem('rental_role') || '').toUpperCase() === 'ADMIN')
  if (isAdmin.value) {
    loadStats()
    loadHealth()
  }
})
</script>

<style scoped>
.kb-admin-page {
  min-height: 100vh;
  background: var(--jade-paper);
}
.page-content {
  max-width: 1000px;
  margin: 0 auto;
  padding: 24px 16px;
}
.card {
  background: #fff;
  border-radius: 14px;
  border: 1px solid #f0f0f0;
  padding: 20px;
  margin-bottom: 16px;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.card-head h3 {
  margin: 0;
  font-size: 16px;
  color: var(--jade-ink);
}

/* 权限拒绝卡片 */
.deny-card {
  text-align: center;
  padding: 80px 20px;
}
.deny-icon { font-size: 48px; margin-bottom: 12px; }
.deny-card h2 { color: var(--jade-ink); margin: 0 0 8px; font-size: 20px; }
.deny-card p { color: #666; margin: 0; }

/* Tab 栏 */
.tab-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 14px;
  padding: 6px;
}
.tab-btn {
  flex: 1;
  border: none;
  background: transparent;
  padding: 10px 16px;
  border-radius: 10px;
  font-size: 14px;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
}
.tab-btn:hover { background: var(--jade-pale); color: var(--jade-600); }
.tab-btn.active { background: var(--jade-600); color: #fff; }

/* 统计卡 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.stat-card {
  background: var(--jade-paper);
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  padding: 16px;
  text-align: center;
}
.stat-card.highlight { background: var(--jade-pale); border-color: #bdd4c5; }
.stat-label { font-size: 12px; color: #999; margin-bottom: 6px; }
.stat-value { font-size: 22px; font-weight: 700; color: var(--jade-ink); }
.user-stat-row {
  margin-top: 14px;
  padding: 12px 16px;
  background: var(--jade-paper);
  border-radius: 10px;
  display: flex;
  gap: 20px;
  align-items: center;
  flex-wrap: wrap;
  font-size: 13px;
  color: #666;
}
.user-stat-title { font-weight: 600; color: var(--jade-ink); }
.user-stat-row strong { color: var(--jade-600); }
.tip-row { color: #999; }

/* 健康 */
.health-row { display: flex; align-items: center; gap: 10px; }
.health-dot {
  width: 12px; height: 12px; border-radius: 50%;
  display: inline-block;
}
.health-dot.green { background: #10b981; box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.15); }
.health-dot.red { background: #ef4444; box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.15); }
.health-text { color: var(--jade-ink); font-size: 14px; }
.health-text.fail { color: #ef4444; }

/* 状态提示 */
.state-tip { padding: 24px; text-align: center; color: #999; font-size: 14px; }
.state-tip.error { color: #ef4444; }
.state-tip.empty { color: #999; }

/* 按钮 */
.btn-primary {
  background: var(--jade-600);
  color: #fff;
  border: none;
  border-radius: 10px;
  padding: 10px 20px;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s;
}
.btn-primary:hover:not(:disabled) { background: var(--jade-700); }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-ghost {
  background: var(--jade-pale);
  color: var(--jade-600);
  border: none;
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 13px;
  cursor: pointer;
}
.btn-ghost:hover:not(:disabled) { background: var(--jade-600); color: #fff; }
.btn-ghost:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-danger-sm {
  background: #fff;
  color: #ef4444;
  border: 1px solid #fecaca;
  border-radius: 8px;
  padding: 5px 12px;
  font-size: 12px;
  cursor: pointer;
}
.btn-danger-sm:hover:not(:disabled) { background: #ef4444; color: #fff; }
.btn-danger-sm:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-danger {
  background: #ef4444;
  color: #fff;
  border: none;
  border-radius: 10px;
  padding: 10px 20px;
  font-size: 14px;
  cursor: pointer;
}
.btn-danger:hover:not(:disabled) { background: #dc2626; }
.btn-danger:disabled { opacity: 0.6; cursor: not-allowed; }

/* 表格 */
.table-wrap { overflow-x: auto; }
.doc-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.doc-table th {
  text-align: left;
  color: #999;
  font-weight: 500;
  padding: 10px 12px;
  border-bottom: 1px solid #f0f0f0;
  white-space: nowrap;
}
.doc-table td {
  padding: 12px;
  color: var(--jade-ink);
  border-bottom: 1px solid var(--jade-paper);
  vertical-align: middle;
}
.doc-table tr:hover td { background: #fafafa; }
.td-title { max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.td-time { color: #999; white-space: nowrap; }

/* 徽章 */
.badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}
.badge-purple { background: #f3e8ff; color: #9333ea; }
.badge-green { background: #d1fae5; color: #059669; }
.badge-blue { background: #dbeafe; color: #2563eb; }
.badge-gray { background: #f3f4f6; color: #666; }

/* 入库 */
.mode-switch {
  display: inline-flex;
  background: #f3f4f6;
  border-radius: 10px;
  padding: 4px;
  margin-bottom: 18px;
}
.mode-btn {
  border: none;
  background: transparent;
  padding: 8px 20px;
  border-radius: 8px;
  font-size: 13px;
  color: #666;
  cursor: pointer;
}
.mode-btn.active { background: #fff; color: var(--jade-600); font-weight: 600; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
.ingest-form { display: flex; flex-direction: column; gap: 14px; }
.form-row { display: flex; gap: 12px; flex-wrap: wrap; }
.form-field { display: flex; flex-direction: column; gap: 6px; }
.form-field.grow { flex: 1; min-width: 200px; }
.form-field label { font-size: 13px; color: #666; }
.form-field label em { color: #ef4444; font-style: normal; }
.form-field input[type="text"],
.form-field input[type="number"],
.form-field select,
.form-field textarea {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 14px;
  color: var(--jade-ink);
  outline: none;
  background: #fff;
  font-family: inherit;
  transition: border-color 0.2s;
}
.form-field input:focus,
.form-field select:focus,
.form-field textarea:focus { border-color: var(--jade-600); }
.form-field textarea { resize: vertical; line-height: 1.6; }
.form-field input[type="file"] { font-size: 13px; color: #666; }
.file-picked { margin: 4px 0 0; font-size: 12px; color: #10b981; }
.ingest-form .btn-primary { align-self: flex-start; }

.ingest-loading {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  padding: 12px 16px;
  background: var(--jade-pale);
  border-radius: 10px;
  color: var(--jade-600);
  font-size: 13px;
}
.spinner {
  width: 16px; height: 16px;
  border: 2px solid #bdd4c5;
  border-top-color: var(--jade-600);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
.spinner.red { border-color: #fecaca; border-top-color: #ef4444; }
@keyframes spin { to { transform: rotate(360deg); } }
.ingest-result {
  margin-top: 14px;
  padding: 12px 16px;
  border-radius: 10px;
  font-size: 13px;
}
.ingest-result.success { background: #d1fae5; color: #059669; }
.ingest-result.error { background: #fee2e2; color: #ef4444; }

/* 检索调试 */
.search-bar { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.search-input {
  flex: 1;
  min-width: 220px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 10px 14px;
  font-size: 14px;
  color: var(--jade-ink);
  outline: none;
}
.search-input:focus { border-color: var(--jade-600); }
.topk-wrap { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #666; }
.topk-wrap input {
  width: 60px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 9px 8px;
  font-size: 13px;
  outline: none;
}
.topk-wrap input:focus { border-color: var(--jade-600); }

.retrieval-cols {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 18px;
}
.retrieval-col {
  background: var(--jade-paper);
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  padding: 14px;
  min-height: 120px;
}
.col-title { font-size: 13px; font-weight: 600; margin-bottom: 10px; }
.col-title.vec { color: var(--jade-600); }
.col-title.bm25 { color: #059669; }
.col-title.rrf { color: #f59e0b; }
.col-tip { font-size: 12px; color: #999; padding: 8px 0; }
.col-tip.warn { color: #f59e0b; }
.hit-list { list-style: none; margin: 0; padding: 0; }
.hit-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px dashed #eee;
  font-size: 12px;
}
.hit-list li:last-child { border-bottom: none; }
.hit-rank {
  width: 20px; height: 20px;
  border-radius: 6px;
  background: var(--jade-pale);
  color: var(--jade-600);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}
.hit-vid { color: var(--jade-ink); font-family: monospace; }
.hit-score { margin-left: auto; color: #666; font-family: monospace; }
.hit-from {
  background: #fef3c7;
  color: #b45309;
  border-radius: 6px;
  padding: 1px 6px;
  font-size: 11px;
  font-family: monospace;
}

/* 精排结果 */
.final-section { margin-top: 20px; }
.final-title { font-size: 14px; font-weight: 600; color: var(--jade-ink); margin-bottom: 12px; }
.final-card {
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 10px;
  background: #fff;
  transition: border-color 0.2s;
}
.final-card:hover { border-color: #bdd4c5; }
.final-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 8px; }
.final-rank { color: #999; font-size: 12px; font-weight: 600; }
.final-doc { color: var(--jade-ink); font-size: 14px; }
.from-badge { font-size: 11px; }
.final-score { color: var(--jade-600); font-size: 12px; font-family: monospace; }
.final-vid { margin-left: auto; color: #999; font-size: 11px; font-family: monospace; }
.final-text {
  margin: 0;
  color: #666;
  font-size: 13px;
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 危险区 */
.danger-zone { border-color: #fecaca; }
.danger-desc { color: #666; font-size: 13px; line-height: 1.6; margin: 0 0 14px; }

@media (max-width: 768px) {
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
  .retrieval-cols { grid-template-columns: 1fr; }
  .tab-bar { flex-wrap: wrap; }
}
</style>
