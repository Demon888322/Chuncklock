# ChunkLock

[![Build and test](https://github.com/Demon888322/Chuncklock/actions/workflows/build.yml/badge.svg)](https://github.com/Demon888322/Chuncklock/actions/workflows/build.yml)

**ChunkLock** is a Paper plugin for claiming chunks and protecting player builds. Players can manage trusted members, configure protection flags per claim, and view claim boundaries in game. Claim data is stored locally with SQLite by default, with MySQL available as an alternative.

**Maintainer:** [Demon888322](https://github.com/Demon888322)

## Features

- Claim, unclaim, and inspect the chunk you are standing in.
- Grant and revoke trusted-player access to your claims.
- Control PvP, explosions, container access, redstone interactions, and natural mob spawning per claim.
- Preview claimed chunk boundaries with particles.
- Store claims in SQLite or MySQL.
- Give every player a configurable number of starter claim slots.
- Unlock additional slots by completing progressively harder in-game objectives or using Vault-supported currency.

## Requirements and compatibility

| Component | Requirement |
| --- | --- |
| Java runtime | Java 21 or newer |
| Paper server | 1.20.4 or newer |
| API compatibility builds | Paper 1.20.4 and Paper 1.21.11 |

The default build targets the Paper 1.20.4 API. Continuous integration also compiles and runs the automated tests against Paper 1.21.11. Intermediate releases are not individually tested on live servers; report any compatibility issues through [GitHub Issues](https://github.com/Demon888322/Chuncklock/issues).

## Installation

1. Download `ChunkLock.jar` from a project release, or build it using the instructions below.
2. Place the JAR in the server's `plugins` directory.
3. Start the Paper server to generate `plugins/ChunkLock/config.yml` and `messages.yml`.
4. Stop the server, edit the configuration if needed, and restart it.

SQLite is enabled by default and requires no separate database service. To use MySQL, set `storage.type` to `mysql` and configure `storage.mysql` in `config.yml`. Create a dedicated database account with only the permissions the plugin requires. Set credentials in the server's local configuration; never commit real secrets to source control.

## Claim progression

Players start with 16 claim slots by default. Server administrators can change `progression.starting-claims` and the overall `progression.maximum-claims` in `config.yml`.

After using the available slots, a player can unlock the next slot in either of two ways:

- Complete the current objective using `/chunk unlock task`.
- Pay the configured price using `/chunk unlock money`.

This progression limits how many chunks a player may claim; it does not prevent building in unclaimed chunks. Existing claim protection rules continue to apply to claimed chunks.

`/chunk progress` shows the player's current slot count and objective. Objectives rotate between online playtime, blocks mined in Survival mode, and hostile mobs defeated in Survival mode. Playtime includes idle time while connected. Objective targets increase after each full three-task cycle. Adjust the base targets and `progression.tasks.difficulty-multiplier` in the configuration.

Money unlocks require Vault and a compatible economy plugin. Vault is optional: without a registered economy, currency unlocks are unavailable and task progression continues to work. Set `progression.economy.cost-per-claim` to change the default price of 5,000 currency units. Disable either unlock route in `config.yml` if it is not wanted.

## Commands

Player commands apply to the chunk the player is currently standing in.

| Command | Description |
| --- | --- |
| `/chunk claim` | Claim the current chunk. |
| `/chunk unclaim` | Remove your claim on the current chunk. |
| `/chunk trust <player>` | Trust an online player in your current claim. |
| `/chunk untrust <player>` | Remove a trusted player from your current claim. |
| `/chunk flag <flag> <true\|false>` | Configure a protection flag on your current claim. |
| `/chunk info` | Show the current claim's owner, trusted members, and flags. |
| `/chunk progress` | View your claim slots and current unlock objective. |
| `/chunk unlock task` | Unlock a slot after completing the current objective. |
| `/chunk unlock money` | Purchase a slot using the configured economy price. |
| `/chunk visualize` | Toggle claim-boundary particles. |
| `/chunk admin unclaim` | Administratively remove the current chunk's claim. |

### Claim flags

All flags default to `false`. A flag set to `true` allows the corresponding action in the claim; trusted members retain their access.

| Flag | When `true` |
| --- | --- |
| `pvp` | Allows player-versus-player damage in the claim. |
| `explosions` | Allows explosions to affect blocks in the claim. |
| `chest-access` | Allows untrusted players to access containers. Also accepts `containers` and `container-access` as command aliases. |
| `redstone` | Allows untrusted players to use redstone controls. |
| `mob-spawning` | Allows natural and chunk-generation mob spawning. |

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `chunklock.claim` | Everyone | Claim and unclaim chunks; manage trusted members. |
| `chunklock.flag` | Everyone | Change flags on claims you own. |
| `chunklock.admin` | Operators | Administratively unclaim the current chunk. |
| `chunklock.progress` | Everyone | View claim-slot progression. |
| `chunklock.unlock` | Everyone | Unlock claim slots with completed objectives or currency. |
| `chunklock.limit.<number>` | Not granted | Apply the matching configured claim limit. |

`claims.default-limit` sets the standard claim limit. The `claims.permission-limits` section maps limit values to permission nodes. When a player has multiple configured limit permissions, the highest matching limit is used.

## Configuration

The plugin generates `config.yml` with settings for:

| Setting | Purpose |
| --- | --- |
| `storage.type` | Select `sqlite` or `mysql`. |
| `storage.sqlite.file` | SQLite database file; relative paths are resolved from the plugin data folder. |
| `storage.mysql.*` | MySQL host, port, database, username, password, and SSL setting. |
| `storage.pool.*` | Connection-pool size and connection timeout. |
| `claims.default-limit` | Default claim limit. |
| `claims.permission-limits` | Permission-based claim-limit tiers. |
| `progression.starting-claims` | Free claim slots for each player. |
| `progression.maximum-claims` | Server-wide cap on claim slots per player. |
| `progression.tasks.*` | Task objectives and difficulty scaling. |
| `progression.economy.cost-per-claim` | Currency price for each purchased slot. |
| `progression.economy.enabled` | Enable or disable currency unlocks. |
| `visualizer.interval-ticks` | Boundary-particle refresh interval, with a minimum of 5 ticks. |
| `visualizer.spacing` | Distance between boundary particles, in blocks. |

Customize player-facing text in `messages.yml`. Messages support MiniMessage formatting.

## Build and test

Install JDK 21 or newer and Maven, then run these commands from the project root:

```shell
mvn clean verify
```

To run the same verification against Paper 1.21.11:

```shell
mvn -Dpaper.api.version=1.21.11-R0.1-SNAPSHOT clean verify
```

The shaded plugin JAR is written to `target/ChunkLock.jar`. GitHub Actions runs both Paper API configurations for pushes and pull requests. These builds verify compilation and automated tests; they do not replace testing on a live Paper server.

## Contributing

Bug reports and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup and submission guidance. Review [SECURITY.md](SECURITY.md) to report a security concern privately.

## License

No license has been specified for this repository. Contact the maintainer before redistributing or reusing this project.
