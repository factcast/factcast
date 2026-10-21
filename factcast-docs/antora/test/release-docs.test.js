'use strict'

const assert = require('node:assert/strict')
const { EventEmitter } = require('node:events')
const { test } = require('node:test')
const vm = require('node:vm')
const releaseVersions = require('../extensions/release-versions')
const latestRedirects = require('../extensions/latest-redirects')

function aggregateReleaseDocs(buckets) {
  const generator = new EventEmitter()
  let result
  generator.updateVariables = ({ contentAggregate }) => { result = contentAggregate }
  releaseVersions.register.call(generator)
  generator.emit('contentAggregated', { contentAggregate: buckets })
  return result
}

function releaseOrigin(refname, version = 'latest', reftype = 'tag') {
  return { refname, reftype, descriptor: { name: 'docs', version, nav: ['modules/ROOT/nav.adoc'] } }
}

function sourceFile(origin, contents) {
  return { path: 'modules/ROOT/pages/index.adoc', src: { origin }, contents: Buffer.from(contents) }
}

test('legacy releases with identical page paths become independent versions with their own navigation', () => {
  const first = releaseOrigin('0.12.5')
  const second = releaseOrigin('0.12.6')
  const firstPage = sourceFile(first, '= First release')
  const secondPage = sourceFile(second, '= Second release')

  const result = aggregateReleaseDocs([
    { name: 'docs', version: 'latest', origins: [first, second], files: [firstPage, secondPage] },
  ])

  assert.deepEqual(result.map(({ version }) => version), ['0.12.5', '0.12.6'])
  assert.deepEqual(result.map(({ files }) => files), [[firstPage], [secondPage]])
  assert.equal(result[0].files[0].contents.toString(), '= First release')
  assert.equal(result[1].files[0].contents.toString(), '= Second release')
  assert.equal(result[0].nav.origin, first)
  assert.equal(result[1].nav.origin, second)
  assert.notEqual(result[0].nav, result[1].nav)
})

test('a new numeric release joins the legacy versions without removing them', () => {
  const origins = [releaseOrigin('0.12.5'), releaseOrigin('0.12.6'), releaseOrigin('0.13.0', '0.13.0')]
  const result = aggregateReleaseDocs(origins.map((origin) => ({
    ...origin.descriptor, origins: [origin], files: [sourceFile(origin, origin.refname)],
  })))

  assert.deepEqual(result.map(({ version }) => version), ['0.12.5', '0.12.6', '0.13.0'])
  assert.deepEqual(result.map(({ files }) => files[0].contents.toString()), ['0.12.5', '0.12.6', '0.13.0'])
})

test('prereleases, development branches and releases before Antora are excluded', () => {
  const origins = [
    releaseOrigin('0.12.4'),
    releaseOrigin('0.12.7-RC'),
    releaseOrigin('0.13.0-SNAPSHOT'),
    releaseOrigin('main', 'main', 'branch'),
    releaseOrigin('0.12.6'),
  ]
  const result = aggregateReleaseDocs([
    { name: 'docs', version: 'latest', origins, files: origins.map((origin) => sourceFile(origin, origin.refname)) },
  ])

  assert.deepEqual(result.map(({ version }) => version), ['0.12.6'])
})

test('a mislabelled future release fails instead of silently changing its identity', () => {
  const origin = releaseOrigin('0.12.7')
  assert.throws(() => aggregateReleaseDocs([
    { ...origin.descriptor, origins: [origin], files: [] },
  ]), /0\.12\.7.*version/)
})

test('an empty release selection fails instead of publishing an empty site', () => {
  assert.throws(() => aggregateReleaseDocs([]), /No.*release/)
})

function publishedFile(version, relative, contents = '<h1>Page</h1>', family = 'page') {
  const path = `docs/${version}/${relative}`
  return {
    src: { component: 'docs', version, family },
    out: { path },
    pub: { url: `/${path}` },
    contents: Buffer.from(contents),
  }
}

function generateLatestFiles(files, versions = ['0.12.6', '0.12.5']) {
  const generated = []
  const generator = new EventEmitter()
  latestRedirects.register.call(generator)
  generator.emit('beforePublish', {
    playbook: { site: { url: 'https://docs.factcast.org' } },
    contentCatalog: {
      getComponents: () => [{ name: 'docs', versions: versions.map((version) => ({ version })) }],
      getFiles: () => files,
    },
    siteCatalog: { addFile: (file) => generated.push(file) },
  })
  return generated
}

