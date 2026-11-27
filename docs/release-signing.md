# Pianova signed release setup

The tag release workflow signs a minified APK and Android App Bundle and creates a **draft** GitHub Release for review. It runs only for version tags matching `v*`.

## One-time signing key setup

Create a release keystore locally (do not commit or upload the raw file to the repository):

```bash
keytool -genkeypair -v -keystore pianova-release.keystore -alias pianova-release -keyalg RSA -keysize 2048 -validity 10000
```

Keep the keystore and passwords backed up securely. Losing the signing key can prevent updates to apps signed with it.

## Configure repository Actions secrets

In the repository settings, add these Actions secrets:

- `RELEASE_KEYSTORE_BASE64`: Base64-encoded keystore bytes (one line).
- `RELEASE_KEYSTORE_PASSWORD`: keystore password.
- `RELEASE_KEY_ALIAS`: alias used when generating the key (example: `pianova-release`).
- `RELEASE_KEY_PASSWORD`: password for the key.

Encode the keystore locally. On Linux or Termux:

```bash
base64 -w 0 pianova-release.keystore
```

Copy the output directly into `RELEASE_KEYSTORE_BASE64`. Do not print the password in chat, commit it, or store it in workflow files.

## Trigger a release candidate

After the signing secrets are configured and CI is green:

```bash
git tag -a v1.0.0 -m "Pianova v1.0.0 release candidate"
git push origin v1.0.0
```

The workflow runs the full unit tests, dependency-license inventory, R8/minified APK and AAB builds, and signature verification. It uploads artifacts and creates a draft release. Review artifacts and the licensing status in `LICENSES.md` before publishing the draft.

## Signing configuration

Release signing is activated only when all four environment values are present. Pull-request and regular CI builds remain unsigned; minification and resource shrinking remain enabled for release validation.

