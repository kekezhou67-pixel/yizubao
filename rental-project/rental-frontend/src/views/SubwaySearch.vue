<template>
  <div class="ke-sb-page">
    <PageHero title="地铁找房" subtitle="把通勤的时间，留给自己的生活。选择线路，发现沿线好房。">
      <div class="ke-sb-search">
        <div class="ke-sb-sel">
          <label>城市</label>
          <select v-model="selectedCity" @change="onCityChange">
            <option value="">请选择城市</option>
            <option v-for="c in cities" :key="c" :value="c">{{ c }}</option>
          </select>
        </div>
        <div class="ke-sb-sel">
          <label>线路</label>
          <select v-model="selectedLine" @change="onLineChange" :disabled="!selectedCity">
            <option value="">请选择线路</option>
            <option v-for="l in lines" :key="l" :value="l">{{ l }}</option>
          </select>
        </div>
        <button class="ke-sb-btn" :disabled="!selectedLine" @click="confirmLine">查看沿线房源</button>
      </div>
    </PageHero>

    <div class="ke-sb-body">
      <!-- 未选城市：显示热门城市快捷入口 -->
      <div v-if="!selectedCity" class="ke-sb-hot">
        <h2 class="ke-sb-h2">热门城市</h2>
        <p class="ke-sb-tip">选择城市开始地铁找房</p>
        <div class="ke-sb-cities">
          <div v-for="c in hotCities" :key="c.name" class="ke-sb-city-card" @click="quickCity(c.name)">
            <div class="ke-sb-cc-num">{{ c.name }}</div>
            <div class="ke-sb-cc-count">{{ cityCounts[c.name] != null ? cityCounts[c.name] + '套' : '加载中…' }}</div>
            <div class="ke-sb-cc-img" :style="{ backgroundImage: 'url(/city-images/' + encodeURIComponent(c.name) + '.jpg)', backgroundSize: 'cover', backgroundPosition: 'center' }"></div>
          </div>
        </div>
      </div>

      <!-- 已选城市但未选线路 -->
      <div v-if="selectedCity && !selectedLine">
        <h2 class="ke-sb-h2">{{ selectedCity }}地铁线路 <span class="ke-sb-stat">共 {{ lines.length }} 条</span></h2>
        <p class="ke-sb-tip">选择一条线路查看沿线所有站点</p>
        <div class="ke-sb-lines">
          <div v-for="l in lines" :key="l" class="ke-sb-line-card" @click="quickLine(l)">
            <div class="ke-sb-lc-dot"></div>
            <span>{{ l }}</span>
          </div>
        </div>
      </div>

      <!-- 已选线路：显示站点 -->
      <div v-if="selectedLine && stations.length">
        <h2 class="ke-sb-h2">{{ selectedLine }} <span class="ke-sb-stat">共 {{ stations.length }} 站 · {{ totalHouses }} 套沿线房源</span></h2>
        <p class="ke-sb-tip">点击站点查看附近房源</p>
        <div class="ke-sb-line-stations">
          <div v-for="(s, idx) in stations" :key="s.id" class="ke-sb-station-card" :class="{on:selectedStation?.id===s.id}" @click="showHouses(s)">
            <span class="ke-sb-sidx">{{ idx + 1 }}</span>
            <span class="ke-sb-sdot"></span>
            <span class="ke-sb-sname">{{ s.name }}</span>
            <span v-if="s.houseCount" class="ke-sb-scnt">{{ s.houseCount }}套</span>
          </div>
        </div>
      </div>

      <!-- 全部空：底部推荐内容 -->
      <div v-if="!selectedLine" class="ke-sb-feature">
        <h2 class="ke-sb-h2">热门地铁房</h2>
        <p class="ke-sb-tip">全国地铁沿线精选房源</p>
        <div v-if="hotHouses.length" class="ke-sb-fh-grid">
          <div v-for="h in hotHouses" :key="h.id" class="ke-sb-fh-card" @click="$router.push('/detail/'+h.id)">
            <img v-if="h.coverImage" :src="h.coverImage" />
            <div class="ke-sb-fh-info">
              <h4>{{ h.title }}</h4>
              <p>🚇 {{ h.subwayLineName }} {{ h.subwayStationName }} {{ h.distanceToSubway }}m</p>
              <p class="ke-sb-price"><b>{{ h.price }}</b>元/月</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 房源弹窗 -->
    <div class="ke-sb-overlay" v-if="selectedStation" @click.self="selectedStation=null">
      <div class="ke-sb-modal">
        <div class="ke-sb-mhd">
          <div>
            <h3>{{ selectedLine }} · {{ selectedStation.name }}站</h3>
            <p>该站点附近 {{ stationHouses.length }} 套房源</p>
          </div>
          <button class="ke-sb-close" @click="selectedStation=null">✕</button>
        </div>
        <div v-if="stationHouses.length===0" class="ke-sb-empty">
          <svg viewBox="0 0 24 24" fill="none" stroke="#ccc" stroke-width="1.5" width="60" height="60"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/></svg>
          <p>该站暂无房源</p>
        </div>
        <div v-else class="ke-sb-list">
          <div v-for="h in stationHouses" :key="h.id" class="ke-sb-hcard" @click="$router.push('/detail/'+h.id)">
            <img v-if="h.coverImage" :src="h.coverImage" />
            <div class="ke-sb-hinfo">
              <h4>{{ h.title }}</h4>
              <p>{{ h.bedrooms }}室{{ h.livingRooms }}厅 · {{ h.area }}㎡ · 距{{ h.subwayStationName }}站{{ h.distanceToSubway }}m</p>
              <div class="ke-sb-tags">
                <span v-if="h.subwayLineName" class="ke-sb-tag">🚇 {{ h.subwayLineName }}</span>
                <span class="ke-sb-tag">{{ h.decoration }}</span>
              </div>
              <p class="ke-sb-price"><b>{{ h.price }}</b>元/月</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed, onMounted } from 'vue'
