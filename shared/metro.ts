import reference from './metro/wuhan-reference.json';
import mapData from './metro/wuhan-map.json';
import mapHtml from './metro/map.html?raw';

export const metroMapDocument=mapHtml.replace('__METRO_DATA__',JSON.stringify(mapData).replaceAll('<','\\u003c'));
export const officialMapUrl='https://h5.whrtmpay.com/whrtMap/#/?download=true';
export function referenceTimes(code:'0458'|'0447',lineName:string,direction:string){
 const station=reference.stations[code];
 const requested=reference.index.data.flatMap(line=>line.stationInfos).find(site=>site.siteCode===code);
 const platform=reference.index.data.find(line=>line.lineName===lineName)?.stationInfos.find(site=>site.siteName===requested?.siteName);
 return {fetchedAt:station.fetchedAt,days:station.data.flatMap(day=>day.timetable.filter(row=>row.siteCode===platform?.siteCode&&row.lineName===lineName).flatMap(row=>{
  const prefix=direction===row.firstSite?'down':direction===row.lastSite?'up':null;
  return prefix?[{label:day.date,first:prefix==='down'?row.downFirst:row.upFirst,last:prefix==='down'?row.downLast:row.upLast}]:[];
 }))};
}
