'use strict'

module.exports.register = function ({ config }) {
  this.once('contentAggregated', ({ contentAggregate }) => {
    const blog = contentAggregate.find(({ name }) => name === 'blog')
    const post = blog.files.find(({ path }) => path.startsWith('modules/ROOT/pages/posts/'))
    blog.files = blog.files.filter(({ path }) => !path.startsWith('modules/ROOT/pages/posts/'))
    for (const { filename, contents } of config.posts || []) {
      const file = post.clone()
      file.path = `modules/ROOT/pages/posts/${filename}`
      file.src = { ...post.src, path: file.path, basename: filename, stem: filename.slice(0, -5) }
      file.contents = Buffer.from(contents)
      blog.files.push(file)
    }
  })
}
