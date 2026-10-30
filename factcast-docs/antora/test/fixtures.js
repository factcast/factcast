'use strict'

module.exports.register = function ({ config }) {
  this.once('contentAggregated', ({ contentAggregate }) => {
    const blog = contentAggregate.find(({ name }) => name === 'docs')
    const post = blog.files.find(({ path }) => path.startsWith('modules/blog/pages/posts/'))
    blog.files = blog.files.filter(({ path }) => !path.startsWith('modules/blog/pages/posts/'))
    for (const { filename, contents } of config.posts || []) {
      const file = post.clone()
      file.path = `modules/blog/pages/posts/${filename}`
      file.src = { ...post.src, path: file.path, basename: filename, stem: filename.slice(0, -5) }
      file.contents = Buffer.from(contents)
      blog.files.push(file)
    }
    if (config.versions?.length) {
      // Model release snapshots without creating commits or depending on repository tags.
      const rootPage = blog.files.find(({ path }) => path === 'modules/ROOT/pages/About/index.adoc')
      const rootNav = blog.files.find(({ path }) => path === 'modules/ROOT/nav.adoc')
      rootPage.contents = Buffer.from('= Documentation\n\nxref:docs:blog:posts/current.adoc[Current blog article]\n')
      rootNav.contents = Buffer.from('* xref:About/index.adoc[About]\n')
      blog.startPage = 'ROOT:About/index.adoc'
      blog.files = blog.files.filter((file) => file === rootPage || file === rootNav || file.path.startsWith('modules/blog/'))
      for (const { version, withoutBlog } of config.versions) {
        const release = { ...blog, version, nav: [...blog.nav], files: blog.files.map((file) => file.clone()) }
        for (const file of release.files) file.src = { ...file.src }
        if (withoutBlog) {
          release.files = release.files.filter(({ path }) => !path.startsWith('modules/blog/'))
          release.nav = release.nav.filter((path) => !path.startsWith('modules/blog/'))
        } else {
          const stale = release.files.find(({ path }) => path === 'modules/blog/pages/posts/current.adoc')
          stale.contents = Buffer.from('= Stale release article\n:page-blog-date: 2020-01-01\n:page-blog-summary: Outdated release snapshot.\n\nStale release content.\n')
        }
        contentAggregate.push(release)
      }
    }
  })
}
