import {useEffect,useRef,useState,type ReactNode} from 'react';
import {createPortal} from 'react-dom';
import {ArrowDownUp,ArrowLeft,ArrowLeftRight,ArrowRight,ChevronDown,ChevronRight,Clock,Footprints,Info,Map,MapPin,RefreshCw,Route,Search,Star,TrainFront,Wallet} from 'lucide-react';
import type {Design} from '../../shared/design';
import type {Scenario} from '../../shared/usage';
import heroArt from '../../design/assets/route-hero.png';
import clockArt from '../../design/assets/route-clock.png';
import './routes.css';
import {metroMapDocument,officialMapUrl,referenceTimes} from '../../shared/metro';

export type RoutePreviewStage='query'|'results'|'detail'|'late';
export const routeCopyDefaults={routeTitle:'查路线',routeSubtitle:'输入目的地，查看公共交通路线',routeQuery:'查询路线',routeMapTitle:'地铁线路图',routeWarningTitle:'建议最晚出发'};

function MetroArtwork(){return <svg viewBox="0 0 360 140" aria-label="装饰性线路示意图，并非实际地铁图" className="route-metro-art"><path d="M-10 117L35 108 95 78H174L233 27H310L372 11" fill="none" stroke="var(--blue)" strokeWidth="3"/><path d="M25 137H130L209 84H295L339 111 370 74" fill="none" stroke="var(--pink)" strokeWidth="3"/><path d="M245 6V140" fill="none" stroke="#c7a5e2" strokeWidth="3"/>{[[35,108],[95,78],[174,78],[233,27],[310,27]].map(([x,y])=><circle key={x} cx={x} cy={y} r="4" fill="var(--blue)"/>)}{[[130,137],[209,84],[295,84],[339,111],[245,84]].map(([x,y])=><circle key={x} cx={x} cy={y} r="4.5" fill="var(--pink)"/>)}<circle cx="245" cy="84" r="7" fill="#fff0fb" stroke="var(--pink)" strokeWidth="3"/></svg>;}

