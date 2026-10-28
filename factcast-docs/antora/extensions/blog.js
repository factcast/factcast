'use strict'

const path = require('node:path').posix
const { createHash } = require('node:crypto')

module.exports.register = function ({ config }) {
  const pageSize = config.pageSize ?? 10
  if (!Number.isSafeInteger(pageSize) || pageSize < 1) {
    throw new Error('Blog page_size must be a positive integer')
  }
  const posts = []
  let index
  let baseUrl

  this.once('contentClassified', ({ contentCatalog, playbook }) => {
    const component = contentCatalog.getComponentVersion('blog', '')
    if (!component) return
    baseUrl = publishingUrl(playbook.site.url)
    const { loadAsciiDoc } = this.getFunctions()
    const pages = contentCatalog.findBy({ component: 'blog', version: '', module: 'ROOT', family: 'page' })
    for (const page of pages) {
      if (!page.out || !page.src.relative.startsWith('posts/')) continue
      const document = loadAsciiDoc(page, contentCatalog, { headerOnly: true })
      const draft = document.getAttribute('page-blog-draft')
      if (draft !== undefined && draft !== 'true' && draft !== 'false') {
        throw new Error(`${page.src.relative}: page-blog-draft must be true or false`)
      }
      if (draft === 'true') {
        // Remove drafts before conversion so neither HTML, aliases nor search can expose them.
        contentCatalog.removeFile(page)
        continue
      }
      posts.push(readPost(page, document))
    }
    posts.sort((a, b) => b.date.localeCompare(a.date) || a.page.src.relative.localeCompare(b.page.src.relative))
    const tags = [...new Set(posts.flatMap((post) => post.tags))].sort()
    index = contentCatalog.getById({
      component: 'blog', version: '', module: 'ROOT', family: 'page', relative: 'index.adoc',
    })
    if (!index) throw new Error('Blog requires modules/ROOT/pages/index.adoc')
    createListing(contentCatalog, posts, 'index.adoc', 'FactCast Blog', tags, pageSize, index)
    for (const tag of tags) {
      createListing(contentCatalog, posts.filter((post) => post.tags.includes(tag)),
        `tags/${tagSlug(tag)}/index.adoc`, `Tag: ${tag}`, tags, pageSize)
    }
  })

  this.once('documentsConverted', ({ siteCatalog }) => {
    if (!index) return
    for (const post of posts) {
      post.page.contents = Buffer.concat([Buffer.from(metadata(post, post.page)), post.page.contents])
    }
    // Publish through Antora's catalog so the configured output destination is respected.
    siteCatalog.addFile({
      contents: Buffer.from(rss(posts, index, baseUrl)),
      out: { path: 'blog/feed.xml' },
    })
  })
}

function readPost(page, document) {
  const read = (name, required = false) => {
    const value = decodeHeader(String(document.getAttribute(name) ?? '')).trim()
    if ((required && !value) || /[\r\n\u0000-\u0008\u000b\u000c\u000e-\u001f\ufffe\uffff]/u.test(value)) {
      throw new Error(`${page.src.relative}: ${name} must contain single-line plain text`)
    }
    return value
  }
  if (!/^posts\/(?:[A-Za-z0-9_-]+\/)*[A-Za-z0-9][A-Za-z0-9._-]*\.adoc$/.test(page.src.relative)) {
    throw new Error(`${page.src.relative}: use URL-safe post filenames (letters, digits, dots, hyphens, underscores)`)
  }
  const title = read('doctitle', true)
  const date = read('page-blog-date', true)
  const timestamp = Date.parse(`${date}T00:00:00Z`)
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !Number.isFinite(timestamp) ||
      new Date(timestamp).toISOString().slice(0, 10) !== date) {
    throw new Error(`${page.src.relative}: page-blog-date must be a valid YYYY-MM-DD calendar date`)
  }
  return {
    page, title, date,
    summary: read('page-blog-summary', true),
    author: read('page-blog-author'),
    tags: [...new Set(read('page-blog-tags').split(',').map((tag) => tag.trim()).filter(Boolean))],
  }
}

function decodeHeader(value) {
  // Asciidoctor escapes header text; undo that once before encoding for HTML or XML.
  return value.replace(/&(amp|lt|gt);/g, (_, entity) => ({ amp: '&', lt: '<', gt: '>' })[entity])
}

function publishingUrl(siteUrl) {
  let url
  try { url = new URL(siteUrl) } catch { throw new Error('Blog requires an absolute HTTP(S) site.url') }
  if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
    throw new Error('Blog site.url must be HTTP(S), without credentials, query or fragment')
  }
  if (!url.pathname.endsWith('/')) url.pathname += '/'
  return url
}

