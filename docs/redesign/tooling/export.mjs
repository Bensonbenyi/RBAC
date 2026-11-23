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
const titles=['统一权限管理系统需求与详细设计说明书','统一权限管理系统概要设计说明书'];
for(const title of titles){
const source=path.join(root,title+'.adoc');
const out=path.join(root,'build');fs.mkdirSync(out,{recursive:true});
const pdf=path.join(root,`${title}-v${version}.pdf`);
const doc=asciidoctor.loadFile(source,{safe:'safe',standalone:true});
if(doc.getRevisionNumber()!==version)throw Error('Document revision does not match VERSION');
if(doc.getSections().length!==(title.includes('概要')?9:8))throw Error('Unexpected chapter count');
let html=doc.convert();
html=html.replace(`<h1>${title}</h1>`,`<h1>统一权限管理系统<br>${title.replace('统一权限管理系统','')}</h1>`)
 .replace(/version 3\.\d+\.\d+,?/,`版本 ${version}`)
 .replace(/(2026)-(\d{2})-(\d{2})<\/span>/g,'$1 年 $2 月 $3 日</span>')
 .replace(/(<h[234][^>]*>|<a href="#[^"]+">)(\d+(?:\.\d+)*)\. /g,'$1$2 ');
// Use the source folder as HTML base; AsciiDoc remains portable with relative images.
html=html.replace('<head>',`<head><base href="${pathToFileURL(root+'/').href}">`);
html=html.replace(/<link rel="stylesheet" href="tooling\/spec.css">/,`<style>${fs.readFileSync(path.join(root,'tooling/spec.css'),'utf8').replace('content: "统一权限管理系统需求与详细设计说明书"',`content: "${title}"`)}</style>`);
// Asciidoctor may have already inlined the stylesheet.
html=html.replace('content: "统一权限管理系统需求与详细设计说明书"',`content: "${title}"`);
const htmlPath=path.join(out,title+'.html');fs.writeFileSync(htmlPath,html);
const chrome=process.env.PDF_CHROME_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const browser=await chromium.launch({executablePath:chrome});
try{
 const page=await browser.newPage();
 await page.goto(pathToFileURL(htmlPath).href,{waitUntil:'networkidle'});
 await page.evaluate(async(isOutline)=>{
  if(isOutline)document.body.classList.add('outline');
  await document.fonts.ready;await Promise.all([...document.images].map(i=>i.decode()));
  for(const row of document.querySelectorAll('table.use-case tr')) {
   if(/^(主成功场景|扩展场景)/.test(row.cells[0]?.textContent.trim())) row.classList.add('scene-band');
  }
  for(const a of document.querySelectorAll('#toc a')){
   const label=document.createElement('span');label.className='toc-label';label.textContent=a.textContent;
   const dots=document.createElement('span');dots.className='toc-leader';
   const num=document.createElement('span');num.className='toc-page';num.textContent='00';
   a.replaceChildren(label,dots,num);
  }
 },title.includes('概要'));
 // Use the V2 text width and the print stylesheet's figure heights.
 await page.emulateMedia({media:'print'});
 await page.addStyleTag({content:'@media print { body { width:146.5mm; } }'});
 await page.evaluate(()=>{
  // Set physical sizes before Chromium fragments the page. The aspect ratio and text stay intact.
  const mm = 96 / 25.4;
  for(const img of document.querySelectorAll('.diagram img')) {
   const file=img.getAttribute('src').split('/').pop();
   const ratio=img.naturalWidth/img.naturalHeight;
   const dense=/^(?:class-|domain\.svg|packages-|architecture-classes-)/.test(file);
   const interaction=/^(?:auth-|user-|role-|dept-|sys-|perm-|course-|story-|adapter-|flow-)/.test(file);
   let heightLimit=171;
   if(dense) heightLimit=202;
   if(interaction && img.naturalHeight<=855) heightLimit=106;
   if(/^course-0[1-7]\.svg$/.test(file)) heightLimit=98;
   const height=Math.min(heightLimit,146.5/ratio);
   img.style.width=`${(height*ratio).toFixed(3)}mm`;
   img.style.height=`${height.toFixed(3)}mm`;
  }
  for(const section of document.querySelectorAll('.sect2,.sect3,.sect4')) {
   const children=[...section.children];
   const firstFigure=children.findIndex(child=>child.classList.contains('diagram'));
   if(firstFigure<1 || !children.slice(0,firstFigure).every(child=>child.matches('h3,h4,h5,.paragraph'))) continue;
   const blocks=children.slice(0,firstFigure+1);
   const introHeight=blocks.at(-1).getBoundingClientRect().bottom-blocks[0].getBoundingClientRect().top;
   if(introHeight>196*mm) continue;
   const intro=document.createElement('div');
   intro.className='plate-intro';
   section.prepend(intro);
   intro.append(...blocks);
  }
  // A short call table and the first interaction form one reading unit.
  for(const calls of document.querySelectorAll('.scene-detail')) {
   const intro=calls.nextElementSibling?.querySelector(':scope > .plate-intro');
   if(!intro || calls.offsetHeight+intro.offsetHeight>190*mm) continue;
   intro.prepend(calls);
  }
  for(const table of document.querySelectorAll('table')) {
   if(table.offsetHeight<175) table.classList.add('keep-table');
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
 const expectedFigures=(fs.readFileSync(source,'utf8').match(/^image::/gm)||[]).length;
 if(audit.broken.length || audit.images!==expectedFigures)throw Error(JSON.stringify({expectedFigures,...audit}));
 fs.writeFileSync(htmlPath,await page.content());fs.writeFileSync(path.join(out,title+'-export-check.json'),JSON.stringify(audit,null,2));
 console.log(`Exported ${pdf}; ${audit.images} figures; TOC converged.`);
}finally{await browser.close();}

}
