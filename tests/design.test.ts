import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile,mkdtemp,writeFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {validateDesign} from '../shared/design.ts';
import {DesignStore,ConflictError} from '../ui-preview/server/design-store.ts';
import {demoUsage,isPublicationExpected,expectedPublication,shiftDate} from '../shared/usage.ts';
const design=JSON.parse(await readFile(new URL('../design/project.json',import.meta.url),'utf8'));
test('reject corrupt configuration without removing required UI semantics',()=>{
 assert.throws(()=>validateDesign({...design,tokens:{...design.tokens,pink:'red'}}));
 assert.throws(()=>validateDesign({...design,layout:{...design.layout,sections:['hero']}}));
 assert.throws(()=>validateDesign({...design,copy:{...design.copy,greeting:'   '}}));
});
test('atomic save survives reopening and stale writers cannot overwrite newer edits',async()=>{
 const directory=await mkdtemp(join(tmpdir(),'campus-design-'));
 try{
  await writeFile(join(directory,'project.json'),JSON.stringify(design));
  const store=new DesignStore(directory);const next=structuredClone(design);next.copy.greeting='欢迎回来';
  const result=await store.save(next,design.revision);
  assert.equal(result.revision,design.revision+1);
  assert.equal((await new DesignStore(directory).read()).copy.greeting,'欢迎回来');
  await assert.rejects(()=>store.save(design,design.revision),ConflictError);
  await assert.rejects(()=>store.save({...design,tokens:{}},result.revision));
  assert.equal((await store.read()).copy.greeting,'欢迎回来');
 }finally{await rm(directory,{recursive:true,force:true});}
});
test('simultaneous saves serialize and only one writer wins the revision',async()=>{
 const directory=await mkdtemp(join(tmpdir(),'campus-design-'));
 try{await writeFile(join(directory,'project.json'),JSON.stringify(design));const store=new DesignStore(directory);
 const results=await Promise.allSettled([store.save(design,design.revision),store.save(design,design.revision)]);
 assert.equal(results.filter(x=>x.status==='fulfilled').length,1);
 assert.equal((await store.read()).revision,design.revision+1);
 }finally{await rm(directory,{recursive:true,force:true});}
});
test('publication follows Shanghai next-day 08:00 across month and year boundaries',()=>{
 assert.equal(expectedPublication('2026-12-31'),'2027-01-01T08:00:00+08:00');
 assert.equal(isPublicationExpected('2026-10-22','2026-10-23T07:59:59+08:00'),false);
 assert.equal(isPublicationExpected('2026-10-22','2026-10-23T08:00:00+08:00'),true);
 assert.equal(shiftDate('2026-03-01',-1),'2026-02-28');
});
test('published zero is an available record, missing statistics are null, offline retains data',()=>{
 const clock='2026-10-23T09:00:00+08:00';
 assert.equal(demoUsage('energy','2026-10-22','available',clock)?.total,'0.0000');
 assert.equal(demoUsage('electricity','2026-10-22','pending',clock),null);
 assert.ok(demoUsage('electricity','2026-10-22','offline',clock));
 assert.equal(demoUsage('electricity','2026-10-23','available',clock),null);
});
