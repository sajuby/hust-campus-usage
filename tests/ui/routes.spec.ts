import {test,expect} from '@playwright/test';

async function openRoutes(page:import('@playwright/test').Page){
 await page.goto('/');await expect(page.locator('.save-state')).toContainText('已保存');
 await page.getByRole('button',{name:'手机查询',exact:true}).click();
 await page.locator('.category-tabs').getByRole('button',{name:'查路线',exact:true}).click();
}
test('routes query, official references, last-train demo, and offline cached result',async({page})=>{
 await openRoutes(page);
 await expect(page.locator('.category-tabs button')).toHaveCount(4);
 await page.getByRole('button',{name:'查询路线',exact:true}).click();
 await expect(page.getByRole('button',{name:'查询中',exact:true})).toBeDisabled();
 await expect(page.locator('.route-result-card')).toHaveCount(2);
 await page.locator('.route-result-card').first().getByRole('button',{name:'查看详情'}).click();
 await expect(page.locator('.route-official-times')).toContainText('官方首末班');
 await expect(page.locator('.route-official-times')).toContainText('23:43');
 await page.getByRole('button',{name:'末班提醒演示',exact:true}).click();
 await expect(page.locator('.route-warning')).toContainText('22:11');
 await expect(page.locator('.route-warning')).toContainText('23:19');
 await page.getByRole('button',{name:'返回推荐路线',exact:true}).click();
 await page.locator('.left-sidebar select').selectOption('offline');
 await page.getByRole('button',{name:'更新路线',exact:true}).click();
 await expect(page.locator('.route-message')).toContainText('保留此前演示结果');
 await expect(page.locator('.route-result-card')).toHaveCount(2);
 await page.locator('.category-tabs').getByRole('button',{name:'能源',exact:true}).click();
 await expect(page.locator('.daily-value')).toContainText('0.0000');
});

test('official reference day classes and bundled subway map work without supplier network',async({page})=>{
 await page.route('https://advh5.whrtmpay.com/**',route=>route.abort());
 await page.route('https://h5.whrtmpay.com/**',route=>route.abort());
 await openRoutes(page);await page.getByRole('button',{name:'查询路线',exact:true}).click();
 await page.locator('.route-result-card').first().getByRole('button',{name:'查看详情'}).click();
 await page.getByLabel('武汉火车站日期类型').selectOption('工作日');
 await expect(page.locator('.route-official-leg').first()).toContainText('6:00');
 await page.getByLabel('洪山广场日期类型').selectOption('周五');
 await expect(page.locator('.route-official-leg').nth(1)).toContainText('23:43');
 await page.locator('.phone').screenshot({path:'artifacts/official-metro-studio-detail.png'});
 await page.getByRole('button',{name:'返回推荐路线',exact:true}).click();await page.locator('.route-map-open').click();
 const map=page.frameLocator('iframe[title="武汉地铁离线线路图"]');
 await expect(map.locator('#line option')).toHaveCount(14);
 await map.getByLabel('搜索地铁站').fill('洪山广场');await expect(map.getByLabel('搜索地铁站')).toHaveValue('洪山广场');await map.getByRole('button',{name:'搜索',exact:true}).click();
 await expect(map.getByRole('status')).toContainText('洪山广场');
 await expect(map.getByRole('status')).toContainText('2号线');
 const before=await map.locator('#map').getAttribute('data-zoom');await map.getByRole('button',{name:'放大',exact:true}).click();
 expect(Number(await map.locator('#map').getAttribute('data-zoom'))).toBeGreaterThan(Number(before));
 await page.getByRole('dialog',{name:'地铁线路图',exact:true}).screenshot({path:'artifacts/official-metro-studio-map.png'});
 await page.getByRole('button',{name:'关闭线路图',exact:true}).click();await expect(page.getByRole('dialog',{name:'地铁线路图'})).toHaveCount(0);
});
test('route copy is editable, undoable, saved and restored with old design compatibility',async({page,request})=>{
 const before=await(await request.get('/api/design')).json();
 try{
  await openRoutes(page);await page.locator('.route-heading h2').click();
  await page.getByRole('button',{name:'文案',exact:true}).click();
  await page.getByLabel('路线页面标题',{exact:true}).fill('去想去的地方');
  await expect(page.locator('.route-heading h2')).toHaveText('去想去的地方');
  await page.getByRole('button',{name:'撤销',exact:true}).click();
  await expect(page.locator('.route-heading h2')).toHaveText('查路线');
  await page.getByRole('button',{name:'重做',exact:true}).click();
  await page.getByRole('button',{name:'保存设计',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('设计已保存');
  await page.reload();await page.getByRole('button',{name:'手机查询',exact:true}).click();
  await page.locator('.category-tabs').getByRole('button',{name:'查路线',exact:true}).click();
  await expect(page.locator('.route-heading h2')).toHaveText('去想去的地方');
 }finally{const current=await(await request.get('/api/design')).json();await request.put('/api/design',{data:{design:before,expectedRevision:current.revision}});}
});
test('route screenshots and narrow layouts do not overflow',async({page})=>{
 await openRoutes(page);
 await page.locator('.phone').screenshot({path:'artifacts/routes-studio-query.png'});
 await page.getByRole('button',{name:'查询路线',exact:true}).click();await expect(page.locator('.route-result-card')).toHaveCount(2);
 await page.locator('.phone').screenshot({path:'artifacts/routes-studio-results.png'});
 for(const width of ['320','360','393','412']){
  await page.getByLabel('手机宽度').selectOption(width);
  expect(await page.locator('.phone-scroll').evaluate(e=>e.scrollWidth>e.clientWidth)).toBe(false);
 }
 await page.getByRole('button',{name:'末班提醒演示',exact:true}).click();
 await page.locator('.phone-scroll').evaluate(e=>{e.scrollTop=0;});
 await page.locator('.phone').screenshot({path:'artifacts/routes-studio-last-transfer.png'});
});
