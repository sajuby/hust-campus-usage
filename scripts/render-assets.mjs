import {chromium} from '@playwright/test';
import {readFile,mkdir,copyFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';
const root=fileURLToPath(new URL('..',import.meta.url));
const browser=await chromium.launch({...(process.env.CAMPUS_CHROME_PATH?{executablePath:process.env.CAMPUS_CHROME_PATH}:{}),headless:true});
try{
 const page=await browser.newPage({viewport:{width:1200,height:500},deviceScaleFactor:1});
 const destination=resolve(root,'android/app/src/main/res/drawable-nodpi');await mkdir(destination,{recursive:true});
 const names=process.argv.includes('--routes')?['route-background','route-hero','route-clock']:['campus-banner','route-background','route-hero','route-clock'];
 for(const name of names){
  if(name.startsWith('route-')){
   await copyFile(resolve(root,`design/assets/${name}.png`),resolve(destination,name.replaceAll('-','_')+'.png'));
   continue;
  }
  const source=await readFile(resolve(root,`design/assets/${name}.svg`),'utf8');
  const width=Number(source.match(/width="(\d+)"/)[1]),height=Number(source.match(/height="(\d+)"/)[1]);
  await page.setViewportSize({width,height});
  await page.setContent('<html><body style="margin:0;background:transparent">'+source+'</body></html>');
  await page.locator('svg').screenshot({path:resolve(destination,name.replaceAll('-','_')+'.png'),omitBackground:true});
 }
 console.log('Synchronized shared illustrations for Android: '+names.join(', '));
}finally{await browser.close();}
