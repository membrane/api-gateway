# Membrane Installation Guide for RPMs (RHEL 8)

## Overview

This guide provides step-by-step instructions for installing **Membrane API Gateway** on RHEL 8 using RPM packages.

## Prerequisites

Before installation, ensure that:

- You have **root or sudo privileges**.
- **Java 21** or newer is installed and available in your `$PATH`.
- The **yum** or **dnf** package manager is available.

To verify your Java version:

```bash
 java -version
```

## Installation Steps

### 1. Add the Membrane Repository

Use the following command to add the Membrane repository:

```bash
sudo yum-config-manager --add-repo https://membrane.github.io/rpm/unstable/el8/membrane.repo
````

If `yum-config-manager` is not available, install it via:

 ```bash
 sudo yum install -y yum-utils
 ```

### 2. Install Membrane

Once the repository is added, install Membrane:

```bash
sudo yum install -y membrane
```

This will:

* Install Membrane binaries and scripts.
* Set up a systemd service (`membrane.service`).
* Place the default configuration at `/etc/membrane/proxies.xml`.

### 3. Configure Membrane

Edit the configuration file as needed:

```bash
sudo nano /etc/membrane/proxies.xml
```

Example:

```xml
<api port="2000">
  <target url="https://api.predic8.de"/>
</api>
```

### 4. Start and Enable the Service

Start Membrane:

```bash
sudo systemctl start membrane
```

Optionally, enable it to start automatically on boot:

```bash
sudo systemctl enable membrane
```

### 5. Verify Installation

Check service status and logs:

```bash
systemctl status membrane --no-pager
journalctl -u membrane -f
```

Test locally:

```bash
curl -v http://localhost:2000/
```