test('latest URLs redirect to the matching newest page and retain query and fragment', () => {
  const files = [
    publishedFile('0.12.5', 'About/index.html'),
    publishedFile('0.12.6', 'About/index.html'),
  ]
  const redirects = generateLatestFiles(files)

  assert.deepEqual(redirects.map(({ out }) => out.path), ['docs/latest/About/index.html'])
  const html = redirects[0].contents.toString()
  assert.match(html, /rel="canonical" href="https:\/\/docs.factcast.org\/docs\/0.12.6\/About\/index.html"/)
  const script = html.match(/<script>(.*?)<\/script>/s)[1]
  let destination
  vm.runInNewContext(script, {
    location: { search: '?q=cache', hash: '#snapshots', replace: (url) => { destination = url } },
  })
  const resolved = new URL(destination, 'https://docs.factcast.org/docs/latest/About/index.html')
  assert.equal(resolved.href, 'https://docs.factcast.org/docs/0.12.6/About/index.html?q=cache#snapshots')
  assert.equal(files[1].out.path, 'docs/0.12.6/About/index.html')
})

test('the refresh fallback only runs without JavaScript so it cannot discard query strings and anchors', () => {
  const [redirect] = generateLatestFiles([publishedFile('0.12.6', 'About/index.html')])
  assert.match(redirect.contents.toString(), /<noscript>\s*<meta http-equiv="refresh"[^>]+>\s*<\/noscript>/)
})

test('latest also preserves component entry points and existing image URLs', () => {
  const entry = publishedFile('0.12.6', 'index.html', '', 'alias')
  const image = publishedFile('0.12.6', '_images/example.png', 'image bytes', 'image')
  const files = generateLatestFiles([entry, image])

  assert.deepEqual(files.map(({ out }) => out.path), ['docs/latest/index.html', 'docs/latest/_images/example.png'])
  assert.deepEqual(files[1].contents, image.contents)
  assert.match(files[0].contents.toString(), /location.replace/)
})

test('adding a newer release moves latest without changing numbered pages', () => {
  const pages = ['0.12.5', '0.12.6', '0.13.0'].map((version) => publishedFile(version, 'About/index.html'))
  const files = generateLatestFiles(pages, ['0.13.0', '0.12.6', '0.12.5'])
  assert.equal(files.length, 1)
  assert.match(files[0].contents.toString(), /0\.13\.0\/About\/index.html/)
  assert.deepEqual(pages.map(({ out }) => out.path), [
    'docs/0.12.5/About/index.html', 'docs/0.12.6/About/index.html', 'docs/0.13.0/About/index.html',
  ])
})

test('latest entry points redirect straight to their target so a second redirect cannot lose anchors', () => {
  const entry = publishedFile('0.12.6', 'index.html', '', 'alias')
  entry.rel = publishedFile('0.12.6', 'landing.html')
  const files = generateLatestFiles([entry])
  assert.match(files[0].contents.toString(), /0\.12\.6\/landing\.html/)
  assert.doesNotMatch(files[0].contents.toString(), /0\.12\.6\/index\.html/)
})

test('a page with a preferred alias still redirects to the page rather than back to its alias', () => {
  const page = publishedFile('0.12.6', 'About/index.html')
  const alias = publishedFile('0.12.6', 'index.html', '', 'alias')
  page.rel = alias
  alias.rel = page
  const files = generateLatestFiles([page, alias])
  assert.equal(files.length, 2)
  for (const file of files) {
    assert.match(file.contents.toString(), /0\.12\.6\/About\/index\.html/)
  }
})

test('unpublished source files do not get latest URLs', () => {
  const page = publishedFile('0.12.6', 'private.html')
  delete page.out
  assert.deepEqual(generateLatestFiles([page]), [])
})

test('redirect HTML encodes unusual page names without permitting script injection', () => {
  const page = publishedFile('0.12.6', 'a<&".html')
  const html = generateLatestFiles([page])[0].contents.toString()
  assert.equal((html.match(/<script>/g) || []).length, 1)
  assert.match(html, /a&lt;&amp;&quot;\.html/)
  assert.doesNotMatch(html.match(/<script>(.*?)<\/script>/s)[1], /</)
})
