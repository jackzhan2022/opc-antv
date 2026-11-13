"""Scoped update for the installed creator release. Run as root with STAGING RELEASE [--check].

Only creator artifacts and its manifest change. Additive V2/V3 schema is retained on rollback.
Secret-bearing command output is never printed. Existing creator data is backed up and verified.
"""
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tarfile
import time
from urllib.request import urlopen
import zipfile
import zlib


def command(*args, input=None):
    result = subprocess.run(args, input=input, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError('Scoped command failed: ' + Path(args[0]).name)
    return result.stdout


def sql(query):
    return command('docker', 'exec', '-i', 'sinx-platform-mysql', 'sh', '-c',
        'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -h127.0.0.1 -uroot --default-character-set=utf8mb4 --batch --raw --skip-column-names', input=query)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def migrations(path):
    with zipfile.ZipFile(path) as archive:
        return {name: hashlib.sha256(archive.read(name)).hexdigest() for name in archive.namelist()
            if name.startswith('BOOT-INF/classes/db/migration/') and name.endswith('.sql')}


def flyway_checksums(path):
    with zipfile.ZipFile(path) as archive:
        checksums = {}
        for name in migrations(path):
            checksum = 0
            for line in archive.read(name).decode('utf-8-sig').splitlines():
                checksum = zlib.crc32(line.encode('utf-8'), checksum)
            checksums[name.rsplit('/', 1)[1].split('__')[0][1:]] = str(checksum if checksum < 2**31 else checksum - 2**32)
        return checksums


def switch(path, target):
    temporary = path.with_name('current-structure-next')
    if temporary.exists() or temporary.is_symlink():
        raise RuntimeError('Unexpected release link')
    temporary.symlink_to(target, target_is_directory=True)
    os.replace(temporary, path)


def ready(url):
    for _ in range(45):
        try:
            with urlopen(url, timeout=3) as response:
                if json.load(response).get('status') == 'UP':
                    return
        except Exception:
            pass
        time.sleep(2)
    raise RuntimeError('Service readiness check failed')


def preserved_state():
    files = [Path('/etc/sinx-platform/application.env'), Path('/etc/sinx-platform/deployment.json'),
        Path('/etc/creator-platform/application.env'), Path('/etc/systemd/system/creator-platform.service'),
        Path('/opt/sinx-platform/current/app.jar')]
    files.extend(p for p in Path('/etc/nginx').rglob('*') if p.is_file())
    return {'files': {str(p): digest(p) for p in files},
        'platform_backend': str(Path('/opt/sinx-platform/current').resolve()),
        'platform_frontend': str(Path('/var/www/sinx-platform/current').resolve()),
        'platform_pid': command('systemctl', 'show', 'sinx-platform.service', '--property=MainPID', '--value').strip()}


def business_state():
    # Original V1 columns only, allowing additive schema changes while detecting lost or altered data.
    selections = {'creator_record': 'id,name,phone,wechat,city,source,maintainer_id,maintainer_name,notes,archived,version,created_at,updated_at',
        'creator_profession': '*', 'creator_tag': '*', 'creator_work': '*', 'creator_audit': '*'}
    if initial_schema == '3':
        selections['creator_record'] += ',intro,quote_amount,quote_unit'
    return {table: hashlib.sha256(sql('SELECT ' + columns + ' FROM creator_platform.' + table + ' ORDER BY 1,2;').encode()).hexdigest()
        for table, columns in selections.items()}


def extract(source, destination):
    with tarfile.open(source) as archive:
        for item in archive.getmembers():
            if item.issym() or item.islnk() or not (item.isfile() or item.isdir()) or not (destination / item.name).resolve().is_relative_to(destination.resolve()):
                raise RuntimeError('Unsafe frontend archive')
        destination.mkdir(parents=True, mode=0o755)
        if hasattr(tarfile, 'data_filter'):
            archive.extractall(destination, filter='data')
        else:
            archive.extractall(destination)
    if not (destination / 'index.html').is_file():
        raise RuntimeError('Missing frontend entry')
    for item in destination.rglob('*'):
        item.chmod(0o755 if item.is_dir() else 0o644)


if os.geteuid() != 0:
    raise SystemExit('Run with sudo')
staging = Path(sys.argv[1]).resolve()
release = sys.argv[2]
if not re.fullmatch('[a-z0-9-]{1,64}', release):
    raise SystemExit('Invalid release')
manifest_path = Path('/etc/creator-platform/deployment.json')
manifest = json.loads(manifest_path.read_text())
old_backend = Path('/opt/creator-platform/current').resolve()
old_frontend = Path('/var/www/creator-platform/current').resolve()
backend = Path('/opt/creator-platform/releases') / release
frontend = Path('/var/www/creator-platform/releases') / release
backup = Path('/var/backups') / ('creator-platform-release-' + release)
if manifest.get('release') != '20261113-creators' or old_backend != Path(manifest['backend']) or old_frontend != Path(manifest['frontend']):
    raise SystemExit('Unexpected existing creator release')
if any(path.exists() for path in (backend, frontend, backup)):
    raise SystemExit('Release destination already exists')
initial_schema = sql('SELECT version FROM creator_platform.flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1;').strip()
if initial_schema not in ('1', '3'):
    raise SystemExit('Unexpected existing creator schema')
hashes = json.loads((staging / 'hashes.json').read_text())
for name in ('creator.jar', 'creator-frontend.tar.gz'):
    if digest(staging / name) != hashes.get(name):
        raise SystemExit('Artifact checksum mismatch')
old_sql = migrations(old_backend / 'app.jar')
new_sql = migrations(staging / 'creator.jar')
if not all(new_sql.get(name) == value for name, value in old_sql.items()) or set(new_sql) - set(old_sql) != {
    'BOOT-INF/classes/db/migration/V2__creator_dictionaries.sql', 'BOOT-INF/classes/db/migration/V3__creator_profile_and_quote.sql'}:
    raise SystemExit('Unexpected migration changes')
applied = sql('SELECT version,checksum FROM creator_platform.flyway_schema_history WHERE success=1 ORDER BY installed_rank;').strip().splitlines()
expected_checksums = flyway_checksums(staging / 'creator.jar')
if len(applied) != int(initial_schema) or any(expected_checksums.get(row.split('\t')[0]) != row.split('\t')[1] for row in applied):
    raise SystemExit('Existing migration checksum mismatch')
before = preserved_state()
command('systemctl', 'is-active', 'creator-platform.service')
command('systemctl', 'is-active', 'sinx-platform.service')
print('Creator update preflight passed: checked artifacts and additive V2/V3', flush=True)
if '--check' in sys.argv[3:]:
    raise SystemExit(0)
backup.mkdir(mode=0o700)
for name in ('application.env', 'deployment.json'):
    shutil.copy2(Path('/etc/creator-platform') / name, backup / name)
    (backup / name).chmod(0o600)
(backup / 'release-state.json').write_text(json.dumps({'before': before, 'backend': str(old_backend), 'frontend': str(old_frontend)}))
(backup / 'release-state.json').chmod(0o600)
extract(staging / 'creator-frontend.tar.gz', frontend)
backend.mkdir(parents=True, mode=0o755)
shutil.copyfile(staging / 'creator.jar', backend / 'app.jar')
(backend / 'app.jar').chmod(0o644)
changed = False
verification_unit = 'creator-platform-upgrade-check.service'
verification_started = False
try:
    command('systemctl', 'stop', 'creator-platform.service')
    changed = True
    data_before = business_state()
    dump = command('docker', 'exec', 'sinx-platform-mysql', 'sh', '-c',
        'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -h127.0.0.1 -uroot --single-transaction --set-gtid-purged=OFF --no-tablespaces --databases creator_platform')
    if 'CREATE TABLE' not in dump or 'Dump completed' not in dump:
        raise RuntimeError('Incomplete creator backup')
    (backup / 'creator_platform.sql').write_text(dump)
    (backup / 'creator_platform.sql').chmod(0o600)
    # Run migration and data verification on a private port before accepting live traffic.
    unit_state = subprocess.run(['systemctl', 'is-active', verification_unit], text=True, capture_output=True)
    if unit_state.stdout.strip() in ('active', 'activating', 'deactivating'):
        raise RuntimeError('An upgrade verification service is already active')
    verification_started = True
    command('systemd-run', '--unit=' + verification_unit, '--collect',
        '--property=User=creator-platform', '--property=Group=creator-platform',
        '--property=WorkingDirectory=/opt/creator-platform',
        '--property=EnvironmentFile=/etc/creator-platform/application.env',
        '--property=NoNewPrivileges=true', '--property=PrivateTmp=true',
        '--property=MemoryMax=512M', '--property=UMask=0077',
        '/usr/bin/java', '-Xms48m', '-Xmx192m', '-XX:MaxMetaspaceSize=160m',
        '-XX:+ExitOnOutOfMemoryError', '-jar', str(backend / 'app.jar'), '--server.port=18099', '--server.address=127.0.0.1')
    ready('http://127.0.0.1:18099/creator-api/health')
    if sql('SELECT version FROM creator_platform.flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1;').strip() != '3':
        raise RuntimeError('Creator migration mismatch')
    if business_state() != data_before:
        raise RuntimeError('Existing creator business data changed')
    command('systemctl', 'stop', verification_unit)
    verification_started = False
    switch(Path('/opt/creator-platform/current'), backend)
    command('systemctl', 'start', 'creator-platform.service')
    ready('http://127.0.0.1:18089/creator-api/health')
    switch(Path('/var/www/creator-platform/current'), frontend)
    ready('http://127.0.0.1:18088/api/health')
    if preserved_state() != before:
        raise RuntimeError('A preserved service or configuration changed')
    manifest.update({'release': release, 'previous_release': old_backend.name, 'backend': str(backend),
        'frontend': str(frontend), 'schema_version': 3, 'updated_at': datetime.datetime.now(datetime.timezone.utc).isoformat()})
    manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
    manifest_path.chmod(0o600)
    print(json.dumps({'deployment': 'completed', 'release': release, 'creator_schema': 3,
        'existing_business_data': 'preserved', 'platform': 'unchanged', 'backup': str(backup)}), flush=True)
except Exception as error:
    if verification_started:
        subprocess.run(['systemctl', 'stop', verification_unit], capture_output=True)
    if changed:
        try:
            command('systemctl', 'stop', 'creator-platform.service')
            switch(Path('/opt/creator-platform/current'), old_backend)
            switch(Path('/var/www/creator-platform/current'), old_frontend)
            shutil.copy2(backup / 'deployment.json', manifest_path)
            command('systemctl', 'start', 'creator-platform.service')
            ready('http://127.0.0.1:18089/creator-api/health')
            print('Previous creator release restored; additive schema retained', flush=True)
        except Exception:
            print('Creator rollback needs manual diagnosis; consult root-only backup', flush=True)
    print('Update stopped (' + type(error).__name__ + '); command output suppressed', flush=True)
    raise SystemExit(1) from None
