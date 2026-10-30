'use strict'

const legacyReleases = new Set(['0.12.5', '0.12.6'])

module.exports.register = function ({ config = {} }) {
  this.once('contentAggregated', ({ contentAggregate }) => {
    const releases = []
    const versions = new Set()

    for (const bucket of contentAggregate) {
      if (bucket.name !== 'docs') {
        releases.push(bucket)
        continue
      }

      // Antora merges both legacy tags into one "latest" bucket. Split by origin
      // before classification assigns page IDs, preserving each tag's own files.
      for (const origin of bucket.origins) {
        const version = origin.refname
        if (origin.reftype !== 'tag' || !isSupportedRelease(version)) continue

        const descriptor = origin.descriptor
        if (descriptor.version !== version && !(legacyReleases.has(version) && descriptor.version === 'latest')) {
          throw new Error(`Release ${version} must use its release tag as the documentation version (set version: true).`)
        }
        if (versions.has(version)) throw new Error(`Duplicate documentation release: ${version}`)
        versions.add(version)

        const release = {
          ...descriptor,
          version,
          origins: [origin],
          files: bucket.files.filter((file) => file.src.origin === origin),
        }
        if (descriptor.nav) {
          // Navigation resolution needs the origin attached by Antora's aggregator.
          release.nav = Object.assign([...descriptor.nav], { origin })
        }
        releases.push(release)
      }
    }

    if (!versions.size) throw new Error('No supported documentation releases found; fetch release tags first.')
    if (config.currentBlog) addCurrentBlog(releases, contentAggregate)
    this.updateVariables({ contentAggregate: releases })
  })
}

function addCurrentBlog(releases, contentAggregate) {
  const docs = contentAggregate.filter(({ name }) => name === 'docs')
  const branches = docs.flatMap(({ origins }) => origins).filter(({ reftype }) => reftype === 'branch')
  const files = docs.flatMap(({ files }) => files)
    .filter((file) => file.src.origin === branches[0] && file.path.startsWith('modules/blog/'))
  if (branches.length !== 1 || !files.length) {
    throw new Error('Release publishing requires one current branch source containing the blog module.')
  }

  const stableDocs = releases.filter(({ name }) => name === 'docs')
  const latest = [...stableDocs].sort((a, b) => b.version.localeCompare(a.version, 'en', { numeric: true }))[0]
  for (const release of stableDocs) {
    // Retain each tag's documentation, replacing every historical blog snapshot.
    release.files = release.files.filter((file) => !file.path.startsWith('modules/blog/'))
    if (release.nav) {
      release.nav = Object.assign(release.nav.filter((entry) => !entry.startsWith('modules/blog/')),
        { origin: release.nav.origin })
    }
  }
  // Reuse the blog extension's stable routes and shared navigation without
  // registering a development documentation version in Antora's catalog.
  latest.files.push(...files)
  latest.origins.push(branches[0])
  latest.nav ||= Object.assign([], { origin: latest.origins[0] })
  latest.nav.push('modules/blog/nav.adoc')
}

function isSupportedRelease(version) {
  if (!/^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$/.test(version)) return false
  const [major, minor, patch] = version.split('.').map(Number)
  return major > 0 || minor > 12 || (minor === 12 && patch >= 5)
}
