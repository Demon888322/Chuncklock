# ChunkLock

ChunkLock is a Paper plugin for claiming chunks and protecting player builds.

## Features

- Claim and unclaim the chunk you are standing in.
- Trust and remove trusted players on your claims.
- Configure PvP, explosions, container access, redstone, and natural mob spawning per claim.
- Inspect claim ownership, trusted members, and flags.
- Visualize claim boundaries with particles.
- Store claims in SQLite or MySQL.
- Configure default and permission-based claim limits.

## Requirements and compatibility

- Java 21 or newer.
- Paper 1.20.4 or newer.
- The build and automated tests are checked against the Paper 1.20.4 and 1.21.11 APIs. Other Paper releases in between are not individually server-integration-tested.

## Installation

1. Download `ChunkLock.jar` from a release, or build it using the instructions below.
2. Copy it into the Paper server's `plugins` directory.
3. Start the server once to generate `plugins/ChunkLock/config.yml` and `messages.yml`.
4. Stop the server before changing the storage configuration, then restart it.

The default storage is SQLite and requires no external database service. For MySQL, set `storage.type` to `mysql` and configure the connection fields in `config.yml`. Use a dedicated database user and a strong password; do not publish server credentials.

## Commands

| Command | Description |
| --- | --- |
| `/chunk claim` | Claim the current chunk. |
| `/chunk unclaim` | Remove your claim on the current chunk. |
| `/chunk trust <player>` | Allow an online player to build in your current claim. |
| `/chunk untrust <player>` | Remove a trusted player from your current claim. |
| `/chunk flag <flag> <true\|false>` | Change a protection flag on your current claim. |
| `/chunk info` | Show the current claim's owner, trusted members, and flags. |
| `/chunk visualize` | Toggle claim-boundary particles. |
| `/chunk admin unclaim` | Remove the current chunk's claim. |

Available flags are `pvp`, `explosions`, `chest-access`, `redstone`, and `mob-spawning`. They default to `false`.

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `chunklock.claim` | Everyone | Claim, unclaim, trust, and untrust chunks. |
| `chunklock.flag` | Everyone | Change flags on owned claims. |
| `chunklock.admin` | Operators | Administratively unclaim the current chunk. |
| `chunklock.limit.<number>` | Not granted by default | Apply the matching configured claim limit. |

`claims.default-limit` sets the standard limit. `claims.permission-limits` maps limit values to permission nodes; the highest matching configured limit is used.

## Build and test

Install a JDK 21+ and Maven, then run:

```shell
mvn clean verify
```

To test against the newer Paper API endpoint:

```shell
mvn -Dpaper.api.version=1.21.11-R0.1-SNAPSHOT clean verify
```

The default build compiles against Paper 1.20.4 to preserve the oldest supported API baseline. The alternate build checks API compatibility against Paper 1.21.11. These checks do not replace testing on a live Paper server.

## Configuration

The generated `config.yml` documents storage, claim-limit, and visualizer settings. Message text can be customized in `messages.yml` using MiniMessage formatting.
