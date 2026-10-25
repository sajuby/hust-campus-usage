export const sectionIds = ['hero', 'summary', 'quick', 'trend', 'notice'] as const;
export const cardIds = ['electricity', 'energy', 'month', 'balance'] as const;
export type SectionId = typeof sectionIds[number];
export type CardId = typeof cardIds[number];
export interface Design {
  schemaVersion: 1;
  revision: number;
  updatedAt: string;
  tokens: {
    background: string; surface: string; card: string; pink: string; blue: string;
    purple: string; text: string; ink: string; radius: number; gap: number;
    pagePadding: number; bodySize: number; valueSize: number; heroHeight: number; glow: boolean;
  };
  copy: {
    appTitle: string; greeting: string; subtitle: string; heroNote: string;
    quickTitle: string; trendTitle: string; noticeTitle: string; noticeBody: string;
    electricityLabel: string; energyLabel: string; monthLabel: string; balanceLabel: string;
    routeTitle?: string; routeSubtitle?: string; routeQuery?: string; routeMapTitle?: string; routeWarningTitle?: string;
  };
  layout: {sections: SectionId[]; cards: CardId[]};
}
export function validateDesign(input: unknown): Design {
  if (!input || typeof input !== 'object' || Array.isArray(input)) throw new Error('设计配置必须是对象');
  const d = input as Design;
  if (d.schemaVersion !== 1 || !Number.isInteger(d.revision) || d.revision < 1 || !Number.isFinite(Date.parse(d.updatedAt))) throw new Error('不支持的配置版本或时间');
  if (!d.tokens || !d.copy || !d.layout) throw new Error('缺少设计配置');
  for (const key of ['background','surface','card','pink','blue','purple','text','ink'] as const) {
    if (typeof d.tokens[key] !== 'string' || !/^#[a-f\d]{6}$/i.test(d.tokens[key])) throw new Error(`无效颜色：${key}`);
  }
  const ranges = {radius:[8,36],gap:[8,24],pagePadding:[12,28],bodySize:[14,20],valueSize:[24,38],heroHeight:[120,220]} as const;
  for (const [key, [min,max]] of Object.entries(ranges)) {
    const n = d.tokens[key as keyof typeof ranges];
    if (!Number.isFinite(n) || n < min || n > max) throw new Error(`尺寸超出范围：${key}`);
  }
  if (typeof d.tokens.glow !== 'boolean') throw new Error('光效设置不正确');
  const copyKeys = ['appTitle','greeting','subtitle','heroNote','quickTitle','trendTitle','noticeTitle','noticeBody','electricityLabel','energyLabel','monthLabel','balanceLabel'] as const;
  for (const key of copyKeys) if (typeof d.copy[key] !== 'string' || !d.copy[key].trim() || d.copy[key].length > 120) throw new Error(`文案为空或过长：${key}`);
  for (const key of ['routeTitle','routeSubtitle','routeQuery','routeMapTitle','routeWarningTitle'] as const) {
    const value=d.copy[key];
    if(value!==undefined&&(typeof value!=='string'||!value.trim()||value.length>120))throw new Error(`路线文案为空或过长：${key}`);
  }
  function permutation(actual: unknown, expected: readonly string[]) {
    return Array.isArray(actual) && actual.length === expected.length && new Set(actual).size === expected.length && actual.every(x => expected.includes(x));
  }
  if (!permutation(d.layout.sections,sectionIds) || !permutation(d.layout.cards,cardIds)) throw new Error('布局需要保留所有必要组件，且不能重复');
  return JSON.parse(JSON.stringify(d)) as Design;
}
