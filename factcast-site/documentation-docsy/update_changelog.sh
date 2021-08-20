#!/bin/bash
git tag -l | xargs git tag -d && git fetch -t
# rm -rf /tmp/github*

 docker run -it --rm -u `id -u`:`id -g` -v "$(pwd)":/usr/local/src/your-app githubchangeloggenerator/github-changelog-generator \
  -u factcast -p factcast -o content/changelog.md --since-tag factcast-0.1.0-RC4 -t $GHCLT &&  (\
 cd content

# cat ../unfiltered_changelog.header > unfiltered-changelog.md

# cat changelog.md | \
#  grep -v "Snyk" | \
#  grep -v "dependabot-" | \
#  grep -v "renovate" | \
#  grep -v "Dependabot" | \
#  grep -v "DepShield" \
#  >> changelog1.md
  
#  cat changelog.md >> unfiltered-changelog.md

  echo "+++">c.md
  echo "title = \"Changelog\"">>c.md
  echo "weight = 100010">>c.md
  echo "type = \"docs\"">>c.md
  echo "+++">>c.md
  echo "">>c.md
  
#  echo "{{% alert info %}}This changelog is filtered. All automatically created PRs regarding dependency upgrades are hidden for readabilities sake. If you are interested, please look at '[unfiltered-changelog](/about/unfiltered-changelog)' {{% /alert %}}">>c.md
  echo "">>c.md

  tail -n +3 changelog.md >> c.md  

  mv c.md en/About/changelog.md
#  mv unfiltered-changelog.md en/About
#  rm changelog1.md
#  rm changelog.md
  
)   
