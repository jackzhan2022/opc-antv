"""First creator deployment; root-only secrets, checked artifacts and coordinated rollback.

Run: sudo python3 install-first-release.py STAGING RELEASE
This intentionally refuses to overwrite an existing creator installation.
"""
import datetime
import hashlib
import json
import os
from pathlib import Path
import pwd
import re
import secrets
import shutil
import subprocess
import sys
import tarfile
import time
import zipfile
from urllib.request import urlopen

def command(*args,input=None):
    result=subprocess.run(args,input=input,text=True,capture_output=True)
    if result.returncode:
        raise RuntimeError('Scoped command failed: '+Path(args[0]).name)
    return result.stdout

def sql(query):
    return command('docker','exec','-i','sinx-platform-mysql','sh','-c',
        'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -h127.0.0.1 -uroot --default-character-set=utf8mb4 --batch --raw --skip-column-names',input=query)

def migration_hashes(path):
    with zipfile.ZipFile(path) as archive:
        return {n:hashlib.sha256(archive.read(n)).hexdigest() for n in archive.namelist() if n.startswith('BOOT-INF/classes/db/migration/') and n.endswith('.sql')}

def write_private(path,values):
    if any(any(c in str(value) for c in '\r\n"\\') for value in values.values()):
        raise RuntimeError('Invalid environment value')
    path.write_text(''.join(key+'="'+str(value)+'"\n' for key,value in values.items()))
    path.chmod(0o600)

def switch(path,target):
    temporary=path.with_name('current-creator-next')
    if temporary.exists() or temporary.is_symlink():raise RuntimeError('Unexpected temporary release link')
    temporary.symlink_to(target,target_is_directory=True)
    os.replace(temporary,path)

def wait_ready(url):
    for attempt in range(45):
        try:
            with urlopen(url,timeout=3) as response:
                if json.load(response).get('status')=='UP':return
        except Exception:pass
        time.sleep(2)
    raise RuntimeError('Service readiness check failed')

def extract(source,destination):
    with tarfile.open(source) as archive:
        for item in archive.getmembers():
            if item.issym() or item.islnk() or not (item.isfile() or item.isdir()) or not (destination/item.name).resolve().is_relative_to(destination.resolve()):
                raise RuntimeError('Unsafe frontend archive')
        destination.mkdir(parents=True,mode=0o755)
        if hasattr(tarfile,'data_filter'):archive.extractall(destination,filter='data')
        else:archive.extractall(destination)
    if not (destination/'index.html').is_file():raise RuntimeError('Missing frontend entry')
    for item in destination.rglob('*'):item.chmod(0o755 if item.is_dir() else 0o644)

if os.geteuid()!=0:raise SystemExit('Run with sudo')
staging=Path(sys.argv[1]).resolve()
release=sys.argv[2]
if not re.fullmatch('[a-z0-9-]{1,64}',release):raise SystemExit('Invalid release')
config=Path('/etc/sinx-platform/application.env')
manifest_path=Path('/etc/sinx-platform/deployment.json')
nginx=Path('/etc/nginx/sites-available/x.aixmax.cn.conf')
creator_config=Path('/etc/creator-platform')
platform_backend=Path('/opt/sinx-platform/releases')/release
creator_backend=Path('/opt/creator-platform/releases')/release
creator_frontend=Path('/var/www/creator-platform/releases')/release
backup=Path('/var/backups')/('sinx-platform-release-'+release)
if any(p.exists() for p in (creator_config,creator_backend,creator_frontend,platform_backend,backup)):
    raise SystemExit('Installation or release already exists; use an explicit update procedure')
manifest=json.loads(manifest_path.read_text())
if manifest.get('release')!='20261113-wecom-client':raise SystemExit('Unexpected existing platform release')
previous_backend=Path('/opt/sinx-platform/current').resolve()
if previous_backend!=Path('/opt/sinx-platform/releases/20261113-wecom-client'):raise SystemExit('Unexpected platform backend link')
if sql('SELECT version FROM sinx_platform.flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1;').strip()!='2':
    raise SystemExit('Unexpected existing platform schema')
if sql("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='creator_platform';").strip()!='0':
    raise SystemExit('Creator database already exists')
