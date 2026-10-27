"""Validate a complete comparison and render its recorded measurements."""

import csv
import json
import math
import os
from pathlib import Path
import statistics
import sys

sys.dont_write_bytecode = True
BENCHMARK = 'org.factcast.store.benchmark.ConcurrentUnconditionalPublishBenchmark.publish'
VERSIONS = ('0.9.15', '0.12.5')
SUPPORTED_VERSIONS = ('0.9.15', '0.11.2', '0.12.5', 'current')
BATCHING_VERSIONS = ('0.12.5', 'current')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def seconds(value):
    number, unit = value.split()
    return float(number) * {'s': 1, 'ms': .001, 'us': .000001, 'ns': .000000001,
                          'min': 60}[unit]


def release_versions(manifest):
    versions = tuple(manifest['versions'])
    require(versions and all(version in SUPPORTED_VERSIONS for version in versions),
            'Select supported releases in baseline-first order')
    return versions


def publisher_counts(manifest):
    counts = manifest['settings']['publishers']
    # Original reports recorded one integer; retain support for regenerating those artifacts.
    counts = [counts] if isinstance(counts, int) else counts
    require(counts and all(isinstance(count, int) and count > 0 for count in counts)
            and len(set(counts)) == len(counts), 'Publisher counts must be positive and distinct')
    return counts


def summarize(directory, manifest):
    settings = manifest['settings']
    versions = release_versions(manifest)
    counts = publisher_counts(manifest)
    require(manifest['smoke'] or settings['measurementSeconds'] >= 120,
            'A performance comparison requires at least 120 measured seconds per case')
    expected = {(version, batch, publishers, repetition)
                for version in versions for batch in settings['batchSizes']
                for publishers in counts
                for repetition in range(1, settings['repetitions'] + 1)}
    observed = set()
    measurements = {}
    database_version = None
    for case in manifest['cases']:
        publishers = case.get('publishers', counts[0] if len(counts) == 1 else None)
        key = (case['version'], case['batchSize'], publishers, case['repetition'])
        require(case['status'] == 'passed' and key in expected and key not in observed,
                f'Failed, unexpected, or duplicated case: {key}')
        observed.add(key)
        folder = (Path(directory) / case['directory']).resolve()
        require(folder.is_relative_to(Path(directory).resolve()), 'Case path escapes result directory')
        results = json.loads((folder / 'jmh.json').read_text())
        require(len(results) == 1, f'Expected one JMH result for {key}')
        result = results[0]
        metadata = json.loads((folder / 'metadata.json').read_text())
        require(result['benchmark'] == BENCHMARK and result['params']['batchSize'] == str(key[1])
                and result['threads'] == publishers, f'Wrong workload for {key}')
        require(result['measurementIterations'] == settings['measurementIterations']
                and math.isclose(seconds(result['measurementTime']) * result['measurementIterations'],
                                 settings['measurementSeconds'])
                and result['warmupIterations'] == 1
                and math.isclose(seconds(result['warmupTime']), settings['warmupSeconds']),
                f'Wrong measurement or warmup duration for {key}')
        require(metadata['version'] == key[0] and metadata['commit'] == manifest['versions'][key[0]]
                and metadata['batchSize'] == key[1] and metadata['publishers'] == publishers,
                f'Wrong release metadata for {key}')
        require(metadata['validationEnabled'] is False
                and metadata['schemaRegistryConfigured'] is False
                and metadata['batchingEnabled'] == (key[0] in BATCHING_VERSIONS
                                                    and settings.get('publishBatching', True)),
                f'Wrong validation or publish batching configuration for {key}')
        if metadata['batchingEnabled']:
            require(metadata.get('publishMaxBatchSize', 500) == settings.get('publishMaxBatchSize', 500),
                    f'Concurrent publication batch limit differs for {key}')
        require(metadata['poolSize'] == 64 and metadata['reWriteBatchedInserts'] is True
                and metadata['payloadBytes'] == 256, f'Inconsistent JDBC configuration or payload for {key}')
        require(metadata['verboseStoreLogging'] is False, f'Verbose store logging was enabled for {key}')
        require(metadata['acknowledgedEvents'] > 0
                and metadata['acknowledgedEvents'] == metadata['persistedEvents'],
                f'Persisted events do not match acknowledgements for {key}')
        require(metadata['postgresImage'] == settings['postgresDigest'],
                f'PostgreSQL image differs for {key}')
        database = metadata['database']
        require(all(database[name] == 'on' for name in
                    ('fsync', 'synchronous_commit', 'full_page_writes')),
                f'PostgreSQL durability is disabled for {key}')
        database_version = database_version or database['server_version']
        require(database['server_version'] == database_version, 'PostgreSQL versions differ')
        metric = result['primaryMetric']
        rate = float(metric['score'])
        require(metric['scoreUnit'] == 'ops/s' and math.isfinite(rate) and rate > 0,
                f'Invalid throughput for {key}')
        measurements.setdefault(key[:3], []).append(rate)
    require(observed == expected, 'Comparison is incomplete; all versions, workloads and repetitions are required')
    rows = []
    for batch, publishers in [(batch, count) for batch in settings['batchSizes'] for count in counts]:
        baseline = statistics.median(measurements[(versions[0], batch, publishers)])
        for version in versions:
            rate = statistics.median(measurements[(version, batch, publishers)])
            row = {'publishers': publishers, 'batch_size': batch, 'version': version,
                   'publications_per_second': rate, 'events_per_second': rate * batch,
                   'baseline_version': versions[0], 'ratio_to_baseline': rate / baseline,
                   'repetitions': settings['repetitions'], 'postgres_version': database_version}
            # Preserve the original CSV column when regenerating existing 0.9.15 comparisons.
            if versions[0] == '0.9.15':
                row['ratio_to_0_9_15'] = row['ratio_to_baseline']
            rows.append(row)
    return rows


