"""Coordinated local backup/restore. The restore subcommand replaces this demo's data."""
from client import ROOT
from pathlib import Path
import argparse,subprocess,datetime,json,hashlib
parser=argparse.ArgumentParser();parser.add_argument('action',choices=['backup','restore']);parser.add_argument('--directory');args=parser.parse_args()
if args.action=='restore' and not args.directory:parser.error('restore requires --directory with an existing backup')
apps=['web','trade-service','compliance-service','investigation-service','live-view-service']
def compose(*parts,**kw):return subprocess.run(['docker','compose',*parts],cwd=ROOT,check=True,**kw)
directory=Path(args.directory).resolve() if args.directory else ROOT/'backups'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
directory.mkdir(parents=True,exist_ok=True)
compose('stop',*apps);compose('stop','kafka')
try:
 if args.action=='backup':
  with (directory/'mysql.sql').open('wb') as file:compose('exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --set-gtid-purged=OFF --databases trade investigation',stdout=file)
  for db in ['compliance','liveview']:
   with (directory/(db+'.archive.gz')).open('wb') as file:compose('exec','-T','mongo','sh','-c',f'mongodump --quiet --username root --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --db {db} --archive --gzip',stdout=file)
  subprocess.run(['docker','run','--rm','-v','tradeguard_kafka-data:/data:ro','-v',str(directory)+':/backup','busybox:1.37.0','tar','czf','/backup/kafka.tar.gz','-C','/data','.'],check=True)
  files=['mysql.sql','compliance.archive.gz','liveview.archive.gz','kafka.tar.gz']
  manifest={'project':'tradeguard','createdAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'files':{name:hashlib.sha256((directory/name).read_bytes()).hexdigest() for name in files}}
  (directory/'manifest.json').write_text(json.dumps(manifest,indent=2));print('Backup complete:',directory)
 else:
  manifest=json.loads((directory/'manifest.json').read_text());assert manifest['project']=='tradeguard'
  for name,digest in manifest['files'].items():assert name in ['mysql.sql','compliance.archive.gz','liveview.archive.gz','kafka.tar.gz'] and hashlib.sha256((directory/name).read_bytes()).hexdigest()==digest
  with (directory/'mysql.sql').open('rb') as file:compose('exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot',stdin=file)
  for db in ['compliance','liveview']:
   with (directory/(db+'.archive.gz')).open('rb') as file:compose('exec','-T','mongo','sh','-c',f'mongorestore --quiet --drop --username root --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --nsInclude "{db}.*" --archive --gzip',stdin=file)
  # This exact named volume belongs to this application; no host filesystem is deleted.
  subprocess.run(['docker','run','--rm','-v','tradeguard_kafka-data:/data','-v',str(directory)+':/backup:ro','busybox:1.37.0','sh','-c','find /data -mindepth 1 -delete && tar xzf /backup/kafka.tar.gz -C /data'],check=True)
  print('Coordinated data restored; original .env and signing keys must still be retained.')
finally:
 compose('start','kafka');compose('start',*apps)
