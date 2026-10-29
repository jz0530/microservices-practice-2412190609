#!/usr/bin/env python3
"""Real HTTP workflow verification. --dependency-failure briefly stops product-service and restores it."""
import argparse, json, os, subprocess, time, uuid
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--dependency-failure', action='store_true')
parser.add_argument('--output', default='docs/homework/week-06/verification.json')
args = parser.parse_args()
urls = {name: os.environ.get(name.upper()+'_SERVICE_URL', 'http://localhost:'+str(port)).rstrip('/')
        for name, port in [('user',8101),('product',8102),('order',8103)]}
events = []

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
    wait(service)
    assert request(service,'/actuator/health')['status']=='UP'
user = request('user','/api/users',{'name':'Week06 Alice'},201)
product = request('product','/api/products',{'title':'Week06 HTTP textbook','description':'Three-service workflow','price':25.50},201)
orders_before = request('order','/api/orders')
order = request('order','/api/orders',{'buyerId':user['id'],'productId':product['id']},201)
assert order['buyerId']==user['id'] and order['productId']==product['id'] and order['amount']==25.5
assert order['status']=='PENDING_PAYMENT'
assert request('product','/api/products/'+product['id'])['status']=='RESERVED'
assert request('order','/api/orders/'+order['id'])['id']==order['id']
orders_success = request('order','/api/orders')
assert len(orders_success)==len(orders_before)+1
request('order','/api/orders',{'buyerId':user['id'],'productId':product['id']},409)
assert len(request('order','/api/orders'))==len(orders_success)
fresh = request('product','/api/products',{'title':'Week06 failure guard','description':'Must remain available','price':12},201)
request('order','/api/orders',{'buyerId':str(uuid.uuid4()),'productId':fresh['id']},404)
assert request('product','/api/products/'+fresh['id'])['status']=='AVAILABLE'
assert len(request('order','/api/orders'))==len(orders_success)
request('order','/api/orders',{'buyerId':user['id'],'productId':str(uuid.uuid4())},404)
assert len(request('order','/api/orders'))==len(orders_success)
failure = 'not requested'
if args.dependency_failure:
    try:
        compose('stop','product-service')
        request('order','/api/orders',{'buyerId':user['id'],'productId':fresh['id']},503)
        assert len(request('order','/api/orders'))==len(orders_success)
        failure = 'HTTP 503, no local order written'
    finally:
        compose('start','product-service')
        wait('product')
    assert request('product','/api/products/'+fresh['id'])['status']=='AVAILABLE'
result = {'verifiedAt':time.strftime('%Y-%m-%dT%H:%M:%S%z'),'userId':user['id'],
          'productId':product['id'],'orderId':order['id'],'failureProductId':fresh['id'],
          'ordersBefore':len(orders_before),'ordersAfter':len(orders_success),
          'dependencyFailure':failure,'events':events,'passed':True}
output = Path(args.output)
if not output.is_absolute():output=root/output
output.parent.mkdir(parents=True,exist_ok=True)
output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('PASS: real HTTP workflow and failure guards; evidence: '+str(output),flush=True)
