import copy
import csv
import json
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest import mock

import report
import run


class BenchmarkToolsTest(unittest.TestCase):
    def setUp(self):
        Path('/tmp/agents').mkdir(parents=True, exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(dir='/tmp/agents')
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.manifest = {
            'smoke': False,
            'settings': {'publishers': 256, 'batchSizes': [1, 100],
                         'measurementSeconds': 120, 'measurementIterations': 2,
                         'warmupSeconds': 60, 'repetitions': 1,
                         'postgresDigest': 'postgres@sha256:example'},
            'versions': {'0.9.15': 'old-commit', '0.12.5': 'new-commit'},
            'cases': [],
        }
        for batch in [1, 100]:
            for version, rate in [('0.9.15', 10.0), ('0.12.5', 150.0)]:
                case = {'version': version, 'batchSize': batch, 'repetition': 1,
                        'directory': f'{version}-{batch}', 'status': 'passed'}
                self.manifest['cases'].append(case)
                folder = self.directory / case['directory']
                folder.mkdir()
                (folder / 'jmh.json').write_text(json.dumps([{
                    'benchmark': report.BENCHMARK,
                    'params': {'batchSize': str(batch)}, 'threads': 256,
                    'measurementIterations': 2, 'measurementTime': '60 s',
                    'warmupIterations': 1, 'warmupTime': '60 s',
                    'primaryMetric': {'score': rate, 'scoreUnit': 'ops/s'},
                }]))
                (folder / 'metadata.json').write_text(json.dumps({
                    'version': version, 'commit': self.manifest['versions'][version],
                    'batchSize': batch, 'publishers': 256,
                    'batchingEnabled': version == '0.12.5',
                    'validationEnabled': False, 'schemaRegistryConfigured': False,
                    'payloadBytes': 256, 'poolSize': 64, 'reWriteBatchedInserts': True,
                    'verboseStoreLogging': False,
                    'postgresImage': 'postgres@sha256:example',
                    'database': {'server_version': '15.14', 'fsync': 'on',
                                 'synchronous_commit': 'on', 'full_page_writes': 'on'},
                    'acknowledgedEvents': 12300, 'persistedEvents': 12300,
                }))

    def test_requires_two_measured_minutes_unless_explicitly_smoke(self):
        with self.assertRaises(SystemExit):
            run.parse_args(['--measurement-seconds', '119'])
        defaults = run.parse_args([])
        self.assertEqual((defaults.publishers, defaults.measurement_seconds), ([256], 120))
        smoke = run.parse_args(['--smoke'])
        self.assertLess(smoke.measurement_seconds, 120)
        self.assertTrue(all(count <= 8 for count in smoke.publishers))

    def test_runs_only_single_event_publications_across_selected_publisher_counts(self):
        self.assertEqual(run.parse_args([]).batch_sizes, [1])
        selected = run.parse_args(['--publishers', '256', '1024', '16384'])
        self.assertEqual(selected.publishers, [256, 1024, 16384])
        with self.assertRaises(SystemExit):
            run.parse_args(['--batch-sizes', '100'])
        with self.assertRaises(SystemExit):
            run.parse_args(['--publishers', '256', '256'])
        smoke = run.parse_args(['--smoke', '--publishers', '256', '1024', '16384'])
        self.assertEqual(smoke.publishers, [8])

    def test_compares_and_charts_matching_publisher_counts(self):
        originals = [case for case in self.manifest['cases'] if case['batchSize'] == 1]
        self.manifest['settings'].update(publishers=[256, 1024, 16384], batchSizes=[1])
        self.manifest['cases'] = []
        for publishers, multiplier in [(256, 1), (1024, 2), (16384, 3)]:
            for original in originals:
                case = dict(original, publishers=publishers,
                            directory=f'{publishers}-{original["version"]}')
                folder = self.directory / case['directory']
                shutil.copytree(self.directory / original['directory'], folder)
                path = folder / 'jmh.json'
                result = json.loads(path.read_text())
                result[0]['threads'] = publishers
                result[0]['primaryMetric']['score'] *= multiplier
                path.write_text(json.dumps(result))
                path = folder / 'metadata.json'
                metadata = json.loads(path.read_text())
                metadata['publishers'] = publishers
                path.write_text(json.dumps(metadata))
                self.manifest['cases'].append(case)
        rows = report.write_report(self.directory, self.manifest)
        self.assertEqual([row['publishers'] for row in rows], [256, 256, 1024, 1024, 16384, 16384])
        self.assertEqual([row['ratio_to_baseline'] for row in rows], [1, 15, 1, 15, 1, 15])
        figure = report.create_chart(rows, self.manifest)
        from matplotlib import pyplot as plt
        self.addCleanup(lambda: plt.close(figure))
        self.assertEqual([axis.get_title(loc='left') for axis in figure.axes],
                         ['256 publishers', '1,024 publishers', '16,384 publishers'])
        self.assertEqual([[bar.get_height() for bar in axis.patches] for axis in figure.axes],
                         [[10, 150], [20, 300], [30, 450]])
        self.assertIn('| Publishers |', (self.directory / 'comparison.md').read_text())
        self.manifest['cases'].pop()
        with self.assertRaises(ValueError):
            report.summarize(self.directory, self.manifest)

    def test_converts_publications_to_events_and_compares_matching_batch_sizes(self):
        rows = report.summarize(self.directory, self.manifest)
        hundred = next(row for row in rows if row['batch_size'] == 100 and row['version'] == '0.12.5')
        self.assertEqual(hundred['publications_per_second'], 150.0)
        self.assertEqual(hundred['events_per_second'], 15000.0)
        self.assertEqual(hundred['ratio_to_0_9_15'], 15.0)

    def test_selects_release_pair_without_changing_existing_defaults(self):
        self.assertEqual(tuple(run.parse_args([]).versions), ('0.9.15', '0.12.5'))
        selected = run.parse_args(['--versions', '0.11.2', '0.12.5', '--batch-sizes', '1'])
        self.assertEqual(selected.versions, ['0.11.2', '0.12.5'])
        with self.assertRaises(SystemExit):
            run.parse_args(['--versions', '0.11.2', '0.11.2'])

    def test_selects_one_release_and_an_explicit_concurrent_batch_limit(self):
        selected = run.parse_args(['--versions', '0.12.5', '--publishers', '1024',
                                   '--publish-max-batch-size', '50000'])
        self.assertEqual(selected.versions, ['0.12.5'])
        self.assertEqual(selected.publish_max_batch_size, 50000)
        self.assertEqual(run.parse_args([]).publish_max_batch_size, 500)

    def test_can_disable_concurrent_publication_batching(self):
        selected = run.parse_args(['--versions', '0.12.5', '--publishers', '1024',
                                   '--no-publish-batching'])
        self.assertFalse(selected.publish_batching)
        self.assertTrue(run.parse_args([]).publish_batching)

    def test_selects_and_reports_the_current_working_tree(self):
        selected = run.parse_args(['--versions', 'current', '--publish-max-batch-size', '99999'])
        self.assertEqual(selected.versions, ['current'])
        self.manifest['versions'] = {'current': 'new-commit'}
        self.manifest['settings'].update(batchSizes=[1], publishMaxBatchSize=99999)
        case = next(case for case in self.manifest['cases']
                    if case['version'] == '0.12.5' and case['batchSize'] == 1)
        case['version'] = 'current'
        self.manifest['cases'] = [case]
        path = self.directory / case['directory'] / 'metadata.json'
        metadata = json.loads(path.read_text())
        metadata.update(version='current', publishMaxBatchSize=99999)
        path.write_text(json.dumps(metadata))
        rows = report.summarize(self.directory, self.manifest)
        figure = report.create_chart(rows, self.manifest)
        from matplotlib import pyplot as plt
        self.addCleanup(lambda: plt.close(figure))
        self.assertTrue(any('current concurrent batching enabled (max 99,999)' in text.get_text()
                            for text in figure.texts))

    def test_current_snapshot_includes_modified_new_and_deleted_files(self):
        working = self.directory / 'working'
        archive = self.directory / 'archive'
        working.mkdir()
        archive.mkdir()
        (working / 'modified.java').write_text('working tree change')
        (working / 'new.java').write_text('new source')
        (archive / 'modified.java').write_text('committed content')
        (archive / 'deleted.java').write_text('deleted content')
        with mock.patch.object(run, 'ROOT', working), mock.patch.object(
                run, 'capture', side_effect=['modified.java\0deleted.java\0', 'new.java\0',
                                             'working-tree diff', 'issue5083']):
            run.snapshot_current(archive, self.directory)
        self.assertEqual((archive / 'modified.java').read_text(), 'working tree change')
        self.assertEqual((archive / 'new.java').read_text(), 'new source')
        self.assertFalse((archive / 'deleted.java').exists())
        snapshot = json.loads((self.directory / 'source-current.json').read_text())
        self.assertEqual(snapshot['branch'], 'issue5083')
        self.assertEqual(len(snapshot['changes']), 3)
        self.assertTrue((self.directory / 'current-working-tree.patch').exists())

    def test_verifies_and_labels_disabled_concurrent_batching(self):
        self.manifest['settings']['publishBatching'] = False
        with self.assertRaises(ValueError):
            report.summarize(self.directory, self.manifest)
        for case in self.manifest['cases']:
            if case['version'] == '0.12.5':
                path = self.directory / case['directory'] / 'metadata.json'
                metadata = json.loads(path.read_text())
                metadata['batchingEnabled'] = False
                metadata['publishMaxBatchSize'] = None
                path.write_text(json.dumps(metadata))
        rows = report.summarize(self.directory, self.manifest)
        figure = report.create_chart(rows, self.manifest)
        from matplotlib import pyplot as plt
        self.addCleanup(lambda: plt.close(figure))
        self.assertTrue(any('0.12.5 concurrent batching disabled' in text.get_text()
                            for text in figure.texts))

    def test_rejects_a_batch_limit_that_was_not_applied(self):
        self.manifest['settings']['publishMaxBatchSize'] = 50000
        with self.assertRaises(ValueError):
            report.summarize(self.directory, self.manifest)
        for case in self.manifest['cases']:
            if case['version'] == '0.12.5':
                path = self.directory / case['directory'] / 'metadata.json'
                metadata = json.loads(path.read_text())
                metadata['publishMaxBatchSize'] = 50000
                path.write_text(json.dumps(metadata))
        self.assertEqual(len(report.summarize(self.directory, self.manifest)), 4)

    def test_renders_a_single_release_rerun(self):
        self.manifest['versions'] = {'0.12.5': 'new-commit'}
        self.manifest['settings']['batchSizes'] = [1]
        self.manifest['cases'] = [case for case in self.manifest['cases']
                                  if case['version'] == '0.12.5' and case['batchSize'] == 1]
        rows = report.write_report(self.directory, self.manifest)
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]['events_per_second'], 150)

    def test_uses_selected_baseline_in_ratios_and_report_labels(self):
        self.manifest['versions'] = {'0.11.2': 'old-commit', '0.12.5': 'new-commit'}
        for case in self.manifest['cases']:
            if case['version'] == '0.9.15':
                case['version'] = '0.11.2'
                path = self.directory / case['directory'] / 'metadata.json'
                metadata = json.loads(path.read_text())
                metadata['version'] = '0.11.2'
                path.write_text(json.dumps(metadata))
        rows = report.summarize(self.directory, self.manifest)
        newer = next(row for row in rows if row['version'] == '0.12.5')
        self.assertEqual(newer['baseline_version'], '0.11.2')
        self.assertEqual(newer['ratio_to_baseline'], 15.0)
        report.write_report(self.directory, self.manifest)
        text = (self.directory / 'comparison.md').read_text()
        self.assertIn('Relative to 0.11.2', text)
        self.assertNotIn('0.9.15', text)

    def test_single_workload_chart_fits_title_and_has_no_multiple_scale_caption(self):
        self.manifest['settings']['batchSizes'] = [1]
        self.manifest['cases'] = [case for case in self.manifest['cases'] if case['batchSize'] == 1]
        rows = report.summarize(self.directory, self.manifest)
        figure = report.create_chart(rows, self.manifest)
        from matplotlib import pyplot as plt
        self.addCleanup(lambda: plt.close(figure))
        self.assertEqual(len(figure.axes), 1)
        self.assertGreaterEqual(figure.get_size_inches()[0], 8)
        self.assertEqual([tick.get_text() for tick in figure.axes[0].get_xticklabels()],
                         ['0.9.15', '0.12.5'])
        self.assertFalse(any('separate linear scales' in text.get_text() for text in figure.texts))

    def test_three_version_chart_keeps_release_order_and_baseline_ratios(self):
        self.manifest['versions'] = {'0.9.15': 'old-commit', '0.11.2': 'middle-commit',
                                     '0.12.5': 'new-commit'}
        for batch in self.manifest['settings']['batchSizes']:
            case = {'version': '0.11.2', 'batchSize': batch, 'repetition': 1,
                    'directory': f'0.11.2-{batch}', 'status': 'passed'}
            source = self.directory / f'0.9.15-{batch}'
            folder = self.directory / case['directory']
            shutil.copytree(source, folder)
            metadata = json.loads((folder / 'metadata.json').read_text())
            metadata.update(version='0.11.2', commit='middle-commit')
            (folder / 'metadata.json').write_text(json.dumps(metadata))
            result = json.loads((folder / 'jmh.json').read_text())
            result[0]['primaryMetric']['score'] = 40.0
            (folder / 'jmh.json').write_text(json.dumps(result))
            self.manifest['cases'].append(case)
        rows = report.summarize(self.directory, self.manifest)
        self.assertEqual([row['ratio_to_baseline'] for row in rows], [1, 4, 15, 1, 4, 15])
        figure = report.create_chart(rows, self.manifest)
        from matplotlib import pyplot as plt
        self.addCleanup(lambda: plt.close(figure))
        self.assertEqual([tick.get_text() for tick in figure.axes[0].get_xticklabels()],
                         ['0.9.15', '0.11.2', '0.12.5'])
        self.assertEqual([bar.get_height() for bar in figure.axes[0].patches], [10, 40, 150])
        figure.canvas.draw()
        bounds = [text.get_window_extent(figure.canvas.get_renderer())
                  for text in figure.axes[0].texts]
        self.assertFalse(any(first.overlaps(second)
                             for index, first in enumerate(bounds) for second in bounds[index + 1:]))

    def test_rejects_partial_or_failed_comparisons(self):
        partial = copy.deepcopy(self.manifest)
        partial['cases'].pop()
        with self.assertRaises(ValueError):
            report.summarize(self.directory, partial)
        self.manifest['cases'][0]['status'] = 'failed'
        with self.assertRaises(ValueError):
            report.summarize(self.directory, self.manifest)

    def test_rejects_unverified_counts_or_different_database_settings(self):
        path = self.directory / self.manifest['cases'][0]['directory'] / 'metadata.json'
        metadata = json.loads(path.read_text())
        metadata['persistedEvents'] -= 1
        path.write_text(json.dumps(metadata))
        with self.assertRaises(ValueError):
            report.summarize(self.directory, self.manifest)
        metadata['persistedEvents'] += 1
        metadata['database']['fsync'] = 'off'
        path.write_text(json.dumps(metadata))
        with self.assertRaises(ValueError):
            report.summarize(self.directory, self.manifest)

    def test_rejects_non_finite_throughput(self):
        path = self.directory / self.manifest['cases'][0]['directory'] / 'jmh.json'
        result = json.loads(path.read_text())
        result[0]['primaryMetric']['score'] = float('nan')
        path.write_text(json.dumps(result))
        with self.assertRaises(ValueError):
            report.summarize(self.directory, self.manifest)

    def test_rejects_verbose_logging_that_would_distort_throughput(self):
        path = self.directory / self.manifest['cases'][0]['directory'] / 'metadata.json'
        metadata = json.loads(path.read_text())
        metadata['verboseStoreLogging'] = True
        path.write_text(json.dumps(metadata))
        with self.assertRaises(ValueError):
            report.summarize(self.directory, self.manifest)

    def test_chart_and_csv_use_the_same_recorded_event_rates(self):
        rows = report.summarize(self.directory, self.manifest)
        figure = report.create_chart(rows, self.manifest)
        from matplotlib import pyplot as plt
        self.addCleanup(lambda: plt.close(figure))
        self.assertEqual([bar.get_height() for bar in figure.axes[0].patches], [10.0, 150.0])
        self.assertEqual([bar.get_height() for bar in figure.axes[1].patches], [1000.0, 15000.0])
        self.assertTrue(all(axis.get_ylim()[0] == 0 for axis in figure.axes))
        self.assertTrue(any('PostgreSQL 15.14' in text.get_text() for text in figure.texts))
        report.write_report(self.directory, self.manifest)
        with (self.directory / 'comparison.csv').open() as stream:
            csv_rows = list(csv.DictReader(stream))
        self.assertEqual([float(row['events_per_second']) for row in csv_rows],
                         [row['events_per_second'] for row in rows])
        self.assertTrue((self.directory / 'comparison.png').read_bytes().startswith(b'\x89PNG'))
        self.assertIn('<svg', (self.directory / 'comparison.svg').read_text())
        self.assertIn('15.00', (self.directory / 'comparison.md').read_text())


if __name__ == '__main__':
    unittest.main()
