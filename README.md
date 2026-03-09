# Freesia II

[![Issues](https://img.shields.io/github/issues/NguyenDevs/FreesiaII?style=flat-square)](https://github.com/NguyenDevs/FreesiaII/issues)
[![Commit Activity](https://img.shields.io/github/commit-activity/w/NguyenDevs/FreesiaII?style=flat-square)](https://github.com/NguyenDevs/FreesiaII/commits)
[![Stars](https://img.shields.io/github/stars/NguyenDevs/FreesiaII?style=flat-square)](https://github.com/NguyenDevs/FreesiaII/stargazers)
[![License](https://img.shields.io/github/license/NguyenDevs/FreesiaII)](LICENSE)

> A server-side proxy plugin that enables **Yes Steve Model (YSM)** custom player models and animations across BungeeCord/Velocity networks.

**Maintained by:** [NguyenDevs](https://github.com/NguyenDevs) · **Original author:** [MrHua269](https://github.com/MrHua269) · **License:** Mozilla Public License 2.0

> ⚠️ Freesia is **not** an official Yes Steve Model project.

---

## Background

This project originated as **Cyanidin**, was later renamed **Freesia** by *KikirMeow*, and is now continued under the name **Freesia II** by *NguyenDevs* after an extended period of inactivity and accumulated bugs.

---

## How It Works

Freesia is a hybrid between **MultiPaper** and **Geyser**. It uses MultiPaper's cross-server data exchange mechanism to synchronize YSM packets across a network — handling entity ID differences between worker and sub-servers, and determining player visibility across nodes.

### Architecture

| Component | Role |
|-----------|------|
| **Velocity/Waterfall** | Proxy layer — forwards/processes YSM packets, routes players |
| **Worker** | Dedicated backend node — model sync, entity state, NBT generation, caching (Fabric 1.21, minimal mode) |
| **Backend** | Installed on sub-servers — handles player tracker checks, notifies Velocity |

> Worker nodes run in a stripped-down mode: no world saving, no standard game features, async YSM processing only. They are **not** added to the Velocity server list.

---

## Requirements

- **Velocity/Waterfall** proxy
- **Fabric 1.21** server(s) for Worker nodes + Yes Steve Model mod + Freesia-Worker
- Sub-servers (Spigot/Paper/etc.) with **Freesia-Backend** installed
- Consistent IP/port configuration across all components

---

## Installation

### Quick Start (Recommended for Testing)

1. Download `template server` from the [latest release]([https://github.com/YesSteveModel/Freesia/releases](https://github.com/NguyenDevs/YSM_SERVER))
2. After download completes, run `start.bat` in the created folder

### Production Setup

1. Install **Freesia-Velocity/Waterfall** on your Proxy
2. Set up **Worker** nodes (Fabric 1.21 + YSM mod + Freesia-Worker)
3. Install **Freesia-Backend** on all gameplay sub-servers

---

## Configuration

### Velocity — `Freesia-Velocity.toml`

```toml
[functions]
debug = false
kick_if_ysm_not_installed = false
ysm_detection_timeout_for_kicking = 30000   # milliseconds

[messages]
language = "en_US"   # Options: "en_US", "zh_CN", "vi_VN"

[worker]
worker_master_ip     = "localhost"
worker_master_port   = 19200
worker_msession_ip   = "localhost"
worker_msession_port = 19199
```

### Worker — `config/freesia-worker.toml`

```toml
[worker]
worker_master_ip                              = "localhost"
worker_master_port                            = 19200
controller_reconnect_interval                 = 1
player_data_cache_invalidate_interval_seconds = 30
```

### Worker — `server.properties`

```properties
server-ip=127.0.0.1
server-port=19199   # Must match worker_msession_port in Velocity config
```

> 🔒 **Security:** Never expose ports `19200` (master) or `19199` (msession) to the public internet.

---

## Features

- Full Yes Steve Model support on Velocity / plugin networks
- Asynchronous model synchronization and data generation
- Worker nodes optimized exclusively for YSM packet handling
- Optional kick for players without YSM mod installed
- Multi-language kick messages (`zh_CN` / `en_US` / `vi_VN`)
- Secure design — control and msession ports are internal only

---

## Performance

Freesia has not been formally benchmarked. According to MrHua269, it should handle around **130 concurrent players** without issue. Note that YSM's own cache synchronization can occasionally cause memory leaks and model sync issues.

---

## Building

**Linux / macOS**
```shell
chmod +777 ./gradlew
./gradlew build
```

**Windows**
```shell
gradlew.bat build
```

Build artifacts are output to the `build/libs/` directory of each module.

---

## Documentation & Resources

- 📖 [YSM Plugin Documentation](https://ysm.cfpa.team/wiki/freesia-plugin/)
- 🎬 [Full Setup Tutorial (YouTube)](https://www.youtube.com/watch?v=MdzhIFfh3u0) — Velocity + Spigot/Paper
- 📦 [Release](https://github.com/YesSteveModel/Freesia/releases](https://github.com/NguyenDevs/FreesiaII/releases)
