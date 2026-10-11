![PortBridge cover with a cyan network bridge connecting two Minecraft islands](assets/portbridge-cover.png)

# PortBridge

[![CI](https://github.com/Despical/PortBridge/actions/workflows/build.yml/badge.svg)](https://github.com/Despical/PortBridge/actions/workflows/build.yml)
![Java](https://img.shields.io/badge/Java-25-007396.svg)
![Gradle](https://img.shields.io/badge/Gradle-9.8.0-079ec0?logo=gradle&logoColor=white)
![Paper](https://img.shields.io/badge/Paper-1.21.11%2B-62b47a)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

PortBridge shares a local Minecraft Java server through a managed [Bore TCP tunnel](https://github.com/ekzhang/bore), with a copyable address, route checks and automatic reconnection.

Created by **Despical**. Download it from the [Plugin Hub](https://hub.despical.dev/plugins/portbridge) and read the [documentation](https://docs.despical.dev/portbridge/) for setup details and relay settings.

---

## Features

- Automatic tunnel startup using the running server's bind address and port.
- A pinned Bore release with SHA-256 archive and cached-executable verification.
- Managed native executables for Windows, Linux and macOS, or a custom executable path.
- Random or requested public ports, with optional fallback when a requested port is unavailable.
- Minecraft status and matching ping checks for the local listener and public route.
- Configurable reconnect delays, retry limits and route failure thresholds.
- Clickable, permission-aware chat panels with address copy buttons.
- Configurable English messages, titles, separators and hex theme colors.
- Private relay support with an optional shared secret and displayed hostname.
- Background downloads and network checks, with managed process cleanup on shutdown.

---

## Installation

PortBridge targets **Paper 1.21.11 and later**. Use **Java 25 or newer** for every supported server version. Folia is not supported.

1. Download the plugin JAR from the [Plugin Hub](https://hub.despical.dev/plugins/portbridge) or [GitHub Releases](https://github.com/Despical/PortBridge/releases/latest), or follow [Build from Source](#build-from-source) below.
2. Stop the server and place `portbridge-<version>.jar` in its `plugins/` directory.
3. Keep `enable-status=true` in Minecraft's `server.properties` so PortBridge can check the local listener and public route.
4. Start the server with the Java version required by your Paper release and check the console for PortBridge startup and the connection address.
5. Run `/port address` as an operator or with the corresponding [permission](#permissions).

PortBridge installs `config.yml` and `messages.yml` under `plugins/PortBridge/` when they are missing. On the first tunnel start, it downloads Bore **v0.6.0** unless a verified cached binary or configured executable is available. Existing configuration files are preserved.

Automatic downloads support Windows x64/x86 and Linux/macOS x64/ARM64. Other platforms require a compatible executable configured in `bore.executable`.

Friends need no additional software. Keep the computer, Minecraft server and tunnel running. The server's whitelist, account authentication and Minecraft version requirements still apply.

---

## Getting started

```text
/port help
/port status
/port address
```

Copy the address from the console or click the chat panel's Copy Address button. Friends enter it in Minecraft Java's **Multiplayer > Direct Connection** screen.

The default configuration opens a tunnel shortly after server startup. If automatic startup is disabled, run `/port start`. `/port stop` closes the tunnel and cancels automatic retries; `/port restart` opens a new session.

---

## Commands

`/port`, `/portbridge` and `/pb` share the same command tree. Console access is supported.

| Command | Purpose |
| --- | --- |
| `/port` or `/port status` | Show connection state, target, relay, latency, retries and the latest error. |
| `/port help` | Show available commands and clickable suggestions. |
| `/port address` or `/port ip` | Show and copy the current Minecraft connection address. |
| `/port start` | Start the tunnel in the background. |
| `/port stop` | Stop the tunnel and cancel automatic reconnection. |
| `/port restart` | Restart the tunnel; the public address may change. |
| `/port reload` | Validate settings and messages, then reconnect an active tunnel. |

---

## Permissions

`portbridge.admin` grants all command permissions. Every permission defaults to operators.

| Permission | Purpose |
| --- | --- |
| `portbridge.status` | View tunnel status and command help. |
| `portbridge.address` | View and copy the address, receive connection announcements and the optional join notification. |
| `portbridge.manage` | Start, stop and restart the tunnel. |
| `portbridge.reload` | Reload configuration and messages. |

Chat panels show controls only when the sender has the matching permission. Joining-player notifications use `portbridge.address` and `notifications.on-admin-join`.

---

## Configuration

- `config.yml`: startup, local listener, relay, Bore installation, reconnection, route checks and joining-player notifications.
- `messages.yml`: literal English text, titles, labels, buttons, hover text and `#RRGGBB` theme colors. Formatting tags are not parsed.

Edit files under `plugins/PortBridge/` and run `/port reload`. Reload validates both files before applying replacements. Invalid settings leave the current settings and tunnel active. Reload reconnects an active tunnel and preserves an explicit stop; `startup.auto-start` applies at server startup.

| Setting | Default | Purpose |
| --- | --- | --- |
| `startup.auto-start` | `true` | Open a tunnel when Paper starts. |
| `startup.delay-seconds` | `3` | Wait before preparing the tunnel and checking the local listener. |
| `local.auto-detect` | `true` | Read Paper's actual bind address and server port. |
| `local.host` / `local.port` | `127.0.0.1` / `25565` | Explicit target when automatic detection is disabled. |
| `relay.host` | `bore.pub` | Bore relay hostname; its control connection uses TCP port 7835. |
| `relay.public-host` | empty | Optional hostname displayed to players and used by public route probes. |
| `relay.port` | `0` | Public port; `0` asks the relay for a free port. |
| `relay.fallback-to-random` | `true` | Try a random port if the requested public port is occupied. |
| `relay.secret` | empty | Shared secret for a private relay. |
| `bore.auto-download` | `true` | Install the supported, pinned Bore binary automatically. |
| `bore.executable` | empty | Existing executable; relative paths use the plugin data directory. |
| `bore.download-timeout-seconds` | `60` | Binary download timeout. |
| `reconnect.enabled` | `true` | Retry failed connections automatically. |
| `reconnect.initial-delay-seconds` / `max-delay-seconds` | `5` / `60` | Exponential backoff limits. |
| `reconnect.max-attempts` | `0` | Retry limit; `0` retries indefinitely. |
| `health.enabled` | `true` | Verify Minecraft status through the public tunnel. |
| `health.interval-seconds` / `timeout-seconds` | `30` / `5` | Probe frequency and timeout; the timeout also applies to the local check. |
| `health.failure-threshold` | `3` | Consecutive failed public probes before reconnection. |
| `notifications.on-admin-join` | `true` | Show the address to joining players with `portbridge.address`. |
| `debug` | `false` | Enable additional Bore diagnostic output. |

The **local port** belongs to the running Minecraft server. The **public port** belongs to the relay. Automatic detection reads the current listener; it does not change Minecraft's local port or router settings. With automatic detection disabled, the chosen local listener must already be reachable.

Connection addresses can change after a restart or reconnection. A requested public port remains subject to relay availability and policy. Disabling `health.enabled` skips public route verification; the local Minecraft listener is still checked before a tunnel starts.

---

## Custom relay

The public `bore.pub` service is operated independently and may be unavailable. To use your own Bore relay, run:

```shell
bore server --secret your-private-secret
```

Set `relay.host` and `relay.secret` to match that server. Its TCP control port 7835 and allocated public ports must be reachable. Set `relay.public-host` only when clients and the Minecraft server can reach the forwarded port through that hostname.

Bore's secret authenticates tunnel creation; it does not encrypt forwarded traffic. Minecraft's own encryption, when enabled by its authentication flow, remains intact. PortBridge forwards TCP only, does not preserve real player IP addresses and does not expose Bedrock or UDP query services. A relay may add latency.

---

All command replies, status details, duration/latency labels and ACF permission/error replies can be customized in `messages.yml`. Templates use literal `{name}` placeholders; diagnostic `{reason}` and `{output}` values preserve the original exception or Bore output. Reload applies message changes to both chat panels and the command framework. See the [message reference](https://docs.despical.dev/portbridge/configuration/messages/).

## Build from Source

Install **Java 25** (build toolchain) and **Git**, then open the repository directory.

```bash
./gradlew clean build
```

On Windows, use `gradlew.bat clean build`.

The build runs unit tests and creates the plugin and source JARs under `build/libs/`. Install `portbridge-<version>.jar`; the `-plain`, `-sources` and Javadoc JARs are for developers and are not the server plugin.

Javadocs are optional: run `./gradlew javadocJar` (Windows: `gradlew.bat javadocJar`) to generate `build/docs/javadoc/` and the documentation JAR. CI runs the build and validates Javadocs separately.

Tests cover settings validation, retry limits, Bore output parsing, binary cache verification, archive extraction, Minecraft status packets and chat text layout. These checks use local fixtures and do not establish compatibility with every Paper release or prove live public-relay reachability.

The 1.0.0 release also passed isolated runtime checks on Paper 1.21.11 build 132 with Java 25 and Paper 26.3 build 157 with Java 25: startup, command registration, custom replies and permission errors, message reload validation and separate listener registration.

---

## Security and contributing

Report vulnerabilities privately as described in [SECURITY.md](SECURITY.md). Follow [CONTRIBUTING.md](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md).

Use four spaces, focused classes, English documentation and the existing GPL license/class Javadoc headers. Preserve command permissions, configuration keys and tunnel lifecycle behavior unless a change explicitly requires otherwise. Remove relay secrets and private server details from shared logs and configuration examples.

---

## License

PortBridge is licensed under [GPL-3.0-or-later](LICENSE). Third-party components retain their own licenses and bundled notices.
