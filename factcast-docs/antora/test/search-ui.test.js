'use strict'
const assert = require('node:assert/strict')
const fs = require('node:fs')
const http = require('node:http')
const path = require('node:path')
const { test } = require('node:test')
const { spawn } = require('node:child_process')
const { once } = require('node:events')
const { runInNewContext } = require('node:vm')

test('shared UI provides grouped search and image lightboxes', { timeout: 60000 }, async () => {
  const root = path.resolve('target/site')
  const lunr = require(path.join(root, '_/js/vendor/lunr.js'))
  let searchData
  runInNewContext(fs.readFileSync(path.join(root, 'search-index.js'), 'utf8'), {
    lunr, antoraSearch: { initSearch: (_, data) => { searchData = data } },
  })
  const expectedMatches = lunr.Index.load(searchData.index).search('factcast').map(({ ref }) =>
    searchData.store.documents[ref.split('-')[0]])
  const imagePost = Object.values(searchData.store.documents).find((doc) =>
    doc.url.startsWith('/blog/posts/') && fs.readFileSync(path.join(root, doc.url), 'utf8').includes('class="imageblock"'))
  assert.ok(imagePost, 'a published blog post should contain an image for the lightbox check')
  const imagePage = path.join(root, imagePost.url)
  const imageSource = fs.readFileSync(imagePage, 'utf8').match(/class="imageblock"[\s\S]*?<img[^>]+src="([^"]+)"/)[1]
  const missing = []
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost')
    url.pathname = url.pathname.replace(/^\/preview(?=\/)/, '')
    let file = path.resolve(root, '.' + decodeURIComponent(url.pathname))
    if (!file.startsWith(root + path.sep) && file !== root) { res.writeHead(403).end(); return }
    if (fs.existsSync(file) && fs.statSync(file).isDirectory()) file = path.join(file, 'index.html')
    if (!fs.existsSync(file)) {
      if (url.pathname !== '/favicon.ico') missing.push(url.pathname)
      res.writeHead(404).end(); return
    }
    const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.jpg': 'image/jpeg', '.png': 'image/png', '.svg': 'image/svg+xml' }
    res.setHeader('Content-Type', types[path.extname(file)] || 'application/octet-stream')
    if (file === imagePage) {
      // Exercise linked and opted-out images without changing any authored article.
      const fixtures = `<div class="imageblock"><a href="../index.html"><img id="linked-image" src="${imageSource}"></a></div>` +
        `<div class="imageblock no-lightbox"><img id="opted-out-image" src="${imageSource}"></div>`
      res.end(fs.readFileSync(file, 'utf8').replace('</article>', fixtures + '</article>'))
      return
    }
    fs.createReadStream(file).pipe(res)
  })
  server.listen(0, '127.0.0.1')
  await once(server, 'listening')
  const base = `http://127.0.0.1:${server.address().port}/preview`
  fs.mkdirSync('/tmp/agents', { recursive: true })
  const profile = fs.mkdtempSync('/tmp/agents/factcast-search-chromium-')
  const chrome = spawn(process.env.CHROMIUM_BIN || 'chromium', [
    '--headless=new', '--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage',
    '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=0',
    `--user-data-dir=${profile}`, 'about:blank',
  ], { stdio: ['ignore', 'ignore', 'pipe'] })
  let socket
  try {
    const endpoint = await new Promise((resolve, reject) => {
      let output = ''
      const timeout = setTimeout(() => reject(new Error('Chromium did not start: ' + output.slice(-1000))), 15000)
      chrome.once('error', (error) => { clearTimeout(timeout); reject(error) })
      chrome.stderr.on('data', (data) => {
        output += data.toString()
        const match = output.match(/DevTools listening on (ws:\/\/\S+)/)
        if (match) { clearTimeout(timeout); resolve(match[1]) }
      })
      chrome.on('exit', (code) => { clearTimeout(timeout); reject(new Error('Chromium exited: ' + code + ': ' + output.slice(-1000))) })
    })
    socket = new WebSocket(endpoint)
    await once(socket, 'open')
    let nextId = 0
    const pending = new Map()
    socket.addEventListener('message', ({ data }) => {
      const message = JSON.parse(data)
      const callback = pending.get(message.id)
      if (!callback) return
      pending.delete(message.id)
      message.error ? callback.reject(new Error(JSON.stringify(message.error))) : callback.resolve(message.result)
    })
    function call(method, params = {}, sessionId) {
      return new Promise((resolve, reject) => {
        const id = ++nextId
        pending.set(id, { resolve, reject })
        socket.send(JSON.stringify({ id, method, params, sessionId }))
      })
    }
    const { targetId } = await call('Target.createTarget', { url: 'about:blank' })
    const { sessionId } = await call('Target.attachToTarget', { targetId, flatten: true })
    const pageCall = (method, params) => call(method, params, sessionId)
    await pageCall('Page.enable')
    await pageCall('Runtime.enable')
    async function evaluate(expression) {
      const value = await pageCall('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
      if (value.exceptionDetails) throw new Error(JSON.stringify(value.exceptionDetails))
      return value.result.value
    }
    async function waitFor(expression) {
      const deadline = Date.now() + 15000
      while (Date.now() < deadline) {
        if (await evaluate(expression)) return
        await new Promise((resolve) => setTimeout(resolve, 100))
      }
      throw new Error('Timed out: ' + expression + '; page: ' + await evaluate('location.href'))
    }
    async function screenshot(name) {
      const { data } = await pageCall('Page.captureScreenshot', { format: 'png' })
      fs.writeFileSync('/tmp/agents/' + name, Buffer.from(data, 'base64'))
    }
    const exceptions = []
    socket.addEventListener('message', ({ data }) => {
      const message = JSON.parse(data)
      if (message.method === 'Runtime.exceptionThrown') exceptions.push(message.params)
    })
    async function openSearch(width, mobile, pathname = '/docs/latest/About/index.html') {
      await pageCall('Emulation.setDeviceMetricsOverride', { width, height: 900, deviceScaleFactor: 1, mobile })
      await pageCall('Page.navigate', { url: base + pathname })
      await waitFor("document.readyState === 'complete' && document.querySelector('#search-input')?.disabled === false")
      if (width < 1024) await evaluate("document.querySelector('.navbar-burger').click()")
      await evaluate("document.querySelector('#search-input').focus()")
    }
    async function searchFor(query) {
      await pageCall('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Escape', code: 'Escape' })
      await waitFor("document.querySelectorAll('.search-result-item').length === 0 && document.querySelector('#search-input').value === ''")
      await pageCall('Input.insertText', { text: query })
      await pageCall('Input.dispatchKeyEvent', { type: 'keyDown', key: 'x', code: 'KeyX' })
      await waitFor("document.querySelectorAll('.search-result-item').length > 0")
    }
    const groups = () => evaluate(`Array.from(document.querySelectorAll('.search-result-group')).map(group => ({
      kind: group.dataset.searchGroup,
      title: group.querySelector('h2').textContent,
      urls: Array.from(group.querySelectorAll('.search-result-document-hit > a')).map(a => new URL(a.href).pathname),
      rect: group.getBoundingClientRect().toJSON(),
    }))`)
    async function assertPropertyBreadcrumbs() {
      await searchFor('property')
      const results = await evaluate(`Array.from(document.querySelectorAll('.search-result-item')).map(result => ({
        title: result.querySelector('.search-result-document-title a')?.textContent,
        pageUrl: result.querySelector('.search-result-document-title a')?.href,
        hitUrl: result.querySelector('.search-result-document-hit > a').href,
        sectionTitle: result.querySelector('.search-result-section-title')?.textContent,
      }))`)
      for (const result of results) {
        assert.ok(result.pageUrl, 'page title must be a link')
        assert.equal(new URL(result.pageUrl).pathname, new URL(result.hitUrl).pathname)
        assert.equal(new URL(result.pageUrl).hash, '')
      }
      for (const directory of ['Setup', 'UI']) {
        const result = results.find(result => new URL(result.hitUrl).pathname === `/preview/docs/latest/${directory}/properties.html`)
        assert.equal(result?.title, `${directory} / Properties`)
        assert.notEqual(result.sectionTitle, 'Properties', 'do not repeat the page title in the snippet')
      }
      assert.ok(results.some(result => result.title === 'Setup / gRPC Client / gRPC Basic Auth'))
      assert.equal(await evaluate("document.querySelectorAll('.search-result-document-path').length"), 0)
    }
    await openSearch(1440, false)
    await searchFor('factcast')
    const desktop = await groups()
    assert.deepEqual(desktop.map(group => group.kind), ['docs', 'blog'])
    assert.deepEqual(desktop.map(group => group.title), ['Documentation', 'Blog'])
    assert.ok(desktop[0].rect.x < desktop[1].rect.x)
    assert.equal(desktop[0].rect.y, desktop[1].rect.y)
    assert.ok(desktop[0].urls.length > 0 && desktop[0].urls.every(url => url.startsWith('/preview/docs/')))
    assert.ok(desktop[1].urls.length > 0 && desktop[1].urls.every(url => url.startsWith('/preview/blog/')))
    for (const group of desktop) {
      const expected = expectedMatches.filter((doc) => doc.component === group.kind).map((doc) => '/preview' + doc.url)
      assert.deepEqual(group.urls, expected, `${group.kind}: all matches retain relevance order`)
    }
    assert.equal(await evaluate("document.querySelectorAll('.search-result-group .search-result-highlight').length > 0"), true)
    await evaluate("document.querySelector('.search-result-group a').focus()")
    assert.equal(await evaluate("document.activeElement.matches('.search-result-group a')"), true)
    await evaluate("document.querySelector('#search-input').focus()")
    assert.equal(await evaluate("document.querySelectorAll('.search-result-component-header').length"), 0)
    const bounds = await evaluate("document.querySelector('.search-result-dropdown-menu').getBoundingClientRect().toJSON()")
    assert.ok(bounds.width > 900 && bounds.left >= 0 && bounds.right <= 1440)
    await screenshot('factcast-search-columns-desktop.png')
    await assertPropertyBreadcrumbs()
    await screenshot('factcast-search-paths-desktop.png')
    await searchFor('component:blog')
    assert.deepEqual((await groups()).map(group => group.kind), ['blog'])
    await searchFor('zzzznotafactcasttermzzzz')
    assert.deepEqual(await groups(), [])
    assert.match(await evaluate("document.querySelector('.search-result-dataset').textContent"), /No results found/)
    await openSearch(390, true, imagePost.url)
    await searchFor('factcast')
    const mobile = await groups()
    assert.deepEqual(mobile.map(group => group.kind), ['docs', 'blog'])
    assert.equal(mobile[0].rect.x, mobile[1].rect.x)
    assert.ok(mobile[1].rect.y >= mobile[0].rect.bottom)
    assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true)
    await screenshot('factcast-search-columns-mobile.png')
    await assertPropertyBreadcrumbs()
    assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true)
    await screenshot('factcast-search-paths-mobile.png')
    await searchFor('factcast')
    await evaluate("document.querySelector('[data-search-group=blog] a').click()")
    await waitFor("document.readyState === 'complete' && location.pathname.startsWith('/preview/blog/')")
    for (const [width, mobile, url] of [
      [1440, false, base + imagePost.url],
      [390, true, base + imagePost.url],
      [1440, false, new URL('file://' + imagePage).href],
    ]) {
      await pageCall('Emulation.setDeviceMetricsOverride', { width, height: 900, deviceScaleFactor: 1, mobile })
      await pageCall('Page.navigate', { url })
      await waitFor("document.readyState === 'complete'")
      assert.ok(await evaluate("document.querySelector('.doc .imageblock img').closest('a.image-lightbox')"), 'article image should open a lightbox')
      if (!url.startsWith('file:')) {
        assert.equal(await evaluate("document.getElementById('linked-image').closest('a').getAttribute('href')"), '../index.html')
        assert.equal(await evaluate("document.getElementById('linked-image').closest('a').classList.contains('image-lightbox')"), false)
        assert.equal(await evaluate("document.getElementById('opted-out-image').closest('a')"), null)
      }
      const position = await evaluate(`(() => {
        const trigger = document.querySelector('a.image-lightbox')
        trigger.scrollIntoView({ block: 'center', behavior: 'instant' })
        trigger.focus({ preventScroll: true })
        return { x: scrollX, y: scrollY }
      })()`)
      for (const closeWith of ['escape', 'button']) {
        await evaluate("document.querySelector('a.image-lightbox').click()")
        await waitFor("document.querySelector('#glightbox-body .gslide-image img')?.naturalWidth > 0 && document.querySelector('#glightbox-body .gclose') === document.activeElement")
        await waitFor("!document.querySelector('#glightbox-body .gzoomIn, #glightbox-body .gfadeIn')")
        assert.equal(await evaluate('location.href'), url)
        const dimensions = await evaluate("document.querySelector('#glightbox-body .gslide-image img').getBoundingClientRect().toJSON()")
        assert.ok(dimensions.width > 0 && dimensions.left >= -1 && dimensions.right <= width + 1)
        assert.ok(dimensions.top >= -1 && dimensions.bottom <= 901)
        await pageCall('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Tab', code: 'Tab', windowsVirtualKeyCode: 9 })
        assert.equal(await evaluate("Boolean(document.activeElement.closest('#glightbox-body'))"), true)
        if (closeWith === 'escape') {
          await screenshot(`factcast-lightbox-${url.startsWith('file:') ? 'file' : mobile ? 'mobile' : 'desktop'}.png`)
          await pageCall('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 })
        } else {
          await evaluate("document.querySelector('#glightbox-body .gclose').click()")
        }
        await waitFor("!document.getElementById('glightbox-body')")
        assert.equal(await evaluate("document.activeElement === document.querySelector('a.image-lightbox')"), true)
        assert.equal(await evaluate('location.href'), url)
        const scrollY = await evaluate('scrollY')
        assert.ok(Math.abs(scrollY - position.y) <= 1, `${closeWith}: retain reading position ${position.y}, got ${scrollY}`)
      }
    }
    assert.deepEqual(exceptions, [])
    assert.deepEqual(missing, [])
  } finally {
    socket?.close()
    if (chrome.pid && chrome.exitCode === null) {
      const exited = once(chrome, 'exit')
      chrome.kill('SIGTERM')
      await exited
    }
    fs.rmSync(profile, { recursive: true, force: true })
    server.closeAllConnections()
    server.close()
  }
})
