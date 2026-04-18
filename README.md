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
| **Backend** | Installed on sub-servers — handles player tracker checks, notifies proxy |

> Worker nodes run in a stripped-down mode: no world saving, no standard game features, async YSM processing only. They are **not** added to the Velocity/Waterfall server list.

---

## Requirements

- **Velocity/Waterfall** proxy
- **Fabric 1.21** server(s) for Worker nodes + Yes Steve Model mod + Freesia-Worker
- Sub-servers (Spigot/Paper/etc.) with **Freesia-Backend** installed
- Consistent IP/port configuration across all components

---

## Installation

### Quick Start (Recommended for Testing)

1. Download `template server` from the [latest release](https://github.com/NguyenDevs/YSM_SERVER)
2. After download completes, run `start.bat` in the created folder

### Production Setup

1. Install **Freesia-Velocity/Waterfall** on your Proxy
2. Set up **Worker** nodes (Fabric 1.21 + YSM mod + Freesia-Worker)
3. Install **Freesia-Backend** on all gameplay sub-servers

---

## Configuration

> 🛡️ **Network Policy:** While mTLS provides robust authentication, it is highly recommended to restrict ports `19199` and `19200` to your internal network via firewall to minimize attack surface and optimize resource handling.

### Proxy — `plugins/Freesia/freesia_config.toml`

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

## Security & mTLS Setup

Freesia uses **Mutual TLS (mTLS)** 2-way authentication to secure traffic between the Proxy and Worker nodes.

### 1. Certificate Generation
Start both the Proxy and Worker once. They will automatically generate persistent identity files in their respective `security/` directories.

### 2. Trust Exchange
Exchange the public certificates to establish a mutual trust chain:
*   Copy `proxy_cert.pem` from Proxy to Worker's `security/` folder.
*   Copy `worker_cert.pem` from Worker to Proxy's `security/` folder.

### 3. Verification
Ensure the following configurations are set:

**Proxy — `freesia_security.toml`**
```toml
[security]
enable_tls = true
use_self_signed = true
cert_path = "security/proxy_cert.pem"
key_path = "security/proxy_key.pem"
trust_worker_cert_path = "security/worker_cert.pem"

[firewall]
enable_ip_filter = true
allowed_worker_ips = ["127.0.0.1"]
```

**Worker — `config/freesia_config.toml`**
```toml
[security]
enable_tls = true
trust_all = false
trust_proxy_cert_path = "security/proxy_cert.pem"
worker_cert_path = "security/worker_cert.pem"
worker_key_path = "security/worker_key.pem"
```

---

## Features

- **Mutual TLS (mTLS):** Full 2-way authenticated and encrypted communication.
- **Persistent PKI:** Automatic generation and storage of self-signed RSA-2048 certificates.
- **Zero-Latency Sockets:** Bypassed Nagle's Algorithm for immediate packet dispatch.
- **Multi-Backend Support:** Compatible with both Velocity and Waterfall (BungeeCord) proxies.
- **Optimized Worker:** Dedicated Fabric node for async YSM processing and caching.
- **Security Firewall:** Built-in IP whitelisting for additional network-level protection.

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
- 📦 [Release](https://github.com/NguyenDevs/FreesiaII/releases)
