---
sessionId: session-260831-182531-1tmz
---

# Requirements

**Goal / outcome:** Generated FactCast documentation pages share one complete navigation tree, with nested menus that remain reachable and render above the left-hand AsciiDoc TOC. All local graphics continue to render after the assets are centralized under `src/main/asciidoc/images/` (with database logos under `logos/`).

**Scope:**
- **In scope:** repair/complete the shared docinfo navigation, eliminate submenu hit-area gaps, and establish stacking above `#toc.toc2`; audit and correct local AsciiDoc/HTML image paths and generated asset copying.
- **Out of scope:** documentation prose changes, a new navigation framework, mobile-menu redesign, active-page highlighting, or unrelated site styling.

**Done when:**
- Every generated page receives the same global menu, including the About, Concept, Setup, Usage, Ops, Guides, and UI trees and their individual pages.
- Top-level, nested, and deeply nested dropdowns stay open while the pointer moves from a trigger into the submenu; submenu panels align with their trigger rather than exposing a traversable gap.
- Dropdowns visibly overlay the left TOC instead of being clipped or hidden, while the fixed header does not obscure page content.
- Generated HTML contains no broken local image/logo references, and the central image assets are copied into the generated site.

# Technical Design

**Decisions:**
- Choose the existing shared Asciidoctor docinfo component (`src/main/asciidoc/docinfo-header.html` plus `docinfo.html`) over per-page menus or a new navigation framework; `pom.xml` already enables `docinfo: shared` and preserves nested output directories.
- Choose CSS-only geometry/stacking fixes over JavaScript-managed hover state; the existing nested `<ul>` tree can remain the source of truth, with no runtime dependency added for this defect.
- Choose explicit depth-relative references to the centralized `images/` and `logos/` folders over a broad `imagesdir` migration; this matches the current `relativeBaseDir` setup and keeps AsciiDoc and pass-through HTML references auditable.

**Approach & touches:**
- Audit the complete menu tree in `src/main/asciidoc/docinfo-header.html` against all 90 publishable `.adoc` pages (including deep `Usage/...` and `Setup/gRPC Client/...` paths), retaining working target names and the shared path-resolution behavior.
- Refine `src/main/asciidoc/docinfo.html` so top-level and nested dropdowns have no pointer-crossing gap, parent/child panels align at a contiguous edge, list defaults cannot introduce offsets, and the navbar/dropdowns occupy a stacking layer above `#toc.toc2` while body/TOC offsets remain usable.
- Keep `pom.xml` as the generation contract: `preserveDirectories`, `relativeBaseDir`, shared docinfo, and resource copying for image formats plus `logos/**/*` must remain enabled.
- Audit image macros and pass-through `<img>` references in `src/main/asciidoc/**/*.adoc`; correct only paths whose generated location cannot resolve to `src/main/asciidoc/images/*` or `logos/*`, with particular attention to `Concept`, `UI`, and the deep `Usage/factus/projections` pages.

**Nuances / risks / corners:**
- CSS z-index on a dropdown cannot escape a lower navbar stacking context; raise/layer the navbar itself relative to the Asciidoctor TOC, not only the child menu.
- Reset menu `margin`/`padding` and account for inherited Asciidoctor list styles; avoid creating a new gap while fixing horizontal alignment. Deep submenus must remain reachable at least at the existing desktop layout and must not be clipped by an ancestor.
- Preserve accessible anchors and valid URLs for every menu target, including names with spaces and repository spelling such as `bootstapping.adoc` and `optimistic_locking.adoc`; do not silently normalize filenames.
- Preserve external badge URLs in `index.adoc`; the image audit concerns local assets only, including pass-through logo markup.

# Testing

- Generate the full site and confirm the shared navbar appears on the root page and representative shallow/deep pages, with all 90 publishable page targets resolving.
- In a desktop browser, hover from a top-level item into its dropdown, then through `Setup → gRPC Client` and the deepest `Usage → Factus → Projections` branches; menus must stay open and child panels must be directly reachable.
- On a page with `:toc: left`, open a dropdown over the TOC and confirm the menu is on top, clickable, and not clipped; confirm the fixed header does not cover the page heading.
- Confirm local graphics on `Concept/tail-index`, `UI`, `UI/Setup/plugins`, and the deep projection type pages load from generated `images/`/`logos/` paths; external badges remain unchanged.
- No repository test sources currently exist; the Maven documentation generation plus static link/asset audit is the regression check.

# Assumptions & Open Questions

- **Generated-site base assumption:** the module continues to publish the Asciidoctor output with preserved directories (currently `target/generated-docs`), and the existing `data-href` resolver must also be checked if the output is copied to a deployment root; if deployment uses another prefix, make the base-prefix handling explicit rather than hard-coding the local filesystem path. Alternative: inject build-specific absolute/root-relative URLs, but that would couple the static docs to one hosting location. Impact: incorrect prefix handling can make every menu link fail even when the menu markup is present.

# Delivery Steps

### ✓ Step 1: Repair shared global navigation geometry
Goal: Every generated page has a complete, usable shared menu whose nested dropdowns are reachable and visibly above the left TOC.
Scope: `src/main/asciidoc/docinfo-header.html`, `src/main/asciidoc/docinfo.html`, and the existing shared-docinfo configuration in `pom.xml`.
Acceptance Criteria:
- [ ] The existing menu tree still covers every publishable `.adoc` page across About, Concept, Setup, Usage, Ops, Guides, and UI, including the deepest nested branches and correctly encoded paths.
- [ ] Top-level and nested dropdown edges are contiguous, inherited list spacing cannot create a pointer gap, and moving the pointer into a child or sub-child menu does not close its ancestors.
- [ ] The navbar establishes a stacking layer above `#toc.toc2`; dropdowns remain clickable over the TOC, while the body and TOC top offsets remain aligned below the fixed header.
- [ ] Shared docinfo injection remains enabled for every generated page, and the existing link-prefix behavior works from both root and deeply nested generated pages.
Verification: `bash /home/doc/bin/mvn-lite clean generate-resources` → green

### ✓ Step 2: Restore centralized image references and verify the generated site
Goal: All local documentation graphics and logos resolve after generation without changing documentation prose or external badge links.
Scope: `src/main/asciidoc/**/*.adoc`, centralized `src/main/asciidoc/images/` and `logos/` assets, and the resource-copy configuration in `pom.xml`.
Acceptance Criteria:
- [ ] Every local `image::` macro and pass-through `<img>` reference resolves from its generated page to an existing copied asset under `images/` or `logos/`, including shallow, nested, and deeply nested pages.
- [ ] The plugin continues to copy all required local image formats and `logos/**/*` while preserving output directories; no broad `imagesdir` migration or prose change is introduced.
- [ ] Generated HTML contains zero broken local image/logo URLs, and representative projection, Concept, and UI graphics are visibly rendered.
- [ ] The final generated output retains all valid navbar targets and contains no regressions in the shared menu.
Verification: `bash /home/doc/bin/mvn-lite clean generate-resources` → green