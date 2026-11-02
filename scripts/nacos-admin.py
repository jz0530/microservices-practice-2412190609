"""Local Nacos helper for the personal project. Credentials stay in .env."""
import json, sys, time
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

ROOT = Path(__file__).resolve().parents[1]
CONSOLE = 'http://localhost:18080'
AUTH = 'http://localhost:8848/nacos/v3/auth/user/'
GROUP = 'PERSONAL_GROUP'
SERVICES = ['user-service', 'product-service', 'order-service']


def env():
    values = {}
    for line in (ROOT / '.env').read_text(encoding='utf-8-sig').splitlines():
        if '=' in line and not line.startswith('#'):
            key, value = line.split('=', 1)
            values[key.strip()] = value.strip()
    return values


def post(action, values):
    request = Request(AUTH + action, data=urlencode(values).encode(), method='POST')
    with urlopen(request, timeout=8) as response:
        return json.load(response)


def login():
    value = post('login', {'username': 'nacos', 'password': env()['NACOS_PASSWORD']})
    token = value.get('accessToken') or value.get('data', {}).get('accessToken')
    if not token:
        raise RuntimeError('Nacos login failed; check NACOS_PASSWORD in local .env')
    return token


def initialize():
    for _ in range(90):
        try:
            with urlopen(CONSOLE + '/', timeout=3) as response:
                if response.status == 200:
                    break
        except (URLError, OSError):
            pass
        time.sleep(2)
    else:
        raise RuntimeError('Nacos console did not start')
    try:
        login()
        print('Nacos administrator already initialized; login verified.')
        return
    except (HTTPError, RuntimeError):
        pass
    try:
        post('admin', {'password': env()['NACOS_PASSWORD']})
    except HTTPError:
        raise RuntimeError('Administrator initialization failed; existing password may differ from .env') from None
    login()
    print('Nacos administrator initialized; password remains only in local .env.')


def instances(token, name):
    query = urlencode({
        'serviceName': name,
        'groupName': GROUP,
        'namespaceId': 'public',
        'pageNo': 1,
        'pageSize': 100,
    })
    request = Request(CONSOLE + '/v3/console/ns/instance/list?' + query,
                      headers={'accessToken': token})
    with urlopen(request, timeout=8) as response:
        body = json.load(response)
    if body.get('code') != 0:
        raise RuntimeError('Nacos instance query failed')
    return body['data']['pageItems']


def show():
    token = login()
    print('Nacos console: ' + CONSOLE + ' | namespace=public | group=' + GROUP)
    for name in SERVICES:
        rows = instances(token, name)
        if not rows:
            print(name + ' : no instances')
        for row in rows:
            print(f"{name}: {row['ip']}:{row['port']} healthy={row['healthy']} enabled={row['enabled']}")


if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == 'init':
        initialize()
    else:
        show()
