"""Exercise the platform and the real teacher backend without printing tokens."""
import argparse,json,os,time,urllib.request,urllib.error
from datetime import date
p=argparse.ArgumentParser();p.add_argument('--base',default='http://localhost:8080');p.add_argument('--course',default='http://localhost:8081');p.add_argument('--report',default='docs/implementation/smoke-local.json');a=p.parse_args()
phone=os.environ['RBAC_DEMO_PHONE'];results=[]
def call(base,path,method='GET',body=None,token=None,status=200):
    headers={'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    req=urllib.request.Request(base+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=15) as r:code=r.status;raw=r.read()
    except urllib.error.HTTPError as e:code=e.code;raw=e.read()
    assert code==status,(path,code,raw.decode())
    results.append({'method':method,'path':path,'status':code})
    return json.loads(raw) if raw else None
def login(name,system):return call(a.base,'/auth/login','POST',{'username':name,'password':phone,'systems':[system]})['accessToken']
admin=login('admin','rbac');reviewer=login('reviewer','course-project-management');viewer=login('viewer','course-project-management')
call(a.base,'/auth/me',token=admin);call(a.base,'/api/users',token=admin)
profile=call(a.course,'/me',token=viewer)
assert 'MCO' in profile['permissions'] and 'CV' not in profile['permissions']
term={'name':'联调学期 '+str(int(time.time())),'startDate':'2026-09-01','endDate':'2027-01-31'}
call(a.course,'/terms','POST',term,viewer,403)
created=call(a.course,'/terms','POST',term,reviewer,201)
call(a.course,'/terms/'+str(created['id']),'PATCH',{**term,'name':term['name']+' 修订'},reviewer)
call(a.course,'/terms/'+str(created['id']),'DELETE',token=reviewer,status=204)
courses=call(a.course,'/admin/courses?page=1&pageSize=20',token=reviewer)
pending=next((c for c in courses['items'] if c['courseStatus']==2),None)
assert pending,'Seed a pending review course before the smoke test'
detail=call(a.course,'/courses/'+str(pending['id']),token=reviewer)
assert detail['id']==pending['id']
roles=call(a.base,'/api/roles?size=100',token=admin)['items'];role=next(r for r in roles if r['name']=='reviewer')
permissions=call(a.base,'/api/roles/'+role['id']+'/permissions',token=admin);permission=next(x for x in permissions if x['code']=='CR')
path='/courses/'+str(pending['id'])+'/review';body={'decision':'approved','comment':None}
call(a.course,path,'POST',body,viewer,403)
relation='/api/roles/'+role['id']+'/permissions/'+permission['id']
call(a.base,relation,'DELETE',token=admin,status=204)
try:call(a.course,path,'POST',body,reviewer,403)
finally:call(a.base,relation.rsplit('/',1)[0],'POST',{'permissionId':permission['id']},admin,201)
reviewed=call(a.course,path,'POST',body,reviewer);assert reviewed['courseStatus']==3
call(a.base,'/auth/logout','POST',token=viewer,status=204);call(a.course,'/terms',token=viewer,status=401)
from pathlib import Path
Path(a.report).parent.mkdir(parents=True,exist_ok=True);Path(a.report).write_text(json.dumps({'date':date.today().isoformat(),'checks':results,'result':'passed'},ensure_ascii=False,indent=2)+'\n');print('Smoke integration passed:',len(results),'HTTP checks')
