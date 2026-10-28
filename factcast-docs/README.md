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

See the [blog contributor guide](blog/README.md) for authoring, drafts, pagination,
RSS, a blog-only preview and validation with `mvn-lite -Ptest-blog verify`.
