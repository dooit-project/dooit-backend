#!/usr/bin/env python3
"""Read-only MySQL metadata preflight for personal-system/goal releases. No migration execution."""
import argparse
import json
from pathlib import Path
import subprocess
import sys


MIGRATIONS = {}


def group(filename, tables):
    checks = []
    MIGRATIONS[filename] = checks
    for table in tables.split():
        checks.append((('table', table, ''), {}))
    return checks


def column(checks, table, name, **properties):
    checks.append((('column', table, name), properties))


def index(checks, table, name, columns, unique=False):
    checks.append((('index', table, name), {'columns': columns, 'unique': unique}))


def foreign_key(checks, table, name, column_name, target, delete='RESTRICT'):
    checks.append((('fk', table, name), {
        'column': column_name, 'target_table': target, 'target_column': 'ID', 'delete_rule': delete}))


personal = group('20261012_add_personal_system.sql',
                 'PERSONAL_SYSTEM PERSONAL_SYSTEM_VERSION PERSONAL_SYSTEM_OCCURRENCE PERSONAL_SYSTEM_PRACTICE_RESULT')
index(personal, 'PERSONAL_SYSTEM', 'IDX_PERSONAL_SYSTEM_OWNER_STATUS', 'OWNER_USER_ID,STATUS,ID')
index(personal, 'PERSONAL_SYSTEM_VERSION', 'UK_PERSONAL_SYSTEM_VERSION', 'SYSTEM_ID,VERSION', True)
index(personal, 'PERSONAL_SYSTEM_VERSION', 'IDX_PERSONAL_SYSTEM_VERSION_EFFECTIVE', 'SYSTEM_ID,EFFECTIVE_FROM_DATE,VERSION')
index(personal, 'PERSONAL_SYSTEM_OCCURRENCE', 'UK_PERSONAL_SYSTEM_OCCURRENCE_DATE', 'SYSTEM_ID,SERVICE_DATE', True)
index(personal, 'PERSONAL_SYSTEM_OCCURRENCE', 'IDX_PERSONAL_SYSTEM_OCCURRENCE_OWNER_DATE', 'OWNER_USER_ID,SERVICE_DATE,ID')
foreign_key(personal, 'PERSONAL_SYSTEM', 'FK_PERSONAL_SYSTEM_OWNER', 'OWNER_USER_ID', 'APP_USER')
foreign_key(personal, 'PERSONAL_SYSTEM', 'FK_PERSONAL_SYSTEM_DDAY', 'LINKED_DDAY_GOAL_ID', 'DDAY_GOAL')
foreign_key(personal, 'PERSONAL_SYSTEM_VERSION', 'FK_PERSONAL_SYSTEM_VERSION_SYSTEM', 'SYSTEM_ID', 'PERSONAL_SYSTEM', 'CASCADE')
foreign_key(personal, 'PERSONAL_SYSTEM_OCCURRENCE', 'FK_PERSONAL_SYSTEM_OCCURRENCE_SYSTEM', 'SYSTEM_ID', 'PERSONAL_SYSTEM')
foreign_key(personal, 'PERSONAL_SYSTEM_OCCURRENCE', 'FK_PERSONAL_SYSTEM_OCCURRENCE_OWNER', 'OWNER_USER_ID', 'APP_USER')
foreign_key(personal, 'PERSONAL_SYSTEM_OCCURRENCE', 'FK_PERSONAL_SYSTEM_OCCURRENCE_TASK', 'LINKED_TASK_ID', 'TASK')
foreign_key(personal, 'PERSONAL_SYSTEM_PRACTICE_RESULT', 'FK_PERSONAL_SYSTEM_RESULT_OCCURRENCE', 'OCCURRENCE_ID', 'PERSONAL_SYSTEM_OCCURRENCE', 'CASCADE')

goal = group('20261014_add_goal_journey.sql',
             'GOAL GOAL_VERSION GOAL_PLAN GOAL_PLAN_VERSION GOAL_PLAN_OCCURRENCE GOAL_PRACTICE_RESULT GOAL_DIRECT_LOG')
