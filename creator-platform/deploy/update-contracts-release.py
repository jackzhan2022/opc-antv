"""Scoped creator V3 -> V4 update. Root: STAGING RELEASE [--check].

Backs up the creator database/configuration, checks additive migrations and old
business records, and changes only creator artifacts plus its upload limit.
No secret-bearing command output is printed. Rollback retains additive tables.
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
from urllib.error import HTTPError
import zipfile
import zlib


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def command(*args, input=None):
    result = subprocess.run(args, input=input, text=True, capture_output=True)
    require(result.returncode == 0, 'Scoped command failed: ' + Path(args[0]).name)
    return result.stdout.strip()


def sql(query):
    return command('docker', 'exec', '-i', 'sinx-platform-mysql', 'sh', '-c',
        'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -h127.0.0.1 -uroot --default-character-set=utf8mb4 --batch --raw --skip-column-names', input=query)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def migrations(path):
    with zipfile.ZipFile(path) as archive:
        return {name: archive.read(name) for name in archive.namelist()
            if name.startswith('BOOT-INF/classes/db/migration/') and name.endswith('.sql')}


def checksums(entries):
    values = {}
    for name, content in entries.items():
        checksum = 0
        for line in content.decode('utf-8-sig').splitlines():
            checksum = zlib.crc32(line.encode('utf-8'), checksum)
        values[name.rsplit('/', 1)[1].split('__')[0][1:]] = str(checksum if checksum < 2**31 else checksum - 2**32)
    return values


def ready(port, context='/creator-api'):
    for _ in range(45):
        try:
            with urlopen('http://127.0.0.1:' + str(port) + context + '/health', timeout=3) as response:
                if json.load(response).get('status') == 'UP':
                    return
        except Exception:
            pass
        time.sleep(1)
    raise RuntimeError('Readiness check failed')


def status(url):
    try:
        with urlopen(url, timeout=5) as response:
            return response.status
    except HTTPError as error:
        return error.code


def switch(link, target):
    next_link = link.with_name('current-contracts-next')
    require(not next_link.exists() and not next_link.is_symlink(), 'Unexpected temporary link')
    next_link.symlink_to(target, target_is_directory=True)
    os.replace(next_link, link)


def private_write(path, data):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, 'wb') as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())


def extract(source, target):
    with tarfile.open(source) as archive:
        for member in archive.getmembers():
            require((member.isfile() or member.isdir()) and not member.issym() and not member.islnk()
                and (target / member.name).resolve().is_relative_to(target.resolve()), 'Unsafe frontend archive')
        target.mkdir(parents=True, mode=0o755)
        archive.extractall(target, **({'filter': 'data'} if hasattr(tarfile, 'data_filter') else {}))
    require((target / 'index.html').is_file(), 'Missing frontend entry')
    require(not (target / 'creator-contracts-preview.html').exists(), 'Prototype cannot be deployed')
    for item in target.rglob('*'):
        item.chmod(0o755 if item.is_dir() else 0o644)


def preserved_state(nginx_path):
    paths = [Path('/etc/creator-platform/application.env'), Path('/etc/systemd/system/creator-platform.service'),
        Path('/etc/sinx-platform/application.env'), Path('/etc/sinx-platform/deployment.json'),
        Path('/etc/systemd/system/sinx-platform.service'), Path('/opt/sinx-platform/current/app.jar')]
    paths.extend(p for p in Path('/etc/nginx').rglob('*') if p.is_file() and p.resolve() != nginx_path)
    return {'files': {str(p): digest(p) for p in paths},
        'platform_backend': str(Path('/opt/sinx-platform/current').resolve()),
        'platform_frontend': str(Path('/var/www/sinx-platform/current').resolve()),
        'platform_pid': command('systemctl', 'show', 'sinx-platform.service', '--property=MainPID', '--value')}


def business_state():
    tables = ['creator_record', 'creator_profession', 'creator_tag', 'creator_work', 'creator_audit',
        'creator_dictionary', 'creator_dictionary_audit', 'creator_dictionary_guard']
    return {table: hashlib.sha256(sql('SELECT * FROM creator_platform.' + table + ' ORDER BY 1;').encode()).hexdigest() for table in tables}


def schema_version():
    return sql('SELECT version FROM creator_platform.flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1;')


def verify_checksums(expected):
    rows = sql('SELECT version,checksum FROM creator_platform.flyway_schema_history WHERE success=1 ORDER BY installed_rank;').splitlines()
    require(all(expected.get(row.split('\t')[0]) == row.split('\t')[1] for row in rows), 'Migration checksum mismatch')
    require(len(rows) == int(schema_version()), 'Unexpected migration history')


def upload_limit(text):
    matches = list(re.finditer(r'(?m)^(\s*)location\s+/creator-api/\s*\{', text))
    require(len(matches) == 1, 'Expected one creator API location')
    match = matches[0]
    end = text.find('}', match.end())
    require(end > match.end(), 'Creator API location is not closed')
    body = text[match.end():end]
    if re.search(r'client_max_body_size\s+\S+;', body):
        replacement = re.sub(r'client_max_body_size\s+\S+;', 'client_max_body_size 6m;', body, count=1)
    else:
        replacement = '\n' + match.group(1) + '    client_max_body_size 6m;' + body
    return text[:match.end()] + replacement + text[end:]


def main():
    require(os.geteuid() == 0, 'Root required')
    staging = Path(sys.argv[1]).resolve()
    release = sys.argv[2]
    require(bool(re.fullmatch('[a-z0-9-]{1,64}', release)), 'Invalid release')
    manifest_path = Path('/etc/creator-platform/deployment.json')
    manifest = json.loads(manifest_path.read_text())
    old_backend = Path('/opt/creator-platform/current').resolve()
    old_frontend = Path('/var/www/creator-platform/current').resolve()
    require(manifest.get('release') == '20261113-creator-structure', 'Unexpected creator release')
    require(str(old_backend) == manifest['backend'] and str(old_frontend) == manifest['frontend'], 'Release link mismatch')
    initial_schema = schema_version()
    require(initial_schema in ('3', '4'), 'Unexpected creator schema')
    backend = Path('/opt/creator-platform/releases') / release
    frontend = Path('/var/www/creator-platform/releases') / release
    backup = Path('/var/backups') / ('creator-platform-release-' + release)
    require(not any(p.exists() for p in (backend, frontend, backup)), 'Destination already exists')
    expected_hashes = json.loads((staging / 'hashes.json').read_text())
    for filename in ('creator.jar', 'creator-frontend.tar.gz'):
        require(digest(staging / filename) == expected_hashes.get(filename), 'Artifact digest mismatch')
    old_sql, new_sql = migrations(old_backend / 'app.jar'), migrations(staging / 'creator.jar')
    require(all(new_sql.get(name) == content for name, content in old_sql.items()), 'Old migration was changed')
    require(set(new_sql) - set(old_sql) == {'BOOT-INF/classes/db/migration/V4__creator_contracts.sql'}, 'Unexpected new migration')
    expected_checksums = checksums(new_sql)
    verify_checksums(expected_checksums)
    nginx_path = Path('/etc/nginx/sites-available/x.aixmax.cn.conf').resolve()
    nginx_before = nginx_path.read_bytes()
    nginx_after = upload_limit(nginx_before.decode()).encode()
    before = preserved_state(nginx_path)
    command('systemctl', 'is-active', 'creator-platform.service')
    command('systemctl', 'is-active', 'sinx-platform.service')
    config = Path('/etc/creator-platform/application.env').read_text()
    require(not re.search(r'(?im)^\s*CREATOR_ESS_ENABLED\s*=\s*["\']?true', config), 'ESS configuration needs a separate verified rollout')
    require(status('http://127.0.0.1:18089/creator-api/me') == 401, 'Unexpected creator authentication boundary')
    print('Preflight passed: creator V3, additive V4, artifacts and scoped upload limit verified.', flush=True)
    if '--check' in sys.argv[3:]:
        return
    backup.mkdir(mode=0o700)
    for filename in ('application.env', 'deployment.json'):
        private_write(backup / filename, (Path('/etc/creator-platform') / filename).read_bytes())
    private_write(backup / 'nginx.conf', nginx_before)
    private_write(backup / 'release-state.json', json.dumps({'preserved': before, 'old_backend': str(old_backend), 'old_frontend': str(old_frontend)}).encode())
    private_write(backup / 'update-contracts-release.py', Path(__file__).read_bytes())
    extract(staging / 'creator-frontend.tar.gz', frontend)
    backend.mkdir(parents=True, mode=0o755)
    shutil.copyfile(staging / 'creator.jar', backend / 'app.jar')
    (backend / 'app.jar').chmod(0o644)
    verification_unit = 'creator-platform-contracts-check.service'
    stopped = verification_started = nginx_changed = False
    try:
        require(command('systemctl', 'show', verification_unit, '--property=LoadState', '--value') == 'not-found', 'Verification unit already exists')
        require(not command('ss', '-H', '-ltn', 'sport = :18099'), 'Verification port is occupied')
        command('systemctl', 'stop', 'creator-platform.service')
        stopped = True
        data_before = business_state()
        dump = command('docker', 'exec', 'sinx-platform-mysql', 'sh', '-c',
            'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -h127.0.0.1 -uroot --single-transaction --hex-blob --set-gtid-purged=OFF --no-tablespaces --databases creator_platform')
        require('CREATE TABLE' in dump and 'Dump completed' in dump, 'Database backup incomplete')
        private_write(backup / 'creator_platform.sql', (dump + '\n').encode())
        print('Creator stopped; full creator database and configuration backup completed.', flush=True)
        verification_started = True
        command('systemd-run', '--unit=' + verification_unit, '--collect',
            '--property=User=creator-platform', '--property=Group=creator-platform',
            '--property=WorkingDirectory=/opt/creator-platform',
            '--property=EnvironmentFile=/etc/creator-platform/application.env',
            '--property=NoNewPrivileges=true', '--property=PrivateTmp=true', '--property=MemoryMax=512M', '--property=UMask=0077',
            '/usr/bin/java', '-Xms48m', '-Xmx192m', '-XX:MaxMetaspaceSize=160m', '-XX:+ExitOnOutOfMemoryError',
            '-jar', str(backend / 'app.jar'), '--server.port=18099', '--server.address=127.0.0.1')
        ready(18099)
        command('systemctl', 'is-active', verification_unit)
        require(schema_version() == '4', 'V4 was not applied')
        verify_checksums(expected_checksums)
        require(business_state() == data_before, 'Existing business records changed')
        require(sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='creator_platform' AND table_name IN ('contract_template','creator_contract','contract_audit','contract_callback');") == '4', 'Contract tables missing')
        for endpoint in ('contracts', 'contract-templates', 'contracts/integration'):
            require(status('http://127.0.0.1:18099/creator-api/' + endpoint) == 401, 'Anonymous contract API is accessible')
        print('Candidate healthy: V4 checksums and preserved records verified; anonymous contract APIs denied.', flush=True)
        command('systemctl', 'stop', verification_unit)
        verification_started = False
        switch(Path('/opt/creator-platform/current'), backend)
        command('systemctl', 'start', 'creator-platform.service')
        ready(18089)
        switch(Path('/var/www/creator-platform/current'), frontend)
        nginx_changed = True
        nginx_path.write_bytes(nginx_after)
        command('nginx', '-t')
        command('systemctl', 'reload', 'nginx')
        ready(18088, '/api')
        require(preserved_state(nginx_path) == before, 'Preserved platform/configuration changed')
        require(nginx_path.read_bytes() == nginx_after, 'Upload configuration mismatch')
        manifest.update({'release': release, 'previous_release': old_backend.name, 'backend': str(backend),
            'frontend': str(frontend), 'schema_version': 4, 'updated_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'ess_configured': False, 'upload_limit': '6m'})
        temporary_manifest = manifest_path.with_name('deployment-contracts-next.json')
        private_write(temporary_manifest, (json.dumps(manifest, indent=2) + '\n').encode())
        os.replace(temporary_manifest, manifest_path)
        print(json.dumps({'deployment': 'completed', 'release': release, 'creator_schema': 4,
            'business_data': 'preserved', 'platform': 'unchanged', 'creator_upload_limit': '6m',
            'ess': 'not_configured', 'backup': str(backup)}), flush=True)
    except Exception as error:
        if verification_started:
            subprocess.run(['systemctl', 'stop', verification_unit], capture_output=True)
        if stopped:
            try:
                command('systemctl', 'stop', 'creator-platform.service')
                switch(Path('/opt/creator-platform/current'), old_backend)
                switch(Path('/var/www/creator-platform/current'), old_frontend)
                shutil.copy2(backup / 'deployment.json', manifest_path)
                if nginx_changed:
                    nginx_path.write_bytes(nginx_before)
                    command('nginx', '-t')
                    command('systemctl', 'reload', 'nginx')
                command('systemctl', 'start', 'creator-platform.service')
                ready(18089)
                print('Previous creator artifacts restored; additive V4 tables retained for diagnosis.', flush=True)
            except Exception:
                print('Rollback requires manual diagnosis using the root-only backup.', flush=True)
        print('Release stopped (' + type(error).__name__ + '); command output suppressed.', flush=True)
        raise SystemExit(1) from None


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('Release preflight stopped (' + type(error).__name__ + '); no secret output.', flush=True)
        raise SystemExit(1) from None
