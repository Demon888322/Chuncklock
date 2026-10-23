# Contributing to ChunkLock

Thank you for taking the time to improve ChunkLock. Bug reports, documentation updates, and focused pull requests are welcome.

## Before opening an issue

- Search [existing issues](https://github.com/Demon888322/Chuncklock/issues) to avoid duplicates.
- For a bug, include the Paper build, Java version, plugin version, reproduction steps, expected behavior, and relevant sanitized log output.
- Never include database passwords, access tokens, player personal data, or other secrets in an issue.
- Report security vulnerabilities privately; see [SECURITY.md](SECURITY.md).

## Development setup

1. Install JDK 21 or newer and Maven.
2. Clone the repository and create a branch for your change.
3. Implement the smallest change that addresses the issue, keeping player-facing messages and documentation consistent with the behavior.
4. Add or update tests when changing behavior.
5. Run the verification commands below before submitting a pull request.

## Verification

Run the full build and test suite against the default Paper 1.20.4 API:

```shell
mvn clean verify
```

Also check the Paper 1.21.11 API target when a change affects Bukkit or Paper integration:

```shell
mvn -Dpaper.api.version=1.21.11-R0.1-SNAPSHOT clean verify
```

Both builds use Java 21. Do not commit generated files from `target/`, server logs, database files, credentials, or local IDE configuration.

## Pull request checklist

- [ ] The change is focused and its user-facing impact is explained.
- [ ] Relevant tests and documentation are added or updated.
- [ ] `mvn clean verify` passes.
- [ ] The Paper 1.21.11 check passes when applicable.
- [ ] No secrets, database files, logs, or generated build output are included.
- [ ] Compatibility or configuration changes are clearly called out.

Use the pull request template to summarize the change and its verification.
