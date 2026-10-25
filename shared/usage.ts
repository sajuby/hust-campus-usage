export type Category = 'electricity' | 'energy' | 'network';
export type Scenario = 'available' | 'zero' | 'pending' | 'offline' | 'login';
export interface DailyUsage {date: string; category: Exclude<Category,'network'>; total: string; room: string; public: string}
export function shiftDate(date: string, delta: number): string {
  const d = new Date(`${date}T12:00:00Z`); d.setUTCDate(d.getUTCDate()+delta); return d.toISOString().slice(0,10);
}
export function expectedPublication(date: string): string {return `${shiftDate(date,1)}T08:00:00+08:00`;}
export function isPublicationExpected(date: string, now: string): boolean {return Date.parse(now) >= Date.parse(expectedPublication(date));}
// Synthetic fixture data for isolated visual testing. It is never a school response.
export function demoUsage(category: Exclude<Category,'network'>, date: string, scenario: Scenario, now: string): DailyUsage | null {
  if (scenario === 'pending' || !isPublicationExpected(date, now)) return null;
  if (scenario === 'zero' || category === 'energy') return {date,category,total:'0.0000',room:'0.0000',public:'0.0000'};
  const day = Number(date.slice(-2));
  const values = ['1.2000','2.4000','1.8000','3.0000','0.6000','2.1000','1.5000'];
  const total = values[day % values.length];
  const room = (Number(total)*0.76).toFixed(4);
  return {date,category,total,room,public:(Number(total)-Number(room)).toFixed(4)};
}
export function usageRange(category: Exclude<Category,'network'>, end: string, count: number, scenario: Scenario, now: string) {
  return Array.from({length:count},(_,i)=>{const date=shiftDate(end,i-count+1); return {date,reading:demoUsage(category,date,scenario,now)};});
}
