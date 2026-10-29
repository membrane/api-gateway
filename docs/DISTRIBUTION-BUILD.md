# Releasing a Distribution

7.8.0 is used as a sample to illustrate process. Use the current number.

## Preparation

1. If there are breaking changes add them to docs/MIGRATION-GUIDE.md
2. Create a release notes description in distribution/release-notes

## Creating the Release

1. Actions/Pull Request for Release/Run workflow
2. From: master Version: 7.8.0
3. Wait for action to finish
   => a pull request is created
4. Approve job "Release 7.8.0"
5. Squash and merge
   => Release action is started

## Editing the Release Description

1. github/releases
2. click on edit on the version
3. check the release notes
4. check "latest"
5. click "Update release"
6. Make sure the release is shown as latest on the github project homepage

## Publishing to Maven Central

1. Run Action "Publish package to the Maven Central Repository" / Use Workflow from Tag: v7.8.0 / Release to v7.8.0
2. Run Workflow
3. Check if the release is available on Maven Central: https://central.sonatype.com/artifact/org.membrane-soa/membrane-api-gateway

## Publishing the Docker Image

1. "Build Docker Image" action should be started automatically
2. check that the image is at docker hub: https://hub.docker.com/r/predic8/membrane/tags
3. Run action "Promote Docker Tag to Latest" / Run Workflow
4. Use Workflow from Tag: v7.8.0
5. Existing tag to promote to latest: 7.8.0
6. Run workflow
7. Check if the new version is tagged as latest: docker run -it predic8/membrane
   => Membrane API Gateway 7.8.0 up and running!


## Publishing the JSON Schema for YAML

1. Look at gitlab membrane-api.io project. Is the file src/v7.8.0.json checked in?
2. Check if Job starts. Otherwise: gitlab: membrane-api.io / Pipelines / New Pipeline / New Pipeline
3. Try to download: https://www.membrane-api.io/v7.8.0.json

## Check Version

1. Download latest distribution
2. Unzip
3. ./membrane.sh
4. Open conf/apis.yaml in VSC and try autocomplete

## Cleanup

1. Merge the "Snapshot Version" pull request