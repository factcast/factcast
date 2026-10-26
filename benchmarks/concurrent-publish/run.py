"""Build isolated release tags and compare their unconditional FactStore publication throughput."""

import argparse
import json
import os
from pathlib import Path
import shlex
import shutil
import signal
import subprocess
import sys
import tarfile
import tempfile

sys.dont_write_bytecode = True
from report import BENCHMARK, SUPPORTED_VERSIONS, VERSIONS, write_report

ROOT = Path(__file__).resolve().parents[2]
SOURCES = Path('factcast-store/src/test/java/org/factcast/store/benchmark')


def positive(value):
    number = int(value)
    if number < 1:
        raise argparse.ArgumentTypeError('must be positive')
    return number


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--versions', nargs='+', choices=SUPPORTED_VERSIONS, default=VERSIONS,
                        help='release tags in chart order; the first is the baseline')
    parser.add_argument('--publishers', type=positive, default=256)
    parser.add_argument('--batch-sizes', type=positive, nargs='+', default=[1, 100])
    parser.add_argument('--warmup-seconds', type=positive, default=60)
    parser.add_argument('--measurement-seconds', type=positive, default=120)
    parser.add_argument('--repetitions', type=positive, default=1)
    parser.add_argument('--postgres-image', default='postgres:15')
    parser.add_argument('--smoke', action='store_true', help='cap at 8 publishers, 2s warmup and 4s measurement')
    arguments = parser.parse_args(argv)
    if len(arguments.versions) < 2 or len(set(arguments.versions)) != len(arguments.versions):
        parser.error('select at least two distinct release versions')
    if arguments.smoke:
        arguments.publishers = min(arguments.publishers, 8)
        arguments.warmup_seconds = min(arguments.warmup_seconds, 2)
        arguments.measurement_seconds = min(arguments.measurement_seconds, 4)
    elif arguments.measurement_seconds < 120:
        parser.error('normal comparisons require at least 120 measured seconds; use --smoke for verification')
    if len(set(arguments.batch_sizes)) != len(arguments.batch_sizes):
        parser.error('batch sizes must be distinct')
    return arguments


def capture(command, cwd=ROOT):
    return subprocess.check_output(command, cwd=cwd, text=True, stderr=subprocess.STDOUT).strip()


def execute(command, log, cwd=ROOT, timeout=1800):
    displayed = list(map(str, command))
    if '-cp' in displayed:
        displayed[displayed.index('-cp') + 1] = '<release-classpath>'
    print(f'Running {shlex.join(displayed)}\nLog: {log}', flush=True)
    with Path(log).open('w') as stream:
        process = subprocess.Popen(command, cwd=cwd, stdout=stream, stderr=subprocess.STDOUT,
                                   start_new_session=True)
        try:
            code = process.wait(timeout=timeout)
        except (subprocess.TimeoutExpired, KeyboardInterrupt):
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
            raise
    if code:
        raise RuntimeError(f'Command failed ({code}); inspect {log}')


def prepare_release(version, destination, output):
    commit = capture(['git', 'rev-parse', f'{version}^{{commit}}'])
    archive = output / f'{version}.tar'
    with archive.open('wb') as stream:
        subprocess.run(['git', 'archive', '--format=tar', commit], cwd=ROOT, stdout=stream, check=True)
    destination.mkdir()
    with tarfile.open(archive) as stream:
        stream.extractall(destination, filter='data')
    archive.unlink()
    shutil.copytree(ROOT / SOURCES, destination / SOURCES, dirs_exist_ok=True)
    # The benchmark classes are compiled with the release's own JMH, Spring and store dependencies.
    execute(['mvn-lite', '--builder', 'singlethreaded', '-P=-not-central', '-f', str(destination / 'pom.xml'), '-pl', 'factcast-store', '-am', 'install', '-DskipUnitTests=true',
             '-DskipTests', '-DskipITs', '-Djacoco.skip=true', '-Dmaven.javadoc.skip=true'],
            output / f'build-{version}.log')
    classpath = output / f'classpath-{version}.txt'
    execute(['mvn-lite', '--builder', 'singlethreaded', '-f', str(destination / 'factcast-store/pom.xml'),
             'org.apache.maven.plugins:maven-dependency-plugin:3.11.0:build-classpath',
             '-DincludeScope=test', f'-Dmdep.outputFile={classpath}'],
            output / f'classpath-{version}.log')
    dependencies = classpath.read_text().strip()
    module = destination / 'factcast-store/target'
    return commit, os.pathsep.join([str(module / 'test-classes'), str(module / 'classes'), dependencies])


