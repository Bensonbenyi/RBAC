#!/usr/bin/env python3
"""Observe existing sessions through an operator-triggered restart or dependency outage."""
import argparse,json,os,time,urllib.request,urllib.error
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--base',required=True);p.add_argument('--mode',choices=['restart','dependency'],required=True);p.add_argument('--report',required=True);a=p.parse_args()
def call(path,token=None,body=None):
 headers={'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 req=urllib.request.Request(a.base+path,data=json.dumps(body).encode() if body is not None else None,headers=headers)
 try:
  with urllib.request.urlopen(req,timeout=8) as r:return r.status,json.load(r)
 except urllib.error.HTTPError as e:return e.code,None
 except Exception:return 0,None
system='rbac' if a.mode=='restart' else 'course-project-management'
_,data=call('/auth/login',body={'username':'admin' if a.mode=='restart' else 'reviewer','password':os.environ['RBAC_DEMO_PHONE'],'systems':[system]})
token=data['accessToken'];path='/auth/me' if a.mode=='restart' else '/course-api/terms'
assert call(path,token)[0]==200
print('Ready: trigger',a.mode,'now',flush=True)
start=time.monotonic();first_failure=None;statuses=[];restored=False
while time.monotonic()-start<150:
 code,_=call(path,token);now=time.monotonic();statuses.append({'afterSeconds':round(now-start,3),'status':code})
 if code!=200 and first_failure is None:first_failure=now;print('Observed unavailable status',code,flush=True)
 if first_failure is not None and code==200:restored=True;break
 time.sleep(.5)
report={'date':'2026-11-17','mode':a.mode,'path':path,'previousTokenAccepted':restored,'firstFailureToRecoverySeconds':round(time.monotonic()-first_failure,3) if restored else None,'observations':statuses}
if a.mode=='dependency':report['courseReturned503']=any(x['status']==503 for x in statuses)
Path(a.report).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps({k:v for k,v in report.items() if k!='observations'}))
if not restored or (a.mode=='dependency' and not report['courseReturned503']):raise SystemExit(1)