function tagSlug(tag) {
  // Keep punctuation-distinct categories such as C++ and C# separate, with stable URLs.
  const label = tag.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'tag'
  return `${label}-${createHash('sha256').update(tag).digest('hex').slice(0, 12)}`
}

function escape(value) {
  return String(value).replace(/[&<>"']/g, (char) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&apos;',
  })[char])
}

function relativeUrl(from, url) {
  const relative = path.relative(from.out.dirname, url.replace(/^\//, '')) || '.'
  return url.endsWith('/') ? `${relative}/` : relative
}

function link(from, url, label) {
  return `<a href="${escape(relativeUrl(from, url))}">${escape(label)}</a>`
}

function metadata(post, from) {
  const author = post.author ? ` <span>By ${escape(post.author)}</span>` : ''
  const tags = post.tags.length
    ? `<p>Tags: ${post.tags.map((tag) => link(from, `/blog/tags/${tagSlug(tag)}/index.html`, tag)).join(', ')}</p>`
    : ''
  return `<div class="blog-metadata"><p><time datetime="${post.date}">${post.date}</time>${author}</p>` +
    `<p>${escape(post.summary)}</p>${tags}</div>\n`
}

function createListing(catalog, posts, relative, title, tags, pageSize, existing) {
  const pageCount = Math.max(1, Math.ceil(posts.length / pageSize))
  const directory = path.dirname(relative)
  const pages = Array.from({ length: pageCount }, (_, i) => {
    if (i === 0 && existing) return existing
    return catalog.addFile({
      src: {
        component: 'blog', version: '', module: 'ROOT', family: 'page',
        relative: i === 0 ? relative : path.join(directory, 'page', `${i + 1}.adoc`),
      },
      contents: Buffer.from(`= FactCast Blog\n:page-pagination: false\n\nblog-index::[]\n`),
    })
  })
  pages.forEach((page, number) => {
    const categories = tags.length ? '<nav aria-label="Blog categories">Categories: ' +
      [link(page, '/blog/index.html', 'All posts'),
        ...tags.map((tag) => link(page, `/blog/tags/${tagSlug(tag)}/index.html`, tag))].join(' | ') + '</nav>' : ''
    const articles = posts.slice(number * pageSize, (number + 1) * pageSize).map((post) =>
      `<section class="blog-post"><h2>${link(page, post.page.pub.url, post.title)}</h2>` +
      metadata(post, page) + `${link(page, post.page.pub.url, 'Read article')}</section>`
    ).join('\n') || '<p>No published posts yet.</p>'
    const pagination = pageCount > 1 ? '<nav aria-label="Blog pagination">' + pages.map((target, i) =>
      i === number ? `<span aria-current="page">${i + 1}</span>` : link(page, target.pub.url, `Page ${i + 1}`)
    ).join(' | ') + '</nav>' : ''
    const heading = relative.startsWith('tags/') ? `<h2>${escape(title)}</h2>` : ''
    const listing = `${heading}\n${categories}\n${articles}\n${pagination}\n<p>${link(page, '/blog/feed.xml', 'RSS feed')}</p>`
    const source = page.contents.toString()
    if (!/^blog-index::\[\][ \t]*$/m.test(source)) {
      throw new Error(`${page.src.relative}: missing blog-index::[] placeholder`)
    }
    // Pass escaped HTML through unchanged; summaries must not become AsciiDoc directives.
    page.contents = Buffer.from(source.replace(/^blog-index::\[\][ \t]*$/m,
      () => `++++\n${listing}\n++++`))
  })
}

function rss(posts, index, baseUrl) {
  const absolute = (url) => new URL(url.replace(/^\//, ''), baseUrl).href
  const items = posts.map((post) => {
    const url = escape(absolute(post.page.pub.url))
    // RSS descriptions can be interpreted as HTML; encode their plain text before XML encoding.
    return `<item><title>${escape(post.title)}</title><link>${url}</link>` +
      `<guid isPermaLink="true">${url}</guid><pubDate>${new Date(`${post.date}T00:00:00Z`).toUTCString()}</pubDate>` +
      `<description>${escape(escape(post.summary))}</description>` +
      post.tags.map((tag) => `<category>${escape(tag)}</category>`).join('') + '</item>'
  }).join('\n')
  return '<?xml version="1.0" encoding="UTF-8"?>\n' +
    '<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom"><channel>' +
    `<title>FactCast Blog</title><link>${escape(absolute(index.pub.url))}</link>` +
    '<description>Technical articles, announcements and release news from FactCast.</description>' +
    `<atom:link href="${escape(absolute('/blog/feed.xml'))}" rel="self" type="application/rss+xml"/>` +
    `<language>en</language>\n${items}\n</channel></rss>\n`
}
