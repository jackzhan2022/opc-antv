"""One-time V3 -> V4 platform update. Root only; STAGING RELEASE [--check].

Preserves existing identity/configuration data. Failure restores old artifacts with
SSO disabled, so a legacy backend cannot reopen revoked application access.
"""
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import tarfile
import time
from urllib.request import urlopen
import zipfile
import zlib


class ReleaseError(RuntimeError):
    pass


def require(condition, message):
    if not condition:
        raise ReleaseError(message)


def command(*args, input=None):
    result = subprocess.run(args, input=input, text=True, capture_output=True)
    if result.returncode:
        raise ReleaseError('Scoped command failed: ' + Path(args[0]).name)
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
    result = {}
    for name, content in entries.items():
        checksum = 0
        for line in content.decode('utf-8-sig').splitlines():
            checksum = zlib.crc32(line.encode('utf-8'), checksum)
        result[name.rsplit('/', 1)[1].split('__')[0][1:]] = str(checksum if checksum < 2**31 else checksum - 2**32)
    return result


def private_write(path, data):
    temporary = path.with_name(path.name + '.app-access-next')
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, 'wb') as output:
        output.write(data)
        output.flush()
        os.fsync(output.fileno())
    os.replace(temporary, path)


def switch(path, target):
    temporary = path.with_name('current-app-access-next')
    require(not temporary.exists() and not temporary.is_symlink(), 'Unexpected temporary release link')
    temporary.symlink_to(target, target_is_directory=True)
    os.replace(temporary, path)


def ready(port):
    for _ in range(45):
        try:
            with urlopen('http://127.0.0.1:' + str(port) + '/api/health', timeout=3) as response:
                if json.load(response).get('status') == 'UP':
                    return
        except Exception:
            pass
        time.sleep(1)
    raise ReleaseError('Platform readiness check failed')


def providers(port):
    with urlopen('http://127.0.0.1:' + str(port) + '/api/auth/providers', timeout=5) as response:
        return {item['id']: item['enabled'] for item in json.load(response)}


def preserved_state():
    files = [Path('/etc/creator-platform/application.env'), Path('/etc/creator-platform/deployment.json'),
        Path('/etc/systemd/system/creator-platform.service'), Path('/opt/creator-platform/current/app.jar'),
        Path('/etc/systemd/system/sinx-platform.service')]
    files.extend(path for path in Path('/etc/nginx').rglob('*') if path.is_file())
    return {'files': {str(path): digest(path) for path in files},
        'creator_pid': command('systemctl', 'show', 'creator-platform.service', '--property=MainPID', '--value'),
        'creator_backend': str(Path('/opt/creator-platform/current').resolve()),
        'creator_frontend': str(Path('/var/www/creator-platform/current').resolve()),
        'creator_schema': sql('SELECT version FROM creator_platform.flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1;')}


def identity_state():
    selections = {'platform_member': 'id', 'external_identity': 'provider,tenant_id,subject_id',
        'pending_identity': 'id', 'member_audit': 'id', 'member_admin_guard': 'id'}
    return {table: hashlib.sha256(sql('SELECT * FROM sinx_platform.' + table + ' ORDER BY ' + order + ';').encode()).hexdigest()
        for table, order in selections.items()}


def extract(source, destination):
    with tarfile.open(source) as archive:
        for item in archive.getmembers():
            require((item.isfile() or item.isdir()) and not item.issym() and not item.islnk()
                and (destination / item.name).resolve().is_relative_to(destination.resolve()), 'Unsafe frontend archive')
        destination.mkdir(mode=0o755)
        if hasattr(tarfile, 'data_filter'):
            archive.extractall(destination, filter='data')
        else:
            archive.extractall(destination)
    require((destination / 'index.html').is_file(), 'Missing frontend entry')
    for item in destination.rglob('*'):
        item.chmod(0o755 if item.is_dir() else 0o644)


def disable_sso(data):
    lines = data.decode().splitlines()
    lines = [line for line in lines if line.split('=', 1)[0].strip() != 'CREATOR_SSO_ENABLED']
    return ('\n'.join(lines) + '\nCREATOR_SSO_ENABLED="false"\n').encode()


