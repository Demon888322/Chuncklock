# ChunkLock

[![Build and test](https://github.com/Demon888322/Chuncklock/actions/workflows/build.yml/badge.svg)](https://github.com/Demon888322/Chuncklock/actions/workflows/build.yml)

A Paper plugin for claiming chunks and protecting player builds. Manage access, tune protection rules per claim, and store claim data in SQLite or MySQL.

## Contents

- [Features](#features)
- [Requirements and compatibility](#requirements-and-compatibility)
- [Installation](#installation)
- [Commands](#commands)
- [Permissions](#permissions)
- [Configuration](#configuration)
- [Build and test](#build-and-test)
- [Contributing](#contributing)

## Features

- Claim, unclaim, and inspect the chunk you are standing in.
- Grant and revoke trusted-player access to your claims.
- Configure PvP, explosions, container access, redstone interaction, and natural mob spawning for each claim.
- Preview claim boundaries with particles.
- Use SQLite by default or configure a MySQL database.
- Set default and permission-based claim limits.

## Requirements and compatibility

| Requirement | Supported baseline |
| --- | --- |
| Java runtime | Java 21 or newer |
| Paper | 1.20.4 or newer |
| Verified Paper API endpoints | 1.20.4 and 1.21.11 |

The plugin compiles against Paper 1.20.4 by default to retain the oldest supported API baseline. GitHub Actions also builds and tests against Paper 1.21.11. Other releases between those API endpoints have not each been tested on a live server.

## Installation

1. Download or build `ChunkLock.jar` using the instructions in [Build and test](#build-and-test).
2. Copy the JAR into your Paper server's `plugins` directory.
3. Start the server. ChunkLock creates its default configuration and message files in `plugins/ChunkLock`.
4. Stop the server, adjust configuration if needed, and restart it.

SQLite is the default and needs no separate database service. To use MySQL, set `storage.type` to `mysql` and configure the connection under `storage.mysql` in `config.yml`. Use a dedicated database account and keep its password private.

## Commands

Run player commands while standing in the chunk they apply to.

| Command | Description |
| --- | --- |
| `/chunk claim` | Claim the current chunk. |
| `/chunk unclaim` | Remove your claim on the current chunk. |
| `/chunk trust <player>` | Trust an online player in your current claim. |
| `/chunk untrust <player>` | Remove a trusted player from your current claim. |
| `/chunk flag <flag> <true\|false>` | Enable or disable a protection flag. |
| `/chunk info` | Show the current claim's owner, members, and flags. |
| `/chunk visualize` | Toggle claim-boundary particles. |
| `/chunk admin unclaim` | Administratively unclaim the current chunk. |

Available flags are `pvp`, `explosions`, `chest-access`, `redstone`, and `mob-spawning`. Flags default to `false`; setting a flag to `true` enables that action in the claim.

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `chunklock.claim` | Everyone | Claim and unclaim chunks; manage trusted members. |
| `chunklock.flag` | Everyone | Change flags on claims you own. |
| `chunklock.admin` | Operators | Administratively unclaim a chunk. |
| `chunklock.limit.<number>` | Not granted by default | Assign a configured permission-based claim limit. |

Set `claims.default-limit` for the standard claim limit. Use `claims.permission-limits` to associate configured limits with permission nodes.

## Configuration

The generated `config.yml` includes settings for:

| Setting | Purpose |
| --- | --- |
| `storage.type` | Select `sqlite` or `mysql`. |
| `storage.sqlite.file` | Set the SQLite database file path. |
| `storage.mysql.*` | Configure the MySQL host, port, database, account, and SSL option. |
| `storage.pool.*` | Configure the database connection pool. |
| `claims.default-limit` | Set the default number of claims per player. |
| `claims.permission-limits` | Map claim limits to permission nodes. |
| `visualizer.*` | Configure particle interval and boundary spacing. |

Customize player-facing text in `messages.yml`. Messages support MiniMessage formatting.

## Build and test

Install a JDK 21 or newer and Maven, then run from the project root:

```shell
mvn clean verify
```

To compile and run tests against the newer Paper API endpoint:

```shell
mvn -Dpaper.api.version=1.21.11-R0.1-SNAPSHOT clean verify
```

The shaded plugin JAR is created at `target/ChunkLock.jar`. The GitHub Actions workflow runs both Paper API variants on pull requests and pushes to `main`. Automated API/build checks do not replace testing on a live Paper server.

## Contributing

Bug reports and pull requests are welcome. When submitting a change, describe the player-facing behavior and include relevant test coverage. Run `mvn clean verify` before opening a pull request.