/** An editable implementation of the three approved layouts. The studio always uses isolated demo data. */
export function RouteQuery({d,scenario,select,tabs,stage,onStage}:{d:Design;scenario:Scenario;select:(component:string)=>void;tabs:ReactNode;stage:RoutePreviewStage;onStage:(stage:RoutePreviewStage)=>void}) {
 const [origin,setOrigin]=useState('武汉火车站');const [destination,setDestination]=useState('江汉路');
 const [city,setCity]=useState('武汉市');const [strategy,setStrategy]=useState('综合推荐');
 const [updating,setUpdating]=useState(false);const [message,setMessage]=useState('');
 const [showMap,setShowMap]=useState(false);
 const request=useRef(0);
 const copy=(key:keyof typeof routeCopyDefaults)=>d.copy[key]??routeCopyDefaults[key];
 const results=stage==='results',detail=stage==='detail'||stage==='late',late=stage==='late';
 useEffect(()=>()=>{request.current++;},[]);
 useEffect(()=>{document.querySelector('.phone-scroll')?.scrollTo({top:0});},[stage]);
 function changed(){request.current++;setUpdating(false);setMessage('');onStage('query');}
 async function query(){
  if(updating)return;
  if(!origin.trim()||!destination.trim()){setMessage('请选择起点和目的地');return;}
  const id=++request.current;setUpdating(true);setMessage('');
  await new Promise(r=>setTimeout(r,450));if(request.current!==id)return;setUpdating(false);
  if(scenario==='offline'){setMessage('网络不可用，保留此前演示结果');return;}
  const found=city==='武汉市'&&origin.includes('武汉火车站')&&destination.includes('江汉路');
  onStage(found?'results':'query');setMessage(found?'':'此演示只提供武汉火车站至江汉路样例');
 }
 const mapCard=(compact:boolean)=><section className={`route-map ${compact?'compact':''}`} data-component="routeMap" onClick={()=>select('routeMap')}>
  <MetroArtwork/>
  <div className="route-map-copy"><span className="route-map-icon">{compact?<Map/>:<TrainFront/>}</span><div><h3>{copy('routeMapTitle')==='地铁线路图'?city.replace(/市$/,'')+copy('routeMapTitle'):copy('routeMapTitle')}</h3><small>{city==='武汉市'?'官方线网资料 · 可离线查看':compact?'查看全城地铁线路':'查看线路图  ›'}</small></div>{compact&&<button className="route-map-open" onClick={()=>setShowMap(true)}>查看线路图<ChevronRight size={12}/></button>}</div>
  {!compact&&<button className="route-map-target" onClick={()=>setShowMap(true)} aria-label="打开地铁线路图"/>}
 </section>;
 const fields=(compact:boolean)=><section className={`route-search-card ${compact?'compact':''}`} data-component="routeForm" onClick={()=>select('routeForm')}>
  <div className="route-place-pair">
   <label><i className="route-dot pink"/><span><small>{compact?'出发地':'起点'}</small><input aria-label="路线起点" value={origin} onChange={e=>{changed();setOrigin(e.target.value);}} placeholder="我的位置"/></span></label>
   <button className="route-swap" aria-label="交换起终点" onClick={()=>{const before=origin;changed();setOrigin(destination);setDestination(before);}}>{compact?<ArrowLeftRight/>:<ArrowDownUp/>}</button>
   <label><i className="route-dot blue"/><span><small>{compact?'目的地':'终点'}</small><input aria-label="路线目的地" value={destination} onChange={e=>{changed();setDestination(e.target.value);}} placeholder="输入目的地 / 地铁站"/></span></label>
  </div>
  <div className="route-departure"><Clock size={19}/><span>{compact?'现在出发 · 20:16':'现在出发'}</span>{compact?<><ChevronDown size={15}/><button className="route-update" onClick={query} disabled={updating} aria-label={updating?'查询中':'更新路线'}><RefreshCw size={15}/>{updating?'更新中':'更新'}</button></>:<ChevronRight size={19}/>}</div>
  {!compact&&<button className="route-primary" onClick={query} disabled={updating}>{updating?<RefreshCw className="spin" size={22}/>:<Search size={24}/>}{updating?'查询中':copy('routeQuery')}</button>}
 </section>;
 return <div className={`route-shell route-stage-${stage}`}>
  <div className="route-fixed-chrome">
   <header className="route-app-header"><div><div className="route-title-orbit"/><h1>查询</h1>{stage!=='query'&&<p>{d.copy.subtitle}</p>}</div><label className="route-city"><MapPin size={19}/><select aria-label="路线城市" value={city} onChange={e=>{changed();setCity(e.target.value);}}>{['武汉市','北京市','上海市','广州市','深圳市','成都市','杭州市','南京市'].map(c=><option key={c}>{c}</option>)}</select><ChevronDown size={16}/></label></header>
   {tabs}
  </div>
  <div className="route-preview">
   {detail?<>
    <div className="route-detail-heading"><button aria-label="返回推荐路线" onClick={()=>onStage('results')}><ArrowLeft size={23}/></button><div><h2>换乘提醒</h2><p>{origin} → {destination}</p></div></div>
    {late?<section className="route-warning" data-component="routeWarning" onClick={()=>select('routeWarning')}>
     <img src={clockArt} alt=""/><span className="route-warning-pill"><Clock size={20}/>时间紧张</span><h3>{copy('routeWarningTitle')}</h3><strong>22:11</strong><p>距建议时间还有 <b>3 分钟</b></p><div className="route-warning-arrival"><span>⚑ 预计 <b>23:19</b> 到达</span><small>已预留进站与换乘时间</small></div>
    </section>:<OfficialTimes/>}
    <section className="route-timeline" onClick={()=>select('routeResults')}><h3><Route size={23}/>关键换乘</h3><div className="route-timeline-inset">
     <div className="route-timeline-stop"><h4>武汉火车站</h4><b>{late?'22:23 上车 · ':''}4号线</b><p>柏林方向 · 11站 · 28分钟</p>{!late&&<small>本站首末班见上方官方资料</small>}</div>
     <div className="route-timeline-stop"><h4>洪山广场</h4>{late&&<b>22:51 到达</b>}<p>换乘步行 {late?'5':'3'} 分钟{late?' · 预留 4 分钟':''}</p><div className="route-transfer"><TrainFront size={21}/><span>2号线 · 天河机场方向<small>{late?'23:00 示例末班':'本站首末班见上方官方资料'}</small></span><ChevronRight size={16}/></div></div>
     <div className="route-timeline-stop"><h4>江汉路</h4><p>{late?'23:16 到站 · 步行 3 分钟':'4站 · 16分钟 · 出站步行1分钟'}</p></div>
    </div></section>
    <p className="route-detail-note"><Info size={13}/>{late?'演示时刻表，实际请以运营信息为准':'耗时为估算，请以现场运营信息为准'}</p>
    <div className="route-detail-actions"><button className="route-primary" onClick={query} disabled={updating}><RefreshCw size={23}/>{updating?'查询中':'更新判断'}</button><button className="route-other" onClick={()=>onStage('results')}><Route size={23}/>其他路线</button></div>
   </>:<>
    {results?<div className="route-hero" onClick={()=>select('routeForm')}><img src={heroArt} alt=""/><div><h2>从校园出发<br/>探索更大的{city.replace(/市$/,'')}</h2><p>便捷出行，让每一次抵达都更简单</p></div></div>:<div className="route-heading" onClick={()=>select('routeForm')}><h2>{copy('routeTitle')}</h2><p><MapPin size={20}/>已定位武汉 · 可手动切换</p></div>}
    {fields(results)}
    {!results&&<button className="route-manual" onClick={()=>{select('routeForm');document.querySelector<HTMLInputElement>('[aria-label="路线起点"]')?.focus();}}>定位不准确？手动选择起点 <ChevronRight size={15}/></button>}
    {results&&<div className="route-strategies">{['综合推荐','地铁优先','步行少','换乘少'].map(value=><button key={value} onClick={()=>setStrategy(value)} className={strategy===value?'active':''}>{value}</button>)}</div>}
    {message&&<p className="route-message" role="status">{message}</p>}
    {results&&[48,51].map((duration,index)=><section key={duration} className={`route-result-card ${index?'alternative':''}`} data-component="routeResults" onClick={()=>select('routeResults')}>
     <div className="route-result-head"><span>{index?<><Footprints size={14}/>步行更少</>:<><Star size={14} fill="currentColor"/>推荐路线</>}</span><button onClick={()=>onStage('detail')}>查看详情<ChevronRight size={13}/></button></div>
     <div className="route-duration"><strong>{duration}<small>分钟</small></strong><span>预计 {index?'21:07':'21:04'} 到达</span></div>
     <div className="route-line-row"><div className="route-lines"><span>4号线</span><ArrowRight size={16}/><span>2号线</span></div><div className="route-mini-transfer"><svg viewBox="0 0 110 22"><path d="M8 11H55" stroke="#35b580" strokeWidth="2"/><path d="M55 11H102" stroke="#ee76b3" strokeWidth="2"/><circle cx="8" cy="11" r="4" fill="var(--card)" stroke="#35b580" strokeWidth="2"/><circle cx="102" cy="11" r="4" fill="var(--card)" stroke="#ee76b3" strokeWidth="2"/><circle cx="55" cy="11" r="6" fill="var(--card)" stroke="#8898bd" strokeWidth="2"/><circle cx="55" cy="11" r="2" fill="var(--pink)"/></svg><small>洪山广场</small></div></div>
     <p><MapPin size={13}/>洪山广场换乘</p><div className="route-metrics"><span><Footprints size={15}/>步行 {index?90:130} 米</span><i/><span><TrainFront size={15}/>15 站</span><i/><span><Wallet size={15}/>约 5 元</span></div>
    </section>)}
    {mapCard(results)}
   </>}
   {!detail&&<div className="route-demo-note"><i/><span>✦</span>方案预览 · 演示数据<i/></div>}
  </div>
  {showMap&&createPortal(<div className="studio-map-backdrop"><div className="route-map-modal" role="dialog" aria-modal="true" aria-label="地铁线路图" style={{backgroundColor:d.tokens.background,color:d.tokens.text}}><div className="route-map-modal-header"><strong>武汉地铁官方线路图</strong><button onClick={()=>setShowMap(false)} aria-label="关闭线路图">关闭</button></div>{city==='武汉市'?<iframe title="武汉地铁离线线路图" srcDoc={metroMapDocument} sandbox="allow-scripts"/>:<p>官方内置图目前覆盖武汉，请打开高德查看当前城市。</p>}<div className="route-map-modal-footer"><a href={officialMapUrl} target="_blank" rel="noreferrer">官方在线图</a><a href="https://map.amap.com/subway/index.html" target="_blank" rel="noreferrer">高德备用图</a></div></div></div>,document.body)}
 </div>;
}

