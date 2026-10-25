import {readFile,writeFile,rename,mkdir} from 'node:fs/promises';
import {join} from 'node:path';
import {randomUUID} from 'node:crypto';
import {validateDesign, type Design} from '../../shared/design.ts';
export class ConflictError extends Error {}
export class DesignStore {
  private queue: Promise<unknown> = Promise.resolve();
  private directory: string;
  constructor(directory: string) {this.directory=directory;}
  async read(): Promise<Design> {return validateDesign(JSON.parse(await readFile(join(this.directory,'project.json'),'utf8')));}
  save(input: unknown, expectedRevision: number): Promise<Design> {
    const task = this.queue.then(async()=>{
      const next = validateDesign(input);
      const current = await this.read();
      if (current.revision !== expectedRevision) throw new ConflictError('文件已被其他窗口修改，请重新载入后再编辑');
      next.revision = current.revision + 1; next.updatedAt = new Date().toISOString();
      await mkdir(this.directory,{recursive:true});
      const temp = join(this.directory, `${randomUUID()}.tmp`);
      await writeFile(temp,JSON.stringify(next,null,2)+'\n','utf8');
      await rename(temp,join(this.directory,'project.json'));
      return next;
    });
    this.queue = task.catch(()=>{}); return task;
  }
}
