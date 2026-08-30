<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ -->
<script setup>
import {computed,onMounted,reactive,ref} from 'vue'
import {api} from '../api'

const props=defineProps({username:{type:String,required:true}})
const emit=defineEmits(['error'])
const summary=ref(null),orders=ref([]),loading=ref(false),showOrder=ref(false),showReport=ref(false),reportTarget=ref(null)
const filters=reactive({status:'',plantCode:'',keyword:'',exceptionOnly:false})
const today=new Date().toISOString().slice(0,10)
const endDate=new Date(Date.now()+7*86400000).toISOString().slice(0,10)
const form=reactive({orderNo:'',productCode:'',productName:'',plantCode:'ZH-SH',lineCode:'LINE-A',plannedQuantity:100,priority:5,plannedStartDate:today,plannedEndDate:endDate})
const report=reactive({eventKey:'',goodQuantity:0,scrapQuantity:0,equipmentCode:'EQ-A01',shiftCode:'DAY',reasonCode:''})
const isAdmin=computed(()=>props.username==='admin')
const statusText={DRAFT:'草稿',RELEASED:'已下达',IN_PROGRESS:'生产中',PAUSED:'已暂停',COMPLETED:'已完工',CANCELLED:'已取消'}

async function load(){loading.value=true;try{[orders.value,summary.value]=await Promise.all([api.productionOrders(filters),api.executionSummary()])}catch(e){emit('error',e.message)}finally{loading.value=false}}
function openCreate(){Object.assign(form,{orderNo:`MO-${new Date().toISOString().replace(/[-:TZ.]/g,'').slice(0,14)}`,productCode:'',productName:'',plantCode:'ZH-SH',lineCode:'LINE-A',plannedQuantity:100,priority:5,plannedStartDate:today,plannedEndDate:endDate});showOrder.value=true}
async function createOrder(){try{await api.createProductionOrder(form);showOrder.value=false;await load()}catch(e){emit('error',e.message)}}
async function command(item,kind){const order=item.order;try{
  if(kind==='material')await api.updateProductionReadiness(order.id,{materialReady:true,qualityReleased:order.qualityReleased,andonOpen:order.andonOpen,note:'仓储与生产主管确认关键物料齐套'})
  if(kind==='quality')await api.updateProductionReadiness(order.id,{materialReady:order.materialReady,qualityReleased:true,andonOpen:order.andonOpen,note:'质量部门完成终检并放行'})
  if(kind==='andon')await api.updateProductionReadiness(order.id,{materialReady:order.materialReady,qualityReleased:order.qualityReleased,andonOpen:!order.andonOpen,note:order.andonOpen?'现场异常已关闭':'现场异常触发安灯'})
  if(kind==='release')await api.releaseProductionOrder(order.id)
  if(['START','PAUSE','RESUME'].includes(kind))await api.productionAction(order.id,kind)
  if(kind==='complete'&&confirm(`确认对 ${order.orderNo} 执行完工关账？`))await api.completeProductionOrder(order.id)
  await load()
}catch(e){emit('error',e.message)}}
function openReporting(item){reportTarget.value=item;Object.assign(report,{eventKey:`ui-${crypto.randomUUID()}`,goodQuantity:0,scrapQuantity:0,equipmentCode:'EQ-A01',shiftCode:'DAY',reasonCode:''});showReport.value=true}
async function saveReport(){try{await api.productionReport(reportTarget.value.order.id,report);showReport.value=false;await load()}catch(e){emit('error',e.message)}}
function mayComplete(item){return item.order.status==='IN_PROGRESS'&&item.accountedQuantity===item.order.plannedQuantity&&item.order.qualityReleased&&!item.order.andonOpen}
onMounted(load)
</script>

