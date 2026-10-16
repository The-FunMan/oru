# Security policy

## Supported versions

Only the latest stable release of Oru receives security fixes. Development prereleases are not supported.

## Reporting a vulnerability

Report it privately with GitHub's private vulnerability reporting: open the **Security** tab of this repository and choose **Report a vulnerability**. Do not open a public issue, and do not send the details by e-mail.

Include:

- the Oru version (Settings > About) and the Android version and device model;
- the steps that reproduce the problem, and the file or input involved;
- what you expect an attacker to gain.

## Scope

In scope:

- keystore import and export, and the handling of signing material;
- the external trigger that starts a patch run from another app;
- APK handling: reading, writing, copying and cleaning up files;
- installer paths, including Shizuku and root;
- network requests that Oru makes itself;
- the native code in `native/` and `native-packager/`.

Out of scope:

- vulnerabilities in the apps that Oru patches;
- the behaviour of patch bundles made by other authors. Report those to the repository of the bundle;
- problems that need a device that is already compromised.

## Release signing key

Releases of Oru are signed with one project key. The key is held as an encrypted secret in this repository's protected release environments and as an offline backup of the maintainer, and it is never committed. Stable releases are built only from the `main` branch after the maintainer approves the run. The certificate fingerprint of the key is published in the release notes, and Oru's updater accepts an update only if it is signed with that certificate.

If the key is ever lost or may have leaked, the maintainer will:

1. revoke the secrets in the repository and stop the release workflows;
2. yank the affected releases with the yank workflow and say so in a pinned notice and on the project page;
3. publish the new certificate fingerprint and the steps to move to it, using Android's signing key rotation where the platform allows it and a clean reinstall where it does not.

If you think the key or a release has been compromised, report it as described above. Do not install a build whose fingerprint does not match the one in the release notes.

## Response

Reports are read on a best-effort basis. There is no bug bounty.
