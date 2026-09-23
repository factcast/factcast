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

      const children = pages
        .filter((candidate) => {
          const src = candidate.src

          // Same Antora component/version/module
          if (src.component !== current.component) return false
          if (src.version !== current.version) return false
          if (src.module !== current.module) return false

          // Only pages in exactly the same directory
          if (path.posix.dirname(src.relative) !== currentDir) return false

          // Don't include the current page
          if (src.relative === current.relative) return false

          // Don't include another index.adoc
          if (path.posix.basename(src.relative) === 'index.adoc') return false

          return true
        })
        .sort((a, b) => a.src.relative.localeCompare(b.src.relative))

      const list = children
        .map((child) => {
          const filename = path.posix.basename(child.src.relative)

          // "./" explicitly means "same pages subdirectory" in Antora
          return `* xref:./${filename}[]`
        })
        .join('\n')

      page.contents = Buffer.from(
        source.replace(/^child-pages::\[\]\s*$/gm, list)
      )
    }
  })
}