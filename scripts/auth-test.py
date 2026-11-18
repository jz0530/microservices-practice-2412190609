"""Week09: real gateway requests; never prints passwords, signing keys or JWTs."""
import base64, hashlib, hmac, json, os, sys, time, uuid
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

ROOT=Path(__file__).resolve().parents[1]
BASE=os.environ.get("GATEWAY_URL","http://127.0.0.1:8080").rstrip("/")
env={}
if (ROOT/".env").exists():
    for line in (ROOT/".env").read_text(encoding="utf-8-sig").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            k,v=line.split("=",1);env[k.strip()]=v.strip().strip('"').strip("'")
env.update(os.environ)

def call(method,path,body=None,token=None,headers=None):
    hdr={"Content-Type":"application/json"}
    if token: hdr["Authorization"]="Bearer "+token
    if headers: hdr.update(headers)
    request=Request(BASE+path,data=json.dumps(body).encode() if body is not None else None,headers=hdr,method=method)
    try:
        with urlopen(request,timeout=15) as response: status=response.status; raw=response.read()
    except HTTPError as error: status=error.code;raw=error.read()
    except (URLError,TimeoutError) as error:
        raise AssertionError(method+" "+path+" transport failure; write outcome may be unknown; NOT retried") from error
    payload=json.loads(raw) if raw else None
    print(f"{method} {path} -> HTTP {status}",flush=True)
    return status,payload
def expect(actual,wanted,label):
    assert actual==wanted,f"{label}: expected {wanted}, got {actual}"
    print("PASS  "+label,flush=True)
def login(name,key):
    assert env.get(key),"Missing local setting: "+key
    status,data=call("POST","/api/auth/login",{"username":name,"password":env[key]})
    expect(status,200,name+" login (JWT redacted)")
    assert data.get("token") and data.get("role") in ("BUYER","ADMIN")
    return data["token"],data
def expired():
    def enc(x):return base64.urlsafe_b64encode(json.dumps(x,separators=(",",":")).encode()).rstrip(b"=")
    data=enc({"alg":"HS512","typ":"JWT"})+b"."+enc({"sub":"expired","role":"BUYER","exp":int(time.time())-60})
    sig=hmac.new(base64.b64decode(env["JWT_SECRET"]),data,hashlib.sha512).digest()
    return (data+b"."+base64.urlsafe_b64encode(sig).rstrip(b"=")).decode()
def main():
    print("Week09 auth-test | gateway="+BASE,flush=True)
    expect(call("GET","/actuator/health")[0],200,"gateway health")
    expect(call("GET","/api/products")[0],401,"no Token denied")
    expect(call("GET","/api/products",token="invalid")[0],401,"invalid Token denied")
    expect(call("GET","/api/products",token=expired())[0],401,"expired Token denied")
    expect(call("POST","/api/auth/login",{"username":"buyer","password":"deliberately-wrong"})[0],401,"wrong password denied")
    buyer,b=login("buyer","BUYER_PASSWORD"); admin,a=login("admin","ADMIN_PASSWORD"); buyer2,b2=login("buyer2","BUYER2_PASSWORD")
    assert b["buyerId"]!=b2["buyerId"] and a["role"]=="ADMIN"
    expect(call("GET","/api/products",token=buyer)[0],200,"valid Token reaches product route")
    expect(call("GET","/api/users/"+b["buyerId"],token=buyer)[0],200,"valid Token reaches user route")
    product={"title":"JWT-test-"+uuid.uuid4().hex[:8],"description":"Week09 repeatable test","price":25.5}
    expect(call("POST","/api/products",product,token=buyer)[0],403,"BUYER cannot create product")
    expect(call("POST","/api/products",product,token=buyer,headers={"X-User-Role":"ADMIN"})[0],403,"forged ADMIN header denied")
    expect(call("GET","/api/products",headers={"X-User-Role":"ADMIN","X-User-Id":"fake"})[0],401,"forged headers without Token denied")
    status,p=call("POST","/api/products",product,token=admin);expect(status,201,"ADMIN creates product")
    pid=p["id"]
    for token,role in [(buyer,"BUYER"),(admin,"ADMIN")]:
        expect(call("POST","/api/products/"+pid+"/reservations",token=token)[0],403,role+" cannot call internal reservation")
    status,p=call("GET","/api/products/"+pid,token=buyer);expect(status,200,"read product before order")
    assert p["status"]=="AVAILABLE"
    status,before=call("GET","/api/orders",token=admin);expect(status,200,"admin order list before denied write")
    assert isinstance(before,list)
    expect(call("POST","/api/orders",{"buyerId":b2["buyerId"],"productId":pid},token=buyer,
        headers={"X-Buyer-Id":b2["buyerId"],"X-User-Role":"ADMIN"})[0],403,"cannot order as another buyer even with forged headers")
    status,after=call("GET","/api/orders",token=admin);expect(status,200,"admin order list after denied write")
    assert isinstance(after,list) and len(before)==len(after)
    status,p=call("GET","/api/products/"+pid,token=buyer);expect(status,200,"read product after denied write")
    assert p["status"]=="AVAILABLE"
    print("PASS  denied write: no new order; product AVAILABLE")
    status,order=call("POST","/api/orders",{"buyerId":b["buyerId"],"productId":pid},token=buyer)
    expect(status,201,"BUYER creates own order through gateway + Feign")
    assert order["buyerId"]==b["buyerId"] and order["productId"]==pid and order["status"]=="PENDING_PAYMENT" and order["amount"]==25.5
    status,p=call("GET","/api/products/"+pid,token=buyer);expect(status,200,"read reserved product")
    assert p["status"]=="RESERVED"
    print("PASS  normal order: PENDING_PAYMENT; product RESERVED")
    expect(call("GET","/api/orders/"+order["id"],token=buyer)[0],200,"owner reads own order")
    expect(call("GET","/api/orders/"+order["id"],token=buyer2,headers={"X-Buyer-Id":b["buyerId"],"X-User-Role":"ADMIN"})[0],403,"other buyer cannot read order using forged identity")
    status,own=call("GET","/api/orders",token=buyer2);expect(status,200,"other buyer sees only own order list")
    assert isinstance(own,list) and all(o["buyerId"]==b2["buyerId"] for o in own)
    expect(call("POST","/api/products;x",product,token=buyer)[0],400,"matrix path denied")
    expect(call("POST","/api/products/",product,token=buyer)[0],403,"trailing slash keeps role check")
    print("PASS  ALL: routing, login, JWT, roles, forged headers, ownership, normal order")
if __name__=="__main__":
    try:main()
    except Exception as error:
        print("FAIL  "+str(error),file=sys.stderr);sys.exit(1)
