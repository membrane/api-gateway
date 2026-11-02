# Release Checklist

7.8.0 is used as an example. Replace it with the version you are releasing.

Each step is marked **manual** (you do it) or **automatic** (a workflow does it; you only check the result).

## 1. Preparation (manual)

1. If there are breaking changes, add them to `docs/MIGRATION-GUIDE.md`.
2. Write the release notes to `distribution/release-notes/7.8.0.md`. The file name must match the version: it becomes the description of the GitHub release.

## 2. Release Pull Request (manual)

1. GitHub Actions: run **Pull Request for Release** (`release-pr.yml`) with:
   - Branch: `master`. For a patch release on an older line, use its maintenance branch, e.g. `7.7.x`.
   - Version: `7.8.0`
2. Wait for the workflow to finish. It creates a pull request "Release 7.8.0".
3. Review the pull request and wait for its checks to pass.
4. Squash and merge it.

## 3. Create Release (automatic)

Merging the "Release 7.8.0" pull request starts **Create Release** (`release-build.yml`). It:

- creates the GitHub release v7.8.0 as a **pre-release**,
- triggers the website pipeline that publishes the JSON Schema (step 7),
- starts **Build Docker Image** with `latest=false` (step 6),
- opens a "Snapshot version" pull request (step 9).

Wait for it to finish before continuing.

## 4. Release Description (manual)

1. Open GitHub Releases and edit v7.8.0.
2. Check the release notes.
3. Uncheck "Set as a pre-release" and check "Set as the latest release". For a patch release on an older line (e.g. 7.7.x), do not set it as latest.
4. Click "Update release".
5. Make sure the release is shown as latest on the GitHub project homepage.

## 5. Maven Central (manual)

This workflow never starts on its own: the release is created with `GITHUB_TOKEN`, which does not trigger other workflows.

1. GitHub Actions: run **Publish package to the Maven Central Repository** (`maven-publish.yml`):
   - Use workflow from: tag `v7.8.0`
2. Check that the release appears at https://central.sonatype.com/artifact/org.membrane-soa/membrane-api-gateway (this can take a while).

## 6. Docker Image

1. **Automatic:** check that **Build Docker Image** succeeded and that `7.8.0` is listed at https://hub.docker.com/r/predic8/membrane/tags
2. **Manual:** the build does not move `latest`. Run **Promote Docker Tag to Latest** (`update-docker-latest.yml`); skip this for a patch release on an older line:
   - Use workflow from: tag `v7.8.0`
   - Existing tag to promote to latest: `7.8.0`
3. Check:

   ```sh
   docker run -it predic8/membrane
   ```

   => `Membrane API Gateway 7.8.0 up and running!`

## 7. JSON Schema for YAML (automatic)

Create Release triggers the website pipeline. Check that https://www.membrane-api.io/v7.8.0.json can be downloaded.

If not, run **Trigger Website Pipeline** (`trigger-website-pipeline.yml`) in GitHub Actions with version `7.8.0`.

## 8. Check the Distribution (manual)

1. Download the distribution from the GitHub release.
2. Unzip it.
3. Run `./membrane.sh`.
4. Open `conf/apis.yaml` in VS Code and check that autocomplete works.

## 9. Cleanup (manual)

The "Snapshot version" pull request is created automatically. Merge it.
