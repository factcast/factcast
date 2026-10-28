'use strict'

module.exports.register = function () {
    this.once('documentsConverted', ({contentCatalog}) => {
        for (const component of contentCatalog.getComponents()) {
            // Antora orders component.versions according to its version rules.
            // Resolve a page without specifying a version to let Antora select
            // the latest component version.
            const latestVersion = component.versions[0]?.version

            if (latestVersion == null) continue

            const pages = contentCatalog.findBy({
                component: component.name,
                version: latestVersion,
                family: 'page',
            })

            for (const page of pages) {
                const stableId = page.asciidoc?.attributes?.['stable']

                if (!stableId) continue

                validateStableId(stableId, page)

                const aliasRelative = `/t/${stableId}/index.adoc`

                contentCatalog.addFile({
                    src: {
                        component: component.name,
                        version: '',
                        module: 'ROOT',
                        family: 'alias',
                        relative: aliasRelative,
                    },
                    rel: page,
                })
            }
        }
    })
}

function validateStableId(stableId, page) {
    if (!/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(stableId)) {
        throw new Error(
            `Invalid stable-doc-id '${stableId}' in ${page.src.path}. ` +
            'Only letters, digits, ".", "_" and "-" are allowed.'
        )
    }
}
