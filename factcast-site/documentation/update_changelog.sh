#!/bin/bash
git tag -l | xargs git tag -d && git fetch -t
# rm -rf /tmp/github*

 docker run -it --rm -v "$(pwd)":/usr/local/src/your-app ferrarimarco/github-changelog-generator \
  -u factcast -p factcast -o content/changelog.md --since-tag factcast-0.1.0-RC4 -t $GHCLT &&  (\
 cd content
 cat changelog.md | \
  grep -v "Snyk" | \
  grep -v "dependabot-" | \
  grep -v "Dependabot" | \
  grep -v "DepShield" \
  > changelog1.md
  mv changelog.md unfiltered-changelog.md
  echo "This changelog is filtered. All automatically created PRs regarding dependency upgrades are hidden for readabilities sake. If you are interested, please look at '[unfiltered-changelog](/unfiltered-changelog)'">changelog.md
  echo "">>changelog.md
  echo "">>changelog.md
  
  cat changelog1.md >>changelog.md
  
  
  
)   