column(goal, 'TASK', 'LINKED_GOAL_ID', type='bigint', nullable=True)
for table, name, columns, unique in [
    ('GOAL', 'IDX_GOAL_OWNER_STATUS', 'OWNER_USER_ID,STATUS,ID', False),
    ('GOAL', 'IDX_GOAL_OWNER_TARGET_DATE', 'OWNER_USER_ID,TARGET_DATE,ID', False),
    ('GOAL_VERSION', 'UK_GOAL_VERSION', 'GOAL_ID,VERSION', True),
    ('GOAL_VERSION', 'IDX_GOAL_VERSION_EFFECTIVE', 'GOAL_ID,EFFECTIVE_FROM_DATE,VERSION', False),
    ('GOAL_PLAN', 'IDX_GOAL_PLAN_OWNER_STATUS', 'OWNER_USER_ID,STATUS,ID', False),
    ('GOAL_PLAN', 'IDX_GOAL_PLAN_GOAL_STATUS', 'GOAL_ID,STATUS,ID', False),
    ('GOAL_PLAN_VERSION', 'UK_GOAL_PLAN_VERSION', 'PLAN_ID,VERSION', True),
    ('GOAL_PLAN_VERSION', 'IDX_GOAL_PLAN_VERSION_EFFECTIVE', 'PLAN_ID,EFFECTIVE_FROM_DATE,VERSION', False),
    ('GOAL_PLAN_OCCURRENCE', 'UK_GOAL_PLAN_OCCURRENCE_DATE', 'PLAN_ID,SERVICE_DATE', True),
    ('GOAL_PLAN_OCCURRENCE', 'IDX_GOAL_OCCURRENCE_OWNER_DATE', 'OWNER_USER_ID,SERVICE_DATE,ID', False),
    ('GOAL_PLAN_OCCURRENCE', 'IDX_GOAL_OCCURRENCE_GOAL_DATE', 'GOAL_ID,SERVICE_DATE,ID', False),
    ('GOAL_DIRECT_LOG', 'UK_GOAL_DIRECT_LOG_IDEMPOTENCY', 'OWNER_USER_ID,GOAL_ID,IDEMPOTENCY_KEY', True),
    ('GOAL_DIRECT_LOG', 'IDX_GOAL_DIRECT_LOG_GOAL_DATE', 'GOAL_ID,PRACTICED_DATE,ID', False),
    ('TASK', 'IDX_TASK_LINKED_GOAL_COMPLETED', 'LINKED_GOAL_ID,COMPLETED_AT,ID', False),
]:
    index(goal, table, name, columns, unique)
for table, name, column_name, target, delete in [
    ('GOAL', 'FK_GOAL_OWNER', 'OWNER_USER_ID', 'APP_USER', 'RESTRICT'),
    ('GOAL', 'FK_GOAL_DDAY', 'LINKED_DDAY_GOAL_ID', 'DDAY_GOAL', 'RESTRICT'),
    ('GOAL_VERSION', 'FK_GOAL_VERSION_GOAL', 'GOAL_ID', 'GOAL', 'CASCADE'),
    ('GOAL_PLAN', 'FK_GOAL_PLAN_OWNER', 'OWNER_USER_ID', 'APP_USER', 'RESTRICT'),
    ('GOAL_PLAN', 'FK_GOAL_PLAN_GOAL', 'GOAL_ID', 'GOAL', 'RESTRICT'),
    ('GOAL_PLAN_VERSION', 'FK_GOAL_PLAN_VERSION_PLAN', 'PLAN_ID', 'GOAL_PLAN', 'CASCADE'),
    ('GOAL_PLAN_VERSION', 'FK_GOAL_PLAN_VERSION_GOAL', 'GOAL_ID', 'GOAL', 'RESTRICT'),
    ('GOAL_PLAN_OCCURRENCE', 'FK_GOAL_OCCURRENCE_OWNER', 'OWNER_USER_ID', 'APP_USER', 'RESTRICT'),
    ('GOAL_PLAN_OCCURRENCE', 'FK_GOAL_OCCURRENCE_GOAL', 'GOAL_ID', 'GOAL', 'RESTRICT'),
    ('GOAL_PLAN_OCCURRENCE', 'FK_GOAL_OCCURRENCE_PLAN', 'PLAN_ID', 'GOAL_PLAN', 'RESTRICT'),
    ('GOAL_PLAN_OCCURRENCE', 'FK_GOAL_OCCURRENCE_TASK', 'LINKED_TASK_ID', 'TASK', 'RESTRICT'),
    ('GOAL_PRACTICE_RESULT', 'FK_GOAL_RESULT_OCCURRENCE', 'OCCURRENCE_ID', 'GOAL_PLAN_OCCURRENCE', 'CASCADE'),
    ('GOAL_DIRECT_LOG', 'FK_GOAL_DIRECT_LOG_OWNER', 'OWNER_USER_ID', 'APP_USER', 'RESTRICT'),
    ('GOAL_DIRECT_LOG', 'FK_GOAL_DIRECT_LOG_GOAL', 'GOAL_ID', 'GOAL', 'RESTRICT'),
    ('TASK', 'FK_TASK_LINKED_GOAL', 'LINKED_GOAL_ID', 'GOAL', 'RESTRICT'),
]:
    foreign_key(goal, table, name, column_name, target, delete)

fingerprint = group('20261015_add_goal_direct_log_request_hash.sql', '')
column(fingerprint, 'GOAL_DIRECT_LOG', 'CREATE_REQUEST_HASH', type='varchar', length=64, nullable=True)
column(fingerprint, 'GOAL_DIRECT_LOG', 'IDEMPOTENCY_KEY', type='varchar', length=160, nullable=False, collation='utf8mb4_bin')