if sql("SELECT COUNT(*) FROM mysql.user WHERE User='creator';").strip()!='0':raise SystemExit('Creator database user already exists')
hashes=json.loads((staging/'hashes.json').read_text())
for name in ('platform.jar','creator.jar','creator-frontend.tar.gz','creator-platform.service'):
    if hashlib.sha256((staging/name).read_bytes()).hexdigest()!=hashes.get(name):raise SystemExit('Artifact checksum mismatch')
old_migrations=migration_hashes(previous_backend/'app.jar')
new_migrations=migration_hashes(staging/'platform.jar')
if not all(new_migrations.get(n)==value for n,value in old_migrations.items()) or len(new_migrations)!=len(old_migrations)+1 or 'BOOT-INF/classes/db/migration/V3__oauth2_authorization.sql' not in new_migrations:
    raise SystemExit('Unexpected platform migration changes')
if set(migration_hashes(staging/'creator.jar'))!={'BOOT-INF/classes/db/migration/V1__creator_records.sql'}:
    raise SystemExit('Unexpected creator migrations')
initial_nginx={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in Path('/etc/nginx').rglob('*') if p.is_file() and p.resolve()!=nginx.resolve()}
routes='''
    # Independent Creator Platform.
    location = /creators { return 302 /creators/; }
    location ^~ /creators/ {
        alias /var/www/creator-platform/current/;
        try_files $uri $uri/ /creators/index.html;
    }
    location = /creator-api { return 302 /creator-api/; }
    location /creator-api/ {
        proxy_pass http://127.0.0.1:18089;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 30s;
        access_log off;
    }
'''
original_nginx=nginx.read_text()
if original_nginx.count('    location /api/ {')!=1 or '/creator-api/' in original_nginx:raise SystemExit('Unexpected gateway structure')
updated_nginx=original_nginx.replace('    location /api/ {',routes+'    location /api/ {')
backup.mkdir(mode=0o700)
for source in (config,manifest_path,nginx):
    shutil.copy2(source,backup/source.name)
    (backup/source.name).chmod(0o600)
extract(staging/'creator-frontend.tar.gz',creator_frontend)
for destination,source in ((platform_backend,staging/'platform.jar'),(creator_backend,staging/'creator.jar')):
    destination.mkdir(parents=True,mode=0o755)
    shutil.copyfile(source,destination/'app.jar')
    (destination/'app.jar').chmod(0o644)
