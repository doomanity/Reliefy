# Signing key

`reliefy-release.p12` is Reliefy's app signing key (PKCS#12, encrypted with AES-256).

It is safe to keep in a public repo **only because its password is not here**: the
password lives in the GitHub Actions secret `RELIEFY_KEYSTORE_PASSWORD`
(Settings → Secrets and variables → Actions). Without that secret the build still runs,
but the APK is left unsigned and no release is published.

Keep a copy of the password somewhere safe. If it is lost, future versions can't be
installed as updates over the current app (Android checks that the key matches).
