import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {chromium} from 'playwright';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const source=fs.readFileSync(path.join(root,'统一权限管理系统需求与详细设计说明书.adoc'),'utf8');
const files=[...new Set([...source.matchAll(/image::([^\[]+)/g)].map(m=>path.join(root,'assets',m[1])))];
const browser=await chromium.launch({executablePath:process.env.PDF_CHROME_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
const report=[];
try {
 const page=await browser.newPage();
 for(const file of files){
  await page.goto(pathToFileURL(file).href);
  const check=await page.evaluate(async()=>{
   await document.fonts.ready;
   const svg=document.querySelector('svg');const vb=svg.viewBox.baseVal;
   const boxes=[...svg.querySelectorAll('text')].map(t=>{const b=t.getBBox();return {text:t.textContent,x:b.x,y:b.y,w:b.width,h:b.height,size:parseFloat(t.getAttribute('font-size'))};});
   const clipped=boxes.filter(b=>b.x<-.5||b.y<-.5||b.x+b.w>vb.width+.5||b.y+b.h>vb.height+.5);
   const collisions=[];
   for(let i=0;i<boxes.length;i++)for(let j=i+1;j<boxes.length;j++){
    const a=boxes[i],b=boxes[j];
    const dx=Math.min(a.x+a.w,b.x+b.w)-Math.max(a.x,b.x);
    const dy=Math.min(a.y+a.h,b.y+b.h)-Math.max(a.y,b.y);
    if(dx>3&&dy>3)collisions.push([a.text,b.text]);
   }
   return {width:vb.width,height:vb.height,texts:boxes.length,clipped,collisions,minFont:Math.min(...boxes.map(b=>b.size))};
  });report.push({file:path.relative(root,file),...check});
 }
}finally{await browser.close();}
fs.writeFileSync(path.join(root,'build/diagram-audit.json'),JSON.stringify(report,null,2));
const failures=report.filter(r=>r.clipped.length||r.collisions.length);
console.log(JSON.stringify({figures:report.length,failures},null,2));
if(failures.length)process.exitCode=1;
