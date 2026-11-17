#!/usr/bin/env python3
"""Measure a fixed authorization workload; keep credentials and tokens out of the report."""
from collections import Counter
import argparse,concurrent.futures,json,os,statistics,time,urllib.request,urllib.error
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--base',required=True);p.add_argument('--strategy',choices=['structured','ddd'],required=True);p.add_argument('--requests',type=int,default=1000);p.add_argument('--concurrency',type=int,default=8);p.add_argument('--output',required=True);a=p.parse_args()
body=json.dumps({'username':'admin','password':os.environ['RBAC_DEMO_PHONE'],'systems':['rbac']}).encode()
with urllib.request.urlopen(urllib.request.Request(a.base+'/auth/login',data=body,headers={'Content-Type':'application/json'}),timeout=15) as response:token=json.load(response)['accessToken']
def request(_):
 start=time.perf_counter();code=0
 try:
  req=urllib.request.Request(a.base+('/auth/permissions/user.view' if _%2==0 else '/api/users?page=0&size=20'),headers={'Authorization':'Bearer '+token})
  with urllib.request.urlopen(req,timeout=15) as response:
   code=response.status
   result=json.load(response)
   if (_%2==0 and result.get('allowed') is not True) or (_%2==1 and 'items' not in result):code=0
 except urllib.error.HTTPError as e:code=e.code
 except Exception:code=0
 return (time.perf_counter()-start)*1000,code
for i in range(100):request(i)
start=time.perf_counter()
with concurrent.futures.ThreadPoolExecutor(max_workers=a.concurrency) as pool:results=list(pool.map(request,range(a.requests)))
elapsed=time.perf_counter()-start;times=sorted(t for t,_ in results);errors=sum(c!=200 for _,c in results)
report={'strategy':a.strategy,'target':a.base,'workload':['GET /auth/permissions/user.view','GET /api/users?page=0&size=20'],'requests':a.requests,'warmup':100,'concurrency':a.concurrency,'elapsedSeconds':round(elapsed,3),'requestsPerSecond':round(a.requests/elapsed,2),'p50Ms':round(statistics.median(times),2),'p95Ms':round(times[max(0,int(len(times)*.95)-1)],2),'maxMs':round(max(times),2),'statusCounts':dict(Counter(c for _,c in results)),'errors':errors,'errorRate':errors/a.requests,'date':'2026-11-17','client':'auxiliary 1.92.106.198, Python urllib, one connection per request'}
Path(a.output).parent.mkdir(parents=True,exist_ok=True);Path(a.output).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps(report,ensure_ascii=False))
if errors:raise SystemExit(1)
