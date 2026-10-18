#!/usr/bin/env python3
"""One-time, volume-preserving dooit -> prd_dooit transition. Default: preflight only."""
import argparse
import gzip
import json
import os
from pathlib import Path
import subprocess
import time
from datetime import datetime

ROOT = Path(__file__).resolve().parent.parent
os.chdir(ROOT)


def run(args, env=None):
    result = subprocess.run(args, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        # Docker errors may contain interpolated secrets; do not print raw output.
        raise RuntimeError('Command failed: ' + ' '.join(args[:3]))
    return result.stdout


def inspect(name):
    return json.loads(run(['docker', 'inspect', name]))[0]


def compose(project, env, *args, config=None):
    return run(['docker', 'compose', '--project-directory', str(ROOT), '--env-file', str(ROOT / '.env'),
                '-p', project, '-f', str(config or ROOT / 'docker-compose.yml'), '--profile', 'monitoring', *args], env)


def healthy():
    for _ in range(90):
        if all(inspect('dooit-' + s)['State'].get('Health', {}).get('Status') == 'healthy'
               for s in ('mysql', 'app', 'prometheus', 'grafana')):
            return
        time.sleep(2)
    raise RuntimeError('PRD containers did not become healthy')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    os.umask(0o077)
    app = inspect('dooit-app')
    project = app['Config']['Labels']['com.docker.compose.project']
    if project == 'prd_dooit':
        print('PRD is already in prd_dooit; no changes made.')
        return
    if project != 'dooit':
        raise RuntimeError('Unexpected production project; refusing transition')
    environment = os.environ.copy()
    for key in ('COMPOSE_FILE', 'COMPOSE_PROJECT_NAME', 'COMPOSE_ENV_FILES', 'COMPOSE_PROFILES'):
        environment.pop(key, None)
    current = dict(v.split('=', 1) for v in app['Config']['Env'])
    environment.update(DOOIT_APP_IMAGE_TAG=app['Config']['Image'].split(':', 1)[1],
                       DOOIT_APP_COMMIT_SHA=current['DOOIT_APP_COMMIT_SHA'], DOOIT_COMPOSE_PROJECT_NAME='dooit')
    config = json.loads(compose('dooit', environment, 'config', '--format', 'json'))
    snapshots = {}
    for service, wanted in config['services'].items():
        old = inspect('dooit-' + service)
        if old['Config']['Labels']['com.docker.compose.project'] != 'dooit':
            raise RuntimeError('A PRD service belongs to another project')
        live = dict(v.split('=', 1) for v in old['Config']['Env'])
        different = [k for k, v in wanted.get('environment', {}).items() if str(v) != live.get(k)]
        if different and not (service == 'alloy' and different == ['DOOIT_COMPOSE_PROJECT_NAME']):
            raise RuntimeError('Unexpected environment differences for ' + service + ': ' + ','.join(different))
        image_id = json.loads(run(['docker', 'image', 'inspect', wanted['image']]))[0]['Id']
        if image_id != old['Image']:
            raise RuntimeError('Image drift for ' + service)
        snapshots[service] = {'image': old['Image'], 'volumes': sorted(
            (m['Name'], m['Destination']) for m in old['Mounts'] if m['Type'] == 'volume')}
    print('Preflight OK: same PRD images, environment and named volumes; target project=prd_dooit.')
    if not args.apply:
        print('No mutation. Apply with scripts/split-production-project.py --apply')
        return

    stamp = datetime.now().strftime('%Y%m%d-%H%M%S')
    backup = ROOT / 'backups' / ('project-split-' + stamp)
    backup.mkdir(parents=True, mode=0o700)
    env_path = ROOT / '.env'
    original_env = env_path.read_bytes()
    (backup / 'production.env').write_bytes(original_env)
    rollback = backup / 'rollback-compose.json'
    rollback.write_text(json.dumps(config))
    label = 'gui/' + str(os.getuid()) + '/pj.dooit.backend.production'
    registered = subprocess.run(['launchctl', 'print', label], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0
    plist = Path.home() / 'Library/LaunchAgents/pj.dooit.backend.production.plist'
    if registered:
        run(['launchctl', 'bootout', label])
    switched = False
    try:
        # Stop writes before the final backup, but keep MySQL and named volumes.
        compose('dooit', environment, 'stop', 'app')
        dump = run(['docker', 'exec', 'dooit-mysql', 'sh', '-c',
                    'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --single-transaction --no-tablespaces --routines --triggers -u"$MYSQL_USER" "$MYSQL_DATABASE"'])
        if b'-- Dump completed on' not in dump:
            raise RuntimeError('Backup completion marker missing')
        with gzip.open(backup / 'database.sql.gz', 'wb') as output:
            output.write(dump)
        with gzip.open(backup / 'database.sql.gz', 'rb') as source:
            if source.read() != dump:
                raise RuntimeError('Backup verification failed')
        compose('dooit', environment, 'down', '--timeout', '30')
        switched = True
        # Persist only the project selector; keep all existing credential values.
        lines = original_env.decode().splitlines()
        lines = [line for line in lines if not line.startswith('DOOIT_COMPOSE_PROJECT_NAME=')]
        env_path.write_text('\n'.join(lines) + '\nDOOIT_COMPOSE_PROJECT_NAME=prd_dooit\n')
        environment['DOOIT_COMPOSE_PROJECT_NAME'] = 'prd_dooit'
        compose('prd_dooit', environment, 'up', '-d', '--no-build', '--pull', 'never')
        healthy()
        for service, expected in snapshots.items():
            actual = inspect('dooit-' + service)
            volumes = sorted((m['Name'], m['Destination']) for m in actual['Mounts'] if m['Type'] == 'volume')
            if actual['Image'] != expected['image'] or volumes != expected['volumes']:
                raise RuntimeError('Post-transition image/volume mismatch for ' + service)
        print('PRD transitioned to prd_dooit. Original image IDs and all named volumes preserved.')
        print('Private backup: ' + str(backup))
    except Exception:
        if switched:
            compose('prd_dooit', environment, 'down', '--timeout', '30')
        env_path.write_bytes(original_env)
        environment['DOOIT_COMPOSE_PROJECT_NAME'] = 'dooit'
        compose('dooit', environment, 'up', '-d', '--no-build', '--pull', 'never', config=rollback)
        healthy()
        print('Transition failed; restored original dooit project.')
        raise
    finally:
        if registered:
            run(['launchctl', 'bootstrap', 'gui/' + str(os.getuid()), str(plist)])


if __name__ == '__main__':
    main()
