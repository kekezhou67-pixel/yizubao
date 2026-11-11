<template>
 <div ref="root" class="city-picker" @keydown.esc.stop.prevent="close(true)">
  <button ref="trigger" type="button" class="city-trigger" :aria-expanded="open" :aria-controls="uid" @click="toggle"><span>{{ currentCity }} <span class="chevron" aria-hidden="true">⌄</span></span><small>换个城市</small></button>
  <Transition name="city-pop"><section v-if="open" :id="uid" class="city-panel" aria-label="选择找房城市">
   <div class="city-heading"><div><h2>你想住在哪座城？</h2><p>选择后，首页和找房结果将一起更新。</p></div><button type="button" class="city-close" aria-label="关闭城市选择" @click="close(true)">×</button></div>
   <label class="city-search"><span class="sr-only">搜索城市</span><input ref="searchBox" v-model="search" placeholder="输入城市名" type="search" /></label>
   <div class="city-current">当前城市 <strong>{{ currentCity }}</strong><span>已记住你的选择</span></div>
   <div class="city-options"><button v-for="name in filtered" :key="name" type="button" :class="{selected:name===currentCity}" :aria-pressed="name===currentCity" @click="choose(name)">{{ name }}<span v-if="name===currentCity" aria-hidden="true"> ✓</span></button></div>
   <p v-if="!filtered.length" class="city-empty">暂未开通这座城市，请选择其他城市。</p>
   <div class="city-location"><button type="button" :disabled="locating" @click="locate">{{ locating?'正在定位…':'◎ 定位并切换城市' }}</button><p>仅在点击后请求位置授权；坐标仅用于本机匹配，不上传。按距离匹配附近已开通城市，成功后直接切换；定位不等同于精确行政区识别。</p><p v-if="locationMessage" role="status">{{ locationMessage }}</p></div>
  </section></Transition>
 </div>
