import {defineConfig} from '@playwright/test';
export default defineConfig({
 testDir:'./tests/ui',workers:1,timeout:30000,
 use:{baseURL:'http://127.0.0.1:5174',viewport:{width:1540,height:1080},launchOptions:process.env.CAMPUS_CHROME_PATH?{executablePath:process.env.CAMPUS_CHROME_PATH}:{},screenshot:'only-on-failure'},
 reporter:[['list']],outputDir:'test-results',
 webServer:{command:'node scripts/start-ui-tests.mjs',url:'http://127.0.0.1:5174',reuseExistingServer:false,timeout:30000}
});
