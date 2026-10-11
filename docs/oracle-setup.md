# Oracle Cloud Always Free: account and VM setup

**Status, 11 October 2026:** `tankbear-1` has been created in London with 2 OCPUs,
12 GB RAM, Ubuntu 24.04 Arm and a 100 GB boot volume. Reserved public IP:
`145.241.223.194`. SSH has been verified. See [instance record](oracle-instance.md)
for checks and remaining work. The application has not been deployed.

Written 6 October 2026 from general knowledge of Oracle Cloud and the Always Free limits read on 5 October (see
`hosting.md`). Oracle's own pages returned HTTP 403 to my tools, so button names and screens may differ slightly; if a
screen does not match, trust the screen and tell me what it says. The steps below
are the original setup guide; use the status above for the actual provisioned state.

## Before you start

- A card in your own name (credit or debit). Prepaid and virtual cards are often refused. Oracle places a small temporary
  authorisation to verify it; it is not a charge.
- A phone that can receive a text, and an email address you can open.
- Use your real name and the address on the card. Do not use a VPN while signing up: mismatches are the usual reason sign-ups
  are refused.
- Keep this in mind: the **home region is permanent**. Always Free resources can only be created in the home region.
  Choose **UK South (London)** (`uk-london-1`). Do not pick the nearest-looking default if it is not London.
  The static IP must be in the UK for the Fuel Finder allowlist.

## 1. Create the account

1. Go to oracle.com/cloud/free and choose to start for free (the page then goes to the Cloud sign-up form).
2. Enter country (United Kingdom), name and email, verify the email, and set an account (tenancy) name. Any name works;
   it is visible only to you. Suggested: `tankbear`.
3. Choose **UK South (London)** as the home region.
4. Add your card and verify your phone number when asked.
5. You get a free trial credit for 30 days and then the account stays on Always Free. After sign-up it can take a few
   minutes (occasionally longer) before the console works.
6. Turn on multi-factor authentication for your login (profile menu, then security).
7. Set a budget alert so any charge surprises you immediately: Billing, then Budgets, create a budget of 1 pound with an
   email alert at a low percentage.

## 2. Make an SSH key on your Mac

This is how you (and, if you choose, I) log in to the server. Run in Terminal:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/tankbear_oracle -C "tankbear-oracle"
cat ~/.ssh/tankbear_oracle.pub
```

The `.pub` file is safe to paste into Oracle. Never share the file without `.pub`. Give it a passphrase if you like.

## 3. Create the VM

In the console: Menu, Compute, Instances, Create instance.

- **Name:** `tankbear-1`
- **Image:** Canonical Ubuntu 24.04 for Arm (aarch64). If it is not offered, any recent Ubuntu LTS for Arm.
- **Shape:** change shape, Ampere, **VM.Standard.A1.Flex**. Set **2 OCPUs and 12 GB** memory (the Always Free allowance as
  read on 5 October; if the console shows a different Always Free ceiling, use that and tell me).
- **Networking:** create a new virtual cloud network and public subnet, and choose to assign a public IPv4 address.
- **SSH keys:** paste the contents of `tankbear_oracle.pub`.
- **Boot volume:** set a custom size, about 100 GB (the free total is 200 GB across everything; the Great Britain road
  graph needs several GB, plus room for builds).
- Check the page shows **Always Free eligible** labels before pressing Create.

### If you see "Out of host capacity"

This is common for Ampere shapes. Try again at different times of day, try a different availability domain if the
region offers more than one, or try 1 OCPU and 6 GB first and resize later. Upgrading the account to Pay As You Go is
reported to improve availability and to avoid idle-instance reclaim, and usage inside the Always Free limits stays free;
verify this in Oracle's current documentation before you upgrade, and keep the budget alert from step 1.7.

## 4. Give it a fixed address

The address we send to Fuel Finder must not change. Ephemeral public IPs can change if an instance is recreated.

- Networking, then IP management, then Reserved public IPs, create one in the same region, and attach it to the
  instance's network interface. Oracle documents reserved public IPs as part of Always Free; check the current limit on
  the page.
- Note the address. This is the one we ask Fuel Finder to allowlist.

## 5. Open only what is needed

In the VCN's security list (or network security group) keep **only** port 22 (SSH) for now. Do **not** open port 8002 (the
routing engine) to the internet. Later we add 443 (HTTPS) for the API only.

## 6. Check you can log in

```bash
ssh -i ~/.ssh/tankbear_oracle ubuntu@YOUR_RESERVED_IP
```

(The user is `ubuntu` for Ubuntu images.) Accept the host key prompt. If it connects, you are done with the manual part.

## 7. Send me (no secrets)

- The region you chose and the shape/size you got.
- The reserved public IP address.
- Whether SSH works.

Do **not** paste your private key, your Oracle password, your Fuel Finder client id or secret, or any card details.

## What happens next

1. You email Fuel Finder with the allowlist details: organisation name (you decide: individual developer or Tank Bear),
   stated reason (a price comparison app that refreshes Fuel Finder data every 15 minutes), location (Oracle Cloud UK
   South, London), and the static IP.
2. I build and test the server-side code locally first (Node host, refresh job, install script), and only run anything on
   the VM after you say so.
3. The Fuel Finder credentials go onto the VM yourself (file with owner-only permissions), not through chat or Git.
4. Valhalla with the Great Britain graph, HTTPS, the refresh timer, and a health check, step by step, each needing your go.
