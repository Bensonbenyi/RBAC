#!/usr/bin/env python3
"""从当前接口目录构建 OpenAPI 3.0 与本地 Swagger UI。"""
from pathlib import Path
import json,re,sys,shutil
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'docs/redesign/tools'))
from v33_catalog import OPS
R=ROOT/'docs/api';version=(ROOT/'docs/redesign/VERSION').read_text().strip()
def obj(properties,required=()):return dict(type='object',properties=properties,additionalProperties=False,**({'required':list(required)} if required else {}))
def ref(name):return {'$ref':'#/components/schemas/'+name}
def arr(name):return {'type':'array','items':ref(name)}
string={'type':'string'};id_schema={'type':'string','pattern':'^[1-9][0-9]*$','description':'BIGINT 以字符串返回，避免前端精度丢失'}
name={'type':'string','minLength':1,'maxLength':100,'pattern':r'.*\S.*'};phone={'type':'string','pattern':'^1[3-9][0-9]{9}$'}
schemas={
 'Login':obj({'username':{'type':'string','minLength':1,'maxLength':64,'pattern':r'.*\S.*'},'password':dict(phone,description='本版承载手机号'),'systems':{'type':'array','minItems':1,'maxItems':1,'items':{'type':'string','minLength':1,'maxLength':64,'pattern':r'.*\S.*'}}},['username','password','systems']),
 'LoginResult':obj({'accessToken':string,'tokenType':{'type':'string','enum':['Bearer']},'expiresIn':{'type':'integer','format':'int64'}}),
 'Me':obj({'subject':id_schema,'username':string,'name':string,'departmentId':id_schema,'roles':{'type':'array','items':string,'description':'直接与继承角色名称'},'permissions':{'type':'array','items':string,'description':'当前系统有效界面码，不含 API 码'}}),
 'Allowed':obj({'allowed':{'type':'boolean'}}),
 'IdentityUser':obj({'userId':id_schema,'no':string,'name':string}),
 'Error':obj({'code':string,'message':string}),
 'Name':obj({'name':name},['name']),
 'Profile':dict(obj({'name':dict(name,nullable=True),'phone':dict(phone,nullable=True)}),anyOf=[{'required':['name'],'properties':{'name':name}},{'required':['phone'],'properties':{'phone':phone}}]),
 'CreateUser':obj({'username':{'type':'string','minLength':1,'maxLength':64,'pattern':r'.*\S.*'},'name':name,'phone':phone},['username','name','phone']),
 'CreateDepartment':obj({'name':name,'parentId':{'type':'integer','format':'int64','minimum':1,'nullable':True}},['name']),
 'CreateSystem':obj({'systemKey':{'type':'string','minLength':1,'maxLength':64,'pattern':'^[a-z0-9-]+$'},'name':name},['systemKey','name']),
 'CreatePermission':obj({'code':{'type':'string','minLength':1,'maxLength':100,'pattern':'^[a-zA-Z0-9.:_-]+$'},'name':name},['code','name']),
 'AssignRole':obj({'roleId':{'type':'integer','format':'int64','minimum':1}},['roleId'])}
for entity,fields in {'User':['id','username','name','phone','departmentId'],'Role':['id','name','departmentId'],'Department':['id','name','parentId'],'System':['id','systemKey','name'],'Permission':['id','systemId','code','name']}.items():
 schemas[entity]=obj({n:(dict(id_schema,nullable=True) if n=='parentId' else id_schema if n=='id' or n.endswith('Id') else string) for n in fields})
 schemas['Page'+entity]=obj({'items':arr(entity),'page':{'type':'integer'},'size':{'type':'integer'},'total':{'type':'integer','format':'int64'}})
actual=set()
for controller in (ROOT/'backend/src/main/java/cn/edu/xmu/rbac/api').rglob('*Controller.java'):
 source=controller.read_text()
 base_match=re.search(r'@RequestMapping\("([^\"]*)"\)',source)
 base=base_match.group(1) if base_match else ''
 for method,route in re.findall(r'@(Get|Post|Put|Patch|Delete)Mapping(?:\("([^\"]*)"\))?',source):
  actual.add((method.upper(),base+route))
