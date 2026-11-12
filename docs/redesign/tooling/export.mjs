import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {createRequire} from 'node:module';
import {execFileSync} from 'node:child_process';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const version=fs.readFileSync(path.join(root,'VERSION'),'utf8').trim();
if(!/^3\.\d+\.\d+$/.test(version))throw Error('Expected semantic version 3.x.y');
const require=createRequire(import.meta.url);
const asciidoctor=require('@asciidoctor/core')();
// Both dependencies are installed locally for reproducible export.
const {chromium}=require('playwright');
const source=path.join(root,'统一权限管理系统需求与详细设计说明书.adoc');
const out=path.join(root,'build');fs.mkdirSync(out,{recursive:true});
const pdf=path.join(root,`统一权限管理系统需求与详细设计说明书-v${version}.pdf`);
const doc=asciidoctor.loadFile(source,{safe:'safe',standalone:true});
if(doc.getSections().length!==7)throw Error('Expected seven chapters');
let html=doc.convert();
html=html.replace('<h1>统一权限管理系统需求与详细设计说明书</h1>','<h1>统一权限管理系统<br>需求与详细设计说明书</h1>')
 .replace(/version 3\.\d+\.\d+,?/,`版本 ${version}`)
 .replace('2026-11-12</span>','2026 年 11 月 12 日</span>')
 .replace(/(<h[234][^>]*>|<a href="#[^"]+">)(\d+(?:\.\d+)*)\. /g,'$1$2 ');
// Use the source folder as HTML base; AsciiDoc remains portable with relative images.
html=html.replace('<head>',`<head><base href="${pathToFileURL(root+'/').href}">`);
html=html.replace(/<link rel="stylesheet" href="tooling\/spec.css">/,`<style>${fs.readFileSync(path.join(root,'tooling/spec.css'),'utf8')}</style>`);
const htmlPath=path.join(out,'index.html');fs.writeFileSync(htmlPath,html);
const chrome=process.env.PDF_CHROME_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const browser=await chromium.launch({executablePath:chrome});
try{
 const page=await browser.newPage();
 await page.goto(pathToFileURL(htmlPath).href,{waitUntil:'networkidle'});
 await page.evaluate(async()=>{
  await document.fonts.ready;await Promise.all([...document.images].map(i=>i.decode()));
  // Keep section introductions with the first figure.
  for(const section of document.querySelectorAll('.sect2,.sect3')) {
   const first=[...section.children].find(n=>n.classList.contains('diagram'));
   if(!first) continue;
   const group=document.createElement('div');group.className='plate-intro';
   section.prepend(group);
   for(const child of [...section.children]) {
    if(child===group) continue;
    group.append(child);
    if(child===first) break;
   }
  }
  for(const section of document.querySelectorAll('.sect2')) {
   const h=section.querySelector(':scope > h3');
   const first=section.querySelector(':scope > .sect3 > .plate-intro');
   if(h && first) first.prepend(h);
  }
  for(const section of document.querySelectorAll('.sect2,.sect3')) {
   const h=section.querySelector(':scope > h3,:scope > h4');
   if(h && /共同契约与配置装配|错误与异常映射|故障处理/.test(h.textContent)) section.classList.add('keep-section');
  }
  for(const table of document.querySelectorAll('.sect3 > table')) {
   if(table.rows[0]?.textContent.includes('字段') || table.rows[0]?.textContent.includes('输入对象')) table.classList.add('keep-table');
  }
  for(const table of document.querySelectorAll('table.use-case')) {
   const section=table.parentElement;
   const body=document.createElement('div');body.className='use-case-body';
   section.prepend(body);
   for(const child of [...section.children]) {
    if(child===body) continue;
    if(child.classList.contains('diagram')) break;
    body.append(child);
   }
  }
  for(const row of document.querySelectorAll('table.use-case tr')) {
   if(/^(主成功场景|扩展场景)/.test(row.cells[0]?.textContent.trim())) row.classList.add('scene-band');
  }
  for(const a of document.querySelectorAll('#toc a')){
   const label=document.createElement('span');label.className='toc-label';label.textContent=a.textContent;
   const dots=document.createElement('span');dots.className='toc-leader';
   const num=document.createElement('span');num.className='toc-page';num.textContent='00';
   a.replaceChildren(label,dots,num);
  }
 });
 const readPages='import json,sys\nfrom urllib.parse import unquote\nfrom pypdf import PdfReader\np=PdfReader(sys.argv[1])\nprint(json.dumps({unquote(n.lstrip("/")):p.get_destination_page_number(d)+1 for n,d in p.named_destinations.items()}))';
 let prev='',stable=false;
 for(let pass=0;pass<5;pass++){
  await page.pdf({path:pdf,printBackground:true,preferCSSPageSize:true,outline:true,tagged:true});
  const targets=JSON.parse(execFileSync('python3',['-c',readPages,pdf],{encoding:'utf8'}));
  const mapping=await page.evaluate(targets=>{
   const m={};for(const a of document.querySelectorAll('#toc a')){
    const id=decodeURIComponent(a.hash.slice(1));if(!(id in targets))throw Error(`Missing TOC target: ${id}`);
    a.querySelector('.toc-page').textContent=targets[id];m[id]=targets[id];
   }return m;
  },targets);
  if(JSON.stringify(mapping)===prev){stable=true;break;}prev=JSON.stringify(mapping);
 }
 if(!stable)throw Error('TOC pagination did not converge');
 const audit=await page.evaluate(()=>({images:document.images.length,broken:[...document.images].filter(i=>!i.naturalWidth).map(i=>i.src),headings:document.querySelectorAll('h2,h3,h4').length}));
 if(audit.broken.length)throw Error(JSON.stringify(audit));
 fs.writeFileSync(htmlPath,await page.content());fs.writeFileSync(path.join(out,'export-check.json'),JSON.stringify(audit,null,2));
 console.log(`Exported ${pdf}; ${audit.images} figures; TOC converged.`);
}finally{await browser.close();}
