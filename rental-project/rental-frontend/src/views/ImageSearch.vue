<template>
  <div class="image-search-page">
    <PageHero title="以图找房" subtitle="喜欢这样的房间？给我一张照片，或者说说它的样子。" />

    <div class="page-content">
      <!-- ====== 模式切换 tab ====== -->
      <div class="mode-tabs">
        <button
          class="mode-tab"
          :class="{ on: mode === 'image' }"
          @click="mode = 'image'"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="m21 15-5-5L5 21"/></svg>
          以图搜房
        </button>
        <button
          class="mode-tab"
          :class="{ on: mode === 'text' }"
          @click="mode = 'text'"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
          文字找房
        </button>
      </div>

      <!-- ====== 以图搜房 ====== -->
      <div v-if="mode === 'image'" class="search-card">
        <input
          ref="fileInputRef"
          id="img-file-input"
          type="file"
          accept="image/*"
          class="hidden-file"
          @change="onFileChange"
        />

        <!-- 未选择：大虚线上传框 -->
        <label v-if="!selectedFile" for="img-file-input" class="upload-box" tabindex="0" role="button" @keydown.enter.prevent="fileInputRef?.click()" @keydown.space.prevent="fileInputRef?.click()">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="44" height="44"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
          <p class="upload-title">点击上传房源照片</p>
          <p class="upload-tip">支持 JPG、PNG、WebP，选一张喜欢的房间照片，寻找相似房源。</p>
        </label>

        <!-- 已选择：本地预览 -->
        <div v-else class="preview-card">
          <img :src="previewUrl" alt="预览" class="preview-img" />
          <div class="preview-meta">
            <p class="preview-name" :title="selectedFile.name">{{ selectedFile.name }}</p>
            <button class="btn-ghost" type="button" @click="resetFile">重新选择</button>
          </div>
        </div>

        <div class="search-actions">
          <div class="topk-field">
            <label>返回数量</label>
            <select v-model.number="topK" class="topk-select">
              <option :value="5">前 5 套</option>
              <option :value="10">前 10 套</option>
              <option :value="20">前 20 套</option>
            </select>
          </div>
          <button
            class="btn-primary"
            :disabled="!selectedFile || loading"
            @click="searchByImage"
          >
            <span v-if="loading" class="btn-spinner"></span>
            {{ loading ? 'AI 检索中...' : '搜索相似房源' }}
          </button>
        </div>
      </div>

      <!-- ====== 文字找房 ====== -->
      <div v-if="mode === 'text'" class="search-card">
        <textarea
          v-model="description"
          class="desc-input"
          rows="3"
          placeholder="例如：明亮的卧室 大窗户 / 现代化厨房 / 温馨原木风客厅"
        ></textarea>
        <div class="chip-row">
          <span class="chip-label">试试：</span>
          <button
            v-for="c in presetChips"
            :key="c"
            type="button"
            class="chip"
            @click="description = c"
          >{{ c }}</button>
        </div>
        <div class="search-actions">
          <div class="topk-field">
            <label>返回数量</label>
            <select v-model.number="topK" class="topk-select">
              <option :value="5">前 5 套</option>
              <option :value="10">前 10 套</option>
              <option :value="20">前 20 套</option>
            </select>
          </div>
          <button
            class="btn-primary"
            :disabled="!description.trim() || loading"
            @click="searchByText"
          >
            <span v-if="loading" class="btn-spinner"></span>
            {{ loading ? 'AI 检索中...' : '搜索相似房源' }}
          </button>
        </div>
      </div>

      <!-- ====== 错误提示 ====== -->
      <div v-if="error" class="error-box">
        <svg viewBox="0 0 24 24" fill="none" stroke="#ef4444" stroke-width="2" width="16" height="16"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
        <span>{{ error }}</span>
      </div>

      <!-- ====== 结果区（两 tab 共用）====== -->
      <div v-if="searched" class="result-card">
        <div v-if="loading" class="state-box">
          <div class="loading-spinner"></div>
          <p>CLIP 跨模态检索中，首次请求可能需要模型加载，请耐心等待...</p>
        </div>

        <template v-else>
          <p class="result-stat">找到 <b>{{ results.length }}</b> 套相似房源</p>

          <div v-if="results.length" class="ke-grid4">
            <div
              v-for="r in results"
              :key="r.houseId"
              class="ke-card"
              @click="goDetail(r.houseId)"
            >
              <div
                class="ke-card-pic"
                :style="r.coverImage ? { backgroundImage: 'url(' + r.coverImage + ')', backgroundSize: 'cover', backgroundPosition: 'center' } : {}"
              >
                <div class="ke-card-nopic" v-if="!r.coverImage">
                  <svg viewBox="0 0 24 24" fill="none" stroke="#ddd" stroke-width="1.5"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/></svg>
                </div>
                <span class="ke-card-badge">AI相似</span>
                <span class="sim-badge" v-if="r.similarity != null">{{ (r.similarity * 100).toFixed(1) }}%</span>
              </div>
              <div class="ke-card-txt">
                <p class="ke-card-area">{{ r.district }}·{{ r.title }}</p>
                <h3 class="ke-card-title">{{ r.title }}</h3>
                <p class="ke-card-price"><b>{{ r.price }}</b> 元/月</p>
              </div>
            </div>
          </div>

          <div v-else class="state-box">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#ddd" stroke-width="1.5"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            <p>没有搜到相似房源。若刚部署，请管理员先到【知识库管理→检索调试】执行 CLIP 重建入库</p>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import request from '../api/index'