def create_chart(rows, manifest):
    # Keep plotting caches outside the checkout, including when regenerating a report.
    os.environ.setdefault('MPLCONFIGDIR', '/tmp/agents/factcast-publish-matplotlib')
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    from matplotlib.ticker import FuncFormatter

    batches = manifest['settings']['batchSizes']
    counts = publisher_counts(manifest)
    workloads = [(batch, count) for batch in batches for count in counts]
    versions = release_versions(manifest)
    single = len(workloads) == 1
    figure, axes = plt.subplots(1, len(workloads),
                               figsize=(9, 6.3) if single else (5.5 * len(workloads), 5.8), squeeze=False)
    figure.set_facecolor('#f4f7fa')
    for axis, (batch, publishers) in zip(axes[0], workloads):
        selected = [next(row for row in rows if row['batch_size'] == batch
                         and row['publishers'] == publishers and row['version'] == version)
                    for version in versions]
        values = [row['events_per_second'] for row in selected]
        colors = ['#8b9db5', '#008f83'] if len(versions) == 2 else ['#8b9db5', '#337fa5', '#008f83']
        bars = axis.bar(versions, values, width=.54, color=colors, zorder=3)
        axis.bar_label(bars, labels=[f'{value:,.0f}' for value in values], padding=8,
                       fontsize=12, fontweight='bold', color='#182c43')
        # A three-release comparison needs room for two ratio lines above the bar labels.
        axis.set_ylim(0, max(values) * (1.45 if len(versions) > 2 else 1.28))
        title = (f'{publishers:,} publishers' if len(counts) > 1 else
                 f'{batch} event{"s" if batch != 1 else ""} per operation')
        if len(counts) > 1 and len(batches) > 1:
            title += f' · {batch} events per operation'
        axis.set_title(title,
                       loc='left', fontsize=14, fontweight='bold', pad=22, color='#182c43')
        gain = ('' if len(versions) == 1 else
                f'{selected[1]["ratio_to_baseline"]:.2f}× throughput' if len(versions) == 2 else
                '\n'.join(f'{row["version"]}: {row["ratio_to_baseline"]:.2f}× baseline'
                          for row in selected[1:]))
        axis.text(.98, .95, gain,
                  transform=axis.transAxes, ha='right', va='top', color='#00766c', fontweight='bold')
        axis.set_ylabel('Committed events / second')
        axis.yaxis.set_major_formatter(FuncFormatter(lambda value, _: f'{value:,.0f}'))
        axis.grid(axis='y', color='#dde4ec', linewidth=.8, zorder=0)
        axis.tick_params(axis='both', length=0, pad=8)
        for spine in axis.spines.values():
            spine.set_visible(False)
    smoke = 'SMOKE ONLY — ' if manifest['smoke'] else ''
    figure.suptitle(f'{smoke}Concurrent unconditional publication', x=.06, ha='left',
                   fontsize=19, fontweight='bold', color='#182c43')
    settings = manifest['settings']
    batching_versions = ', '.join(version for version in versions if version in BATCHING_VERSIONS)
    if batching_versions:
        batching = (f'{batching_versions} concurrent batching enabled (max {settings.get("publishMaxBatchSize", 500):,})'
                    if settings.get('publishBatching', True) else f'{batching_versions} concurrent batching disabled')
    else:
        batching = 'Concurrent publish batching unavailable in these releases'
    scales = '' if single else ' · separate linear scales'
    workload = (f'{counts[0]:,} concurrent publishers' if len(counts) == 1 else
                '1 event per publication' if batches == [1] else 'Publisher concurrency comparison')
    caption = (f'{workload} · {settings["measurementSeconds"]}s measured · '
               f'{settings["warmupSeconds"]}s warmup\n'
               f'PostgreSQL {rows[0]["postgres_version"].split()[0]} · validation disabled · durable writes{scales}\n'
               f'{batching} · baseline {versions[0]}\n'
               + ('Smoke results verify the harness; do not use for performance conclusions.' if manifest['smoke']
                  else f'Median of {settings["repetitions"]} pass(es); labels show events/second.'))
    figure.text(.06, .035, caption, fontsize=9, color='#52647a', linespacing=1.6)
    figure.subplots_adjust(left=.14 if single else .09, right=.96 if single else .97,
                           top=.78 if single else .80, bottom=.30, wspace=.30)
    return figure


