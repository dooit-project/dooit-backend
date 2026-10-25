#!/usr/bin/env python3
"""Check personal Task isolation using synthetic accounts on localhost STG only."""
import json
import urllib.error
import urllib.request

BASE = 'http://127.0.0.1:18080'
accounts = []
tasks = []
checks = 0


def request(method, path, token=None, body=None, expected=200):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    payload = None if body is None else json.dumps(body, ensure_ascii=False).encode()
    req = urllib.request.Request(BASE + path, data=payload, headers=headers, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        status = response.status
        result = json.loads(response.read())
    if status != expected:
        raise AssertionError(f'{method} {path}: expected {expected}, got {status}')
    return result


def guest():
    account = request('POST', '/api/v1/auth/guest', expected=201)['data']
    accounts.append(account)
    return account['accessToken']


def create_task(token):
    result = request('POST', '/api/v1/tasks', token,
                     {'title': 'STG ownership private', 'description': 'Synthetic private text',
                      'type': 'TODO', 'allDay': False}, expected=201)['data']
    tasks.append((token, result['id']))
    return '/api/v1/tasks/' + str(result['id'])


request('GET', '/actuator/health/readiness')
try:
    owner, other = guest(), guest()
    task = create_task(owner)
    other_task = create_task(other)
    checklist = task + '/checklist-items'
    first = request('POST', checklist, owner, {'title': 'Private open item'}, 201)['data']['id']
    second = request('POST', checklist, owner, {'title': 'Private done item'}, 201)['data']['id']
    request('PATCH', f'{checklist}/{second}/done', owner)
    original_task = request('GET', task, owner)['data']
    original_items = request('GET', checklist, owner)['data']
    other_original = request('GET', other_task, other)['data']

    def unchanged():
        assert request('GET', task, owner)['data'] == original_task, 'Owner Task changed'
        assert request('GET', checklist, owner)['data'] == original_items, 'Owner checklist changed'

    operations = [
        ('GET', task, None),
        ('PUT', task, {'title': 'Unauthorized update', 'type': 'IDEA', 'allDay': False}),
        ('DELETE', task, None),
        ('PATCH', task + '/done', None),
        ('GET', checklist, None),
        ('POST', checklist, {'title': 'Unauthorized item'}),
        ('PUT', f'{checklist}/{first}', {'title': 'Unauthorized update'}),
        ('DELETE', f'{checklist}/{first}', None),
        ('PATCH', f'{checklist}/{first}/done', None),
        ('PATCH', f'{checklist}/{second}/done/cancel', None),
        ('PUT', checklist + '/order', {'orderedItemIds': [second, first]}),
    ]
    for method, path, body in operations:
        for actor, expected in [(other, 404), (None, 401)]:
            result = request(method, path, actor, body, expected)
            assert result.get('data') is None
            if actor:
                assert result['error']['code'] == 20001
            assert 'Private' not in json.dumps(result)
            unchanged()
            checks += 1

    for method, suffix, item_id, body in [
        ('PUT', '', first, {'title': 'Unauthorized update'}),
        ('DELETE', '', first, None),
        ('PATCH', '/done', first, None),
        ('PATCH', '/done/cancel', second, None),
    ]:
        result = request(method, f'{other_task}/checklist-items/{item_id}{suffix}', other, body, 404)
        assert result.get('data') is None and result['error']['code'] == 20004
        unchanged()
        assert request('GET', other_task, other)['data'] == other_original
        assert request('GET', other_task + '/checklist-items', other)['data'] == []
        checks += 1

    for method, path in [('GET', task.replace('/api/v1/', '/api/')),
                         ('PUT', task.replace('/api/v1/', '/api/')),
                         ('DELETE', task.replace('/api/v1/', '/api/')),
                         ('PATCH', task.replace('/api/v1/', '/api/') + '/done'),
                         ('GET', '/api/tasks'), ('GET', '/api/tasks/inbox')]:
        for actor, expected in [(other, 403), (None, 401)]:
            result = request(method, path, actor, expected=expected)
            assert result.get('data') is None
            unchanged()
            checks += 1
    print(f'STG Task ownership smoke passed: {checks} denied requests; owner snapshots unchanged.')
finally:
    # Delete only resources created by this run, using their own authenticated owner.
    cleanup_errors = []
    for token, task_id in reversed(tasks):
        try:
            request('DELETE', '/api/v1/tasks/' + str(task_id), token)
        except Exception as error:
            cleanup_errors.append(type(error).__name__)
    for account in accounts:
        try:
            request('POST', '/api/v1/auth/logout', account['accessToken'],
                    {'refreshToken': account['refreshToken']})
        except Exception as error:
            cleanup_errors.append(type(error).__name__)
    if cleanup_errors:
        raise RuntimeError('Synthetic cleanup incomplete: ' + ', '.join(cleanup_errors))
    print('Synthetic STG tasks deleted and refresh sessions logged out; guest accounts remain in STG only.')
