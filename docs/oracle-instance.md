# Oracle instance record

Verified on 11 October 2026. Infrastructure setup is complete; application deployment
and Fuel Finder access are still pending.

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

[Open instance in Oracle Console](https://cloud.oracle.com/compute/instances/ocid1.instance.oc1.uk-london-1.anwgiljtk5ie6rqck3576b66uet5ce4tv4i3glhxj3weqd23wjq2p7d5eqyq/networking?region=uk-london-1).

The account remains on Free Trial / Always Free; no paid upgrade was performed.
The reserved address was created and attached after the instance launch.

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
- Host IPv4 firewall accepts established traffic, loopback, ICMP and new TCP 22;
  other inbound IPv4 traffic is rejected.
- The subnet's only inbound TCP rule is port 22. Oracle's default ICMP error rules
  remain enabled (type 3/code 4 globally and type 3 within the VCN). Outbound traffic
  is allowed. No IPv6 address was assigned.
- The image also runs rpcbind on port 111, but the cloud and host IPv4 firewall
  rules do not allow inbound access to it.

## Remaining work

- Account MFA and the guide's budget alert have not been verified or configured
  during VM provisioning.
- Test and review the local server changes before the authorised commit stage.
- Configure hostname, HTTPS, server-side credentials and deployment using
  [server-deploy.md](server-deploy.md). Ports 80/443 remain closed at this stage.
- Prove Fuel Finder refresh from this address; request allowlisting if needed.
  No credentials were uploaded and no upstream request was made during provisioning.
- Install Valhalla, build the Great Britain graph and measure journey latency.
- The continuous refresh/history collection period has not started.
