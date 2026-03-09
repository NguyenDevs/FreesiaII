# Freesia II
[![Issues](https://img.shields.io/github/issues/NguyenDevs/FreesiaII?style=flat-square)](https://github.com/NguyenDevs/FreesiaII/issues)
![Commit Activity](https://img.shields.io/github/commit-activity/w/NguyenDevs/FreesiaII?style=flat-square)
![GitHub Repo stars](https://img.shields.io/github/stars/NguyenDevs/FreesiaII?style=flat-square)
![GitHub License](https://img.shields.io/github/license/NguyenDevs/FreesiaII)

A continuation of Cyanidin (work in progress)

# Introduction

This project was formerly known as **Cyanidin**. After the original author abandoned it, the project was handed over and renamed **Freesia** by *KikirMeow*. 
After a long period without regular updates and with many bugs, the project is now being continued by *NguyenDevs* under the name **Freesia II**.
# Documentation

Please refer to the [YSM Documentation](https://ysm.cfpa.team/wiki/freesia-plugin/) for details.

# Building

```shell
chmod +777 ./gradlew
./gradlew build
```

```shell
gradlew.bat build
```

Build artifacts are located in the `build/libs` directory of each module.

# How It Works

In broad terms, Freesia is somewhat of a hybrid between MultiPaper and Geyser. Its working principles are based on MultiPaper's cross-server data exchange mechanism. For certain packets, since it needs to determine whether players can see each other and whether entity IDs differ between the worker and sub-servers, some modifications were made accordingly.

# Performance

Freesia's performance has not been formally benchmarked. However, according to MrHua269, it should be able to handle around 130 players without issue. That said, in many cases YSM's own cache synchronization can cause memory leaks and other unusual problems that break model sync.

# Tutorial Setup

[YesSteveModel - Server Velocity - Spigot/Paper Core full setup](https://www.youtube.com/watch?v=MdzhIFfh3u0)

**Freesia** is a server-side proxy plugin that enables **plugin-based servers** (BungeeCord/Velocity-style networks) to support **[Yes Steve Model (YSM)](https://github.com/YesSteveModel)** — allowing custom player models and animations across the network.

> **Important**: Freesia is **not** an official Yes Steve Model project.  
> It is released under the **Mozilla Public License Version 2.0**.

Original author: [MrHua269](https://github.com/MrHua269)  
Currently maintained by: [NguyenDevs](https://github.com/NguyenDevs)

## Architecture

Freesia consists of three main components:

- **Velocity** — Proxy layer: Forwards and processes YSM packets, routes players  
- **Worker** — Dedicated backend node: Handles model synchronization, entity state updates, NBT generation, caching (runs a minimal Fabric 1.21 server)  
- **Backend** — Installed on sub-servers (plugin servers): Handles player Tracker checks and notifies Velocity

Worker nodes are **not** added to the Velocity server list and run in a stripped-down mode (most game features disabled, no world saving, async YSM processing).

## Features

- Full Yes Steve Model support on Velocity / plugin networks  
- Asynchronous model synchronization & data generation  
- Worker nodes optimized for YSM packet handling only  
- Optional kick for players without YSM mod installed  
- Multi-language kick messages (zh_CN / en_US supported)  
- Secure design: control & msession ports should **not** be exposed publicly

## Requirements

- **Velocity** proxy  
- **Fabric 1.21** server(s) for Worker nodes + **Yes Steve Model** mod + **Freesia-Worker**  
- Sub-servers (Spigot/Paper/etc.) must install **Freesia-Backend**  
- Consistent IP/port configuration between components

## Installation

1. Download the latest release from:  
   https://github.com/YesSteveModel/Freesia/releases

2. Quick start (recommended for testing):  
   - Download `freesia-template.zip` from releases  
   - Extract it  
   - Run `install-freesia.bat` (Windows) or `install-freesia.sh` (Linux)  
   - After download finishes, run `start.bat` / `start.sh` in the created folder

3. For production setups:  
   - Install **Freesia-Velocity** plugin on your Velocity proxy  
   - Set up one or more **Worker** nodes (Fabric 1.21 + YSM + Freesia-Worker)  
   - Install **Freesia-Backend** on all gameplay sub-servers

## Configuration

### Velocity (Freesia-Velocity.toml)

```toml
[functions]
kick_if_ysm_not_installed = false
ysm_detection_timeout_for_kicking = 30000   # milliseconds

[messages]
language = "en_US"          # or "zh_CN"

[worker]
worker_master_ip   = "localhost"
worker_master_port = 19200
worker_msession_ip   = "localhost"
worker_msession_port = 19199
```

### Worker (Fabric server)

**Freesia config** (`config/freesia-worker.toml` or similar):

```toml
[worker]
worker_master_ip = "localhost"
worker_master_port = 19200
controller_reconnect_interval = 1
player_data_cache_invalidate_interval_seconds = 30
```

**server.properties**:

```properties
server-ip=127.0.0.1
server-port=19199          # must match worker_msession_port in Velocity config
```

> **Security warning**:  
> Never expose `worker_master_port` (19200) or `worker_msession_port` (19199) to the public internet.  
> Do **not** add Worker servers to Velocity's server list.

## Usage

1. Start Velocity with Freesia-Velocity installed  
2. Start one or more Worker nodes  
3. Start your sub-servers with Freesia-Backend  
4. Players with Yes Steve Model installed can now use custom models across the network
