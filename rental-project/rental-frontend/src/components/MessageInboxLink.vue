<template><RouterLink class="message-link" to="/app/messages" aria-label="我的消息"><span>消息</span><b v-if="count">{{ count>99?'99+':count }}</b></RouterLink></template>
<script setup>
import {ref,onMounted,onBeforeUnmount} from 'vue'
import {messagesApi} from '../api/messages'
const count=ref(0);let timer,alive=true,busy=false
async function load(){if(busy||document.hidden)return;busy=true;try{const r=await messagesApi.unread();if(alive)count.value=Number(r.data)||0}catch{}finally{busy=false}}
onMounted(()=>{load();timer=setInterval(load,5000)})
onBeforeUnmount(()=>{alive=false;clearInterval(timer)})
</script>
<style scoped>.message-link{display:flex;align-items:center;gap:5px;color:var(--jade-900);font-size:12px;white-space:nowrap}.message-link b{background:var(--jade-600);color:#fff;font-size:9px;font-weight:400;min-width:15px;line-height:15px;padding:0 3px;border-radius:9px;text-align:center}</style>
