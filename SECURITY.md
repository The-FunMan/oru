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

## Response

Reports are read on a best-effort basis. There is no bug bounty.
