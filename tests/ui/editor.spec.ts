import {test,expect} from '@playwright/test';
test('edit, undo, persist to disk, reopen, reorder, and restore',async({page,request})=>{
 const before=await(await request.get('/api/design')).json();
 try{
  await page.goto('/');await expect(page.locator('.save-state')).toContainText('已保存');
  await page.getByRole('button',{name:'文案',exact:true}).click();
  await page.getByLabel('问候语',{exact:true}).fill('同学，欢迎回来。');
  await expect(page.locator('.hero-copy h2')).toHaveText('同学，欢迎回来。');
  await page.getByRole('button',{name:'撤销',exact:true}).click();
  await expect(page.locator('.hero-copy h2')).toHaveText(before.copy.greeting);
  await page.getByRole('button',{name:'重做',exact:true}).click();
  await page.getByRole('button',{name:'保存设计',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('设计已保存');
  await page.reload();await expect(page.locator('.hero-copy h2')).toHaveText('同学，欢迎回来。');
  await page.getByRole('button',{name:'布局',exact:true}).click();
  await page.getByRole('button',{name:'下移问候横幅',exact:true}).click();
  await expect(page.locator('[data-page="home"] > div').first().locator('[data-component="summary"]')).toBeVisible();
 }finally{
  const current=await(await request.get('/api/design')).json();
  await request.put('/api/design',{data:{design:before,expectedRevision:current.revision}});
 }
});
test('all pages, effective zero, next-day publication, offline, and in-flight refresh',async({page})=>{
 await page.goto('/');await page.getByRole('button',{name:'手机查询',exact:true}).click();
 await page.locator('.category-tabs').getByRole('button',{name:'能源',exact:true}).click();
 await expect(page.locator('.daily-value')).toContainText('0.0000');
 await page.locator('.category-tabs').getByRole('button',{name:'电费',exact:true}).click();
 await page.getByLabel('查询日期').fill('2026-10-23');
 await expect(page.locator('.daily-reading')).toContainText('次日早上 8 点后可查');
 await page.getByLabel('查询日期').fill('2026-10-22');
 await page.locator('.left-sidebar select').selectOption('offline');
 await page.getByRole('button',{name:'更新',exact:true}).click();
 await expect(page.getByRole('button',{name:'更新中',exact:true})).toBeDisabled();
 await expect(page.locator('.query-status')).toContainText('网络不可用');
 await expect(page.locator('.daily-value')).not.toContainText('—');
 await page.locator('.category-tabs').getByRole('button',{name:'校园网',exact:true}).click();
 await expect(page.locator('.network-card')).toContainText('123.45');
 await page.getByRole('button',{name:'手机趋势',exact:true}).click();
 await expect(page.locator('.records button')).toHaveCount(7);
 await page.getByRole('button',{name:'手机我的',exact:true}).click();
 await expect(page.locator('.connection')).toHaveCount(2);
 await expect(page.locator('.phone')).toContainText('工作台样例，不读取学校账户');
});
test('narrow viewport, no horizontal overflow, and screenshot',async({page})=>{
 await page.goto('/');await expect(page.locator('.save-state')).toContainText('已保存');
 await page.getByLabel('手机宽度').selectOption('320');
 const overflow=await page.locator('.phone-scroll').evaluate(e=>e.scrollWidth>e.clientWidth);
 expect(overflow).toBe(false);
 await page.getByLabel('用量字号',{exact:true}).fill('38');
 await expect(page.locator('.phone')).toHaveClass(/large-type/);
 expect(await page.locator('.phone-scroll').evaluate(e=>e.scrollWidth>e.clientWidth)).toBe(false);
 await page.getByRole('button',{name:'撤销',exact:true}).click();
 await page.getByLabel('手机宽度').selectOption('393');
 await page.screenshot({path:'artifacts/design-studio.png',fullPage:true});
});
test('monthly chart preserves every calendar day and early-morning date selection is explicit',async({page})=>{
 await page.goto('/');await expect(page.locator('.save-state')).toContainText('已保存');
 await page.locator('input[type="datetime-local"]').fill('2026-10-23T07:50');
 await expect(page.locator('.electricity .metric-foot')).toHaveText('统计日期 10-21');
 await page.getByRole('button',{name:'手机查询',exact:true}).click();
 await page.getByLabel('查询日期').fill('2026-09-15');
 await page.locator('.segmented').getByRole('button',{name:'月',exact:true}).click();
 await expect(page.locator('.chart-point')).toHaveCount(30);
 await page.getByLabel('查询日期').fill('2026-10-15');
 await expect(page.locator('.chart-point')).toHaveCount(31);
});
