'use strict'

const path = require('path')

module.exports.register = function () {
  this.once('contentClassified', ({ contentCatalog }) => {
    const pages = contentCatalog.getPages()

    for (const page of pages) {
      if (!page.contents) continue

      const source = page.contents.toString()
      if (!source.includes('child-pages::[]')) continue

      const current = page.src
      const currentDir = path.posix.dirname(current.relative)

      const children = pages.filter((candidate) => {
        const src = candidate.src

        if (
          src.component !== current.component ||
          src.version !== current.version ||
          src.module !== current.module ||
          src.relative === current.relative
        ) {
          return false
        }

        const dir = path.posix.dirname(src.relative)
        const isIndex = path.posix.basename(src.relative) === 'index.adoc'

        // Sibling page in the same directory
        if (dir === currentDir) return !isIndex

        // Overview page of an immediate subdirectory
        return isIndex && path.posix.dirname(dir) === currentDir
      })

      const navOrder = getNavOrder(contentCatalog, current)

      children.sort((a, b) => {
        const ai = navOrder.get(a.src.relative)
        const bi = navOrder.get(b.src.relative)

        // Both pages occur in nav.adoc
        if (ai !== undefined && bi !== undefined) {
          return ai - bi
        }

        // Pages occurring in nav.adoc come first
        if (ai !== undefined) return -1
        if (bi !== undefined) return 1

        // Fallback for pages not mentioned in navigation
        return a.src.relative.localeCompare(b.src.relative)
      })

      const list = children
        .map((child) => {
          const target = path.posix.relative(currentDir, child.src.relative)
          return `* xref:./${target}[]`
        })
        .join('\n')

      // Only match trailing spaces/tabs (\s would also swallow the following blank lines),
      // and surround the list with blank lines so following text does not continue the last item.
      page.contents = Buffer.from(
        source.replace(/^child-pages::\[\][ \t]*$/gm, `\n${list}\n`)
      )
    }
  })
}

function getNavOrder(contentCatalog, current) {
  const order = new Map()
  let position = 0

  const navFiles = contentCatalog.findBy({
    component: current.component,
    version: current.version,
    family: 'nav'
  })

  for (const navFile of navFiles) {
    const source = navFile.contents.toString()

    const xrefPattern = /xref:([^\[]+)\[/g
    let match

    while ((match = xrefPattern.exec(source)) !== null) {
      let target = match[1]

      // Ignore fragments
      target = target.split('#')[0]

      // Handle module:name.adoc
      let module = current.module
      let relative = target

      const colon = target.indexOf(':')
      if (colon >= 0) {
        module = target.substring(0, colon)
        relative = target.substring(colon + 1)
      }

      if (
        module === current.module &&
        !order.has(relative)
      ) {
        order.set(relative, position++)
      }
    }
  }

  return order
}