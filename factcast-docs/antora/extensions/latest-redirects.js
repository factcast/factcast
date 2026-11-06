'use strict'

const path = require('node:path').posix

module.exports.register = function () {
  this.once('beforePublish', ({ playbook, contentCatalog, siteCatalog }) => {
    for (const component of contentCatalog.getComponents()) {
      if (component.name !== 'docs') continue
      const latest = component.versions[0]?.version
      if (latest == null) continue
      const prefix = `${component.name}/${latest}/`

      for (const file of contentCatalog.getFiles()) {
        if (!file.out?.path.startsWith(prefix)) continue
        const aliasPath = `${component.name}/latest/${file.out.path.substring(prefix.length)}`
        const html = file.src.family === 'page' || file.src.family === 'alias'
        // A page's rel points back to its preferred alias; only an alias's rel
        // points forward to the destination. Following a page's rel creates a loop.
        const destination = file.src.family === 'alias' && file.rel ? file.rel : file
        // These files live outside the content catalog so "latest" never becomes
        // a duplicate version in the selector or the search index.
        siteCatalog.addFile({
          out: { path: aliasPath },
          contents: html ? Buffer.from(redirectHtml(destination, aliasPath, playbook.site.url)) : file.contents,
        })
      }
    }
  })
}

function redirectHtml(file, aliasPath, siteUrl) {
  const target = path.relative(path.dirname(aliasPath), file.out.path)
  const scriptTarget = JSON.stringify(target).replace(/</g, '\\u003c')
  const link = escapeHtml(target)
  const canonical = escapeHtml(new URL(file.pub.url, siteUrl).href)
  // Static hosting cannot redirect an entire URL prefix. A page for each URL
  // preserves deep links, including fragments that never reach the server.
  return `<!DOCTYPE html>
<meta charset="utf-8">
<link rel="canonical" href="${canonical}">
<script>location.replace(${scriptTarget} + location.search + location.hash)</script>
<noscript><meta http-equiv="refresh" content="0; url=${link}"></noscript>
<meta name="robots" content="noindex">
<title>Redirect Notice</title>
<p>This page has moved to <a href="${link}">${link}</a>.</p>
`
}

function escapeHtml(value) {
  return value.replace(/[&<>"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  })[character])
}
