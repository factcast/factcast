# FactCast blog contributor guide

The blog is the `blog` module of the `docs` Antora component, alongside the
`ROOT` documentation module. It shares the documentation navigation and build.
Its published pages, images and RSS keep their existing unversioned `/blog/` URLs.
Every documentation version links to this same current blog. Release snapshots of
the blog are discarded during generation, including their drafts and assets.

## Write a post

Create `factcast-docs/src/main/asciidoc/modules/blog/pages/posts/YYYY-MM-DD-short-title.adoc`:

```adoc
= A useful article title
:page-blog-date: 2026-10-28
:page-blog-summary: A short, single-line description of the article.
:page-blog-author: Your name
:page-blog-tags: Technical, Releases
:page-blog-draft: false

Write the article body using ordinary AsciiDoc.
```

The title, `page-blog-date` and `page-blog-summary` are required for published posts.
Dates must be real calendar dates in `YYYY-MM-DD` format. The metadata date controls
sorting; the filename date is only a convention. Same-date posts sort by filename.
Use single-line plain text for the title and metadata; reserve AsciiDoc formatting,
macros and includes for the body. Metadata is escaped for HTML and RSS.
Use URL-safe filenames with letters, digits, dots, hyphens and underscores.
Subdirectories under `posts/` may use letters, digits, hyphens and underscores.

Store blog images in `factcast-docs/src/main/asciidoc/modules/blog/images/`, rather than beside
the post in `pages/posts/`. Reference an image by its path relative to `images/`:

```adoc
image::concurrent-unconditional-publishing-benchmark.png[Concurrent publishing benchmark]
```

For example, `images/benchmarks/publishing.png` is referenced as
`image::benchmarks/publishing.png[]`. Antora resolves the image from the module's
image catalog, independently of the post's directory.

Article images automatically open in a lightbox. The close button or Escape
returns readers to their original position. Images with an explicit link retain
that link; use `[.no-lightbox]` before an image macro to opt out.

`page-blog-author`, `page-blog-tags` and `page-blog-draft` are optional. Tags are
comma-separated, case-sensitive categories; whitespace and duplicates are removed.
Each category gets its own paginated index. Category URLs include a stable hash to
keep names such as `C++` and `C#` distinct. Untagged posts appear in “All posts”.

Set `:page-blog-draft: true` to keep a post unpublished. Drafts need no publication
metadata and are removed before HTML conversion and search indexing. They do not
appear in indexes, the sitemap or RSS, and have no generated article URL. Only the
literal values `true` and `false` are accepted. An omitted draft attribute means
published; future dates do **not** schedule publication. Use the draft flag until
ready to publish. Do not put confidential files in shared images or attachments:
the flag excludes the article, not independently published assets.

Do not maintain a post list manually. The extension discovers posts automatically.
Keep the `blog-index::[]` placeholder in `index.adoc`; its surrounding introduction
can be edited. Change `page_size` on the blog extension in the documentation playbook to control pagination
(default: 10). The root index, subsequent pages and category pages use the same size.

## Build and preview

From the repository root, with Java 21+ and `mvn-lite` available:

```sh
mvn-lite -f factcast-docs/pom.xml install
python3 -m http.server 8080 --directory factcast-docs/target/site
```

Open <http://localhost:8080/> for the documentation or
<http://localhost:8080/blog/index.html> for the blog. RSS is at
<http://localhost:8080/blog/feed.xml>. The shared search box indexes documentation
and published articles. The Blog menu entry is available from documentation and blog pages.
The output is generated during `generate-resources`, so `package`, `verify` and
`install` all build the whole site; `antora` builds just the site without packaging.
No separate blog Maven module is needed.

The build reads the current Git working tree, including new, uncommitted posts.
The first build needs network access for Antora, its UI bundle and dependencies.
The Maven plugin provisions Node.js automatically. If Node.js 22+ is already
installed, pass `-Dnode.executable=node` to reuse it.

The main playbook defaults to `https://docs.factcast.org/`. Override the publishing
URL, including an optional path prefix, when building a preview:

