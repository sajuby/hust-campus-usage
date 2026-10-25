import {test,expect} from '@playwright/test';
import {readFileSync} from 'node:fs';
const root='design/assets/';
test('four independent local images persist on reload and can be restored without changing the design',async({page,request})=>{
 const before=await(await request.get('/api/design')).json();await page.goto('/');
 await page.getByRole('button',{name:'手机我的',exact:true}).click();await page.getByRole('button',{name:'主题与外观',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'主题与外观'});await expect(dialog).toBeVisible();
 for(const [label,file] of [['首页人物','character-profile.png'],['趋势配图','character-trends-original.jpg'],['我的人物','character-home.png'],['背景图片','route-background.png']]){
  await dialog.getByLabel('选择'+label,{exact:true}).setInputFiles(root+file);
  await expect(dialog.getByRole('status')).toContainText(label+'已更换');
 }
 await expect(dialog.locator('h3 small').filter({hasText:'本地图片'})).toHaveCount(4);
 await dialog.getByRole('button',{name:'返回',exact:true}).click();await page.reload();
 await page.getByRole('button',{name:'手机趋势',exact:true}).click();await expect(page.locator('.character-trend-original')).toHaveAttribute('src',/^blob:/);
 const info=await page.locator('.character-trend-original').evaluate(async node=>{const img=node as HTMLImageElement;await img.decode();const r=img.getBoundingClientRect();return{ratio:r.width/r.height,width:img.naturalWidth,height:img.naturalHeight};});
 expect(info.width).toBe(1256);expect(info.height).toBe(708);expect(info.ratio).toBeCloseTo(1256/708,2);
 await expect(page.locator('.phone')).toHaveClass(/has-custom-background/);
 await page.getByRole('button',{name:'手机我的',exact:true}).click();await page.getByRole('button',{name:'主题与外观',exact:true}).click();
 await dialog.locator('.appearance-slot').filter({hasText:'趋势配图'}).getByRole('button',{name:'恢复默认',exact:true}).click();await expect(dialog.getByRole('status')).toContainText('趋势配图已恢复默认');
 await expect(dialog.locator('h3 small').filter({hasText:'本地图片'})).toHaveCount(3);
 await dialog.getByRole('button',{name:'全部恢复默认',exact:true}).click();await expect(dialog.getByRole('status')).toContainText('已全部恢复默认');
 await expect(dialog.locator('h3 small').filter({hasText:'本地图片'})).toHaveCount(0);await dialog.getByRole('button',{name:'返回',exact:true}).click();
 await page.reload();await expect(page.locator('.phone')).not.toHaveClass(/has-custom-background/);
 expect(await(await request.get('/api/design')).json()).toEqual(before);
});
test('main-campus selection persists, hides cyber utilities, and keeps the official routes distinct',async({page})=>{
 await page.goto('/');await page.getByLabel('水电查询校区').selectOption('MAIN');
 await expect(page.locator('.main-campus-entry')).toContainText('主校区');await expect(page.locator('.character-primary')).toHaveCount(0);await expect(page.locator('.character-chart')).toHaveCount(0);
 await page.getByRole('button',{name:'手机查询',exact:true}).click();await expect(page.locator('.daily-reading')).toHaveCount(0);
 await expect(page.getByRole('link',{name:'打开主校区水电查询'})).toHaveAttribute('href','http://sdhq.hust.edu.cn/MobileUtilityPay1/#/home');
 await page.getByRole('button',{name:'手机趋势',exact:true}).click();await expect(page.locator('.character-full-chart')).toHaveCount(0);
 await page.reload();await expect(page.getByLabel('水电查询校区')).toHaveValue('MAIN');
 await page.getByLabel('水电查询校区').selectOption('CYBER');await expect(page.locator('.character-primary')).toBeVisible();
 await page.getByRole('button',{name:'手机查询',exact:true}).click();await page.locator('.category-tabs').getByRole('button',{name:'查路线',exact:true}).click();
 await expect(page.getByRole('button',{name:'查询路线',exact:true})).toBeVisible();
});
