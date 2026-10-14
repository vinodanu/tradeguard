"""Measured local benchmark. For 30 minutes, use compose.performance.yaml first."""
from client import *
import argparse,threading,statistics,platform,concurrent.futures,subprocess,json
p=argparse.ArgumentParser();p.add_argument('--seconds',type=int,default=1800);p.add_argument('--rate',type=int,default=5);p.add_argument('--clients',type=int,default=20);a=p.parse_args()
assert 1<=a.seconds<=1800 and 1<=a.rate<=5 and 1<=a.clients<=20
token=login('trader');view=login('viewer');stop=threading.Event();latencies=[];errors=[]
def sse():
 while not stop.is_set():
  try:
   r=urllib.request.urlopen(urllib.request.Request(BASE+'/api/v1/stream',headers={'Authorization':'Bearer '+view}),timeout=20)
   with r:
    while not stop.is_set():
     if not r.readline():break
  except Exception:time.sleep(.5)
threads=[threading.Thread(target=sse,daemon=True) for _ in range(a.clients)]
for t in threads:t.start()
def submit():
 start=time.monotonic()
 try:
  order=request('/orders',token,{'portfolioId':'PORTFOLIO_A','instrumentId':'ACME','side':'BUY','quantity':'1','price':'100'})
  wait(lambda:request('/orders/'+order['id'],token),lambda d:d['status']!='EVALUATING',timeout=45);latencies.append(time.monotonic()-start)
 except Exception as e:errors.append(type(e).__name__)
start=time.monotonic();refresh=start
with concurrent.futures.ThreadPoolExecutor(max_workers=30) as pool:
 futures=[]
 for i in range(a.seconds*a.rate):
  if time.monotonic()-refresh>780:token=login('trader');view=login('viewer');refresh=time.monotonic()
  delay=start+i/a.rate-time.monotonic()
  if delay>0:time.sleep(delay)
  futures.append(pool.submit(submit))
 for f in futures:f.result()
stop.set();values=sorted(latencies);p95=values[max(0,int(len(values)*.95)-1)] if values else None
stats=subprocess.run(['docker','stats','--no-stream','--format','{{json .}}'],capture_output=True,text=True)
report={'host':platform.platform(),'durationSeconds':a.seconds,'ordersPerSecond':a.rate,'streamClients':a.clients,'completed':len(values),'errors':errors,'p95Seconds':p95,'targetMet':p95 is not None and p95<2 and not errors,'containerMemoryAtEnd':stats.stdout.splitlines(),'notes':'End-of-run memory is not a peak measurement. Use docker stats during the run to observe peaks.'}
(ROOT/'docs'/'performance-results.json').write_text(json.dumps(report,indent=2));print(json.dumps({k:v for k,v in report.items() if k!='containerMemoryAtEnd'},indent=2))