</template>
<script setup>
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount, useId } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { cities, useCity, nearbyCity } from '../composables/useCity'
const emit=defineEmits(['open']),route=useRoute(),router=useRouter(),{currentCity,setCurrentCity}=useCity(),uid=useId()
const root=ref(null),trigger=ref(null),searchBox=ref(null),open=ref(false),search=ref(''),locating=ref(false),locationMessage=ref('')
const filtered=computed(()=>cities.filter(name=>name.includes(search.value.trim().replace(/市$/,''))))
let locationVersion=0
watch(()=>route.query.city,value=>{if(cities.includes(value))setCurrentCity(value)},{immediate:true})
watch(()=>route.fullPath,()=>close())
async function toggle(){if(open.value)return close();emit('open');open.value=true;search.value='';await nextTick();searchBox.value?.focus()}
function close(focus=false){open.value=false;locationVersion++;locating.value=false;locationMessage.value='';if(focus)trigger.value?.focus()}
async function choose(name){setCurrentCity(name);close(true);await router.replace({path:route.path,query:{...route.query,city:name},hash:route.hash})}
function outside(event){if(open.value&&!root.value?.contains(event.target))close()}
function leave(event){if(open.value && event.relatedTarget && !root.value?.contains(event.relatedTarget))close()}
function locate(){
 if(locating.value)return
 locationMessage.value=''
 if(!navigator.geolocation){locationMessage.value='浏览器不支持定位，请手动选择城市。';return}
 const version=++locationVersion;locating.value=true
 navigator.geolocation.getCurrentPosition(position=>{
  if(version!==locationVersion||!open.value)return
  locating.value=false
  if(position.coords.accuracy>20000){locationMessage.value='定位范围过大，请手动选择城市。';return}
  const result=nearbyCity(position.coords.latitude,position.coords.longitude)
  if(!result){locationMessage.value='附近暂无已开通城市，请手动选择。';return}
  choose(result.name)
 },error=>{if(version!==locationVersion||!open.value)return;locating.value=false;locationMessage.value=error.code===1?'未获得位置授权，你仍可手动选择城市。':error.code===3?'定位超时，请重试或手动选择城市。':'暂时无法获取位置，请手动选择城市。'}, {enableHighAccuracy:false,timeout:12000,maximumAge:0})
}
onMounted(()=>{document.addEventListener('pointerdown',outside);root.value?.addEventListener('focusout',leave)})
onBeforeUnmount(()=>{locationVersion++;document.removeEventListener('pointerdown',outside);root.value?.removeEventListener('focusout',leave)})
defineExpose({close})
</script>
<style scoped>
.city-picker{position:relative;flex-shrink:0}.city-trigger{border:0;border-left:1px solid var(--jade-line);padding:0 0 0 20px;background:none;color:var(--jade-900);line-height:1.5;text-align:left;white-space:nowrap;font:inherit;cursor:pointer}.city-trigger small{display:block;font-size:11px;color:var(--jade-muted);margin-top:3px}.chevron{display:inline-block;font-size:13px;margin-left:4px;transition:transform .2s}.city-trigger[aria-expanded=true] .chevron{transform:rotate(180deg)}.city-panel{position:absolute;top:calc(100% + 24px);left:0;width:430px;max-width:calc(100vw - 40px);background:#fffefa;border:1px solid var(--jade-line);border-radius:9px;box-shadow:0 18px 55px #103d3626;padding:24px;max-height:calc(100svh - var(--header-height) - 30px);overflow:auto}.city-heading{display:flex;justify-content:space-between;gap:15px}.city-heading h2{font-family:var(--font-display);font-size:21px;font-weight:500;line-height:1.5}.city-heading p{font-size:11px;color:var(--jade-muted);margin:7px 0 17px}.city-close{align-self:flex-start;border:0;background:none;font-size:23px;color:var(--jade-muted);padding:0 2px;cursor:pointer}.city-search input{width:100%;padding:11px 13px;background:#f6f8f3;border:1px solid var(--jade-line);border-radius:5px;font:inherit;font-size:13px;outline:none}.city-search input:focus{border-color:var(--jade-600)}.city-current{display:flex;align-items:center;gap:10px;font-size:11px;color:var(--jade-muted);margin:17px 0 12px}.city-current strong{font-size:13px;color:var(--jade-700)}.city-current>span{margin-left:auto;font-size:10px}.city-options{display:grid;grid-template-columns:repeat(5,1fr);gap:6px}.city-options button{border:1px solid transparent;background:none;padding:9px 2px;border-radius:4px;color:var(--jade-ink);font-size:12px;cursor:pointer}.city-options button:hover,.city-options button.selected{background:var(--jade-pale);color:var(--jade-700);border-color:#c5d9ca}.city-empty{padding:20px 0;color:var(--jade-muted);font-size:12px}.city-location{margin-top:18px;padding-top:16px;border-top:1px solid var(--jade-line)}.city-location button{background:none;border:0;color:var(--jade-700);font-size:12px;padding:0;cursor:pointer}.city-location button:disabled{opacity:.5;cursor:wait}.city-location p{font-size:10px;line-height:1.8;color:var(--jade-muted);margin-top:8px}.city-location .city-candidate{margin-top:10px;padding:8px 12px;background:var(--jade-pale);border-radius:4px}.city-pop-enter-active,.city-pop-leave-active{transition:opacity .2s,transform .2s}.city-pop-enter-from,.city-pop-leave-to{opacity:0;transform:translateY(-7px)}button:focus-visible{outline:2px solid var(--jade-600);outline-offset:3px}.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}@media(prefers-reduced-motion:reduce){.city-pop-enter-active,.city-pop-leave-active,.chevron{transition:none}}@media(max-width:1100px){.city-trigger{padding-left:12px;font-size:12px}.city-trigger small{display:none}.city-panel{left:-120px}}@media(max-width:800px){.city-panel{position:fixed;top:calc(var(--header-height) + 8px);left:20px;right:20px;width:auto}.city-trigger{padding-left:10px}.city-options{grid-template-columns:repeat(4,1fr)}}
</style>
