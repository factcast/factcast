'use strict'

;(() => {
  const dropdown = document.querySelector('.search-result-dropdown-menu')
  const searchScript = document.getElementById('search-ui-script')
  if (!dropdown || !searchScript) return

  const siteRoot = new URL(`${searchScript.dataset.siteRootPath || '.'}/`, document.baseURI)

  function createGroup(kind, title) {
    const group = document.createElement('section')
    group.className = 'search-result-group'
    group.dataset.searchGroup = kind
    group.setAttribute('aria-label', title)
    const heading = document.createElement('h2')
    heading.textContent = title
    group.appendChild(heading)
    return group
  }

  function linkPageTitle(result, link) {
    const title = result.querySelector('.search-result-document-title')
    const pageTitle = title.textContent.trim()
    const segments = link.pathname.slice(siteRoot.pathname.length).split('/')
    // Omit Antora's component/version prefix and use the real page title instead of the filename.
    const parents = segments.slice(segments[0] === 'blog' ? 1 : 2, -1).map((segment) => {
      try { return decodeURIComponent(segment) } catch { return segment }
    })
    if (segments.at(-1) === 'index.html' && parents.at(-1)?.toLowerCase() === pageTitle.toLowerCase()) {
      parents.pop()
    }
    const titleLink = document.createElement('a')
    titleLink.href = link.href
    titleLink.hash = ''
    if (parents.length) titleLink.appendChild(document.createTextNode(parents.join(' / ') + ' / '))
    // Retain the title's highlighting while making the whole breadcrumb a link to the page.
    titleLink.append(...title.childNodes)
    title.replaceChildren(titleLink)
    const sectionTitle = result.querySelector('.search-result-section-title')
    if (sectionTitle?.textContent.trim() === pageTitle) sectionTitle.remove()
  }

  function groupResults(dataset) {
    if (!dataset.classList.contains('search-result-dataset') || dataset.classList.contains('search-result-columns')) return
    const results = Array.from(dataset.children).filter((child) =>
      child.classList.contains('search-result-item') && child.querySelector('a'))
    if (!results.length) return

    const docs = createGroup('docs', 'Documentation')
    const blog = createGroup('blog', 'Blog')
    for (const result of results) {
      const link = result.querySelector('a')
      linkPageTitle(result, link)
      const group = link.pathname.startsWith(`${siteRoot.pathname}blog/`) ? blog : docs
      // Move the original result nodes so highlights, links and event handlers are retained.
      group.appendChild(result)
    }
    dataset.classList.add('search-result-columns')
    dataset.replaceChildren(...[docs, blog].filter((group) => group.children.length > 1))
  }

  // Lunr replaces the dataset for every query. Observe only that boundary, not our own node moves.
  new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      for (const node of mutation.addedNodes) {
        if (node.nodeType === Node.ELEMENT_NODE) groupResults(node)
      }
    }
  }).observe(dropdown, { childList: true })
  Array.from(dropdown.children).forEach(groupResults)
})()
