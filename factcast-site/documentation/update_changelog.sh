#!/bin/bash
git tag -l | xargs git tag -d && git fetch -t
rm -rf /tmp/github*
github_changelog_generator -u Mercateo -p factcast -o content/changelog.md 