import { subwayApi } from '../api/modules'
import { houseApi } from '../api/house'
import PageHero from '../components/PageHero.vue'
export default {
  name: 'KeSubway',
  components: { PageHero },
  setup(){
    const loading = ref(false)
    const cities = ref([]), lines = ref([]), stations = ref([])
    const selectedCity = ref(''), selectedLine = ref('')
    const selectedStation = ref(null), stationHouses = ref([])
    const totalHouses = ref(0), hotHouses = ref([])

    const hotCities = [
      { name:'北京' }, { name:'上海' }, { name:'广州' }, { name:'深圳' },
      { name:'杭州' }, { name:'成都' }, { name:'武汉' }, { name:'南京' },
    ]
    // 各城市房源数（实时从后端拉取，房源数据更新后自动同步）
    const cityCounts = ref({})

    const fetchHotHouses = async () => {
      try {
        const r = await houseApi.list({ status: 'APPROVED', page: 1, size: 8 })
        if (r.code === 200) hotHouses.value = (r.data?.list || r.data || []).slice(0, 8)
      } catch (e) { console.error('热门房源加载失败:', e) }
    }

    // 实时拉取热门城市的房源总数（数据源为后端，房源变动即同步）
    const fetchCityCounts = async () => {
      const names = hotCities.map(c => c.name)
      await Promise.all(names.map(async (name) => {
        try {
          const r = await houseApi.list({ city: name, status: 'APPROVED', page: 1, size: 1 })
          if (r.code === 200) cityCounts.value[name] = r.data?.total ?? 0
        } catch (e) { cityCounts.value[name] = 0 }
      }))
    }

    onMounted(async () => {
      loading.value = true
      try {
        const r = await subwayApi.cities()
        cities.value = r.code === 200 ? (r.data || []) : []
      } catch (e) { console.error('城市加载失败:', e) }
      finally { loading.value = false }
      fetchHotHouses()
      fetchCityCounts()
    })

    const quickCity = (name) => { selectedCity.value = name; onCityChange() }
    const quickLine = (line) => { selectedLine.value = line; onLineChange() }

    const onCityChange = async () => {
      selectedLine.value = ''; stations.value = []; selectedStation.value = null
      loading.value = true
      try {
        const r = await subwayApi.lines(selectedCity.value)
        lines.value = r.code === 200 ? (r.data || []) : []
      } catch (e) { console.error('线路加载失败:', e) }
      finally { loading.value = false }
    }

    const onLineChange = async () => {
      selectedStation.value = null
      loading.value = true
      try {
        const r = await subwayApi.stations(selectedCity.value, selectedLine.value)
        stations.value = r.code === 200 ? (r.data || []) : []
        // 拉取该城市全部房源后按线路名精确统计（后端 subwayLineName 参数不生效，需本地过滤）
        const hr = await houseApi.list({ city: selectedCity.value, status: 'APPROVED', page: 1, size: 5000 })
        if (hr.code === 200) {
          const list = hr.data?.list || hr.data || []
          totalHouses.value = list.filter(h => h.subwayLineName === selectedLine.value).length
        }
      } catch (e) { console.error('站点加载失败:', e) }
      finally { loading.value = false }
    }

    const showHouses = async (station) => {
      selectedStation.value = { id: station.id, name: station.name }
      loading.value = true
      try {
        const r = await houseApi.list({ subwayStationId: station.id, status: 'APPROVED' })
        stationHouses.value = r.code === 200 ? (r.data?.list || r.data || []) : []
      } catch (e) { stationHouses.value = [] }
      finally { loading.value = false }
    }

    return { loading, cities,lines,stations,totalHouses,selectedCity,selectedLine,selectedStation,stationHouses,hotCities,hotHouses,cityCounts,onCityChange,onLineChange,quickCity,quickLine,showHouses }
  }
}
</script>

