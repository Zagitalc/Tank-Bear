# Oracle instance record

Verified on 11 October 2026. The API is deployed over HTTPS and Fuel Finder refresh
has succeeded; Great Britain routing is deployed and benchmarked.

## Provisioned resources

| Setting | Value |
| --- | --- |
| Tenancy / compartment | `tank3281` / root |
| Instance | `tankbear-1` |
| Region / availability domain | `uk-london-1` / AD-2 |
| Shape | `VM.Standard.A1.Flex`, labelled Always Free-eligible in the console |
| Compute | 2 OCPUs, 12 GB RAM |
| Image | Canonical Ubuntu 24.04 Minimal aarch64, build `2026.09.18-0` |
| Boot volume | 100 GB, Balanced (10 VPU), in-transit encryption enabled |
| VCN / subnet | `tankbear-vcn` / `tankbear-public`, `10.0.0.0/24` |
| Private IPv4 | `10.0.0.117` |
| Reserved public IPv4 | `145.241.223.194`, named `tankbear-public-ip` |
| Public hostname | `tankbear.duckdns.org` (DuckDNS free account, managed via the owner's GitHub login) |

[Open instance in Oracle Console](https://cloud.oracle.com/compute/instances/ocid1.instance.oc1.uk-london-1.anwgiljtk5ie6rqck3576b66uet5ce4tv4i3glhxj3weqd23wjq2p7d5eqyq/networking?region=uk-london-1).

The account remains on Free Trial / Always Free; no paid upgrade was performed.
The reserved address was created and attached after the instance launch.

On 11 October 2026, `tankbear.duckdns.org` was registered and its A record set to
`145.241.223.194`. Both Cloudflare and Google public DNS resolvers returned that
address. No DuckDNS token is stored in this repository. The API is now live at
`https://tankbear.duckdns.org/status` with a verified Let’s Encrypt certificate.

## Access and verification

On the owner's Mac:

```bash
ssh -i ~/.ssh/tankbear_oracle ubuntu@145.241.223.194
```

The dedicated Ed25519 private key is stored outside this repository under `~/.ssh`.
Only its public key was supplied to Oracle. Public-key fingerprint:
`SHA256:dDnWBv8I/9FpJZla7xap8cyZxhFqQa4/mbtr8agFW3U`.

Verified over SSH:

- Ubuntu 24.04.5 LTS, `aarch64`, 2 CPUs, 11,927 MiB reported RAM.
- Root filesystem reports 96 GiB total and approximately 95 GiB free.
- SSH public-key authentication succeeds; password authentication is disabled.
- Host IPv4 firewall accepts established traffic, loopback, ICMP and new TCP 22, 80 and 443;
  other inbound IPv4 traffic is rejected.
- The subnet allows inbound TCP 22, 80 and 443. Oracle's default ICMP error rules
  remain enabled (type 3/code 4 globally and type 3 within the VCN). Outbound traffic
  is allowed. No IPv6 address was assigned.
- The image also runs rpcbind on port 111, but the cloud and host IPv4 firewall
  rules do not allow inbound access to it.

## Remaining work

- Account MFA and the guide's budget alert have not been verified or configured
  during VM provisioning.
- Code commit `1c38864` on `server/oracle-deployment` is deployed. API and Caddy
  are active and enabled at boot; API binds only to `127.0.0.1:8787`.
- Fuel Finder credentials were entered privately by the owner. The app key was
  generated on the VM; both environment files have mode 0640.
  See [server-deploy.md](server-deploy.md).
- First Fuel Finder refresh succeeded on 11 October at 12:12 UTC (13:12 BST),
  without an allowlist change: 8,131 stations, 8,127 priced stations.
- Valhalla 3.9.1 is pinned by Arm64 digest; Great Britain graph built successfully.
  It binds only to localhost:8002. Full sample searches took 0.43–1.62 seconds; see
  [routing deployment](routing-deployment.md).
- The 15-minute refresh and daily backup timers are enabled. History collection
  began with the first refresh at 12:11:36 UTC on 11 October 2026.
