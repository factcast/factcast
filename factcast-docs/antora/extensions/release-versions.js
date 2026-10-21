'use strict'

const legacyReleases = new Set(['0.12.5', '0.12.6'])

module.exports.register = function () {
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
    this.updateVariables({ contentAggregate: releases })
  })
}

function isSupportedRelease(version) {
  if (!/^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$/.test(version)) return false
  const [major, minor, patch] = version.split('.').map(Number)
  return major > 0 || minor > 12 || (minor === 12 && patch >= 5)
}
