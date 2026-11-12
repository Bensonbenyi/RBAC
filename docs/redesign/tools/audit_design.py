"""Check interface coverage and UML message signatures before publishing."""
from pathlib import Path
import ast,json,re
from flow_catalog import entries, MODULES
R=Path(__file__).resolve().parents[1]
model=json.loads((R/'assets/spec/rbac-spec.mdj').read_text())
def walk(v):
 if isinstance(v,dict):
  yield v
  for x in v.values():yield from walk(x)
 elif isinstance(v,list):
  for x in v:yield from walk(x)
nodes=list(walk(model));byid={n['_id']:n for n in nodes if '_id' in n}
for n in nodes:
 if '$ref' in n:assert n['$ref'] in byid,n
calls=0
for n in nodes:
 if n.get('_type')!='UMLMessage' or 'signature' not in n:continue
 sig=byid[n['signature']['$ref']];label=n['name'];method=sig['name']
 assert re.search(r'\b'+re.escape(method)+r'\(',label),(method,label)
 args=label.split(method+'(',1)[1].rsplit(')',1)[0]
 actual=len(args.split(',')) if args.strip() else 0
 expected=len([p for p in sig['parameters'] if p.get('direction')!='return'])
 assert actual==expected,(label,expected)
 calls+=1
api={ep for _,_,eps,_ in entries() for ep in eps}
source=ast.parse((R/'tools/write_spec.py').read_text())
call_tables=next(ast.literal_eval(n.value) for n in source.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='CALLS' for t in n.targets))
documented={ep.strip() for rows in call_tables.values() for _,eps,_ in rows for ep in eps.split('；')}
assert api==documented,{'withoutFigures':sorted(documented-api),'withoutCallTables':sorted(api-documented)}
adoc=(R/'统一权限管理系统需求与详细设计说明书.adoc').read_text()
figures=set(re.findall(r'image::([^\[]+)',adoc))
for _,_,_,stems in entries():
 for stem in stems:assert 'spec/'+stem+'.svg' in figures,stem
assert len(figures)==len(re.findall(r'^image::',adoc,re.M)),'Duplicate figures'
# Module views must expose the whole object chain, not only the layer names.
for module in MODULES:
 for mode,prefix in [('structured','Structured'),('ddd','Ddd')]:
  title=module.title+'模块 '+('结构化' if mode=='structured' else 'DDD')+' 类图'
  diagram=next(n for n in nodes if n.get('_type')=='UMLClassDiagram' and n.get('name')==title)
  names={byid[v['model']['$ref']]['name'] for v in diagram['ownedViews'] if v.get('_type') in ['UMLClassView','UMLInterfaceView']}
  required={module.controller,module.port,prefix+module.domain+'Service',module.store,'Jpa'+module.domain+'Store',module.domain+'JpaRepository',module.domain+'PO',module.domain+'Result','CloneFactory',module.domain if mode=='ddd' else module.domain+'Data'}
  assert required<=names,(title,required-names)
domain=next(n for n in nodes if n.get('_type')=='UMLClassDiagram' and n.get('name')=='DDD 领域对象完整类图')
domainNames={byid[v['model']['$ref']]['name'] for v in domain['ownedViews'] if v.get('_type')=='UMLClassView'}
assert domainNames=={'User','Role','Department','BusinessSystem','Permission'},domainNames
report={'interfaces':len(api),'figures':len(figures),'signatureBoundCalls':calls,'references':'resolved','endpointCoverage':'complete','moduleClassChains':10,'domainObjects':5}
(R/'assets/spec/design-check.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
print(json.dumps(report,ensure_ascii=False))
