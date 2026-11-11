<template>
  <div class="ke-ph">
    <PageHero title="发布房源" subtitle="认真介绍你的房子，让合适的租客找到它。" />
    <div class="ke-ph-inner">
      <div class="ke-ph-steps">
        <div class="ke-ph-step on"><span>1</span>填写房源信息</div>
        <div class="ke-ph-step"><span>2</span>提交房源</div>
        <div class="ke-ph-step"><span>3</span>等待审核</div>
      </div>

      <div class="ke-ph-card">
        <h2>{{ editId ? '编辑房源' : '发布新房源' }}</h2><p v-if="savedId">基本信息已保存，请继续提交出租资格材料；审核通过后才会公开。<RouterLink to="/app/workflow">查看办理进度 →</RouterLink></p>
        <div class="ke-ph-grid">
          <div class="ke-ph-field"><label>房源标题</label><input v-model="form.title" placeholder="如：整租·松坪村·2室1厅·精装·近地铁" /></div>
          <div class="ke-ph-field"><label>城市</label><input v-model="form.city" placeholder="如：深圳" /></div>
          <div class="ke-ph-field"><label>区域</label><input v-model="form.district" placeholder="如：南山区" /></div>
          <div class="ke-ph-field"><label>详细地址</label><input v-model="form.address" placeholder="如：松坪街12号松坪村5栋1806" /></div>
          <div class="ke-ph-field"><label>月租金（元）</label><input v-model.number="form.price" type="number" placeholder="如：5500" /></div>
          <div class="ke-ph-field"><label>面积（㎡）</label><input v-model.number="form.area" type="number" placeholder="如：65" /></div>
          <div class="ke-ph-field"><label>户型</label>
            <div class="ke-ph-hx"><select v-model.number="form.bedrooms"><option value="1">1室</option><option value="2">2室</option><option value="3">3室</option><option value="4">4室</option></select>
            <select v-model.number="form.livingRooms"><option value="0">0厅</option><option value="1">1厅</option><option value="2">2厅</option></select>
            <select v-model.number="form.bathrooms"><option value="1">1卫</option><option value="2">2卫</option></select></div>
          </div>
          <div class="ke-ph-field"><label>楼层</label><input v-model="form.floorInfo" placeholder="如：18/34层" /></div>
          <div class="ke-ph-field"><label>朝向</label><select v-model="form.orientation"><option>南</option><option>北</option><option>东南</option><option>西南</option><option>东北</option><option>西北</option><option>东</option><option>西</option></select></div>
          <div class="ke-ph-field"><label>装修</label><select v-model="form.decoration"><option>精装</option><option>豪装</option><option>简装</option><option>毛坯</option></select></div>
          <div class="ke-ph-field"><label>联系电话</label><input v-model="form.contactPhone" placeholder="如：13828882301" /></div>
        </div>
        <div class="ke-ph-field full"><label>配套设施（逗号分隔）</label><input v-model="form.facilities" placeholder="如：冰箱,洗衣机,空调,热水器,宽带,衣柜,沙发" /></div>
        <div class="ke-ph-field full"><label>房源描述</label><textarea v-model="form.description" rows="6" placeholder="详细描述房源情况，包括周边配套、交通、小区环境等..."></textarea></div>

        <LocationPicker v-if="!savedId" v-model:latitude="form.latitude" v-model:longitude="form.longitude" :city="form.city"/><button v-if="!savedId" class="ke-ph-submit" @click="submit" :disabled="submitting">{{ submitting ? '提交中...' : '提交房源' }}</button>
        <p class="ke-ph-tip" v-if="submitResult">{{ submitResult }}</p>
      </div>
      <TrustApplication v-if="savedId" :house-id="savedId" :initial="initial" @submitted="materialsDone=true"/><p v-if="materialsDone" role="status">完整申请已提交，审核结果请到“个人中心—我的办理进度”查看。</p>
    </div>
  </div>
</template>

