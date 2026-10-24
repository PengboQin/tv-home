/* Pure discovery/command model for design fixtures; no network or credential access. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.HaModel=api;})(typeof globalThis!=='undefined'?globalThis:this,function(){
 const supported=new Set(['weather','light','switch','automation']);
 const weatherNames={'clear-night':'晴朗夜间',cloudy:'多云',fog:'雾',hail:'冰雹',lightning:'雷电','lightning-rainy':'雷雨',partlycloudy:'晴间多云',pouring:'大雨',rainy:'下雨',snowy:'下雪','snowy-rainy':'雨夹雪',sunny:'晴朗',windy:'有风','windy-variant':'多云有风',exceptional:'异常天气'};
 function finite(v){return typeof v==='number'&&Number.isFinite(v)?v:null;}
 function discover(states,registry={},options={}){
  const entities=new Map((registry.entities||[]).map(e=>[e.entity_id,e])),devices=new Map((registry.devices||[]).map(d=>[d.id,d])),areas=new Map((registry.areas||[]).map(a=>[a.area_id,a.name]));
  const allowed=new Set(options.allowedEntities||[]);const mapped=[];for(const raw of states){const id=raw.entity_id||'',domain=id.split('.')[0],a=raw.attributes||{},m=entities.get(id)||{};
   if(!supported.has(domain)||m.disabled||m.hidden||['config','diagnostic'].includes(m.category))continue;
   const areaId=m.area_id||devices.get(m.device_id)?.area_id||null,reachable=!['unknown','unavailable'].includes(raw.state),binary=['on','off'].includes(raw.state),online=options.online!==false;
   const e={id,domain,name:a.friendly_name||id,areaId,areaName:areas.get(areaId)||'未分组',state:raw.state,online,available:online&&reachable&&(domain==='weather'||binary),lastUpdated:raw.last_updated||null,readOnly:!allowed.has(id)};
   if(domain==='weather')Object.assign(e,{condition:weatherNames[raw.state]||'天气未提供',icon:raw.state==='sunny'?'sun':raw.state==='clear-night'?'moon':['rainy','pouring','lightning-rainy'].includes(raw.state)?'rain':'cloud',temperature:finite(a.temperature),temperatureUnit:typeof a.temperature_unit==='string'?a.temperature_unit:(options.temperatureUnit||'°'),humidity:finite(a.humidity),apparentTemperature:finite(a.apparent_temperature),uvIndex:finite(a.uv_index),windSpeed:finite(a.wind_speed),windSpeedUnit:typeof a.wind_speed_unit==='string'?a.wind_speed_unit:null});
   else if(domain==='automation'){const count=finite(a.current),current=count!==null&&Number.isInteger(count)&&count>=0?count:null;Object.assign(e,{enabled:binary?raw.state==='on':null,current,mode:typeof a.mode==='string'?a.mode:null,lastTriggered:typeof a.last_triggered==='string'&&Number.isFinite(Date.parse(a.last_triggered))?a.last_triggered:null,runningLabel:current===null?'未提供运行状态':current===0?'空闲':a.mode==='queued'?`${current} 项运行 / 排队`:`${current} 项进行中`});}
   else e.on=binary?raw.state==='on':null;
   mapped.push(e);
  }
  mapped.sort((a,b)=>Number(b.available)-Number(a.available)||a.name.localeCompare(b.name,'zh-CN')||a.id.localeCompare(b.id));
  return {entities:mapped,weather:mapped.filter(e=>e.domain==='weather'),switches:mapped.filter(e=>e.domain==='light'||e.domain==='switch'),automations:mapped.filter(e=>e.domain==='automation'),areas:[...new Map(mapped.filter(e=>e.areaId).map(e=>[e.areaId,{id:e.areaId,name:e.areaName}])).values()]};
 }
 function actionFor(entity,targetOn){
  if(!entity||!entity.online||!entity.available||entity.readOnly||!['light','switch','automation'].includes(entity.domain)||typeof targetOn!=='boolean')return null;
  return {type:'call_service',domain:entity.domain,service:targetOn?'turn_on':'turn_off',target:{entity_id:entity.id},service_data:entity.domain==='automation'&&!targetOn?{stop_actions:false}:{}};
 }
 return {discover,actionFor,weatherNames};
});
