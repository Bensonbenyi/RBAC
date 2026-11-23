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
name={'type':'string','minLength':1,'maxLength':100,'pattern':r'.*\S.*'}
integer_id={'type':'integer','format':'int64','minimum':1}
identity_operations={
 'AUTH-01':'login','AUTH-02':'getIdentityCurrentUser','AUTH-03':'logout',
 'AUTH-05':'checkIdentityPermission','AUTH-06':'getApiAuthority','AUTH-07':'lookupIdentityUser',
 'AUTH-08':'listIdentityDepartments','AUTH-09':'listIdentityDepartmentUsers',
}
schemas={
 'Login':obj({'username':{'type':'string','minLength':1,'maxLength':128},'password':{'type':'string','format':'password','minLength':1,'maxLength':256,'writeOnly':True,'description':'本版承载手机号'},'systems':{'type':'array','minItems':1,'uniqueItems':True,'items':{'type':'string','minLength':1}}},['username','password','systems']),
 'LoginResult':obj({'accessToken':string,'tokenType':{'type':'string','enum':['Bearer']},'expiresIn':{'type':'integer','minimum':1}},['accessToken','tokenType','expiresIn']),
 'Me':obj({'subject':string,'no':string,'name':string,'departmentId':integer_id,'roles':{'type':'array','items':string},'permissions':{'type':'array','items':string,'description':'当前系统有效界面码'}},['subject','roles','permissions']),
 'Allowed':obj({'allowed':{'type':'boolean'}},['allowed']),
 'IdentityUser':obj({'userId':string,'no':string,'name':string},['userId','no','name']),
 'IdentityDepartment':obj({'id':integer_id,'code':string,'name':string,'parentId':dict(integer_id,nullable=True),'hasChildren':{'type':'boolean'}},['id','code','name','hasChildren']),
 'IdentityDepartmentUser':obj({'userId':string,'no':string,'name':string,'departmentId':integer_id,'departmentName':string},['userId','no','name','departmentId','departmentName']),
 'Error':obj({'code':string,'message':string}),
 'ApiAuthority':obj({'operationId':string,'allowed':{'type':'boolean'}},['operationId','allowed']),
 'Name':obj({'name':name},['name']),
 'AssignRole':obj({'roleId':{'type':'integer','format':'int64','minimum':1}},['roleId']),
 'GrantPermission':obj({'permissionId':integer_id},['permissionId']),
 'InheritRole':obj({'juniorId':integer_id},['juniorId'])}
for entity,fields in {'User':['id','username','name','departmentId'],'Role':['id','name','departmentId'],'Department':['id','name','parentId'],'System':['id','systemKey','name'],'Permission':['id','systemId','code','name']}.items():
 schemas[entity]=obj({n:(dict(id_schema,nullable=True) if n=='parentId' else id_schema if n=='id' or n.endswith('Id') else string) for n in fields})
 schemas['Page'+entity]=obj({'items':arr(entity),'page':{'type':'integer'},'size':{'type':'integer'},'total':{'type':'integer','format':'int64'}})
for entity in ['IdentityDepartment','IdentityDepartmentUser']:
 schemas[entity+'Page']=obj({'items':arr(entity),'page':{'type':'integer','minimum':1},'pageSize':{'type':'integer','minimum':1,'maximum':100},'total':{'type':'integer','minimum':0}},['items','page','pageSize','total'])
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
 operation={'operationId':identity_operations.get(op.key, op.module.lower()+'.'+op.function),'tags':['统一身份鉴权' if op.key in identity_operations else op.module],'summary':op.title,'description':op.rule+'\n\nAPI 许可：'+op.permission,'parameters':[],'responses':{},'security':[] if op.key=='AUTH-01' else [{'Bearer':[]}]}
 for n in re.findall(r'\{([^}]+)\}',op.route):operation['parameters'].append({'name':n,'in':'path','required':True,'schema':{'type':'string','minLength':1,'maxLength':128} if n=='operationId' else {'type':'string','minLength':1} if n in ['permission','userId'] else integer_id})
 if op.key in identity_operations and op.key!='AUTH-01':
  operation['parameters'].append({'name':'X-Target-System','in':'header','description':'可选目标系统，必须在令牌 aud 中。默认使用 aud 首项。','schema':string})
 if op.key in {'AUTH-05','AUTH-08','AUTH-09'}:
  operation['description']+='\n\n课程网页可通过 VITE_IDENTITY_BASE_URL 直连。允许已配置来源以 Bearer 访问。浏览器的 OPTIONS 预检不发送令牌。'
 if op.key in {'AUTH-08','AUTH-09'}:
  operation['parameters'].extend([
   {'name':'keyword','in':'query','schema':dict(string,maxLength=100)},
   {'name':'page','in':'query','schema':{'type':'integer','minimum':1,'default':1}},
   {'name':'pageSize','in':'query','schema':{'type':'integer','minimum':1,'maximum':100,'default':20}}])
  if op.key=='AUTH-08':operation['parameters'].append({'name':'parentId','in':'query','schema':integer_id})
 if op.function in ['list','options']:
  for n,default,minimum,maximum in [('page',0,0,1000000),('size',20,1,100)]:operation['parameters'].append({'name':n,'in':'query','schema':{'type':'integer','default':default,'minimum':minimum,'maximum':maximum}})
 if op.key=='PERM-05':operation['parameters'].append({'name':'systemId','in':'query','required':True,'schema':{'type':'integer','format':'int64','minimum':1}})
 body={'AUTH-01':'Login','USER-07':'AssignRole','ROLE-03':'Name','ROLE-04':'Name','ROLE-07':'GrantPermission','ROLE-11':'InheritRole'}.get(op.key)
 if body:operation['requestBody']={'required':True,'content':{'application/json':{'schema':ref(body)}}}
 status=op.result.split()[0]
 result={'AUTH-01':'LoginResult','AUTH-02':'Me','AUTH-05':'Allowed','AUTH-06':'ApiAuthority','AUTH-07':'IdentityUser','AUTH-08':'IdentityDepartmentPage','AUTH-09':'IdentityDepartmentUserPage'}.get(op.key)
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
 if op.method=='GET':operation['responses'].pop('409',None)
 if op.key in identity_operations:
  error_codes={'400':'BAD_REQUEST','401':'UNAUTHORIZED','403':'FORBIDDEN','503':'IDENTITY_SERVICE_UNAVAILABLE'}
  missing={'AUTH-06':'API_OPERATION_NOT_FOUND','AUTH-07':'IDENTITY_USER_NOT_FOUND','AUTH-09':'IDENTITY_DEPARTMENT_NOT_FOUND'}.get(op.key)
  if missing:error_codes['404']=missing
  for code,error in error_codes.items():
   operation['responses'][code]['content']['application/json']['example']={'code':error,'message':operation['responses'][code]['description']}
  if not missing:operation['responses'].pop('404')
  operation['responses'].pop('409',None)
 paths.setdefault(op.route,{})[op.method.lower()]=operation
spec={'openapi':'3.0.3','info':{'title':'统一权限管理平台 API','version':version,'description':'RBAC1 双实现契约。课程前端通过统一身份接口实时读取界面权限和组织目录，师生使用课程名册中的工号或学号登录。'},'servers':[{'url':'/'}],'paths':paths,'components':{'securitySchemes':{'Bearer':{'type':'http','scheme':'bearer','bearerFormat':'JWT'}},'schemas':schemas}}
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
