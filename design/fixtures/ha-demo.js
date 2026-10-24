/* Synthetic standard HA states. No actual household IDs, addresses or data. */
window.HaDemo={
 config:{time_zone:"Asia/Shanghai"},
 profile:{allowedEntities:['light.demo_main','light.demo_spot','switch.demo_lamp','switch.demo_desk','switch.demo_porch','switch.demo_aux','automation.demo_arrival','automation.demo_evening','automation.demo_air','automation.demo_weekend']},
 registry:{areas:[{area_id:'demo_living',name:'客厅'},{area_id:'demo_study',name:'书房'},{area_id:'demo_porch',name:'玄关'}],devices:[{id:'demo_device_main',area_id:'demo_living'}],entities:[{entity_id:'light.demo_main',device_id:'demo_device_main'},{entity_id:'light.demo_spot',area_id:'demo_living'},{entity_id:'switch.demo_lamp',area_id:'demo_living'},{entity_id:'switch.demo_desk',area_id:'demo_study'},{entity_id:'switch.demo_porch',area_id:'demo_porch'}]},
 states:[
 {entity_id:'weather.demo_home',state:'partlycloudy',attributes:{friendly_name:'家庭天气',temperature:24,temperature_unit:'°C',humidity:56,apparent_temperature:23,uv_index:3,wind_speed:8,wind_speed_unit:'km/h'}},
 {entity_id:'light.demo_main',state:'on',attributes:{friendly_name:'主照明'}},
 {entity_id:'light.demo_spot',state:'off',attributes:{friendly_name:'射灯'}},
 {entity_id:'switch.demo_lamp',state:'on',attributes:{friendly_name:'落地灯'}},
 {entity_id:'switch.demo_desk',state:'off',attributes:{friendly_name:'书桌灯'}},
 {entity_id:'switch.demo_porch',state:'off',attributes:{friendly_name:'玄关灯'}},
 {entity_id:'switch.demo_aux',state:'unavailable',attributes:{friendly_name:'辅助照明'}},
 {entity_id:'automation.demo_arrival',state:'on',attributes:{friendly_name:'回家照明',current:0,mode:'single',last_triggered:'2026-10-23T12:32:00+08:00'}},
 {entity_id:'automation.demo_evening',state:'on',attributes:{friendly_name:'晚间灯光',current:0,mode:'restart',last_triggered:'2026-10-23T19:00:00+08:00'}},
 {entity_id:'automation.demo_air',state:'on',attributes:{friendly_name:'定时换气',current:2,mode:'queued',last_triggered:'2026-10-23T21:00:00+08:00'}},
 {entity_id:'automation.demo_weekend',state:'off',attributes:{friendly_name:'周末模式',last_triggered:null}}
 ],
 forecast:[{day:'周六',condition:'sunny',high:25,low:18},{day:'周日',condition:'partlycloudy',high:24,low:18},{day:'周一',condition:'rainy',high:21,low:17},{day:'周二',condition:'cloudy',high:22,low:16}],
 apps:[['本地播放器','play'],['媒体库','tv'],['文件管理','nas'],['自己的 App','apps'],['VPN','shield'],['浏览器','link'],['音乐','moon'],['电视设置','settings']]
};
