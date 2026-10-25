import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';
import {execFile,spawn} from 'node:child_process';
import {promisify} from 'node:util';
import {validateDesign} from '../shared/design.ts';
async function main(){
const root=fileURLToPath(new URL('..',import.meta.url));
const config=validateDesign(JSON.parse(await readFile(resolve(root,'design/project.json'),'utf8')));
if(config.schemaVersion!==1)throw new Error('Unsupported design schema');
if(!process.argv.includes('--apply')) {console.log(`Android 构建将直接读取共用设计 v${config.revision}`);process.exit(0);}
const sdk=process.env.ANDROID_HOME||process.env.ANDROID_SDK_ROOT;
const adb=sdk?resolve(sdk,`platform-tools/adb${process.platform==='win32'?'.exe':''}`):'adb';
const exec=promisify(execFile);
const adbEnv={...process.env};
const {stdout}=await exec(adb,['devices'],{windowsHide:true,env:adbEnv});
const devices=stdout.split(/\r?\n/).map(line=>line.trim().split(/\s+/)).filter(parts=>parts[1]==='device').map(parts=>parts[0]);
const serial=process.env.CAMPUS_DEVICE_SERIAL??(devices.length===1?devices[0]:null);
if(!serial){console.error('请连接一个 Android 调试设备；多个设备时设置 CAMPUS_DEVICE_SERIAL。');process.exit(1);}
const pkg='dev.campususage.app.debug';
const installed=await exec(adb,['-s',serial,'shell','pm','path',pkg],{windowsHide:true,env:adbEnv});
if(!installed.stdout.trim())throw new Error('请先在模拟器中安装本项目的 Android 调试包。');
await exec(adb,['-s',serial,'shell','run-as',pkg,'mkdir','-p','files'],{windowsHide:true,env:adbEnv});
await new Promise((resolvePromise,reject)=>{
 const child=spawn(adb,['-s',serial,'exec-in','run-as',pkg,'tee','files/design.json'],{windowsHide:true,env:adbEnv,stdio:['pipe','ignore','pipe']});
 let err='';child.stderr.on('data',chunk=>err+=chunk);child.on('error',reject);child.on('exit',code=>code===0?resolvePromise():reject(new Error(err||'写入设计失败')));
 child.stdin.on('error',reject);child.stdin.end(JSON.stringify(config));
});
await exec(adb,['-s',serial,'shell','am','start','-n',pkg+'/dev.campususage.app.MainActivity','--ez','reloadDesign','true'],{windowsHide:true,env:adbEnv});
console.log(`设计 v${config.revision} 已应用到模拟器 ${serial}。在“我的”页核对版本。`);
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
