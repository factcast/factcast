# FactCast documentation

From the repository root, preview documentation from the current checkout:

```sh
mvn-lite -B -pl factcast-docs package
```

The site is generated in `factcast-docs/target/site`. This also runs the Antora
extension tests using the Node installation managed by the Antora Maven plugin.

To build the published site containing all stable releases:

```sh
mvn-lite -B -pl factcast-docs -Dantora.playbook=antora-playbook-release.yml package
```

The release build requires a full checkout with release tags available. It reads
documentation from stable numeric tags starting at `0.12.5`, excluding prereleases
and development branches. Future releases use `version: true` in `antora.yml` to
derive their version from the tag. The two original Antora releases, `0.12.5` and
`0.12.6`, receive their numeric version during aggregation because their descriptors
declare `latest`. The original tags and document contents remain unchanged.

Each release has permanent URLs under `/docs/<version>/`. Antora's native selectors
switch between those versions. The site root and stable document links use the
highest stable version. Compatibility pages under `/docs/latest/` redirect to the
same page in that version and preserve query strings and anchors; image and
attachment URLs under that prefix remain available too.

The documentation workflow uses the builder from the default branch and publishes
the complete site on each stable GitHub release. It can also be run manually after
merging the versioning setup. Its Kotlin source is `.github/kts/docs.main.kts`;
regenerate `.github/workflows/docs.yaml` after changing it:

```sh
kotlin .github/kts/docs.main.kts
```
