# SSL/TLS Tutorial

Configure how Membrane handles TLS connections: terminate them, share one TLS configuration
between APIs, or forward them untouched.

Each step is explained directly in the configuration file, which is also the Membrane config
you run. If possible, use an editor with YAML support such as Visual Studio Code or
IntelliJ IDEA.

The steps are independent of each other:

1. [10-TLS-Termination.yaml](10-TLS-Termination.yaml) — the gateway terminates TLS with its own
   certificate, decrypts the traffic and can inspect or modify it.
2. [20-Central-SSL-Config.yaml](20-Central-SSL-Config.yaml) — share one TLS configuration
   between several APIs.
3. [30-TLS-Passthrough.yaml](30-TLS-Passthrough.yaml) — the gateway forwards the encrypted
   connection untouched and routes it by the hostname in the TLS SNI extension.

Start with [10-TLS-Termination.yaml](10-TLS-Termination.yaml) and follow the instructions in
the file.
