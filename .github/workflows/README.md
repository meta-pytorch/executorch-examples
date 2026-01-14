# Workflows

## Android Build (`android-build.yml`)

This workflow builds Android demo applications and creates nightly releases.

### Trigger Events

1. **Scheduled (Nightly)**: Runs automatically every day at midnight UTC via cron schedule
2. **Manual Dispatch**: Can be triggered manually via GitHub Actions UI with optional parameters

### Jobs

#### Build Job

Builds APKs for the following apps:
- **LlamaDemo**: Located at `llm/android/LlamaDemo`
- **DeepLabV3Demo**: Located at `dl3/android/DeepLabV3Demo`

The build job:
- Sets up JDK 17
- Uses Gradle to build the APKs
- Supports using a custom local AAR file (via `local_aar` input parameter)
- Uploads APKs as workflow artifacts

#### Create Release Job

Runs only for scheduled (nightly) builds. This job:
- Downloads all APK artifacts from the build job
- Generates a date-based release tag (format: `nightly-YYYYMMDD`)
- Creates a GitHub release with:
  - Tag: `nightly-YYYYMMDD`
  - Name: `Nightly Build YYYY-MM-DD`
  - All built APK files attached
  - Marked as pre-release
  - Build metadata (date and commit SHA)

### How to Access Nightly Releases

Nightly builds are automatically published as GitHub Releases:

1. Go to the [Releases page](https://github.com/meta-pytorch/executorch-examples/releases)
2. Look for releases tagged with `nightly-YYYYMMDD`
3. Download the APK files from the release assets

### Manual Workflow Dispatch

To manually trigger a build:

1. Go to Actions → Android Build
2. Click "Run workflow"
3. Optionally provide a `local_aar` URL to use a custom ExecuTorch AAR file
4. Note: Manual runs do NOT create releases, only scheduled runs do

### Permissions

The workflow requires `contents: write` permission to create releases and push tags to the repository.