import PageHero from '../components/PageHero.vue'

const router = useRouter()

const mode = ref('image')
const topK = ref(10)
const presetChips = ['明亮的卧室', '现代化厨房', '温馨客厅', '简约风格']

// ===== 以图搜房 =====
const fileInputRef = ref(null)
const selectedFile = ref(null)
const previewUrl = ref('')

const onFileChange = (e) => {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  selectedFile.value = file
  previewUrl.value = URL.createObjectURL(file)
}

const resetFile = () => {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  selectedFile.value = null
  if (fileInputRef.value) fileInputRef.value.value = ''
}

onBeforeUnmount(() => {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})

// ===== 文字找房 =====
const description = ref('')

// ===== 检索状态 =====
const loading = ref(false)
const searched = ref(false)
const error = ref('')
const results = ref([])

const runSearch = async (fn) => {
  loading.value = true
  error.value = ''
  results.value = []
  searched.value = true
  try {
    const res = await fn()
    if (res.code === 200) {
      results.value = res.data || []
    }
  } catch (e) {
    error.value = e.message || '检索失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const searchByImage = () => {
  if (!selectedFile.value) return
  runSearch(() => {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    return request.post('/house/search-by-image?topK=' + topK.value, formData, { timeout: 120000 })
  })
}

const searchByText = () => {
  if (!description.value.trim()) return
  runSearch(() => request.post('/house/search-by-text', { description: description.value.trim(), topK: topK.value }, { timeout: 120000 }))
}

const goDetail = (houseId) => router.push('/detail/' + houseId)
</script>

<style scoped>
.image-search-page { background: var(--jade-paper); min-height: 100vh; font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif; color: var(--jade-ink); }
.page-content { max-width: 1000px; margin: 0 auto; padding: 24px 16px; }

/* ====== 模式 tab ====== */
.mode-tabs { display: flex; gap: 10px; margin-bottom: 16px; }
.mode-tab { display: inline-flex; align-items: center; gap: 6px; padding: 10px 22px; font-size: 14px; color: #666; background: #fff; border: 1px solid #f0f0f0; border-radius: 10px; cursor: pointer; transition: all .2s; }
.mode-tab:hover { color: var(--jade-600); }
.mode-tab.on { color: #fff; background: var(--jade-600); border-color: var(--jade-600); font-weight: 500; }

/* ====== 搜索卡片 ====== */
.search-card { background: #fff; border-radius: 14px; border: 1px solid #f0f0f0; padding: 20px; margin-bottom: 16px; }

.hidden-file { display: none; }

/* 上传框 */
.upload-box { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; padding: 48px 20px; border: 2px dashed #bed0bf; border-radius: 12px; background: #f5f7f1; cursor: pointer; transition: all .2s; }
.upload-box:hover { border-color: var(--jade-600); background: var(--jade-pale); }
.upload-title { font-size: 15px; font-weight: 600; color: var(--jade-ink); margin: 0; }
.upload-tip { font-size: 12px; color: #999; margin: 0; }

/* 预览 */
.preview-card { display: flex; gap: 16px; align-items: flex-start; padding: 14px; border: 1px solid #f0f0f0; border-radius: 12px; background: #f5f7f1; }
.preview-img { width: 260px; height: 200px; object-fit: cover; border-radius: 10px; background: #f0f0f0; flex-shrink: 0; }
.preview-meta { display: flex; flex-direction: column; gap: 10px; min-width: 0; padding-top: 4px; }
.preview-name { font-size: 14px; color: var(--jade-ink); margin: 0; word-break: break-all; }
.btn-ghost { padding: 8px 16px; font-size: 13px; color: var(--jade-600); background: var(--jade-pale); border: 1px solid #d3e3d7; border-radius: 10px; cursor: pointer; transition: all .2s; align-self: flex-start; }
.btn-ghost:hover { background: #d3e3d7; }

/* 文字描述 */
.desc-input { width: 100%; box-sizing: border-box; padding: 12px 14px; font-size: 14px; color: var(--jade-ink); border: 1px solid #e5e7eb; border-radius: 10px; resize: vertical; outline: none; font-family: inherit; line-height: 1.6; }
.desc-input:focus { border-color: var(--jade-600); }
.desc-input::placeholder { color: #999; }
.chip-row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 12px; }
.chip-label { font-size: 13px; color: #999; }
.chip { padding: 5px 14px; font-size: 13px; color: var(--jade-600); background: var(--jade-pale); border: 1px solid #d3e3d7; border-radius: 999px; cursor: pointer; transition: all .2s; }
.chip:hover { background: var(--jade-600); color: #fff; border-color: var(--jade-600); }

/* 操作行 */
.search-actions { display: flex; align-items: flex-end; gap: 16px; margin-top: 16px; flex-wrap: wrap; }
.topk-field { display: flex; flex-direction: column; gap: 6px; }
.topk-field label { font-size: 12px; color: #999; }
.topk-select { padding: 9px 12px; font-size: 14px; color: var(--jade-ink); background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; outline: none; cursor: pointer; }
.topk-select:focus { border-color: var(--jade-600); }
.btn-primary { display: inline-flex; align-items: center; gap: 8px; background: var(--jade-600); color: #fff; border: none; border-radius: 10px; padding: 10px 20px; font-size: 14px; cursor: pointer; transition: background .2s; margin-left: auto; }
.btn-primary:hover:not(:disabled) { background: var(--jade-700); }
.btn-primary:disabled { opacity: .5; cursor: not-allowed; }
.btn-spinner { width: 14px; height: 14px; border: 2px solid rgba(255,255,255,.4); border-top-color: #fff; border-radius: 50%; animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* ====== 错误 ====== */
.error-box { display: flex; align-items: center; gap: 8px; padding: 12px 16px; background: #fef2f2; border: 1px solid #fecaca; border-radius: 10px; color: #ef4444; font-size: 13px; margin-bottom: 16px; }

/* ====== 结果区 ====== */
.result-card { background: #fff; border-radius: 14px; border: 1px solid #f0f0f0; padding: 20px; }
.result-stat { font-size: 14px; color: #666; margin: 0 0 16px; }
.result-stat b { color: var(--jade-600); font-size: 18px; }

.state-box { text-align: center; padding: 60px 0; color: #999; font-size: 14px; }
.state-box p { margin-top: 12px; line-height: 1.7; }
.loading-spinner { width: 36px; height: 36px; border: 3px solid var(--jade-pale); border-top-color: var(--jade-600); border-radius: 50%; animation: spin .8s linear infinite; margin: 0 auto; }

/* ====== 房源卡片网格（复用 HouseList 样式）====== */
.ke-grid4 { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
@media (max-width: 1000px) { .ke-grid4 { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 600px) { .ke-grid4 { grid-template-columns: 1fr; } }

.ke-card { background: #fff; border-radius: 0; overflow: hidden; cursor: pointer; transition: transform .2s; }
.ke-card:hover { transform: translateY(-2px); }
.ke-card-pic { position: relative; width: 100%; padding-bottom: 75%; background: #f0f0f0; overflow: hidden; background-size: cover; background-position: center; }
.ke-card-nopic { position: absolute; top: 0; left: 0; width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; background: var(--jade-paper); }
.ke-card-nopic svg { width: 36px; }
.ke-card-badge { position: absolute; top: 8px; left: 8px; padding: 3px 8px; background: var(--jade-600); color: #fff; font-size: 11px; border-radius: 2px; }
.sim-badge { position: absolute; top: 8px; right: 8px; padding: 3px 8px; background: rgba(16, 185, 129, .85); color: #fff; font-size: 11px; border-radius: 2px; font-weight: 600; }
.ke-card-txt { padding: 12px 4px 0; }
.ke-card-area { font-size: 13px; color: var(--jade-ink); margin: 0 0 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ke-card-title { font-size: 13px; color: #666; font-weight: 400; margin: 0 0 8px; line-height: 1.4; height: 2.8em; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.ke-card-price { font-size: 13px; color: var(--jade-ink); margin: 0; }
.ke-card-price b { font-size: 18px; color: #f15b5b; font-weight: 600; }
</style>
