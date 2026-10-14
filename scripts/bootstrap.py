"""Generate local credentials once. Existing secrets are never overwritten."""
from pathlib import Path
from check_linux_files import check
import secrets,base64,os
ROOT=Path(__file__).resolve().parents[1]
check()
try:
 from cryptography.hazmat.primitives.asymmetric import rsa
 from cryptography.hazmat.primitives import serialization
except ImportError:
 raise SystemExit('Install bootstrap dependency: python -m pip install cryptography==46.0.3')
directory=ROOT/'secrets';directory.mkdir(exist_ok=True)
if not (directory/'private.pem').exists():
 key=rsa.generate_private_key(public_exponent=65537,key_size=3072)
 (directory/'private.pem').write_bytes(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
 (directory/'public.pem').write_bytes(key.public_key().public_bytes(serialization.Encoding.PEM,serialization.PublicFormat.SubjectPublicKeyInfo))
 (directory/'private.pem').chmod(0o600)
if not (directory/'mongo-key').exists():
 (directory/'mongo-key').write_text(base64.b64encode(secrets.token_bytes(512)).decode());(directory/'mongo-key').chmod(0o600)
if not (ROOT/'.env').exists():
 values={k:secrets.token_hex(24) for k in ['MYSQL_ROOT_PASSWORD','TRADE_DB_PASSWORD','CASE_DB_PASSWORD','MONGO_ROOT_PASSWORD','COMPLIANCE_DB_PASSWORD','LIVE_DB_PASSWORD']}
 values['DEMO_PASSWORD']='TG-'+secrets.token_urlsafe(18)
 (ROOT/'.env').write_text('\n'.join(f'{k}={v}' for k,v in values.items())+'\n');(ROOT/'.env').chmod(0o600)
print('Local secrets ready. The demo login password is DEMO_PASSWORD in .env. Do not commit or share .env.')
