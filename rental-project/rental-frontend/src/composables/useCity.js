import { ref, readonly } from 'vue'
export const cityCenters = [
 ['杭州',30.2741,120.1551],['深圳',22.5431,114.0579],['北京',39.9042,116.4074],['上海',31.2304,121.4737],['广州',23.1291,113.2644],['成都',30.5728,104.0668],['武汉',30.5928,114.3055],['南京',32.0603,118.7969],['苏州',31.2989,120.5853],['长沙',28.2282,112.9388],['重庆',29.563,106.5516],['西安',34.3416,108.9398],['天津',39.0842,117.2009],['青岛',36.0671,120.3826],['郑州',34.7466,113.6254],['佛山',23.0215,113.1214],['东莞',23.0207,113.7518],['合肥',31.8206,117.2272],['厦门',24.4798,118.0894],['宁波',29.8683,121.544]
]
export const cities=cityCenters.map(c=>c[0])
let saved
try { saved=localStorage.getItem('yzb-city') } catch {}
const currentCity=ref(cities.includes(saved)?saved:'杭州')
export function setCurrentCity(value){if(!cities.includes(value))return;currentCity.value=value;try{localStorage.setItem('yzb-city',value)}catch{}}
export function nearbyCity(latitude,longitude){
 if(!Number.isFinite(latitude)||!Number.isFinite(longitude)||Math.abs(latitude)>90||Math.abs(longitude)>180)return null
 const rad=n=>n*Math.PI/180
 const list=cityCenters.map(([name,lat,lon])=>{const a=Math.sin(rad(lat-latitude)/2)**2+Math.cos(rad(latitude))*Math.cos(rad(lat))*Math.sin(rad(lon-longitude)/2)**2;return {name,distance:6371*2*Math.atan2(Math.sqrt(a),Math.sqrt(Math.max(0,1-a)))}}).sort((a,b)=>a.distance-b.distance)
 return list[0].distance<=80?list[0]:null
}
export function useCity(){return {currentCity:readonly(currentCity),setCurrentCity}}
