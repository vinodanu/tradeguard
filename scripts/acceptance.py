"""Run against a started local Compose stack. --recovery restarts local services."""
from client import *
import argparse,subprocess,concurrent.futures,json
parser=argparse.ArgumentParser();parser.add_argument('--recovery',action='store_true');args=parser.parse_args()
trader,reviewer,admin,viewer,trader2=[login(u) for u in ['trader','reviewer','admin','viewer','trader2']]
results=[]
def passed(name):results.append(name);print('PASS:',name)
def new(instrument='ACME',quantity='10',key=None):
 body={'portfolioId':'PORTFOLIO_A','instrumentId':instrument,'side':'BUY','quantity':quantity,'price':'100'}
 return request('/orders',trader,body,key),body
def complete(order):return wait(lambda:request('/orders/'+order['id'],trader),lambda r:r['status']!='EVALUATING')
order,body=new(key='acceptance-'+str(uuid.uuid4()));normal=complete(order);assert normal['status']=='READY';passed('unrestricted order allowed')
for instrument in ['BANNED','FUND_X']:
 o,_=new(instrument);d=complete(o);assert d['status']=='BLOCKED';evaluation=request('/evaluations/'+d['evaluationId'],trader);assert any(r['code']=='RESTRICTED_INSTRUMENT' for r in evaluation['reasons']);passed(instrument+' restriction blocked')
for instrument in ['UNKNOWN','CYCLE_A','MISSING_REF']:
 o,_=new(instrument);assert complete(o)['status']=='AWAITING_REVIEW';passed(instrument+' data quality review')
request('/orders',viewer,body,expected=(403,));request('/orders/'+normal['id'],trader2,expected=(403,));passed('backend role and portfolio isolation')
key='retry-'+str(uuid.uuid4());first,_=new(key=key);second=request('/orders',trader,body,key);assert first['id']==second['id'];request('/orders',trader,{**body,'quantity':'11'},key,expected=(409,));passed('idempotent submission and mismatch rejection')
execution_body={'expectedVersion':normal['version']}
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
 responses=list(pool.map(lambda _:request('/orders/'+normal['id']+'/execute',trader,execution_body,expected=(200,409)),range(2)))
assert request('/orders/'+normal['id'],trader)['status']=='EXECUTED';assert sum(r.get('status')=='EXECUTED' for r in responses)==1;passed('concurrent execution produces one execution')
review_order,_=new(quantity='1001');d=complete(review_order);assert d['status']=='AWAITING_REVIEW'
cases=wait(lambda:request('/cases?size=100',reviewer)['items'],lambda items:any(c.get('evaluationId')==d['evaluationId'] for c in items))
case=next(c for c in cases if c.get('evaluationId')==d['evaluationId']);request('/cases/'+case['id']+'/resolve',trader,{'expectedVersion':case['version'],'disposition':'APPROVED','note':'Invalid self review'},expected=(403,));case=request('/cases/'+case['id']+'/assign',reviewer,{'expectedVersion':case['version']});request('/cases/'+case['id']+'/resolve',reviewer,{'expectedVersion':case['version'],'disposition':'APPROVED','note':'Synthetic test approved by reviewer'});wait(lambda:request('/orders/'+d['id'],trader),lambda r:r['status']=='READY');passed('review assignment, approval and self-review rejection')
rules=request('/rulesets',admin);draft=request('/rulesets',admin,{'name':'Acceptance rules','restrictedInstruments':['BANNED','ACME'],'restrictedIssuers':['BANNED_CORP'],'maxNotional':'100000'});request('/rulesets/'+draft['id']+'/publish',admin,{'expectedVersion':rules['active']['version']});fresh=request('/orders/'+d['id'],trader);res=request('/orders/'+d['id']+'/execute',trader,{'expectedVersion':fresh['version']});assert res['httpStatus']==202;assert complete(res)['status']=='BLOCKED';passed('rule publication invalidates old approval before execution')
external=str(uuid.uuid4());csv='source,externalTradeId,portfolioId,instrumentId,side,quantity,price,executedAt\nacceptance,'+external+',PORTFOLIO_A,BANNED,BUY,10,100,2025-01-01T12:00:00Z';imported=request('/executions/import',admin,{'csv':csv});again=request('/executions/import',admin,{'csv':csv});assert imported['executionIds']==again['executionIds'];post=wait(lambda:request('/cases?size=100',reviewer)['items'],lambda rows:any(c.get('executionId')==imported['executionIds'][0] for c in rows));case=next(c for c in post if c.get('executionId')==imported['executionIds'][0]);assert case['severity']=='HIGH';passed('historical violation and import deduplication')
request('/executions/import',admin,{'csv':csv+'\ninvalid'},expected=(400,));passed('whole-file CSV validation')
before=request('/dashboard',viewer);rebuilt=request('/operations/projections/rebuild',admin,{});after=request('/dashboard',viewer);assert after['orders']==before['orders'];passed('projection rebuild into a fresh generation')
if args.recovery:
 def compose(*parts):subprocess.run(['docker','compose',*parts],cwd=ROOT,check=True,stdout=subprocess.DEVNULL)
 # Restore initial rules to obtain an ALLOW order for the fail-closed check.
 active=request('/rulesets',admin)['active'];draft=request('/rulesets',admin,{'name':'Recovery rules','restrictedInstruments':['BANNED'],'restrictedIssuers':['BANNED_CORP'],'maxNotional':'100000'});request('/rulesets/'+draft['id']+'/publish',admin,{'expectedVersion':active['version']});o,_=new();ready=complete(o)
 compose('stop','compliance-service')
 try:request('/orders/'+o['id']+'/execute',trader,{'expectedVersion':ready['version']},expected=(503,));assert request('/orders/'+o['id'],trader)['status']=='READY'
 finally:compose('start','compliance-service')
 passed('compliance outage fails closed')
 compose('stop','kafka')
 try:queued,_=new();assert request('/orders/'+queued['id'],trader)['status']=='EVALUATING'
 finally:compose('start','kafka')
 assert complete(queued)['status']=='READY';passed('broker outage preserves and later publishes outbox work')
 compose('restart','compliance-service','investigation-service','live-view-service');wait(lambda:request('/dashboard',viewer),lambda d:d['orders']>0);passed('consumer restart recovery')
# Restore the initial business rules to make repeated runs predictable.
active=request('/rulesets',admin)['active'];draft=request('/rulesets',admin,{'name':'Restored demo rules','restrictedInstruments':['BANNED'],'restrictedIssuers':['BANNED_CORP'],'maxNotional':'100000'});request('/rulesets/'+draft['id']+'/publish',admin,{'expectedVersion':active['version']})
report={'passed':results,'environment':BASE,'scope':'real Compose acceptance; synthetic data only'};(ROOT/'docs'/'acceptance-results.json').write_text(json.dumps(report,indent=2));print(f'{len(results)} scenarios passed')