def main(argv=None):
    args = parse_args(argv)
    for command in ['git', 'java', 'docker', 'mvn-lite']:
        if not shutil.which(command):
            raise RuntimeError(f'Required executable is missing: {command}')
    # Fail before builds and measurements if chart rendering cannot be completed.
    os.environ.setdefault('MPLCONFIGDIR', '/tmp/agents/factcast-publish-matplotlib')
    import matplotlib
    capture(['docker', 'version', '--format', '{{.Server.Version}}'])
    base = Path('/tmp/agents')
    base.mkdir(parents=True, exist_ok=True)
    output = Path(tempfile.mkdtemp(prefix='factcast-publish-', dir=base))
    print(f'Results and isolated builds: {output}', flush=True)
    logging_configuration = output / 'logback.xml'
    shutil.copyfile(Path(__file__).with_name('logback.xml'), logging_configuration)
    try:
        capture(['docker', 'image', 'inspect', args.postgres_image])
    except subprocess.CalledProcessError:
        execute(['docker', 'pull', args.postgres_image], output / 'postgres-pull.log')
    image = json.loads(capture(['docker', 'image', 'inspect', args.postgres_image]))[0]
    digests = image.get('RepoDigests') or []
    if not digests:
        raise RuntimeError('PostgreSQL image must have a repository digest; pull a registry image first')
    digest = digests[0]
    manifest = {'schemaVersion': 1, 'smoke': args.smoke,
                'settings': {'publishers': args.publishers, 'batchSizes': args.batch_sizes,
                             'warmupSeconds': args.warmup_seconds,
                             'measurementSeconds': args.measurement_seconds, 'measurementIterations': 2,
                             'repetitions': args.repetitions, 'postgresImage': args.postgres_image,
                             'postgresDigest': digest, 'postgresImageId': image['Id'], 'heap': '1g',
                             'javaVersion': capture(['java', '-version']),
                             'host': capture(['uname', '-a']), 'availableCpus': os.cpu_count()},
                'versions': {}, 'cases': []}
    manifest_path = output / 'manifest.json'

    def save():
        manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')

    save()
    classpaths = {}
    for version in args.versions:
        commit, classpaths[version] = prepare_release(version, output / f'source-{version}', output)
        manifest['versions'][version] = commit
        save()
    for repetition in range(1, args.repetitions + 1):
        # Alternate release order on repeated passes to reduce consistent first-run bias.
        versions = args.versions if repetition % 2 else tuple(reversed(args.versions))
        for batch in args.batch_sizes:
            for version in versions:
                folder = output / f'batch-{batch}' / version / f'pass-{repetition}'
                folder.mkdir(parents=True)
                temporary = folder / 'tmp'
                temporary.mkdir()
                case = {'version': version, 'batchSize': batch, 'repetition': repetition,
                        'directory': str(folder.relative_to(output)), 'status': 'running'}
                manifest['cases'].append(case)
                save()
                jvm_arguments = ['-Xms1g', '-Xmx1g', f'-Djava.io.tmpdir={temporary}',
                                 f'-Dlogback.configurationFile={logging_configuration}',
                                 f'-Dbenchmark.version={version}',
                                 f'-Dbenchmark.commit={manifest["versions"][version]}',
                                 f'-Dbenchmark.postgres-image={digest}',
                                 f'-Dbenchmark.metadata={folder / "metadata.json"}',
                                 f'-Dbenchmark.batching={str(version == "0.12.5").lower()}']
                command = ['java', f'-Djava.io.tmpdir={temporary}', '-cp', classpaths[version],
                           'org.openjdk.jmh.Main', '^' + BENCHMARK + '$',
                           '-t', str(args.publishers), '-p', f'batchSize={batch}', '-f', '1',
                           '-wi', '1', '-w', f'{args.warmup_seconds}s', '-i', '2',
                           '-r', f'{args.measurement_seconds * 500}ms', '-bm', 'thrpt', '-tu', 's',
                           '-to', f'{args.measurement_seconds + 120}s', '-foe', 'true',
                           '-rf', 'json', '-rff', str(folder / 'jmh.json'),
                           '-jvmArgs', shlex.join(jvm_arguments)]
                try:
                    execute(command, folder / 'run.log', timeout=args.warmup_seconds + args.measurement_seconds + 600)
                    case['status'] = 'passed'
                except BaseException:
                    case['status'] = 'failed'
                    raise
                finally:
                    save()
    write_report(output, manifest)
    print((output / 'comparison.md').read_text(), flush=True)
    print(f'Chart: {output / "comparison.png"}\nVector chart: {output / "comparison.svg"}', flush=True)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, ValueError, OSError, subprocess.SubprocessError) as error:
        sys.exit(f'Benchmark stopped: {error}')
