---
sessionId: session-260831-182531-1tmz
---

# Requirements

**Goal / outcome:** Switch documentation generation from the Asciidoctor Maven plugin to the Antora Maven plugin (`antora-maven-plugin`), restructuring the documentation into an Antora-compliant component layout with native Antora navigation (`nav.adoc`) and built-in site generation.

**Scope:**
- **In scope:**
  - Replacing `asciidoctor-maven-plugin` with `antora-maven-plugin` in `pom.xml`.
  - Creating Antora configuration files (`antora-playbook.yml`, `antora.yml`, `modules/ROOT/nav.adoc`).
  - Reorganizing `.adoc` pages and image assets into Antora's standard module directory structure (`src/main/asciidoc/modules/ROOT/pages/`, `images/`, etc.).
  - Removing obsolete custom Asciidoctor `docinfo.html`/`docinfo-header.html` files in favor of Antora's native navigation and UI layout.
- **Out of scope:**
  - Rewriting documentation prose content.
  - Customizing Antora default UI templates beyond standard playbook/configuration settings.

**Done when:**
- `pom.xml` successfully configures and executes `antora-maven-plugin`.
- Playbook (`antora-playbook.yml`) and component descriptor (`antora.yml`) correctly define the documentation component and navigation.
- All `.adoc` pages, images, and nav links are correctly organized in Antora module directories.
- Running `mvn-lite antora:antora` (or the configured maven goal/phase) successfully generates the complete site with native navigation and working images.

# Technical Design

**Decisions:**
- **Antora Maven Plugin (`org.antora:antora-maven-plugin`)**: Use the official Antora Maven plugin to manage Node.js runtime and build the Antora site as part of the Maven build.
- **Antora Standard Layout**: Structure `src/main/asciidoc` into an Antora component structure (`antora.yml`, `antora-playbook.yml`, `modules/ROOT/pages/`, `modules/ROOT/images/`, `modules/ROOT/nav.adoc`).
- **Native Navigation (`nav.adoc`)**: Replace custom docinfo navbar with Antora's native component navigation (`modules/ROOT/nav.adoc`).

**Proposed Changes:**
- **`pom.xml`**:
  - Remove `asciidoctor-maven-plugin`.
  - Add `antora-maven-plugin` configuration (`org.antora:antora-maven-plugin`).
- **Antora Configuration**:
  - Create `antora-playbook.yml` specifying site generation rules, content sources, and UI bundle.
  - Create `antora.yml` component descriptor (defining component name, title, version, and `nav: modules/ROOT/nav.adoc`).
- **File Reorganization**:
  - Move `.adoc` pages from `src/main/asciidoc/About/...`, `Concept/...`, etc., into `src/main/asciidoc/modules/ROOT/pages/...`.
  - Move images from `src/main/asciidoc/images/...` into `src/main/asciidoc/modules/ROOT/images/...`.
  - Create `src/main/asciidoc/modules/ROOT/nav.adoc` mapping all sections and pages.
- **Cleanup**:
  - Remove `docinfo.html` and `docinfo-header.html`.

# Testing

- Validate that `mvn-lite antora:antora` (or build execution) completes successfully without errors.
- Inspect generated site structure to ensure all pages (About, Concept, Setup, Usage, Ops, Guides, UI) are generated with correct Antora navigation and working image references.

# Delivery Steps

### ✓ Step 1: Create Antora configuration, component descriptor, navigation, and file structure
Goal: Establish the Antora project structure, playbook, component descriptor, navigation file, and module directories.
Scope: `antora-playbook.yml`, `antora.yml`, `modules/ROOT/nav.adoc`, and migrating `.adoc` / image files into `modules/ROOT/pages/` and `modules/ROOT/images/`.
Acceptance Criteria:
- [x] `antora-playbook.yml` and `antora.yml` are created with correct component and content source settings.
- [x] `modules/ROOT/nav.adoc` is created containing the full navigation menu for all sections and pages.
- [x] All `.adoc` documentation files are relocated to `src/main/asciidoc/modules/ROOT/pages/`.
- [x] All graphics and image assets are relocated to `src/main/asciidoc/modules/ROOT/images/`.

### ✓ Step 2: Replace Asciidoctor Maven plugin with Antora Maven plugin and clean up obsolete assets
Goal: Configure `pom.xml` to use `antora-maven-plugin`, remove obsolete docinfo navbar files, and verify successful site generation.
Scope: `pom.xml`, removal of `docinfo.html` / `docinfo-header.html`, and build verification.
Acceptance Criteria:
- [x] `pom.xml` declares and configures `org.antora:antora-maven-plugin`.
- [x] Obsolete custom docinfo files (`docinfo.html`, `docinfo-header.html`) are removed.
- [x] Running the Antora Maven plugin build succeeds and generates the complete documentation site with functional navigation and images.