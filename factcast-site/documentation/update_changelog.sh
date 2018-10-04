#!/bin/bash
git tag -l | xargs git tag -d && git fetch -t
github_changelog_generator -u Mercateo -p factcast -o content/changelog.md 
