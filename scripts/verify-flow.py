#!/usr/bin/env python3
"""Week 07 real HTTP workflow. --dependency-failure briefly stops user-service and restores it."""
import argparse, json, os, runpy, subprocess, time, uuid
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--dependency-failure', action='store_true')
parser.add_argument('--output', default='docs/homework/week-07/verification.json')
args = parser.parse_args()
urls = {name: os.environ.get(name.upper()+'_SERVICE_URL', 'http://localhost:'+str(port)).rstrip('/')
        for name, port in [('user',8101),('product',8102),('order',8103)]}
events = []
registry_events = []
nacos = runpy.run_path(str(root / 'scripts/nacos-admin.py'))


def registry(stage, service, available):
    token = nacos['login']()
    for _ in range(60):
        rows = nacos['instances'](token, service)
        active = [r for r in rows if r['healthy'] and r['enabled']]
        if bool(active) == available:
            snapshot = {'stage': stage, 'service': service,
                        'group': 'PERSONAL_GROUP', 'namespace': 'public',
                        'instances': [{k: r[k] for k in ('ip', 'port', 'healthy', 'enabled')}
                                      for r in rows]}
            registry_events.append(snapshot)
            print('NACOS ' + json.dumps(snapshot), flush=True)
            return
        time.sleep(1)
    raise RuntimeError('Nacos instance state did not converge: ' + service)


def request(service, path, body=None, expected=200):
    method = 'POST' if body is not None else 'GET'
    req = Request(urls[service]+path, data=json.dumps(body).encode() if body is not None else None,
                  headers={'Content-Type':'application/json'}, method=method)
    try:
        with urlopen(req, timeout=12) as response:
            status, raw = response.status, response.read().decode()
    except HTTPError as error:
        status, raw = error.code, error.read().decode()
    payload = json.loads(raw)
    events.append({'method':method,'url':req.full_url,'request':body,'status':status,'response':payload})
    print(f'{method} {req.full_url} -> HTTP {status}',flush=True)
    print(json.dumps(payload,ensure_ascii=False),flush=True)
    assert status == expected, (expected,status,payload)
    return payload

def request_with_retry(service, path, body, expected, attempts=20):
    """Nacos client caches can need a few seconds after first registration."""
    for attempt in range(attempts):
        try:
            return request(service, path, body, expected)
        except AssertionError:
            if attempt == attempts - 1:
                raise
            time.sleep(2)


def wait(service):
    for _ in range(60):
        try:
            with urlopen(urls[service]+'/actuator/health',timeout=3) as response:
                if json.load(response)['status']=='UP':return
        except (URLError,TimeoutError,OSError):pass
        time.sleep(2)
    raise RuntimeError(service+' did not become healthy')

def compose(*cmd):
    subprocess.run(['docker','compose',*cmd],cwd=root,check=True)

for service in urls:
    registry('before', service + '-service', True)
    wait(service)
    assert request(service,'/actuator/health')['status']=='UP'
user = request('user','/api/users',{'name':'Week07 Nacos Buyer'},201)
product = request('product','/api/products',{'title':'Week07 Nacos textbook','description':'DiscoveryClient workflow','price':25.50},201)
orders_before = request('order','/api/orders')
order = request_with_retry('order','/api/orders',{'buyerId':user['id'],'productId':product['id']},201)
assert order['buyerId']==user['id'] and order['productId']==product['id'] and order['amount']==25.5
assert order['status']=='PENDING_PAYMENT'
assert request('product','/api/products/'+product['id'])['status']=='RESERVED'
assert request('order','/api/orders/'+order['id'])['id']==order['id']
orders_success = request('order','/api/orders')
assert len(orders_success)==len(orders_before)+1
request('order','/api/orders',{'buyerId':user['id'],'productId':product['id']},409)
assert len(request('order','/api/orders'))==len(orders_success)
fresh = request('product','/api/products',{'title':'Week07 failure guard','description':'Must remain available','price':12},201)
request('order','/api/orders',{'buyerId':str(uuid.uuid4()),'productId':fresh['id']},404)
assert request('product','/api/products/'+fresh['id'])['status']=='AVAILABLE'
assert len(request('order','/api/orders'))==len(orders_success)
request('order','/api/orders',{'buyerId':user['id'],'productId':str(uuid.uuid4())},404)
assert len(request('order','/api/orders'))==len(orders_success)
failure = 'not requested'
recovery = 'not requested'
if args.dependency_failure:
    try:
        compose('stop','user-service')
        registry('stopped', 'user-service', False)
        request('order','/api/orders',{'buyerId':user['id'],'productId':fresh['id']},503)
        assert len(request('order','/api/orders'))==len(orders_success)
        assert request('product','/api/products/'+fresh['id'])['status']=='AVAILABLE'
        failure = 'HTTP 503, no local order written, product still AVAILABLE'
    finally:
        compose('start','user-service')
        wait('user')
    registry('recovered', 'user-service', True)
    for attempt in range(20):
        try:
            recovered = request('order','/api/orders',{'buyerId':user['id'],'productId':fresh['id']},201)
            recovery = f'HTTP 201 after user-service re-registered (attempt {attempt+1})'
            break
        except AssertionError:
            if attempt == 19:
                raise
            time.sleep(2)
    assert request('product','/api/products/'+fresh['id'])['status']=='RESERVED'
    assert len(request('order','/api/orders'))==len(orders_success)+1
    recovery += '; order written and product RESERVED'
result = {'verifiedAt':time.strftime('%Y-%m-%dT%H:%M:%S%z'),'nacosGroup':'PERSONAL_GROUP',
          'userId':user['id'],'productId':product['id'],'orderId':order['id'],
          'failureProductId':fresh['id'],'ordersBefore':len(orders_before),
          'ordersAfter':len(orders_success),'dependencyFailure':failure,
          'recovery':recovery,'registryEvents':registry_events,
          'ordersAfterRecovery':len(request('order','/api/orders')),
          'events':events,'passed':True}
output = Path(args.output)
if not output.is_absolute():output=root/output
output.parent.mkdir(parents=True,exist_ok=True)
output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('PASS: real HTTP workflow and failure guards; evidence: '+str(output),flush=True)
