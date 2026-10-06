Usage

scripts/local-ci.sh            # same as the CI workflow: unit tests + foss/gms debug APKs
scripts/local-ci.sh test       # unit tests only, for the fastest feedback
scripts/local-ci.sh release    # same as the release workflow: tests + signed APKs + SHA256SUMS in dist/