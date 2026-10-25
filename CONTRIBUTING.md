# Contributing
- Keep the app **dependency-free and offline**. PRs that add `INTERNET`, a library, reflection or native code will fail `scripts/audit.sh` and be declined.
- Run `bash scripts/audit.sh` before opening a PR. Never add a keystore, token or personal data to the repo.
- New debloat entries: `com.amazon.*` only, include what it does and how you tested it (device + Fire OS version).
- Tested devices table welcome (open an issue with: model, Fire OS version, what worked).