expected={(op.method,op.route) for op in OPS}
assert actual==expected, f'API catalog mismatch: missing={actual-expected}, stale={expected-actual}'
paths={}
for op in OPS:
 operation={'operationId':op.module.lower()+'.'+op.function if op.key!='AUTH-07' else 'identity.get','tags':[op.module],'summary':op.title,'description':op.rule+'\n\nAPI 许可：'+op.permission,'parameters':[],'responses':{},'security':[] if op.key=='AUTH-01' else [{'Bearer':[]}]}
 for n in re.findall(r'\{([^}]+)\}',op.route):operation['parameters'].append({'name':n,'in':'path','required':True,'schema':{'type':'string','minLength':1,'maxLength':100} if n in ['permission','operationId'] else {'type':'integer','format':'int64','minimum':1}})
 if op.function in ['list','options']:
  for n,default,minimum,maximum in [('page',0,0,1000000),('size',20,1,100)]:operation['parameters'].append({'name':n,'in':'query','schema':{'type':'integer','default':default,'minimum':minimum,'maximum':maximum}})
 if op.key=='PERM-05':operation['parameters'].append({'name':'systemId','in':'query','required':True,'schema':{'type':'integer','format':'int64','minimum':1}})
 body={'AUTH-01':'Login','AUTH-04':'Profile','USER-03':'CreateUser','USER-04':'Profile','USER-07':'AssignRole','ROLE-03':'Name','ROLE-04':'Name','DEPT-02':'CreateDepartment','DEPT-03':'Name','SYS-02':'CreateSystem','SYS-03':'Name','PERM-02':'CreatePermission','PERM-03':'Name'}.get(op.key)
 if body:operation['requestBody']={'required':True,'content':{'application/json':{'schema':ref(body)}}}
 status=op.result.split()[0]
 result={'AUTH-01':'LoginResult','AUTH-02':'Me','AUTH-05':'Allowed','AUTH-06':'Allowed','AUTH-07':'IdentityUser'}.get(op.key)
 if op.module!='Auth':
  result=('Page'+op.module if op.function in ['list','options'] else op.module if op.function in ['get','create'] else None)
  if op.function in ['permissions','effectivePermissions']:result=arr('Permission')
  if op.function=='roles' or op.function=='inheritedRoles':result=arr('Role')
 response={'description':op.result}
 # USER-07 responds with 201 and no body.
 if result:response['content']={'application/json':{'schema':ref(result) if isinstance(result,str) else result}}
 if status=='201':response['headers']={'Location':{'description':'新资源地址','schema':string}}
 operation['responses'][status]=response
 for code,description in [('400','输入校验失败'),('401','凭据或会话无效'),('403','无操作许可或越界'),('404','对象不存在'),('405','HTTP 方法不支持'),('409','重复、引用冲突或 ROLE_CYCLE'),('503','数据库不可用')]:operation['responses'][code]={'description':description,'content':{'application/json':{'schema':ref('Error')}}}
 paths.setdefault(op.route,{})[op.method.lower()]=operation
spec={'openapi':'3.0.3','info':{'title':'统一权限管理平台 API','version':version,'description':'RBAC1 双实现契约。高级角色继承基础角色；界面码与 API 码独立。切换策略保持 HTTP 行为一致。'},'servers':[{'url':'/'}],'paths':paths,'components':{'securitySchemes':{'Bearer':{'type':'http','scheme':'bearer','bearerFormat':'JWT'}},'schemas':schemas}}
(R/'openapi.json').write_text(json.dumps(spec,ensure_ascii=False,indent=2)+'\n')
# Swagger UI assets are assembled at build time from the pinned official npm package.
assets=R/'node_modules/swagger-ui-dist'
assert assets.exists(),'Run npm ci --prefix docs/api first'
out=ROOT/'.runtime/api-site';out.mkdir(parents=True,exist_ok=True)
for f in ['swagger-ui-bundle.js','swagger-ui.css']:
 shutil.copyfile(assets/f,out/f)
for f in ['index.html','init.js','openapi.json']:
 shutil.copyfile(R/f,out/f)
print(f'OpenAPI and Swagger UI: {len(OPS)} operations, v{version}')