mapping = group('20261016_add_personal_system_goal_mapping.sql', '')
for table, name, unique_name, fk_name, target in [
    ('PERSONAL_SYSTEM', 'MIGRATED_GOAL_ID', 'UK_PERSONAL_SYSTEM_MIGRATED_GOAL', 'FK_PERSONAL_SYSTEM_MIGRATED_GOAL', 'GOAL'),
    ('PERSONAL_SYSTEM', 'MIGRATED_PLAN_ID', 'UK_PERSONAL_SYSTEM_MIGRATED_PLAN', 'FK_PERSONAL_SYSTEM_MIGRATED_PLAN', 'GOAL_PLAN'),
    ('PERSONAL_SYSTEM_OCCURRENCE', 'MIGRATED_OCCURRENCE_ID', 'UK_PERSONAL_OCCURRENCE_MIGRATED', 'FK_PERSONAL_OCCURRENCE_MIGRATED', 'GOAL_PLAN_OCCURRENCE'),
]:
    column(mapping, table, name, type='bigint', nullable=True)
    index(mapping, table, unique_name, name, True)
    foreign_key(mapping, table, fk_name, name, target)

PREREQUISITES = [(('table', name, ''), {}) for name in ('APP_USER', 'DDAY_GOAL', 'TASK')]
PREREQUISITES += [(('column', 'TASK', 'COMPLETED_AT'), {})]

# These queries only return metadata from the explicitly selected database, never application rows.
QUERY = """
SELECT JSON_OBJECT('kind','database','selected',DATABASE() IS NOT NULL);
SELECT JSON_OBJECT('kind','table','table',TABLE_NAME,'name','')
FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE';
SELECT JSON_OBJECT('kind','column','table',TABLE_NAME,'name',COLUMN_NAME,'type',DATA_TYPE,
 'length',CHARACTER_MAXIMUM_LENGTH,'nullable',IS_NULLABLE='YES','collation',COLLATION_NAME)
FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE();
SELECT JSON_OBJECT('kind','index','table',TABLE_NAME,'name',INDEX_NAME,
 'columns',GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ','),'unique',MIN(NON_UNIQUE)=0)
FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() GROUP BY TABLE_NAME,INDEX_NAME;
SELECT JSON_OBJECT('kind','fk','table',k.TABLE_NAME,'name',k.CONSTRAINT_NAME,'column',k.COLUMN_NAME,
 'target_table',k.REFERENCED_TABLE_NAME,'target_column',k.REFERENCED_COLUMN_NAME,'delete_rule',r.DELETE_RULE,
 'local_reference',k.REFERENCED_TABLE_SCHEMA=DATABASE())
FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r
 ON k.CONSTRAINT_SCHEMA=r.CONSTRAINT_SCHEMA AND k.TABLE_NAME=r.TABLE_NAME AND k.CONSTRAINT_NAME=r.CONSTRAINT_NAME
WHERE k.CONSTRAINT_SCHEMA=DATABASE() AND k.REFERENCED_TABLE_NAME IS NOT NULL;
"""


def parse_metadata(text):
    records = [json.loads(line) for line in text.splitlines() if line.strip()]
    database = [row for row in records if row.get('kind') == 'database']
    if len(database) != 1 or database[0].get('selected') != 1:
        raise ValueError('No selected database')
    actual = {}
    for row in records:
        if row['kind'] == 'database':
            continue
        key = row['kind'], row['table'], row['name']
        if key in actual:
            raise ValueError('Duplicate metadata')
        actual[key] = row
    return actual


def mismatches(checks, actual):
    result = []
    for key, expected in checks:
        row = actual.get(key)
        if row is None:
            result.append(('MISSING', key))
        elif any(row.get(field) != value for field, value in expected.items()) or (key[0] == 'fk' and row.get('local_reference') != 1):
            result.append(('DRIFT', key))
    return result


def report(actual):
    failed = False
    for name, checks in [('prerequisites', PREREQUISITES), *MIGRATIONS.items()]:
        failures = mismatches(checks, actual)
        missing = sum(kind == 'MISSING' for kind, _ in failures)
        status = 'READY' if not failures else ('MISSING' if missing == len(checks) else 'PARTIAL_OR_DRIFT')
        print(f'{name}: {status} ({len(checks) - len(failures)}/{len(checks)} checks)')
        for kind, key in failures:
            print(f'  {kind}: {key[0]} {key[1]}' + (f'.{key[2]}' if key[2] else ''))
        failed |= bool(failures)
    print('Read-only checks only; READY does not prove migration history, all columns/types, or data consistency.')
    return 1 if failed else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--metadata-file', type=Path, help='Check a captured JSON-lines metadata fixture without Docker')
    args = parser.parse_args()
    try:
        if args.metadata_file:
            text = args.metadata_file.read_text(encoding='utf-8')
        else:
            # Credentials stay inside the container environment. Never print mysql stderr/connection details.
            result = subprocess.run(['docker', 'compose', 'exec', '-T', 'mysql', 'sh', '-c',
                'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql --batch --raw --skip-column-names -u"$MYSQL_USER" "$MYSQL_DATABASE"'],
                input=QUERY, text=True, capture_output=True, timeout=30, cwd=Path(__file__).resolve().parent.parent)
            if result.returncode:
                raise ValueError('Metadata query failed')
            text = result.stdout
        return report(parse_metadata(text))
    except (OSError, ValueError, KeyError, TypeError, subprocess.TimeoutExpired):
        print('Schema preflight could not verify metadata; check Docker/MySQL availability and metadata permissions.', file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