```sh
mvn-lite -f factcast-docs/pom.xml antora \
  -Dantora.additionalOptions=url=http://localhost:8080/
```

The URL is the **site root**, not the blog component root. For example, a site URL
of `https://example.org/preview/` produces feed links under
`https://example.org/preview/blog/posts/...`. It must be an absolute HTTP(S) URL
without credentials, query or fragment. The local server command above serves at
its root, so use the localhost URL when testing absolute feed or header links.
The build cleans its site output so removed posts and newly marked drafts do not
linger in previously generated files.

For a blog-only preview using the same content, extension and UI:

```sh
mvn-lite -f factcast-docs/pom.xml antora \
  -Dantora.playbook=antora-blog-playbook.yml \
  -Dantora.output=target/blog-site
python3 -m http.server 8080 --directory factcast-docs/target/blog-site
```

This keeps the main site output intact. The blog-only playbook defaults to localhost.
`antora.output` can also select an absolute output directory for either build.

## Validate

With Python 3 and `mvn-lite` available, run:

```sh
mvn-lite -f factcast-docs/pom.xml -Ptest-blog verify
```

The opt-in profile uses Node's built-in test runner and the Node.js runtime already
provisioned by the Antora Maven plugin. Python's XML parser validates RSS without
adding npm test dependencies. The checks cover real Antora builds for
samples, pagination, categories, optional metadata, drafts, an empty blog and
invalid metadata, plus the shared component, navigation, stable URLs, feed, search
index and multiple documentation releases sharing one current blog.
Fixtures, test output and the standalone playbook's Antora cache live under
`/tmp/agents`; fixtures are removed when the test process exits.

## Architecture and integration

The extension in `factcast-docs/antora/extensions/blog.js` uses Antora's public
generator hooks and catalog APIs. It reads metadata with Antora's AsciiDoc loader,
removes drafts at `contentClassified`, adds generated listing pages, and publishes
RSS 2.0 through the site catalog. AsciiDoc bodies and HTML layout remain Antora's
job. The module pins Antora 3.2.1 and reuses the existing Maven plugin, default UI
bundle and supplemental UI. The UI bundle follows the existing moving upstream URL.

The main playbook uses one content source, `factcast-docs/src/main/asciidoc`, and
one component descriptor. That descriptor registers both `ROOT/nav.adoc` and
`blog/nav.adoc`. Reference the blog landing page as `docs:blog:index.adoc`, or link
to an article with `xref:docs:blog:posts/YYYY-MM-DD-short-title.adoc[Article title]`.
Use the explicit `docs` component and omit the version coordinate so references
from older documentation resolve to the current blog.

For a multi-release site, keep the current branch source (`branches: HEAD` in the
local playbook) alongside the release sources. The extension takes the blog only
from Antora's latest `docs` component version (`latest` in the current descriptor).
It removes all older blog modules before conversion and adds a shared Blog
navigation link to every documentation version, including releases from before
the blog existed. Published articles, RSS, images and search entries occur once.
Adding a post requires rebuilding the combined site; release tags need no changes.
If the latest documentation source lacks the blog while older versions contain
one, the build fails with a diagnostic instead of publishing a stale snapshot.

Antora identifies blog resources within the documentation component version.
The blog extension adjusts their publication paths before conversion so pages,
images, attachments and aliases continue to publish under `/blog/`. RSS uses
those resolved URLs, and the existing `sitemap-blog.xml` and `sitemap-docs.xml`
partitioning is preserved. The extension adds a `blog` search keyword to module pages;
search results remain grouped into Documentation and Blog by their public paths.
The blog overview, category listings under `tags/` and their pagination use
`noindex` to stay out of the search index. Only articles appear in blog search
results; overview and category navigation remain available.
The blog-only playbook uses `blog_only: true` to select this module from the same
source tree. GitHub Actions workflows are unchanged.

References: [Antora generator events](https://docs.antora.org/antora/latest/extend/generator-events-reference/),
[RSS 2.0 specification](https://www.rssboard.org/rss-specification).