def main():
    require(os.geteuid() == 0, 'Run with sudo')
    staging = Path(sys.argv[1]).resolve()
    release = sys.argv[2]
    require(re.fullmatch('[a-z0-9-]{1,64}', release) is not None, 'Invalid release name')
    config = Path('/etc/sinx-platform/application.env')
    manifest_path = Path('/etc/sinx-platform/deployment.json')
    manifest = json.loads(manifest_path.read_text())
    old_config = config.read_bytes()
    old_manifest = manifest_path.read_bytes()
    require(config.stat().st_uid == 0 and stat.S_IMODE(config.stat().st_mode) == 0o600 and not config.is_symlink(), 'Unexpected configuration permissions')
    environment = {}
    for line in old_config.decode().splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            require(key not in environment, 'Duplicate environment configuration')
            environment[key] = value.strip().strip('"')
    require(environment.get('SPRING_PROFILES_ACTIVE') == 'prod' and environment.get('CREATOR_SSO_ENABLED') == 'true', 'Unexpected production configuration')
    require(not any(key.startswith('SINX_BOOTSTRAP_') or key in ('SINX_SSO_ENABLED', 'SPRING_APPLICATION_JSON', 'SPRING_CONFIG_IMPORT', 'SPRING_CONFIG_ADDITIONAL_LOCATION') for key in environment), 'Configuration override requires scoped review')
    require(not Path('/opt/sinx-platform/config/application-local.yml').exists(), 'Unexpected imported local configuration')
    old_backend = Path('/opt/sinx-platform/current').resolve()
    old_frontend = Path('/var/www/sinx-platform/current').resolve()
    require(old_backend.is_relative_to(Path('/opt/sinx-platform/releases')) and old_frontend.is_relative_to(Path('/var/www/sinx-platform/releases')), 'Unexpected release roots')
    require(manifest.get('backend') == str(old_backend) and manifest.get('frontend') == str(old_frontend), 'Release manifest does not match current links')
    require(sql('SELECT version FROM sinx_platform.flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1;') == '3', 'Only a V3 platform can use this updater')
    require(sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='sinx_platform' AND table_name IN ('platform_application','member_application_access','application_audit');") == '0', 'Application tables already exist; inspect prior migration')
    backend = Path('/opt/sinx-platform/releases') / release
    frontend = Path('/var/www/sinx-platform/releases') / release
    backup = Path('/var/backups') / ('sinx-platform-release-' + release)
    require(not any(path.exists() for path in (backend, frontend, backup)), 'Release or backup destination already exists')
    hashes = json.loads((staging / 'hashes.json').read_text())
    for name in ('platform.jar', 'frontend.tar.gz'):
        require(digest(staging / name) == hashes.get(name), 'Artifact checksum mismatch')
    old_sql, new_sql = migrations(old_backend / 'app.jar'), migrations(staging / 'platform.jar')
    require(all(new_sql.get(name) == data for name, data in old_sql.items()), 'An existing migration was modified')
    require(set(new_sql) - set(old_sql) == {'BOOT-INF/classes/db/migration/V4__applications_and_member_access.sql'}, 'Unexpected new migrations')
    expected_checksums = checksums(new_sql)
    applied = sql('SELECT version,checksum FROM sinx_platform.flyway_schema_history WHERE success=1 ORDER BY installed_rank;').splitlines()
    require(len(applied) == 3 and all(expected_checksums.get(row.split('\t')[0]) == row.split('\t')[1] for row in applied), 'Applied migration checksum mismatch')
    require(int(sql("SELECT COUNT(*) FROM sinx_platform.platform_member WHERE enabled=true AND platform_role='ADMIN';")) > 0, 'No enabled administrator')
    command('systemctl', 'is-active', 'sinx-platform.service')
    command('systemctl', 'is-active', 'creator-platform.service')
    ready(18088)
    previous_providers = providers(18088)
    protected = preserved_state()
    print(json.dumps({'state': 'preflight_passed', 'release': release, 'schema': 3}), flush=True)
    if '--check' in sys.argv[3:]:
        return
    backend.mkdir(mode=0o755)
    shutil.copyfile(staging / 'platform.jar', backend / 'app.jar')
    (backend / 'app.jar').chmod(0o644)
    extract(staging / 'frontend.tar.gz', frontend)
    backup.mkdir(mode=0o700)
    private_write(backup / 'application.env', old_config)
    private_write(backup / 'deployment.json', old_manifest)
    private_write(backup / 'previous-links.json', json.dumps({'backend': str(old_backend), 'frontend': str(old_frontend)}).encode())
    candidate = 'sinx-app-access-check-' + release
    stopped = False
    try:
        command('systemctl', 'stop', 'sinx-platform.service')
        stopped = True
        before_members = identity_state()
        enabled_members = int(sql('SELECT COUNT(*) FROM sinx_platform.platform_member WHERE enabled=true;'))
        descriptor = os.open(backup / 'sinx_platform.sql', os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, 'wb') as output:
            dump = subprocess.run(['docker', 'exec', 'sinx-platform-mysql', 'sh', '-c',
                'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -h127.0.0.1 -uroot --single-transaction --routines --triggers --events --no-tablespaces --set-gtid-purged=OFF sinx_platform'], stdout=output, stderr=subprocess.PIPE)
            output.flush()
            os.fsync(output.fileno())
        require(dump.returncode == 0 and (backup / 'sinx_platform.sql').stat().st_size > 1000, 'Database backup failed')
        print(json.dumps({'state': 'backup_complete', 'enabled_members': enabled_members, 'backup': str(backup)}), flush=True)
        command('systemd-run', '--quiet', '--collect', '--unit=' + candidate,
            '--property=User=sinx-platform', '--property=Group=sinx-platform', '--property=WorkingDirectory=/opt/sinx-platform',
            '--property=EnvironmentFile=/etc/sinx-platform/application.env', '--property=MemoryMax=640M',
            '--property=UMask=0077', '--property=PrivateTmp=true', '--property=NoNewPrivileges=true',
            '/usr/bin/java', '-Xms64m', '-Xmx256m', '-XX:MaxMetaspaceSize=192m', '-XX:+ExitOnOutOfMemoryError',
            '-jar', str(backend / 'app.jar'), '--server.port=18090', '--server.address=127.0.0.1')
        ready(18090)
        require(providers(18090) == previous_providers, 'Enterprise login channels changed')
        require(identity_state() == before_members, 'Existing members or identities changed')
        require(sql('SELECT version FROM sinx_platform.flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1;') == '4', 'V4 migration was not applied')
        require(sql("SELECT checksum FROM sinx_platform.flyway_schema_history WHERE version='4' AND success=1;") == expected_checksums['4'], 'V4 migration checksum mismatch')
        grants = int(sql("SELECT COUNT(*) FROM sinx_platform.member_application_access WHERE application_id='creator-platform' AND granted=true;"))
        require(grants == enabled_members, 'Existing enabled member access was not preserved')
        require(sql("SELECT COUNT(*) FROM sinx_platform.platform_application WHERE id='creator-platform' AND enabled=true AND integration_type='SSO';") == '1', 'Creator application metadata is missing')
        require(preserved_state() == protected and config.read_bytes() == old_config, 'Unrelated deployment state changed')
        command('systemctl', 'stop', candidate + '.service')
        print(json.dumps({'state': 'private_validation_passed', 'schema': 4, 'preserved_grants': grants}), flush=True)
        switch(Path('/opt/sinx-platform/current'), backend)
        command('systemctl', 'start', 'sinx-platform.service')
        ready(18088)
        require(providers(18088) == previous_providers, 'Production login channels changed')
        switch(Path('/var/www/sinx-platform/current'), frontend)
        require(preserved_state() == protected and config.read_bytes() == old_config, 'Unrelated deployment state changed')
        manifest.update({'release': release, 'backend': str(backend), 'frontend': str(frontend), 'database_schema': 4,
            'updated_at': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'artifact_sha256': hashes,
            'backup': str(backup), 'preserved_member_grants': grants})
        private_write(manifest_path, (json.dumps(manifest, indent=2) + '\n').encode())
        print(json.dumps({'state': 'deployed', 'release': release, 'schema': 4, 'preserved_member_grants': grants,
            'identity_data_preserved': True, 'configuration_preserved': True, 'creator_process_preserved': True,
            'backup': str(backup)}), flush=True)
    except Exception as error:
        if stopped:
            subprocess.run(['systemctl', 'stop', candidate + '.service'], capture_output=True)
            subprocess.run(['systemctl', 'stop', 'sinx-platform.service'], capture_output=True)
            private_write(config, disable_sso(old_config))
            switch(Path('/opt/sinx-platform/current'), old_backend)
            switch(Path('/var/www/sinx-platform/current'), old_frontend)
            private_write(manifest_path, old_manifest)
            restored = subprocess.run(['systemctl', 'start', 'sinx-platform.service'], capture_output=True).returncode == 0
            if restored:
                try:
                    ready(18088)
                except Exception:
                    restored = False
            print(json.dumps({'state': 'failed_closed', 'legacy_artifacts_restored': restored, 'sso_enabled': False,
                'database_retained': True, 'backup': str(backup), 'error_type': type(error).__name__}), flush=True)
        raise


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(json.dumps({'state': 'deployment_error', 'reason': str(error) if isinstance(error, ReleaseError) else type(error).__name__}), flush=True)
        raise SystemExit(1)