print('Artifact hashes, additive migrations and scoped gateway changes verified',flush=True)
changed=False
try:
    command('systemctl','stop','sinx-platform.service')
    changed=True
    dump=command('docker','exec','sinx-platform-mysql','sh','-c',
        'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -h127.0.0.1 -uroot --single-transaction --set-gtid-purged=OFF --no-tablespaces --databases sinx_platform')
    if 'CREATE TABLE' not in dump:raise RuntimeError('Database backup is incomplete')
    (backup/'sinx_platform.sql').write_text(dump)
    (backup/'sinx_platform.sql').chmod(0o600)
    creator_password=secrets.token_urlsafe(32)
    sso_secret=secrets.token_urlsafe(40)
    sql("CREATE DATABASE creator_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;\nCREATE USER 'creator'@'%' IDENTIFIED BY '"+creator_password+"';\nGRANT ALL PRIVILEGES ON creator_platform.* TO 'creator'@'%';")
    try:pwd.getpwnam('creator-platform')
    except KeyError:command('useradd','--system','--user-group','--home-dir','/var/lib/creator-platform','--shell','/usr/sbin/nologin','creator-platform')
    creator_config.mkdir(mode=0o700)
    write_private(creator_config/'application.env',{
        'SPRING_PROFILES_ACTIVE':'prod','SERVER_ADDRESS':'127.0.0.1','CREATOR_PORT':'18089',
        'CREATOR_DB_URL':'jdbc:mysql://127.0.0.1:13316/creator_platform?useUnicode=true&characterEncoding=UTF-8&preserveInstants=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&sslMode=DISABLED',
        'CREATOR_DB_USERNAME':'creator','CREATOR_DB_PASSWORD':creator_password,'CREATOR_COOKIE_SECURE':'true',
        'SINX_BASE_URL':'https://x.aixmax.cn','SINX_BACKEND_URL':'http://127.0.0.1:18088','CREATOR_PUBLIC_BASE_URL':'https://x.aixmax.cn',
        'CREATOR_SSO_CLIENT_ID':'creator-platform','CREATOR_SSO_CLIENT_SECRET':sso_secret,
        'SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE':'4','SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE':'1'})
    original_env=config.read_text()
    additions={'CREATOR_SSO_ENABLED':'true','CREATOR_SSO_CLIENT_ID':'creator-platform','CREATOR_SSO_CLIENT_SECRET':sso_secret,
        'CREATOR_SSO_REDIRECT_URI':'https://x.aixmax.cn/creator-api/login/oauth2/code/sinx','CREATOR_APPLICATION_URL':'https://x.aixmax.cn/creators/'}
    if any(re.search('^'+key+'=',original_env,re.M) for key in additions):raise RuntimeError('SSO configuration already exists')
    original_env=original_env.replace('&connectionTimeZone=UTC','&preserveInstants=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true')
    config.write_text(original_env.rstrip()+'\n'+''.join(key+'="'+value+'"\n' for key,value in additions.items()))
    config.chmod(0o600)
    shutil.copyfile(staging/'creator-platform.service','/etc/systemd/system/creator-platform.service')
    Path('/etc/systemd/system/creator-platform.service').chmod(0o644)
    command('systemd-analyze','verify','/etc/systemd/system/creator-platform.service')
    switch(Path('/opt/sinx-platform/current'),platform_backend)
    switch(Path('/opt/creator-platform/current'),creator_backend)
    switch(Path('/var/www/creator-platform/current'),creator_frontend)
    command('systemctl','daemon-reload')
    command('systemctl','start','sinx-platform.service')
    wait_ready('http://127.0.0.1:18088/api/health')
    command('systemctl','enable','--now','creator-platform.service')
    wait_ready('http://127.0.0.1:18089/creator-api/health')
    if sql('SELECT version FROM sinx_platform.flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1;').strip()!='3':raise RuntimeError('Platform schema mismatch')
    if sql('SELECT COUNT(*) FROM creator_platform.creator_record;').strip()!='0':raise RuntimeError('Unexpected creator records')
    nginx.write_text(updated_nginx)
    command('nginx','-t')
    command('systemctl','reload','nginx')
    current_nginx={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in Path('/etc/nginx').rglob('*') if p.is_file() and p.resolve()!=nginx.resolve()}
    if current_nginx!=initial_nginx:raise RuntimeError('An unrelated gateway file changed')
    manifest.update({'release':release,'previous_release':previous_backend.name,'backend':str(platform_backend),'creator_backend':str(creator_backend),'creator_frontend':str(creator_frontend),'updated_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'schema_version':3})
    manifest_path.write_text(json.dumps(manifest,indent=2)+'\n')
    manifest_path.chmod(0o600)
    (creator_config/'deployment.json').write_text(json.dumps({'release':release,'backend':str(creator_backend),'frontend':str(creator_frontend),'schema_version':1},indent=2)+'\n')
    (creator_config/'deployment.json').chmod(0o600)
    print(json.dumps({'deployment':'completed','release':release,'platform_schema':3,'creator_schema':1,'creator_records':0,'backup':str(backup)}),flush=True)
except Exception as error:
    if changed:
        try:
            if Path('/etc/systemd/system/creator-platform.service').exists():
                command('systemctl','disable','--now','creator-platform.service')
            command('systemctl','stop','sinx-platform.service')
            switch(Path('/opt/sinx-platform/current'),previous_backend)
            shutil.copy2(backup/'application.env',config)
            shutil.copy2(backup/'deployment.json',manifest_path)
            shutil.copy2(backup/'x.aixmax.cn.conf',nginx)
            command('nginx','-t')
            command('systemctl','reload','nginx')
            command('systemctl','start','sinx-platform.service')
            wait_ready('http://127.0.0.1:18088/api/health')
            print('Previous platform release restored; additive SSO schema and isolated creator database retained for diagnosis',flush=True)
        except Exception:
            print('Automatic restore needs manual diagnosis; consult root-only backup',flush=True)
    print('Deployment stopped ('+type(error).__name__+'); secret-bearing command output suppressed',flush=True)
    raise SystemExit(1) from None
