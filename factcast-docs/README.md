# FactCast documentation and blog

Build the complete site, including the blog and shared search, from this directory:

```sh
mvn-lite install
python3 -m http.server 8080 --directory target/site
```

Open <http://localhost:8080/> for the documentation and
<http://localhost:8080/blog/index.html> for the blog.

`mvn-lite antora` builds the site without packaging or installing the module.
For local absolute URLs, append `-Dantora.additionalOptions=url=http://localhost:8080/`.

See the [blog contributor guide](src/main/asciidoc/modules/blog/README.md) for authoring, drafts, pagination,
RSS, a blog-only preview and validation with `mvn-lite -Ptest-blog verify`.

Search results show documentation and blog matches in separate columns, stacked
on smaller screens. Each result uses a linked breadcrumb title, such as
“Setup / Properties”, to distinguish pages with identical names.
With Chromium installed, run `mvn-lite -Ptest-search-ui verify`
to check search and image lightboxes on desktop, mobile and local HTML files.
Set `CHROMIUM_BIN` if the executable
is not available as `chromium` on your PATH.

Article images open in a lightbox when clicked. Close it with the X button or
Escape to return to the same reading position. Existing image links keep their
destination; add `[.no-lightbox]` before an image macro to disable enlargement.

GLightbox 3.3.1 is bundled locally under `supplemental-ui/js/vendor/` and
`supplemental-ui/css/vendor/`, with its MIT license beside the JavaScript file.
The assets are the unmodified `dist` files from the
[GLightbox npm release](https://www.npmjs.com/package/glightbox/v/3.3.1), verified
against npm's SHA-512 integrity value. No CDN is used for the lightbox.
