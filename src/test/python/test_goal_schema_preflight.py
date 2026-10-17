import contextlib
import importlib.util
import io
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location('goal_schema_preflight', ROOT / 'scripts/check-goal-schema.py')
CHECK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECK)


def complete_metadata():
    result = {}
    for key, expected in CHECK.PREREQUISITES + [check for checks in CHECK.MIGRATIONS.values() for check in checks]:
        result[key] = {'kind': key[0], 'table': key[1], 'name': key[2], **expected}
        if key[0] == 'fk':
            result[key]['local_reference'] = 1
    return result


def encoded(actual):
    return '\n'.join(json.dumps(row) for row in [{'kind': 'database', 'selected': 1}, *actual.values()])


class GoalSchemaPreflightTest(unittest.TestCase):
    def report(self, actual):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = CHECK.report(actual)
        return code, output.getvalue()

    def test_complete_and_unapplied_and_partial_migrations(self):
        actual = complete_metadata()
        self.assertEqual(0, self.report(CHECK.parse_metadata(encoded(actual)))[0])
        del actual['column', 'PERSONAL_SYSTEM', 'MIGRATED_GOAL_ID']
        code, text = self.report(actual)
        self.assertEqual(1, code)
        self.assertIn('20261016_add_personal_system_goal_mapping.sql: PARTIAL_OR_DRIFT', text)
        self.assertIn('MISSING: column PERSONAL_SYSTEM.MIGRATED_GOAL_ID', text)
        self.assertIn('20261014_add_goal_journey.sql: READY', text)
        self.assertIn('20261012_add_personal_system.sql: MISSING', self.report({})[1])
        del actual['column', 'TASK', 'COMPLETED_AT']
        self.assertIn('prerequisites: PARTIAL_OR_DRIFT', self.report(actual)[1])

    def test_wrong_collation_column_shape_index_and_foreign_key_cannot_pass(self):
        for key, field, wrong in [
            (('column', 'GOAL_DIRECT_LOG', 'IDEMPOTENCY_KEY'), 'collation', 'utf8mb4_0900_ai_ci'),
            (('column', 'GOAL_DIRECT_LOG', 'CREATE_REQUEST_HASH'), 'length', 32),
            (('column', 'PERSONAL_SYSTEM', 'MIGRATED_GOAL_ID'), 'nullable', False),
            (('index', 'PERSONAL_SYSTEM', 'UK_PERSONAL_SYSTEM_MIGRATED_GOAL'), 'unique', False),
            (('index', 'GOAL_PLAN_VERSION', 'UK_GOAL_PLAN_VERSION'), 'columns', 'VERSION,PLAN_ID'),
            (('fk', 'PERSONAL_SYSTEM', 'FK_PERSONAL_SYSTEM_MIGRATED_GOAL'), 'target_table', 'GOAL_PLAN'),
            (('fk', 'PERSONAL_SYSTEM', 'FK_PERSONAL_SYSTEM_MIGRATED_GOAL'), 'local_reference', 0),
            (('fk', 'PERSONAL_SYSTEM', 'FK_PERSONAL_SYSTEM_MIGRATED_GOAL'), 'delete_rule', 'CASCADE'),
        ]:
            with self.subTest(key=key, field=field):
                actual = complete_metadata()
                actual[key][field] = wrong
                code, text = self.report(actual)
                self.assertEqual(1, code)
                self.assertIn('DRIFT:', text)

    def test_metadata_must_select_database_and_not_duplicate_objects(self):
        for text in ['', '{"kind":"database","selected":0}',
                     encoded(complete_metadata()) + '\n{"kind":"table","table":"GOAL","name":""}']:
            with self.assertRaises(ValueError):
                CHECK.parse_metadata(text)

    def test_no_action_equals_restrict_only_for_innodb(self):
        actual = complete_metadata()
        key = ('fk', 'PERSONAL_SYSTEM', 'FK_PERSONAL_SYSTEM_MIGRATED_GOAL')
        actual[key]['delete_rule'] = 'NO ACTION'
        self.assertEqual([], CHECK.mismatches(CHECK.MIGRATIONS['20261016_add_personal_system_goal_mapping.sql'], actual))
        self.assertEqual('NO ACTION', actual[key]['delete_rule'])
        actual['table', 'PERSONAL_SYSTEM', '']['engine'] = 'NDB'
        self.assertIn(('DRIFT', key), CHECK.mismatches(CHECK.MIGRATIONS['20261016_add_personal_system_goal_mapping.sql'], actual))

    def test_manifest_covers_migration_table_index_fk_and_added_column_names(self):
        for filename, checks in CHECK.MIGRATIONS.items():
            sql = (ROOT / 'docs/db/migrations' / filename).read_text()
            expected_names = {key[2] for key, _ in checks}
            self.assertTrue(set(re.findall(r'CREATE TABLE (\w+)', sql)) <= {key[1] for key, _ in checks if key[0] == 'table'})
            for pattern in [r'CREATE INDEX (\w+)', r'CONSTRAINT (\w+)', r'ADD COLUMN (\w+)']:
                self.assertTrue(set(re.findall(pattern, sql)) <= expected_names, filename + ': ' + pattern)

    def test_query_is_metadata_only_and_release_gate_precedes_build_and_replacement(self):
        self.assertNotRegex(CHECK.QUERY, r'(?i)\b(ALTER|CREATE|INSERT|UPDATE|DELETE|DROP|TRUNCATE)\s')
        self.assertTrue(all(name.lower().startswith('information_schema.') for name in
                            re.findall(r'(?i)\b(?:FROM|JOIN)\s+([\w.]+)', CHECK.QUERY)))
        release = (ROOT / 'scripts/release-production.sh').read_text()
        gate = release.index('python3 ./scripts/check-goal-schema.py')
        self.assertLess(gate, release.index('./gradlew clean test bootJar'))
        self.assertLess(gate, release.index('docker compose up -d app'))

    def test_cli_fixture_exit_codes_without_database_connection(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'metadata.jsonl'
            for text, expected in [(encoded(complete_metadata()), 0),
                                   ('{"kind":"database","selected":1}', 1), ('private-invalid-data', 2)]:
                path.write_text(text)
                result = subprocess.run([sys.executable, str(ROOT / 'scripts/check-goal-schema.py'), '--metadata-file', str(path)],
                                        text=True, capture_output=True, timeout=5)
                self.assertEqual(expected, result.returncode)
                self.assertNotIn('private-', result.stdout + result.stderr)

    def test_connection_errors_and_invalid_output_do_not_expose_credentials(self):
        for result in [subprocess.CompletedProcess([], 1, '', 'private-user@private-host password'),
                       subprocess.CompletedProcess([], 0, 'private-invalid-output', '')]:
            output = io.StringIO()
            with patch.object(sys, 'argv', ['check-goal-schema.py']), patch.object(CHECK.subprocess, 'run', return_value=result) as run, \
                    contextlib.redirect_stderr(output):
                self.assertEqual(2, CHECK.main())
                args, kwargs = run.call_args
                self.assertIn('MYSQL_PWD', args[0][-1])
                self.assertEqual(30, kwargs['timeout'])
            self.assertNotIn('private-', output.getvalue())


if __name__ == '__main__':
    unittest.main()