<style scoped>
.ke-sb-page { background:var(--jade-paper); min-height:100vh; font-family:-apple-system,BlinkMacSystemFont,'PingFang SC','Microsoft YaHei',sans-serif; }
.ke-sb-search { display:flex; gap:12px; align-items:flex-end; margin-top:8px; }
.ke-sb-sel { display:flex; flex-direction:column; }
.ke-sb-sel label { font-size:12px; color:#666; margin-bottom:4px; }
.ke-sb-sel select { padding:10px 20px; border:none; border-radius:4px; font-size:14px; background:#fff; min-width:160px; cursor:pointer; outline:none; }
.ke-sb-btn { padding:10px 24px; background:#f15b5b; color:#fff; border:none; border-radius:4px; font-size:14px; font-weight:500; cursor:pointer; }
.ke-sb-btn:disabled { opacity:.4; cursor:default; }
.ke-sb-btn:hover:not(:disabled) { background:#e04a4a; }

.ke-sb-body {   padding:24px 32px 60px; }
.ke-sb-h2 { font-size:20px; font-weight:700; color:var(--jade-ink); margin:0 0 8px; }
.ke-sb-stat { font-size:13px; color:#999; font-weight:400; margin-left:6px; }
.ke-sb-tip { font-size:13px; color:#999; margin:0 0 20px; }

.ke-sb-line-stations { display:grid; grid-template-columns:repeat(auto-fill, minmax(180px,1fr)); gap:10px; }
.ke-sb-station-card { display:flex; align-items:center; gap:8px; padding:12px 16px; background:#fff; border-radius:6px; border:1px solid #f0f0f0; cursor:pointer; transition:.2s; }
.ke-sb-station-card:hover { border-color:var(--jade-600); box-shadow:0 2px 8px rgba(22,139,120,.1); }
.ke-sb-station-card.on { border-color:var(--jade-600); background:var(--jade-pale); }
.ke-sb-sidx { color:#999; font-size:12px; min-width:20px; }
.ke-sb-sdot { width:8px; height:8px; border-radius:50%; background:var(--jade-600); }
.ke-sb-sname { flex:1; font-size:14px; color:var(--jade-ink); }
.ke-sb-scnt { font-size:12px; color:var(--jade-600); background:var(--jade-pale); padding:2px 8px; border-radius:10px; }

/* 热门城市卡片 */
.ke-sb-hot { margin-bottom:32px; }
.ke-sb-cities { display:grid; grid-template-columns:repeat(4,1fr); gap:12px; }
.ke-sb-city-card { position:relative; height:90px; border-radius:8px; overflow:hidden; cursor:pointer; padding:16px; color:#fff;
  background:linear-gradient(135deg,var(--jade-ink),#3a3a3f); } /* 图片缺失时的兜底底色 */
.ke-sb-cc-img { position:absolute; inset:0; opacity:.92; z-index:0; background-size:cover; background-position:center; }
.ke-sb-cc-img::after { content:''; position:absolute; inset:0; background:linear-gradient(135deg,rgba(0,0,0,.25),rgba(0,0,0,.45)); }
.ke-sb-city-card:hover .ke-sb-cc-img { transform:scale(1.05); transition:.3s; }
.ke-sb-cc-num { position:relative; z-index:1; font-size:22px; font-weight:700; color:#fff; }
.ke-sb-cc-count { position:relative; z-index:1; font-size:13px; color:#fff; margin-top:4px; opacity:.95; }

/* 线路选择 */
.ke-sb-lines { display:grid; grid-template-columns:repeat(4,1fr); gap:10px; }
.ke-sb-line-card { display:flex; align-items:center; gap:8px; padding:12px 16px; background:#fff; border:1px solid #f0f0f0; border-radius:6px; cursor:pointer; transition:.2s; font-size:14px; color:var(--jade-ink); }
.ke-sb-line-card:hover { border-color:var(--jade-600); background:var(--jade-pale); }
.ke-sb-lc-dot { width:10px; height:10px; border-radius:50%; background:var(--jade-600); }

/* 热门地铁房 */
.ke-sb-feature { margin-top:40px; padding-top:24px; border-top:1px solid #f0f0f0; }
.ke-sb-fh-grid { display:grid; grid-template-columns:repeat(4,1fr); gap:12px; }
.ke-sb-fh-card { background:#fff; border-radius:6px; overflow:hidden; cursor:pointer; transition:.2s; }
.ke-sb-fh-card:hover { box-shadow:0 2px 10px rgba(0,0,0,.08); }
.ke-sb-fh-card img { width:100%; height:120px; object-fit:cover; }
.ke-sb-fh-info { padding:10px 12px; }
.ke-sb-fh-info h4 { font-size:13px; color:var(--jade-ink); margin:0 0 4px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.ke-sb-fh-info p { font-size:11px; color:#999; margin:0 0 4px; }
.ke-sb-fh-info .ke-sb-price b { font-size:16px; color:#f15b5b; }

@media (max-width:800px){ .ke-sb-cities,.ke-sb-lines,.ke-sb-fh-grid{ grid-template-columns:repeat(2,1fr); } }

/* 弹窗 */
.ke-sb-overlay { position:fixed; inset:0; background:rgba(0,0,0,.5); z-index:200; display:flex; align-items:center; justify-content:center; padding:20px; }
.ke-sb-modal { background:#fff; border-radius:8px; width:680px; max-height:80vh; overflow-y:auto; }
.ke-sb-mhd { display:flex; justify-content:space-between; align-items:center; padding:20px 24px; border-bottom:1px solid #f0f0f0; }
.ke-sb-mhd h3 { font-size:18px; color:var(--jade-ink); margin:0 0 4px; }
.ke-sb-mhd p { font-size:13px; color:#999; margin:0; }
.ke-sb-close { background:none; border:none; font-size:20px; color:#999; cursor:pointer; padding:0; width:32px; height:32px; }
.ke-sb-close:hover { color:var(--jade-ink); }
.ke-sb-empty { text-align:center; padding:60px 20px; color:#999; }
.ke-sb-empty svg { margin-bottom:12px; }
.ke-sb-empty p { font-size:14px; margin:0; }
.ke-sb-list { padding:16px 24px 24px; display:flex; flex-direction:column; gap:12px; }
.ke-sb-hcard { display:flex; gap:14px; padding:14px; background:#fafafa; border-radius:6px; cursor:pointer; transition:.2s; }
.ke-sb-hcard:hover { background:var(--jade-pale); }
.ke-sb-hcard img { width:120px; height:90px; object-fit:cover; border-radius:4px; flex-shrink:0; }
.ke-sb-hinfo { flex:1; min-width:0; }
.ke-sb-hinfo h4 { font-size:14px; color:var(--jade-ink); margin:0 0 4px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.ke-sb-hinfo p { font-size:12px; color:#999; margin:0 0 6px; }
.ke-sb-tags { display:flex; gap:6px; margin-bottom:6px; }
.ke-sb-tag { font-size:11px; padding:1px 6px; background:#fff; border:1px solid #e8e8e8; border-radius:2px; color:#666; }
.ke-sb-price b { font-size:18px; color:#f15b5b; }
</style>
