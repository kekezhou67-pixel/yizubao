<template>
  <section class="trust-form">
    <h2>出租资格与房源资料</h2>
    <p>资料仅供本人和平台审核人员查看，不会公开展示或发送给小易。请遮盖证件号码、二维码等无关信息，并添加“仅供易租宝出租审核”水印；保留姓名、房屋地址和权属关系供人工核对。请勿上传他人的材料。</p>
    <div class="trust-grid">
      <label>出租身份<select v-model="form.publisherType"><option value="OWNER">产权人出租</option><option value="AUTHORIZED">受托出租</option></select></label>
      <label>申请人姓名<input v-model.trim="form.applicantName" maxlength="80" /></label>
      <label v-for="item in kinds" :key="item.key">{{ item.label }}<input type="file" accept="image/png,image/jpeg,application/pdf" :disabled="uploading" @change="upload($event,item)" /><small>{{ form[item.key] ? '已上传，可重新选择替换' : 'JPG / PNG / PDF，单份不超过8MB' }}</small></label>
      <label>房源实拍照片（3—9张）<input type="file" accept="image/png,image/jpeg" multiple :disabled="uploading" @change="photos" /><small>已上传 {{ form.photoIds.length }} 张 <button type="button" @click="form.photoIds=[]">重新选图</button></small></label>
      <label>押金月数<input v-model.number="form.depositMonths" type="number" min="0" max="12" /></label><label>付款周期（月）<input v-model.number="form.paymentMonths" type="number" min="1" max="12" /></label>
      <label>服务费（元，无则填0）<input v-model.number="form.serviceFee" type="number" min="0" step="0.01" /></label>
      <label>其他费用<input v-model.trim="form.otherFees" maxlength="1000" placeholder="如：物业费每月100元；无则填无" /></label>
      <label>水电燃气计费<textarea v-model.trim="form.utilityTerms" maxlength="1000" placeholder="请说明计费标准和承担方" /></label>
    </div>
    <label class="trust-consent"><input v-model="form.consent" type="checkbox" />我确认有权出租并提供上述材料，同意平台仅为出租资格人工审核保存与使用材料；了解这不属于官方证件真伪核验。撤回、删除材料可联系平台申请处理。</label>
    <p v-if="error" role="alert">{{ error }}</p><p v-if="uploading" role="status">正在安全上传，请稍候……</p>
    <button class="jade-button" :disabled="busy||uploading||!form.consent" @click="submit">{{ busy?'正在提交…':'提交材料，等待审核' }}</button>
  </section>
</template>
<script setup>
import {ref,reactive,computed} from 'vue'
import request from '../api/index'
const props=defineProps({houseId:{type:Number,required:true},initial:{type:Object,default:null}}),emit=defineEmits(['submitted'])
const form=reactive({publisherType:'OWNER',applicantName:'',identityId:'',propertyId:'',authorizationId:'',photoIds:[],depositMonths:1,paymentMonths:1,serviceFee:0,otherFees:'无',utilityTerms:'',consent:false}),busy=ref(false),uploading=ref(false),error=ref('')
if(props.initial){const a=props.initial;Object.assign(form,{publisherType:a.publisher_type,applicantName:a.applicant_name,identityId:a.identity_id,propertyId:a.property_id,authorizationId:a.authorization_id,photoIds:JSON.parse(a.photo_ids||'[]'),depositMonths:a.deposit_months,paymentMonths:a.payment_months,serviceFee:a.service_fee,otherFees:a.other_fees,utilityTerms:a.utility_terms})}
const kinds=computed(()=>[{key:'identityId',kind:'IDENTITY',label:'已遮盖无关信息的身份材料'},{key:'propertyId',kind:'PROPERTY',label:'已遮盖无关信息的权属证明'},...(form.publisherType==='AUTHORIZED'?[{key:'authorizationId',kind:'AUTHORIZATION',label:'产权人出租授权书'}]:[])])
async function file(f,kind){if(f.size>8*1024*1024)throw Error('文件不得超过8MB');const data=new FormData();data.append('file',f);data.append('kind',kind);return (await request.post('/trust/upload',data,{timeout:120000})).data.id}
async function upload(e,item){const f=e.target.files[0];if(!f)return;uploading.value=true;error.value='';try{form[item.key]=await file(f,item.kind)}catch(e){error.value=e.message}finally{uploading.value=false}}
async function photos(e){const files=[...e.target.files];if(files.length+form.photoIds.length>9){error.value='最多9张照片';return}uploading.value=true;error.value='';try{for(const f of files)form.photoIds.push(await file(f,'PHOTO'))}catch(e){error.value=e.message}finally{uploading.value=false;e.target.value=''}}
async function submit(){busy.value=true;error.value='';try{await request.post('/trust/application/'+props.houseId,form);emit('submitted')}catch(e){error.value=e.response?.data?.message||e.message}finally{busy.value=false}}
</script>
<style scoped>.trust-form{padding:24px;background:#f1f5ee;border:1px solid var(--jade-line);border-radius:8px}.trust-form h2{margin-bottom:14px}.trust-form p,.trust-consent{font-size:13px;line-height:1.9;color:var(--jade-muted)}.trust-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px;margin:22px 0}.trust-grid label{display:flex;flex-direction:column;gap:8px;font-size:13px}.trust-grid input,.trust-grid select,.trust-grid textarea{padding:10px;border:1px solid var(--jade-line);background:white;border-radius:4px;max-width:100%}.trust-grid small{color:var(--jade-muted)}.trust-consent{display:block;margin:20px 0}.trust-consent input{margin-right:8px}@media(max-width:650px){.trust-grid{grid-template-columns:1fr}}</style>
