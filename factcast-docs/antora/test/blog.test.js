'use strict'

const assert = require('node:assert/strict')
const { test } = require('node:test')
const { mkdtempSync, readFileSync, writeFileSync, existsSync, readdirSync, rmSync, mkdirSync } = require('node:fs')
const path = require('node:path')
const { spawnSync } = require('node:child_process')
const { runInNewContext } = require('node:vm')

const docsDir = path.resolve(__dirname, '../..')
mkdirSync('/tmp/agents', { recursive: true })
const fixtureDir = mkdtempSync('/tmp/agents/factcast-blog-test-')
process.on('exit', () => rmSync(fixtureDir, { recursive: true, force: true }))

function build(name, posts, options = {}, versions = []) {
  const output = path.join(fixtureDir, name)
  const playbook = {
    site: { title: 'FactCast Blog', url: 'https://example.org/preview/', start_page: 'docs:blog:index.adoc' },
    content: { sources: [{ url: path.resolve(docsDir, '..'), branches: 'HEAD', start_path: 'factcast-docs/src/main/asciidoc' }] },
    ui: {
      bundle: {
        url: 'https://gitlab.com/antora/antora-ui-default/-/jobs/artifacts/HEAD/raw/build/ui-bundle.zip?job=bundle-stable',
        snapshot: true,
      },
      supplemental_files: path.join(docsDir, 'supplemental-ui'),
    },
    antora: { extensions: [
      ...(posts === undefined ? [] : [{ require: path.join(__dirname, 'fixtures.js'),
        posts: Object.entries(posts).map(([filename, contents]) => ({ filename, contents })),
        versions,
      }]),
      { require: path.resolve(__dirname, '../extensions/blog.js'), page_size: 2, blog_only: true, ...options },
      { require: '@antora/lunr-extension' },
    ] },
    runtime: { cache_dir: '/tmp/agents/factcast-blog-antora-cache', log: { failure_level: 'warn' } },
    output: { clean: true },
  }
  const playbookPath = path.join(fixtureDir, `${name}.json`)
  writeFileSync(playbookPath, JSON.stringify(playbook))
  const result = spawnSync('mvn-lite', [
    '-f', path.join(docsDir, 'pom.xml'), 'antora',
    `-Dantora.playbook=${playbookPath}`, `-Dantora.output=${output}`,
  ], { encoding: 'utf8', timeout: 180000 })
  return { output, result, read: (file) => readFileSync(path.join(output, file), 'utf8') }
}

function requireSuccess(build) {
  assert.equal(build.result.status, 0, build.result.stdout + build.result.stderr)
}

function parseFeed(xml) {
  // Use a real XML parser to catch malformed escaping, not only string patterns.
  const parsed = spawnSync('python3', ['-c', `
import sys, json, xml.etree.ElementTree as ET
root = ET.fromstring(sys.stdin.read())
assert root.tag == 'rss' and root.attrib['version'] == '2.0'
channel = root.find('channel')
assert all(channel.findtext(name) for name in ['title', 'link', 'description'])
print(json.dumps({'link': channel.findtext('link'), 'items': [
    {name: item.findtext(name) for name in ['title', 'pubDate', 'description', 'link', 'guid']}
    for item in channel.findall('item')]}))
`], { input: xml, encoding: 'utf8' })
  assert.equal(parsed.status, 0, parsed.stderr)
  return JSON.parse(parsed.stdout)
}