def write_report(directory, manifest):
    directory = Path(directory)
    rows = summarize(directory, manifest)
    baseline = release_versions(manifest)[0]
    with (directory / 'comparison.csv').open('w', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    title = 'SMOKE ONLY — harness verification' if manifest['smoke'] else 'Concurrent unconditional publication'
    lines = [f'# {title}', '',
             f'| Publishers | Events / publish | Version | Publications / s | Events / s | Relative to {baseline} |',
             '| ---: | ---: | --- | ---: | ---: | ---: |']
    for row in rows:
        lines.append(f'| {row["publishers"]} | {row["batch_size"]} | {row["version"]} | '
                     f'{row["publications_per_second"]:,.2f} | {row["events_per_second"]:,.2f} | '
                     f'{row["ratio_to_baseline"]:.2f}× |')
    lines += ['', '![Throughput comparison](comparison.png)', '',
              'Rates are medians across repetitions. Ratios compare matching publisher counts and application batch sizes.',
              'Raw JMH measurements and verified database metadata are retained beside this report.', '',
              '```json', json.dumps(manifest['settings'], indent=2), '```', '']
    (directory / 'comparison.md').write_text('\n'.join(lines))
    figure = create_chart(rows, manifest)
    try:
        figure.savefig(directory / 'comparison.png', dpi=180, facecolor=figure.get_facecolor())
        figure.savefig(directory / 'comparison.svg', facecolor=figure.get_facecolor())
    finally:
        import matplotlib.pyplot as plt
        plt.close(figure)
    return rows


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    arguments = parser.parse_args()
    try:
        write_report(arguments.directory, json.loads((arguments.directory / 'manifest.json').read_text()))
    except (ValueError, KeyError, OSError) as error:
        parser.exit(1, f'Cannot report an invalid comparison: {error}\n')
    print(arguments.directory / 'comparison.md')
