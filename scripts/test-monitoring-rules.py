#!/usr/bin/env python3
"""Validate the provisioned PromQL with the running Prometheus image, without network access."""
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
rules = json.loads((ROOT / 'config/monitoring/grafana/provisioning/alerting/dooit-rules.json').read_text())
expressions = {rule['uid']: rule['data'][0]['model']['expr']
               for group in rules['groups'] for rule in group['rules']}
down = expressions['dooit-backend-scrape-down']
errors = expressions['dooit-api-high-5xx']


def series(status, values, uri='/api/v1/tasks'):
    return {'series': 'http_server_requests_seconds_count{job="dooit-backend",uri="' + uri
            + '",status="' + status + '"}', 'values': values}


def case(name, inputs, expression, expected):
    return {'name': name, 'interval': '1m', 'input_series': inputs,
            'promql_expr_test': [{'expr': expression, 'eval_time': '10m',
                                 'exp_samples': [{'labels': '{}', 'value': expected}]}]}

cases = [
    case('healthy target', [{'series': 'up{job="dooit-backend"}', 'values': '1+0x20'}], down, 0),
    case('failed scrape', [{'series': 'up{job="dooit-backend"}', 'values': '0+0x20'}], down, 1),
    case('missing target', [], down, 1),
    case('no API traffic', [], errors, 0),
    case('successful traffic only', [series('200', '0+20x20')], errors, 0),
    case('exactly five percent is not above threshold',
         [series('200', '0+19x20'), series('500', '0+1x20')], errors, 0),
    case('high ratio but fewer than five errors',
         [series('200', '0+0.4x20'), series('500', '0+0.1x20')], errors, 0),
    case('sustained ten percent errors',
         [series('200', '0+18x20'), series('500', '0+2x20')], errors, 1),
    case('actuator failures excluded',
         [series('200', '0+20x20'), series('500', '0+20x20', '/actuator/health')], errors, 0),
]
image = subprocess.check_output(['docker', 'inspect', 'dooit-prometheus', '--format', '{{.Image}}'], text=True).strip()
with tempfile.TemporaryDirectory(prefix='dooit-promql-') as directory:
    folder = Path(directory)
    folder.chmod(0o755)
    fixture = folder / 'tests.json'
    fixture.write_text(json.dumps({'rule_files': [], 'evaluation_interval': '1m', 'tests': cases}))
    fixture.chmod(0o644)
    subprocess.run(['docker', 'run', '--rm', '--network', 'none', '--entrypoint', '/bin/promtool',
                    '-v', f'{folder}:/tests:ro', image, 'test', 'rules', '/tests/tests.json'], check=True)
print(f'Provisioned PromQL: {len(cases)} scenarios passed.')
