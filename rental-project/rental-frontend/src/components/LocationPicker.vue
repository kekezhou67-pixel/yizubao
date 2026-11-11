<template><div><p>请拖动地图找到房屋，点击实际位置落点；滚轮可缩放。坐标须与填写地址一致。</p><div ref="element" class="location-picker"/><div class="location-coordinates"><label>纬度<input type="number" step="0.0000001" :value="latitude" @input="$emit('update:latitude',Number($event.target.value))"/></label><label>经度<input type="number" step="0.0000001" :value="longitude" @input="$emit('update:longitude',Number($event.target.value))"/></label></div></div></template>
<script setup>
import {ref,onMounted,onBeforeUnmount,watch} from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
const props=defineProps({latitude:Number,longitude:Number,city:String}),emit=defineEmits(['update:latitude','update:longitude']),element=ref(null)
let map,marker
const centers={'杭州':[30.2741,120.1551],'北京':[39.9042,116.4074],'上海':[31.2304,121.4737],'深圳':[22.5431,114.0579],'广州':[23.1291,113.2644],'成都':[30.5728,104.0668],'宁波':[29.8683,121.5440]}
function position(){if(!map)return;if(props.latitude&&props.longitude){const p=[props.latitude,props.longitude];if(!marker)marker=L.circleMarker(p,{radius:9,color:'#168b78',fillOpacity:.8}).addTo(map);else marker.setLatLng(p);map.panTo(p)}}
onMounted(()=>{map=L.map(element.value,{scrollWheelZoom:true}).setView(centers[props.city]||centers['杭州'],12);L.tileLayer('https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',{subdomains:['1','2','3','4'],maxZoom:18,attribution:'高德地图'}).addTo(map);map.on('click',e=>{emit('update:latitude',Number(e.latlng.lat.toFixed(7)));emit('update:longitude',Number(e.latlng.lng.toFixed(7)))});position()})
watch(()=>[props.latitude,props.longitude],position);watch(()=>props.city,c=>{if(map&&centers[c])map.setView(centers[c],12)});onBeforeUnmount(()=>map?.remove())
</script>
<style scoped>.location-picker{height:330px;z-index:0;border:1px solid var(--jade-line);border-radius:6px}.location-coordinates{display:flex;gap:16px;margin:12px 0}.location-coordinates input{display:block;padding:10px;border:1px solid var(--jade-line);max-width:100%}p,label{font-size:13px;line-height:1.8}p{margin:18px 0}@media(max-width:650px){.location-coordinates{flex-direction:column}}</style>