function OfficialTimes(){
 const [chosen,setChosen]=useState<Record<string,string>>({});
 const legs=[{code:'0458' as const,line:'4号线',station:'武汉火车站',direction:'柏林'},{code:'0447' as const,line:'2号线',station:'洪山广场',direction:'天河机场'}];
 // Route paths are demo data; the labelled reference times below are the public official snapshot.
 return <section className="route-unknown route-official-times"><h3><Clock size={18}/>官方首末班</h3>{legs.map(leg=>{
  const source=referenceTimes(leg.code,leg.line,leg.direction);const day=source.days.find(d=>d.label===chosen[leg.code])??source.days.find(d=>d.label==='周日'||d.label==='休息日')??source.days[0];
  return <div className="route-official-leg" key={leg.code}><strong>{leg.station} · {leg.line}</strong><p>开往{leg.direction}方向</p><div className="route-official-values"><span>首班 {day.first}</span><b>末班 {day.last}</b></div><select aria-label={`${leg.station}日期类型`} value={day.label} onChange={e=>setChosen({...chosen,[leg.code]:e.target.value})}>{source.days.map(d=><option key={d.label}>{d.label}</option>)}</select><small>内置官方资料 · 2026-10-25 采集 · 日期类型样例</small></div>;
 })}<p>来源：武汉地铁官方。节假日及调休请核对官方原表。仅查询首末班，不计算最晚出发。</p></section>;
}
