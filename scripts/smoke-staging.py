#!/usr/bin/env python3
"""Synthetic quick-capture smoke against the fixed local STG endpoint only."""
import json
import urllib.error
import urllib.request
import uuid

BASE = 'http://127.0.0.1:18080'
token = None
created = set()


def request(method, path, body=None, key=None, expected=200):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if key:
        headers['Idempotency-Key'] = key
    payload = None if body is None else json.dumps(body, ensure_ascii=False).encode()
    req = urllib.request.Request(BASE + path, data=payload, headers=headers, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        status = response.status
        data = json.loads(response.read())
        assert status == expected, f'{method} {path}: expected {expected}, got {status}'
        return data, response.headers


def capture(text, selected=None, key=None, expected=201):
    body = {'text': text, 'referenceDate': '2026-10-18', 'timeZone': 'Asia/Seoul'}
    if selected is not None:
        body['requestedType'] = selected
    result, headers = request('POST', '/api/v1/tasks/quick-capture', body, key, expected)
    if expected == 201:
        created.add(result['data']['task']['id'])
        return result['data'], headers
    assert result['error']['code'] == 10001
    return result, headers


request('GET', '/actuator/health/readiness')
auth, _ = request('POST', '/api/v1/auth/guest', expected=201)
token = auth['data']['accessToken']
try:
    today, _ = capture('오늘 로그인 버튼 연결하기', 'TODO')
    assert today['task']['type'] == 'TODO' and today['task']['plannedDate'] == '2026-10-18'
    assert today['task']['startAt'] is None and not today['task']['allDay']
    tomorrow, _ = capture('내일 영단어 30개', 'TODO')
    assert tomorrow['task']['plannedDate'] == '2026-10-19'
    listing, _ = request('GET', '/api/v1/tasks/today?date=2026-10-18')
    assert tomorrow['task']['id'] not in {t['id'] for t in listing['data']}
    inbox, _ = capture('단어 공부', 'TODO')
    assert inbox['task']['status'] == 'INBOX' and inbox['task']['plannedDate'] is None
    idea, _ = capture('내일 오후 3시 생각해 볼 아이디어', 'IDEA')
    assert idea['task']['type'] == 'IDEA' and idea['task']['title'] == '내일 오후 3시 생각해 볼 아이디어'
    assert not idea['parsed']
    schedule, _ = capture('내일 오후 3시 회의', 'SCHEDULE')
    assert schedule['task']['startAt'] == '2026-10-19T15:00:00'
    assert schedule['task']['endAt'] == '2026-10-19T16:00:00'
    auto, _ = capture('오늘 회의')
    assert auto['task']['type'] == 'SCHEDULE' and auto['task']['allDay']
    weekly, _ = capture('매주 월요일 운동', 'SCHEDULE')
    saved, _ = request('GET', '/api/v1/tasks/' + str(weekly['task']['id']))
    assert saved['data']['recurrenceSeriesId'] is not None
    assert saved['data']['recurrence']['frequency'] == 'WEEKLY'
    capture('내일 오후 3시 공부', 'TODO', expected=400)
    capture('매주 월요일 운동', 'TODO', expected=400)
    capture('회의 준비', 'SCHEDULE', expected=400)
    capture('내일 공부', 'UNKNOWN', expected=400)
    key = str(uuid.uuid4())
    first, _ = capture('내일 재시도 검증', 'TODO', key)
    replay, headers = capture('내일 재시도 검증', 'TODO', key)
    assert first == replay and headers.get('Idempotency-Replayed') == 'true'
    print('STG smoke passed: selected types, planned dates, Today separation, schedule times, weekly persistence, legacy auto, errors and replay.')
finally:
    for task_id in created:
        request('DELETE', '/api/v1/tasks/' + str(task_id) + '?recurrenceScope=ALL')
    print('Synthetic STG tasks deleted; the test guest and idempotency records remain in STG only.')