<template>
  <section class="execution-head">
    <div><span>SHOP FLOOR CONTROL · 生产执行闭环</span><h3>生产执行中心</h3><p>从物料齐套、生产下达、开停工到幂等报工、质量放行与完工关账，全程保留事件与审计证据。</p></div>
    <button @click="openCreate">+ 新建生产订单</button>
  </section>
  <section v-if="summary" class="execution-kpis">
    <article><small>生产订单</small><strong>{{summary.totalOrders}}</strong><em>{{summary.activeOrders}} 个执行中</em></article>
    <article><small>计划 / 已核算</small><strong>{{summary.plannedQuantity}}</strong><em>{{summary.goodQuantity+summary.scrapQuantity}} 已报工</em></article>
    <article><small>一次合格率</small><strong>{{summary.firstPassYield}}%</strong><em>{{summary.scrapQuantity}} 件报废</em></article>
    <article :class="{alert:summary.blockedOrders}"><small>受阻 / 逾期</small><strong>{{summary.blockedOrders}}</strong><em>{{summary.overdueOrders}} 个逾期</em></article>
  </section>
  <section class="execution-filters">
    <input v-model="filters.keyword" placeholder="搜索订单、产品或工厂" @keyup.enter="load">
    <select v-model="filters.status" @change="load"><option value="">全部状态</option><option v-for="(label,key) in statusText" :key="key" :value="key">{{label}}</option></select>
    <input v-model="filters.plantCode" placeholder="工厂代码" @keyup.enter="load">
    <label><input v-model="filters.exceptionOnly" type="checkbox" @change="load"> 仅看异常</label>
    <button @click="load">{{loading?'刷新中':'查询'}}</button>
  </section>
  <section class="execution-list">
    <article v-for="item in orders" :key="item.order.id" class="order-card" :class="{blocked:item.blockers.length}">
      <header>
        <div><code>{{item.order.orderNo}}</code><h4>{{item.order.productName}}</h4><small>{{item.order.productCode}} · {{item.order.plantCode}} / {{item.order.lineCode}}</small></div>
        <span :class="['state',item.order.status.toLowerCase()]">{{statusText[item.order.status]||item.order.status}}</span>
      </header>
      <div class="order-body">
        <div class="progress-block"><div><span>完成进度</span><b>{{item.accountedQuantity}} / {{item.order.plannedQuantity}}</b></div><div class="progress"><i :style="{width:`${Math.min(item.completionRate,100)}%`}"></i></div><small>良品 {{item.order.goodQuantity}} · 报废 {{item.order.scrapQuantity}} · FPY {{item.firstPassYield}}%</small></div>
        <dl><div><dt>优先级</dt><dd>P{{10-item.order.priority}}</dd></div><div><dt>计划窗口</dt><dd>{{item.order.plannedStartDate}} — {{item.order.plannedEndDate}}</dd></div></dl>
        <div class="gates"><span :class="{ok:item.order.materialReady}">物料{{item.order.materialReady?'已齐套':'待齐套'}}</span><span :class="{ok:item.order.qualityReleased}">质量{{item.order.qualityReleased?'已放行':'待放行'}}</span><span :class="{danger:item.order.andonOpen}">安灯{{item.order.andonOpen?'开启':'正常'}}</span></div>
        <p v-if="item.blockers.length" class="blockers">{{item.blockers.join(' · ')}}</p>
      </div>
      <footer>
        <button v-if="isAdmin&&!item.order.materialReady&&item.order.status==='DRAFT'" @click="command(item,'material')">确认齐套</button>
        <button v-if="isAdmin&&item.order.status==='DRAFT'" class="primary-action" :disabled="!item.order.materialReady" @click="command(item,'release')">下达</button>
        <button v-if="item.order.status==='RELEASED'" class="primary-action" @click="command(item,'START')">开工</button>
        <button v-if="item.order.status==='IN_PROGRESS'" @click="openReporting(item)">生产报工</button>
        <button v-if="item.order.status==='IN_PROGRESS'" @click="command(item,'PAUSE')">暂停</button>
        <button v-if="item.order.status==='PAUSED'" class="primary-action" @click="command(item,'RESUME')">恢复</button>
        <button v-if="isAdmin&&['RELEASED','IN_PROGRESS','PAUSED'].includes(item.order.status)" :class="{danger:item.order.andonOpen}" @click="command(item,'andon')">{{item.order.andonOpen?'关闭安灯':'开启安灯'}}</button>
        <button v-if="isAdmin&&item.accountedQuantity===item.order.plannedQuantity&&!item.order.qualityReleased" @click="command(item,'quality')">质量放行</button>
        <button v-if="isAdmin&&mayComplete(item)" class="primary-action" @click="command(item,'complete')">完工关账</button>
      </footer>
    </article>
    <div v-if="!loading&&!orders.length" class="empty">没有符合条件的生产订单</div>
  </section>

  <div v-if="showOrder" class="execution-modal" @click.self="showOrder=false"><form @submit.prevent="createOrder"><header><h3>建立生产订单</h3><button type="button" @click="showOrder=false">×</button></header><div class="form">
    <label>生产订单号<input v-model="form.orderNo" required maxlength="40"></label><label>产品编码<input v-model="form.productCode" required maxlength="40"></label><label class="wide">产品名称<input v-model="form.productName" required maxlength="120"></label><label>工厂代码<input v-model="form.plantCode" required></label><label>产线代码<input v-model="form.lineCode" required></label><label>计划数量<input v-model.number="form.plannedQuantity" type="number" min="1" required></label><label>优先级（1-9）<input v-model.number="form.priority" type="number" min="1" max="9" required></label><label>计划开始<input v-model="form.plannedStartDate" type="date" required></label><label>计划完工<input v-model="form.plannedEndDate" type="date" required></label>
  </div><button class="submit">保存生产订单</button></form></div>
  <div v-if="showReport" class="execution-modal" @click.self="showReport=false"><form @submit.prevent="saveReport"><header><div><small>生产报工</small><h3>{{reportTarget.order.orderNo}}</h3></div><button type="button" @click="showReport=false">×</button></header><p class="report-hint">本次最多可报 {{reportTarget.order.plannedQuantity-reportTarget.accountedQuantity}} 件。重复提交同一幂等键不会重复记账。</p><div class="form">
    <label>良品数量<input v-model.number="report.goodQuantity" type="number" min="0" required></label><label>报废数量<input v-model.number="report.scrapQuantity" type="number" min="0" required></label><label>设备编号<input v-model="report.equipmentCode" required></label><label>班次<select v-model="report.shiftCode"><option value="DAY">白班</option><option value="NIGHT">夜班</option><option value="MIDDLE">中班</option></select></label><label class="wide">报废原因代码（有报废时必填）<input v-model="report.reasonCode" :required="report.scrapQuantity>0" placeholder="如：DIMENSION_NG"></label>
  </div><button class="submit">确认报工</button></form></div>