function verifyLocalLinks(output) {
  for (const file of readdirSync(output, { recursive: true }).filter((name) => name.endsWith('.html'))) {
    const html = readFileSync(path.join(output, file), 'utf8')
    for (const [, href] of html.matchAll(/href="([^"#?]+)(?:[?#][^"]*)?"/g)) {
      if (/^(?:[a-z]+:|\/\/)/i.test(href)) continue
      const target = href.startsWith('/')
        ? path.join(output, href.replace(/^\/preview\//, ''))
        : path.resolve(output, path.dirname(file), decodeURIComponent(href))
      assert.ok(existsSync(target), `${file}: broken link ${href}`)
    }
  }
}

test('standalone samples build with ordering, links, metadata, RSS, search and no drafts', () => {
  // Keep fixtures independent of articles that contributors edit, rename or mark as drafts.
  const samples = {
    '2026-10-28-example.adoc': `= Sample: Writing a technical article
:page-blog-date: 2026-10-28
:page-blog-author: FactCast contributors
:page-blog-tags: Technical, Samples
:page-blog-summary: A sample technical article.

Sample content.
`,
    '2026-10-21-announcement.adoc': `= Sample: A short announcement
:page-blog-date: 2026-10-21
:page-blog-summary: A sample announcement.

Sample content.
`,
    '2026-10-14-release.adoc': `= Sample: Release news
:page-blog-date: 2026-10-14
:page-blog-tags: Releases, Samples
:page-blog-summary: A sample release article.

Sample content.
`,
    '2026-10-29-draft.adoc': '= Unpublished draft\n:page-blog-draft: true\n\nDraft content.\n',
  }
  const site = build('samples', samples)
  requireSuccess(site)
  const index = site.read('blog/index.html')
  assert.ok(index.indexOf('Sample: Writing a technical article') < index.indexOf('Sample: A short announcement'))
  assert.match(index, /2026-10-28/)
  assert.match(index, /FactCast contributors/)
  assert.match(index, /Technical/)
  assert.match(index, /A sample technical article/)
  assert.match(index, /page\/2\.html/)
  assert.match(site.read('blog/page/2.html'), /Sample: Release news/)
  const samplesTag = readdirSync(path.join(site.output, 'blog/tags')).find((name) => name.startsWith('samples-'))
  assert.match(site.read(`blog/tags/${samplesTag}/index.html`), /Sample: Release news/)
  const minimal = site.read('blog/posts/2026-10-21-announcement.html')
  assert.match(minimal, /2026-10-21/)
  assert.doesNotMatch(minimal, /By undefined|Tags: undefined/)
  assert.ok(!existsSync(path.join(site.output, 'blog/posts/2026-10-29-draft.html')))
  const search = site.read('search-index.js')
  assert.match(search, /2026-10-28-example/)
  assert.doesNotMatch(search, /2026-10-29-draft|Unpublished draft/)
  const feed = parseFeed(site.read('blog/feed.xml'))
  assert.equal(feed.link, 'https://example.org/preview/blog/index.html')
  assert.deepEqual(feed.items.map((item) => item.title), [
    'Sample: Writing a technical article', 'Sample: A short announcement', 'Sample: Release news',
  ])
  for (const item of feed.items) {
    assert.ok(item.description)
    assert.ok(Number.isFinite(Date.parse(item.pubDate)))
    assert.equal(item.guid, item.link)
    assert.ok(item.link.startsWith('https://example.org/preview/blog/posts/'))
    assert.ok(existsSync(path.join(site.output, new URL(item.link).pathname.replace('/preview/', ''))))
  }
  verifyLocalLinks(site.output)
})

test('pagination and category pages escape metadata and keep distinct category names', () => {
  const posts = Object.fromEntries([
    ['z.adoc', 'Old', '2026-01-01', 'C++, C#'],
    ['a.adoc', 'Newest & <example>', '2026-03-01', 'C++'],
    ['b.adoc', 'Middle', '2026-02-01', 'C++'],
    ['c.adoc', 'Second oldest', '2026-01-02', 'C#'],
  ].map(([filename, title, date, tags]) => [filename,
    `= ${title}\n:page-blog-date: ${date}\n:page-blog-draft: false\n:page-blog-tags: ${tags}\n:page-blog-author: A & <B>\n:page-blog-summary: Literal <script>alert(1)</script> & xref:missing.adoc[]\n\nBody.\n`,
  ]))
  posts['draft.adoc'] = '= Secret\n:page-blog-draft: true\n:page-aliases: leaked-draft.adoc\n\nSecret body.\n'
  const site = build('categories', posts)
  requireSuccess(site)
  const index = site.read('blog/index.html')
  assert.match(index, /&lt;script&gt;/)
  assert.doesNotMatch(index, /<script>alert\(1\)/)
  assert.match(index, /A &amp; &lt;B&gt;/)
  const categories = readdirSync(path.join(site.output, 'blog/tags'))
  assert.equal(categories.length, 2)
  const categoryPages = categories.map((name) => site.read(`blog/tags/${name}/index.html`))
  assert.ok(categoryPages.some((html) => html.includes('page/2.html')))
  const search = site.read('search-index.js')
  const lunr = require(path.join(site.output, '_/js/vendor/lunr.js'))
  let searchData
  runInNewContext(search, { lunr, antoraSearch: { initSearch: (_, data) => { searchData = data } } })
  const indexedUrls = Object.values(searchData.store.documents).map((doc) => doc.url).sort()
  assert.deepEqual(indexedUrls, ['a', 'b', 'c', 'z'].map((name) => `/blog/posts/${name}.html`),
    'only published articles belong in search; overview, categories and pagination stay excluded')
  const feed = parseFeed(site.read('blog/feed.xml'))
  assert.deepEqual(feed.items.map((item) => item.title), ['Newest & <example>', 'Middle', 'Second oldest', 'Old'])
  assert.equal(feed.items[0].description, 'Literal &lt;script&gt;alert(1)&lt;/script&gt; &amp; xref:missing.adoc[]')
  assert.doesNotMatch(site.read('search-index.js'), /Secret body|draft\.html/)
  assert.ok(!existsSync(path.join(site.output, 'blog/leaked-draft.html')))
  assert.doesNotMatch(site.read('sitemap.xml'), /draft/)
  verifyLocalLinks(site.output)
})

test('an empty blog builds with an empty index and a valid empty feed', () => {
  const site = build('empty', {})
  requireSuccess(site)
  assert.match(site.read('blog/index.html'), /No published posts yet/)
  assert.deepEqual(parseFeed(site.read('blog/feed.xml')).items, [])
  assert.ok(!existsSync(path.join(site.output, 'blog/page/2.html')))
  verifyLocalLinks(site.output)
})

test('all documentation versions share only the latest blog, including releases without a blog module', () => {
  const site = build('multi-release', {
    'current.adoc': '= Current article\n:page-blog-date: 2026-10-28\n:page-blog-summary: Current blog content.\n\nimage::concurrent-unconditional-publishing-benchmark.png[Benchmark]\n',
    'draft.adoc': '= Secret draft\n:page-blog-draft: true\n:page-aliases: secret.adoc\n\nUnpublished body.\n',
  }, { blog_only: false }, [{ version: '0.12.6' }, { version: '0.11.0', without_blog: true }])
  requireSuccess(site)
  for (const version of ['latest', '0.12.6', '0.11.0']) {
    const page = site.read(`docs/${version}/About/index.html`)
    assert.match(page, /href="(?:\.\.\/){3}blog\/index.html">\s*Blog\s*<\/a>/)
    assert.match(page, /href="(?:\.\.\/){3}blog\/posts\/current.html"[^>]*>Current blog article<\/a>/)
    assert.equal((page.match(/class="nav-link"[^>]*>Blog<\/a>/g) || []).length, 1)
    assert.ok(!existsSync(path.join(site.output, `docs/${version}/blog`)))
  }
  const feed = parseFeed(site.read('blog/feed.xml'))
  assert.deepEqual(feed.items.map(({ title }) => title), ['Current article'])
  const search = site.read('search-index.js')
  assert.doesNotMatch(search, /Stale release|Secret draft|Unpublished body|docs\/(?:0.12.6|0.11.0)\/blog/)
  const lunr = require(path.join(site.output, '_/js/vendor/lunr.js'))
  let searchData
  runInNewContext(search, { lunr, antoraSearch: { initSearch: (_, data) => { searchData = data } } })
  const searchIndex = lunr.Index.load(searchData.index)
  const results = searchIndex.search('keyword:blog').map(({ ref }) => searchData.store.documents[ref.split('-')[0]].url)
  assert.deepEqual([...new Set(results.filter((url) => url.includes('/posts/')))], ['/blog/posts/current.html'])
  assert.doesNotMatch(site.read('sitemap-docs.xml'), /\/blog\//)
  assert.doesNotMatch(site.read('sitemap-blog.xml'), /Stale|draft|secret|\/docs\//)
  assert.ok(existsSync(path.join(site.output, 'blog/_images/concurrent-unconditional-publishing-benchmark.png')))
  verifyLocalLinks(site.output)
})

test('invalid published metadata fails the build with an actionable error', () => {
  const site = build('invalid', {
    'invalid.adoc': '= Invalid\n:page-blog-date: 2026-02-30\n:page-blog-summary: Invalid calendar date.\n\nBody.\n',
  })
  assert.notEqual(site.result.status, 0)
  // mvn-lite summarizes failures; inspect its retained log for the diagnostic.
  const logPath = site.result.stdout.match(/Full Maven log:\s+(\S+)/)?.[1]
  assert.match(logPath ? readFileSync(logPath, 'utf8') : site.result.stdout, /invalid.adoc.*page-blog-date/)
})

test('a metadata category stays plain text even if it resembles an AsciiDoc macro', () => {
  const site = build('literal-category', {
    'literal.adoc': '= Literal category\n:page-blog-date: 2026-01-01\n:page-blog-summary: Plain metadata.\n:page-blog-tags: xref:missing.adoc[]\n\nBody.\n',
  })
  requireSuccess(site)
  const category = readdirSync(path.join(site.output, 'blog/tags'))[0]
  assert.match(site.read(`blog/tags/${category}/index.html`), /Tag: xref:missing.adoc\[\]/)
  verifyLocalLinks(site.output)
})

test('invalid page sizes are rejected before generation', () => {
  const extension = require('../extensions/blog')
  for (const pageSize of [0, -1, 1.5, '2']) {
    assert.throws(() => extension.register.call({}, { config: { pageSize } }), /page_size/)
  }
})

test('the documentation playbook builds docs and blog with shared navigation and search', () => {
  const output = path.join(fixtureDir, 'integrated')
  const result = spawnSync('mvn-lite', [
    '-f', path.join(docsDir, 'pom.xml'), 'antora',
    `-Dantora.output=${output}`,
  ], { encoding: 'utf8', timeout: 180000 })
  requireSuccess({ result })
  assert.ok(existsSync(path.join(output, 'docs/latest/About/index.html')))
  assert.ok(existsSync(path.join(output, 'blog/index.html')))
  const read = (file) => readFileSync(path.join(output, file), 'utf8')
  assert.match(read('sitemap.xml'), /sitemap-blog\.xml/)
  assert.match(read('sitemap.xml'), /sitemap-docs\.xml/)
  assert.doesNotMatch(read('sitemap-blog.xml'), /\/docs\/latest\//)
  assert.doesNotMatch(read('sitemap-docs.xml'), /https:\/\/docs.factcast.org\/blog\//)
  const docs = read('docs/latest/About/index.html')
  const blog = read('blog/index.html')
  assert.match(docs, /href="[^\"]*blog\/index.html">\s*Blog\s*<\/a>/)
  assert.match(blog, /href="[^\"]*docs\/latest\/About\/index.html">\s*About\s*<\/a>/)
  const search = read('search-index.js')
  assert.match(search, /About\/index/)
  const lunr = require(path.join(output, '_/js/vendor/lunr.js'))
  let searchData
  runInNewContext(search, {
    lunr,
    antoraSearch: { initSearch: (_, data) => { searchData = data } },
  })
  assert.deepEqual(Object.keys(searchData.store.componentVersions), ['docs/latest'])
  const blogPages = Object.values(searchData.store.documents).filter((doc) => doc.url.startsWith('/blog/'))
  assert.ok(blogPages.length > 0)
  assert.ok(blogPages.every((doc) => doc.component === 'docs' && doc.version === 'latest'))
  assert.ok(blogPages.every((doc) => doc.url.startsWith('/blog/posts/')), 'only blog articles should be indexed')
  const searchIndex = lunr.Index.load(searchData.index)
  const feed = parseFeed(read('blog/feed.xml'))
  const publishedPosts = feed.items.map((item) => new URL(item.link).pathname).sort()
  // Query the emitted index: stored URLs alone do not prove that posts are searchable.
  const indexedPosts = searchIndex.search('keyword:blog').map(({ ref }) =>
    searchData.store.documents[ref.split('-')[0]].url).filter((url) => url.startsWith('/blog/posts/'))
  assert.deepEqual([...new Set(indexedPosts)].sort(), publishedPosts)
  assert.equal(feed.link, 'https://docs.factcast.org/blog/index.html')
})
