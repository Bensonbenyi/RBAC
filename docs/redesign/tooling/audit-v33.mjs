import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {createRequire} from 'node:module';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const require=createRequire(import.meta.url),{chromium}=require('playwright');
const dir=path.join(root,'assets/v33');
const browser=await chromium.launch({executablePath:process.env.PDF_CHROME_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
const errors=[];let figures=0;
try{
 const page=await browser.newPage();
 for(const name of fs.readdirSync(dir).filter(n=>n.endsWith('.svg'))){
  figures++;await page.goto(pathToFileURL(path.join(dir,name)).href);await page.evaluate(()=>document.fonts.ready);
  const bad=await page.evaluate(()=>{
   const s=document.querySelector('svg'),v=s.viewBox.baseVal;
   return [...document.querySelectorAll('text')].flatMap(t=>{
    const b=t.getBBox();return b.x < -1 || b.y < -1 || b.x+b.width>v.width+1 || b.y+b.height>v.height+1 ? [{text:t.textContent,x:b.x,y:b.y,w:b.width,h:b.height}]:[];
   });
  });
  if(bad.length)errors.push({name,bad});
 }
 for(const doc of fs.readdirSync(root).filter(n=>n.endsWith('.adoc'))){
  const text=fs.readFileSync(path.join(root,doc),'utf8');
  for(const [,image] of text.matchAll(/^image::([^\[]+)/gm))if(!fs.existsSync(path.join(root,'assets',image)))errors.push({doc,missing:image});
 }
}finally{await browser.close();}
fs.mkdirSync(path.join(root,'build'),{recursive:true});
fs.writeFileSync(path.join(root,'build/figure-audit.json'),JSON.stringify({figures,errors},null,2));
if(errors.length){console.log(JSON.stringify(errors,null,2));process.exitCode=1;}else console.log(`Verified ${figures} SVG figures: text inside viewBox; document images present.`);