<script>
import { ref, reactive, onMounted } from 'vue'
import { houseApi } from '../api/house'
import {useRoute} from 'vue-router'
import request from '../api/index'
import LocationPicker from '../components/LocationPicker.vue'
import TrustApplication from '../components/TrustApplication.vue'
import PageHero from '../components/PageHero.vue'
export default {
  name: 'KePublish',
  components: { PageHero, TrustApplication, LocationPicker },
  setup(){
    const route=useRoute(),editId=Number(route.query.edit)||0,savedId=ref(0),initial=ref(null),materialsDone=ref(false)

    const form = reactive({ title:'',city:'',district:'',address:'',latitude:null,longitude:null,price:'',area:'',bedrooms:2,livingRooms:1,bathrooms:1,floorInfo:'',orientation:'南',decoration:'精装',contactPhone:'',facilities:'',description:'' })
    const submitting = ref(false); const submitResult = ref('')

    const submit = async () => {
      if(!form.title||!form.city||!form.price){ submitResult.value='请填写标题、城市和价格'; return }
      submitting.value=true; submitResult.value=''
      try {
        const r = editId ? await request.put('/house/update',{...form,id:editId}) : await houseApi.publish(form)
        if(r.code===200)savedId.value=editId||r.data.id
        submitResult.value = r.code===200 ? '提交成功！等待审核中...' : (r.message||'提交失败')
      } catch(e){ submitResult.value = e.message || '网络错误' } finally { submitting.value=false }
    }
    onMounted(async()=>{if(editId){try{const r=(await request.get('/trust/application/'+editId)).data;initial.value=r.application;const raw=r.house;for(const key of Object.keys(form)){const snake=key.replace(/[A-Z]/g,c=>'_'+c.toLowerCase());if(raw[snake]!=null)form[key]=raw[snake]}}catch(e){submitResult.value=e.message}}})
    return { form, submitting, submitResult, submit,editId,savedId,initial,materialsDone }
  }
}
</script>

<style scoped>
.ke-ph { background:var(--jade-paper); min-height:100vh; font-family:-apple-system,BlinkMacSystemFont,'PingFang SC','Microsoft YaHei',sans-serif; }
.ke-ph-inner {   padding:32px 24px; }
.ke-ph-steps { display:flex; gap:0; margin-bottom:28px; }
.ke-ph-step { flex:1; text-align:center; padding:12px; background:#f0f0f0; color:#999; font-size:14px; }
.ke-ph-step:first-child{ border-radius:6px 0 0 6px; }
.ke-ph-step:last-child{ border-radius:0 6px 6px 0; }
.ke-ph-step span { display:inline-block; width:24px; height:24px; line-height:24px; border-radius:50%; background:#ccc; color:#fff; margin-right:6px; font-size:12px; }
.ke-ph-step.on { background:var(--jade-600); color:#fff; }
.ke-ph-step.on span { background:#fff; color:var(--jade-600); }
.ke-ph-card { background:#fff; border-radius:8px; padding:32px; }
.ke-ph-card h2 { font-size:20px; font-weight:700; color:var(--jade-ink); margin:0 0 24px; }
.ke-ph-grid { display:grid; grid-template-columns:repeat(2,1fr); gap:16px; }
.ke-ph-field { display:flex; flex-direction:column; gap:4px; }
.ke-ph-field.full { grid-column:span 2; }
.ke-ph-field label { font-size:13px; color:#666; font-weight:500; }
.ke-ph-field input,select,textarea { padding:8px 12px; border:1px solid #e0e0e0; border-radius:4px; font-size:14px; outline:none; }
.ke-ph-field input:focus,select:focus,textarea:focus { border-color:var(--jade-600); }
.ke-ph-hx { display:flex; gap:6px; }
.ke-ph-hx select { flex:1; }
.ke-ph-submit { width:100%; padding:14px; background:var(--jade-600); color:#fff; border:none; border-radius:6px; font-size:16px; font-weight:600; cursor:pointer; margin-top:24px; }
.ke-ph-submit:hover { background:var(--jade-700); }
.ke-ph-submit:disabled { opacity:.6; }
.ke-ph-tip { text-align:center; font-size:14px; color:#52c41a; margin-top:12px; }
</style>
