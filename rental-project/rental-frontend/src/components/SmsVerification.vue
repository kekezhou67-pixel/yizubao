<template>
 <div class="sms-verification">
  <template v-if="!sentForCurrent">
   <label :for="uid">图形验证 <span>不区分大小写</span></label>
   <div class="picture-row"><input :id="uid" v-model="answer" placeholder="输入右侧字符" maxlength="5" autocomplete="off" :disabled="!!ticket || busy" /><button type="button" class="captcha-picture" @click="refresh" :disabled="busy" aria-label="换一张图形验证码"><img v-if="image" :src="image" alt="图形验证码，点击换一张" /><span v-else>点击加载图片</span></button></div>
   <div class="captcha-actions"><button type="button" @click="verify" :disabled="!answer || !image || !!ticket || busy">{{ ticket?'✓ 图形验证通过':'验证图形' }}</button><button type="button" @click="refresh" :disabled="busy">换一张</button></div>
  </template>
  <p v-else class="captcha-success" role="status">✓ 图形验证已通过，请填写手机收到的短信验证码。</p>
  <p v-if="error" class="captcha-error" role="alert">{{ error }}</p>
  <div class="sms-row"><slot /><button type="button" class="send-sms" @click="send" :disabled="busy || remaining>0 || (!ticket && !sentForCurrent)">{{ remaining?`${remaining} 秒后重试`:sentForCurrent?'重新获取手机验证码':'获取手机验证码' }}</button></div>
  <p class="captcha-note" aria-live="polite">{{ note || '先完成图形验证，再获取手机验证码。' }}</p>
 </div>
</template>
<script setup>
import { ref, computed, useId, watch, onMounted, onBeforeUnmount } from 'vue'
import { userApi } from '../api/user'
const props=defineProps({target:{type:String,default:''}}),emit=defineEmits(['sent','target-change']),uid=useId()
const answer=ref(''),image=ref(''),id=ref(''),ticket=ref(''),error=ref(''),note=ref(''),busy=ref(false),remaining=ref(0),sentTarget=ref('')
const sentForCurrent=computed(()=>!!sentTarget.value && sentTarget.value===props.target.trim())
let deadline=0,timer,permitTimer,disposed=false,revision=0
function clearPermit(){clearTimeout(permitTimer);ticket.value=''}
async function load(){
 clearPermit();answer.value='';image.value='';id.value=''
 const res=await userApi.captcha()
 if(!disposed){id.value=res.data.id;image.value=res.data.image}
}
async function refresh(){
 if(busy.value)return
 busy.value=true;error.value='';note.value='';sentTarget.value=''
 try{await load()}catch(e){if(!disposed)error.value=e.message||'图片加载失败，请重试'}finally{busy.value=false}
}
async function verify(){
 if(busy.value || ticket.value)return
 if(!/^1[3-9]\d{9}$/.test(props.target.trim())){error.value='请先输入正确的11位手机号';return}
 const target=props.target.trim(),current=revision;busy.value=true;error.value=''
 try{
  const res=await userApi.verifyCaptcha({id:id.value,answer:answer.value.trim(),target})
  if(current===revision&&!disposed){
   ticket.value=res.data.ticket
   permitTimer=setTimeout(()=>{clearPermit();refresh().then(()=>{if(!disposed)note.value='图形验证已过期，请重新验证'})},115000)
  }
 }catch(e){if(current===revision&&!disposed){try{await load()}catch(_){}error.value=e.message||'图形验证失败，请换一张重试'}}
 finally{if(current!==revision&&!disposed){try{await load()}catch(e){error.value=e.message||'图片加载失败，请重试'}}busy.value=false}
}
async function send(){
 if(busy.value||remaining.value)return
 // A resend needs a new one-use permit. Successful delivery itself never resets the picture.
 if(sentForCurrent.value&&!ticket.value){await refresh();if(!disposed)note.value='请完成图形验证后重新获取短信；已有短信验证码在有效期内仍可使用。';return}
 if(!ticket.value)return
 const permit=ticket.value,target=props.target.trim(),current=revision
 busy.value=true;error.value='';clearPermit()
 try{
  await userApi.sendCode(target,permit)
  if(current!==revision||disposed)return
  sentTarget.value=target;deadline=Date.now()+60000;remaining.value=60
  note.value='短信验证码5分钟内有效，填写后即可提交，无需再次输入图形验证码。'
  emit('sent')
 }catch(e){
  if(current===revision&&!disposed){sentTarget.value='';note.value='';try{await load()}catch(_){}error.value=e.message||'发送失败，请重新完成图形验证'}
 }finally{if(current!==revision&&!disposed){try{await load()}catch(e){error.value=e.message||'图片加载失败，请重试'}}busy.value=false}
}
watch(()=>props.target.trim(),()=>{const spent=!!sentTarget.value||!!ticket.value;revision++;clearPermit();sentTarget.value='';deadline=0;remaining.value=0;note.value='';answer.value='';error.value='';emit('target-change');if(spent&&!busy.value)refresh()})
onMounted(()=>{refresh();timer=setInterval(()=>{remaining.value=Math.max(0,Math.ceil((deadline-Date.now())/1000))},500)})
onBeforeUnmount(()=>{disposed=true;clearInterval(timer);clearTimeout(permitTimer)})
</script>
<style scoped>
.captcha-success{padding:11px 13px;background:#edf7f2;border:1px solid #c9dfd4;border-radius:5px;color:#087d69;font-size:12px;line-height:1.7;margin-bottom:13px}.sms-verification{margin:18px 0}.sms-verification label{display:block;font-size:13px;font-weight:600;margin-bottom:8px}.sms-verification label span{font-size:11px;font-weight:400;color:#77857f;margin-left:8px}.picture-row{display:flex;gap:10px}.picture-row input,.sms-row :deep(input){min-width:0;width:100%;height:49px;padding:0 13px;border:1px solid #dce5df;border-radius:6px;background:#fff;outline:none;color:#163e35;font:inherit;font-size:14px}.picture-row input:focus,.sms-row :deep(input:focus){border-color:#168b78;box-shadow:0 0 0 3px #168b7812}.captcha-picture{padding:0;flex:0 0 145px;height:49px;overflow:hidden;border:1px solid #dce5df;border-radius:5px;background:#ebf4ef;font-size:12px}.captcha-picture img{width:100%;height:100%;object-fit:fill}.captcha-actions{display:flex;justify-content:space-between;margin:9px 0 17px}.captcha-actions button{background:none;border:0;font:inherit;color:#087d69;font-size:12px;padding:3px 0;cursor:pointer}.captcha-actions button:disabled{opacity:.6;cursor:default}.sms-row{display:flex;gap:10px}.send-sms{flex:0 0 145px;border:1px solid #168b78;background:#edf7f2;border-radius:5px;color:#087d69;font:inherit;font-size:12px;cursor:pointer}.send-sms:disabled{background:#f0f2ef;border-color:#e2e6e0;color:#8c9790;cursor:not-allowed}.captcha-error{color:#ab3c35;font-size:12px;margin:8px 0}.captcha-note{font-size:11px;color:#77857f;line-height:1.6;margin:8px 0 0}
</style>