</template>

<style scoped>
.execution-head{margin:28px 0 16px;padding:24px 28px;background:#173744;color:#fff;border-left:5px solid #d89b46;display:flex;justify-content:space-between;align-items:center}.execution-head span{font-size:11px;letter-spacing:.13em;color:#e3b471}.execution-head h3{font-size:24px;margin:6px 0}.execution-head p{margin:0;color:#cbd8dc}.execution-head button,.execution-filters button{border:0;background:#fff;color:#235a74;padding:11px 16px;font-weight:700}.execution-kpis{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}.execution-kpis article{background:#fff;border:1px solid #dce2df;border-top:3px solid #235a74;padding:17px 19px}.execution-kpis article.alert{border-top-color:#a14d43}.execution-kpis small,.execution-kpis em{display:block;color:#68777c;font-style:normal}.execution-kpis strong{display:block;font-size:27px;margin:6px 0;color:#173744}.execution-filters{display:flex;gap:10px;align-items:center;background:#fff;border:1px solid #dce2df;padding:13px;margin:14px 0}.execution-filters input,.execution-filters select{border:1px solid #ccd6d2;padding:9px;background:#fff}.execution-filters>input:first-child{min-width:260px}.execution-filters label{margin-left:auto;font-size:12px;color:#526166}.execution-filters button{background:#235a74;color:#fff;padding:10px 18px}.execution-list{display:grid;grid-template-columns:repeat(2,minmax(360px,1fr));gap:14px}.order-card{background:#fff;border:1px solid #dce2df;border-top:3px solid #235a74}.order-card.blocked{border-top-color:#b46d3c}.order-card>header{padding:17px 19px 12px;display:flex;justify-content:space-between;border-bottom:1px solid #edf0ef}.order-card code{font-size:11px;color:#235a74}.order-card h4{font-size:17px;margin:6px 0}.order-card header small{color:#68777c}.state{height:fit-content;padding:5px 9px;border-radius:14px;background:#edf1f0;color:#526166;font-size:11px}.state.in_progress,.state.completed{background:#e4f1e9;color:#287047}.state.paused{background:#fff0dc;color:#94601b}.order-body{padding:15px 19px}.progress-block>div:first-child{display:flex;justify-content:space-between}.progress{height:7px;background:#edf0ef;margin:9px 0}.progress i{display:block;height:100%;background:#2f7788}.progress-block small{color:#68777c}.order-body dl{display:flex;gap:25px;margin:15px 0}.order-body dl div{min-width:90px}.order-body dt{font-size:10px;color:#7b878b}.order-body dd{margin:4px 0 0;font-size:12px}.gates{display:flex;gap:7px}.gates span{background:#f0f2f1;color:#68777c;padding:5px 8px;font-size:10px}.gates span.ok{background:#e5f1e8;color:#287047}.gates span.danger{background:#fbe7e5;color:#9b3f37}.blockers{color:#9a5b28;background:#fff6e9;padding:8px 10px;font-size:11px;margin:12px 0 0}.order-card>footer{display:flex;gap:6px;flex-wrap:wrap;padding:12px 19px;border-top:1px solid #edf0ef}.order-card>footer button{border:1px solid #c8d3d0;background:#fff;color:#235a74;padding:6px 9px;font-size:11px}.order-card>footer .primary-action{background:#235a74;color:#fff;border-color:#235a74}.order-card>footer .danger{color:#a03f38;border-color:#d8aaa6}.order-card button:disabled{opacity:.45;cursor:not-allowed}.empty{grid-column:1/-1;background:#fff;border:1px dashed #ccd6d2;color:#68777c;padding:50px;text-align:center}.execution-modal{position:fixed;inset:0;z-index:40;background:#14262e99;display:grid;place-items:center;padding:20px}.execution-modal form{width:min(660px,100%);max-height:90vh;overflow:auto;background:#fff;padding:24px}.execution-modal header{display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid #dce2df;margin-bottom:18px}.execution-modal header h3{margin:0 0 14px}.execution-modal header button{border:0;background:none;font-size:24px;color:#68777c}.form{display:grid;grid-template-columns:1fr 1fr;gap:13px}.form label{display:grid;gap:6px;color:#526166;font-size:12px}.form .wide{grid-column:1/-1}.form input,.form select{border:1px solid #ccd6d2;padding:11px;background:#fff}.submit{width:100%;border:0;background:#235a74;color:#fff;padding:12px;margin-top:18px;font-weight:700}.report-hint{font-size:12px;background:#edf3f3;color:#526166;padding:10px}.execution-modal header small{color:#d89b46}@media(max-width:1100px){.execution-list{grid-template-columns:1fr}.execution-kpis{grid-template-columns:repeat(2,1fr)}}@media(max-width:720px){.execution-head{align-items:flex-start;gap:15px;flex-direction:column}.execution-kpis{grid-template-columns:1fr 1fr}.execution-filters{align-items:stretch;flex-direction:column}.execution-filters>input:first-child{min-width:0}.execution-filters label{margin-left:0}.form{grid-template-columns:1fr}.form .wide{grid-column:auto}}
</style>
