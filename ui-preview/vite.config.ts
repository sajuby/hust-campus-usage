import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {DesignStore,ConflictError} from './server/design-store.ts';
const root = fileURLToPath(new URL('..',import.meta.url));
const store = new DesignStore(resolve(root,process.env.CAMPUS_TEST_CONFIG_DIR||'design'));
export default defineConfig({
  plugins:[react(),{
    name:'local-design-editor',
    configureServer(server) {
      server.middlewares.use('/api',async(req,res,next)=>{
        if (!req.url?.startsWith('/design') && req.url !== '/android/apply') return next();
        res.setHeader('Content-Type','application/json; charset=utf-8');
        res.setHeader('Cache-Control','no-store');
        try {
          const port=server.config.server.port;
          if (req.headers.origin && req.headers.origin !== `http://127.0.0.1:${port}` && req.headers.origin !== `http://localhost:${port}`) throw new Error('仅允许本机预览页面访问');
          if(req.url === '/design' && req.method==='GET') return res.end(JSON.stringify(await store.read()));
          if(req.url === '/design' && req.method==='PUT') {
            if (!req.headers['content-type']?.startsWith('application/json')) throw new Error('需要 JSON 内容');
            let body=''; for await(const chunk of req) {body += chunk.toString();if(body.length>65536)throw new Error('配置过大');}
            const payload=JSON.parse(body);
            return res.end(JSON.stringify(await store.save(payload.design,payload.expectedRevision)));
          }
          if(req.url === '/android/apply' && req.method==='POST') {
            const run = await promisify(execFile)(process.execPath,[resolve(root,'scripts/sync-design.mjs'),'--apply'],{cwd:root,timeout:30000,windowsHide:true});
            return res.end(JSON.stringify({message:run.stdout.trim()}));
          }
          res.statusCode=405; res.end(JSON.stringify({error:'不支持的操作'}));
        } catch(e) {const error=e as Error&{stderr?:string};res.statusCode=e instanceof ConflictError?409:400;res.end(JSON.stringify({error:error.stderr?.trim()||error.message||String(e)}));}
      });
    }
  }],
  server:{fs:{allow:[root]},host:'127.0.0.1',port:5173,strictPort:true},
  build:{outDir:'dist'}
});
