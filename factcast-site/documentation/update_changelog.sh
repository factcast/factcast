#!/bin/bash
git tag -l | xargs git tag -d && git fetch -t
# rm -rf /tmp/github*

github_changelog_generator  -u factcast -p factcast -o content/changelog.md --since-tag factcast-0.1.0-RC4
