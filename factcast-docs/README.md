# FactCast documentation and blog

Preview documentation from the current checkout, including the blog and shared search,
from this directory:

```sh
mvn-lite install
python3 -m http.server 8080 --directory target/site
```

Open <http://localhost:8080/> for the documentation and
<http://localhost:8080/blog/index.html> for the blog.
Multi-release builds share one current blog at `/blog/`, and every documentation
version links to it.

`mvn-lite antora` builds the site without packaging or installing the module.
For local absolute URLs, append `-Dantora.additionalOptions=url=http://localhost:8080/`.

From the repository root, build the published site containing all stable releases
and the current blog:

```sh
mvn-lite -B -pl factcast-docs -Dantora.playbook=antora-playbook-release.yml package
```

The release build requires a full checkout with release tags available. It reads
documentation from stable numeric tags starting at `0.12.5`, excluding prereleases
and development branches. The version mapping in `antora.yml` derives stable
versions from numeric tags and keeps local branch previews at `latest`. The two
original Antora releases, `0.12.5` and `0.12.6`, receive their numeric version during
aggregation because their descriptors declare `latest`. The original tags and
document contents remain unchanged. Only the blog module
from the current default-branch checkout is added to the newest stable version;
development documentation and historical blog snapshots are excluded.

Each release has permanent URLs under `/docs/<version>/`. Antora's native selectors
switch between those versions. The site root and stable document links use the
highest stable version. Compatibility pages under `/docs/latest/` redirect to the
same page in that version and preserve query strings and anchors; image and
attachment URLs under that prefix remain available too.

The documentation workflow uses the builder from the default branch and publishes
the complete site on each stable GitHub release and on blog page changes to `main`.
It can also be run manually. Its Kotlin source is `.github/kts/docs.main.kts`;
regenerate `.github/workflows/docs.yaml` after changing it:

```sh
kotlin .github/kts/docs.main.kts
```

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
