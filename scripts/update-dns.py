from pathlib import Path
import urllib.request,urllib.parse,ipaddress
root=Path(__file__).resolve().parents[1]
values=dict(line.split('=',1) for line in (root/'.env').read_text().splitlines() if '=' in line and not line.startswith('#'))
request=urllib.request.Request('http://169.254.169.254/latest/api/token',method='PUT',headers={'X-aws-ec2-metadata-token-ttl-seconds':'60'})
with urllib.request.urlopen(request,timeout=5) as r:token=r.read().decode()
request=urllib.request.Request('http://169.254.169.254/latest/meta-data/public-ipv4',headers={'X-aws-ec2-metadata-token':token})
with urllib.request.urlopen(request,timeout=5) as r:address=str(ipaddress.IPv4Address(r.read().decode()))
query=urllib.parse.urlencode({'domains':values['DUCKDNS_SUBDOMAIN'],'token':values['DUCKDNS_TOKEN'],'ip':address})
try:
 with urllib.request.urlopen('https://www.duckdns.org/update?'+query,timeout=10) as r:result=r.read().decode().strip()
 if result!='OK':raise RuntimeError('DNS update rejected')
except Exception:raise SystemExit('DNS update failed. Check hostname and token without exposing credentials.')
print('DNS record updated. Verify the Atlas allowlist matches the current host IP.')
