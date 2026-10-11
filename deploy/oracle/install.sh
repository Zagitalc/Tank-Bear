#!/usr/bin/env bash
# Prepares a fresh Ubuntu 24.04 (arm64 or amd64) server for Tank Bear. Run once as root: sudo ./install.sh
# Safe to re-run. It installs software, creates the service user and folders, and installs the systemd units.
# It starts Docker, but no Tank Bear services, writes no secrets, and keeps routing private.
set -euo pipefail

NODE_VERSION="24.19.0"   # pinned patch within the Node 24 major in .nvmrc
APP_USER="tankbear"
APP_DIR="/opt/tankbear"
DATA_DIR="/var/lib/tankbear"
ENV_DIR="/etc/tankbear"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

[ "$(id -u)" -eq 0 ] || { echo "Run as root (sudo ./install.sh)." >&2; exit 1; }
. /etc/os-release
[ "${ID:-}" = "ubuntu" ] || { echo "Written for Ubuntu; found ${ID:-unknown}. Stop and ask." >&2; exit 1; }

case "$(uname -m)" in
  aarch64) NODE_ARCH="arm64" ;;
  x86_64)  NODE_ARCH="x64" ;;
  *) echo "Unsupported CPU: $(uname -m)" >&2; exit 1 ;;
esac

echo "== Packages"
export DEBIAN_FRONTEND=noninteractive
# Debian packages normally start services during installation. On the first install,
# keep Caddy stopped until its hostname and the API have been configured.
if ! dpkg-query -W -f='${Status}' caddy 2>/dev/null | grep -q 'install ok installed'; then
  if [ ! -e /run/systemd/system/caddy.service ] && [ ! -L /run/systemd/system/caddy.service ]; then
    systemctl mask --runtime caddy.service
    trap 'systemctl unmask --runtime caddy.service' EXIT
  fi
fi
apt-get update -y
apt-get install -y --no-install-recommends ca-certificates curl xz-utils sqlite3 rsync caddy docker.io docker-compose-v2 iptables-persistent
systemctl enable --now docker

echo "== Node ${NODE_VERSION} (official tarball, checksum verified)"
if [ ! -x /opt/node/bin/node ] || [ "$(/opt/node/bin/node --version)" != "v${NODE_VERSION}" ]; then
  tmp="$(mktemp -d)"
  base="https://nodejs.org/dist/v${NODE_VERSION}"
  file="node-v${NODE_VERSION}-linux-${NODE_ARCH}.tar.xz"
  curl -fsSLo "${tmp}/${file}" "${base}/${file}"
  curl -fsSLo "${tmp}/SHASUMS256.txt" "${base}/SHASUMS256.txt"
  (cd "${tmp}" && grep " ${file}\$" SHASUMS256.txt | sha256sum -c -)
  rm -rf /opt/node && mkdir -p /opt/node
  tar -xJf "${tmp}/${file}" -C /opt/node --strip-components=1
  rm -rf "${tmp}"
fi
/opt/node/bin/node --version

echo "== Service user and folders"
id -u "${APP_USER}" >/dev/null 2>&1 || useradd --system --home-dir "${DATA_DIR}" --shell /usr/sbin/nologin "${APP_USER}"
install -d -o "${APP_USER}" -g "${APP_USER}" -m 0750 "${DATA_DIR}" "${DATA_DIR}/backups"
install -d -o root -g root -m 0755 "${APP_DIR}"
install -d -o root -g "${APP_USER}" -m 0750 "${ENV_DIR}"
if [ ! -f "${ENV_DIR}/tankbear.env" ]; then
  install -o root -g "${APP_USER}" -m 0640 "${HERE}/tankbear.env.example" "${ENV_DIR}/tankbear.env"
  echo "Created ${ENV_DIR}/tankbear.env from the template. Fill it in (see docs/server-deploy.md)."
fi
install -d -m 0755 /var/lib/valhalla/custom_files

echo "== systemd units"
install -m 0644 "${HERE}"/systemd/tankbear-api.service "${HERE}"/systemd/tankbear-refresh.service \
  "${HERE}"/systemd/tankbear-refresh.timer "${HERE}"/systemd/tankbear-backup.service "${HERE}"/systemd/tankbear-backup.timer /etc/systemd/system/
install -m 0755 "${HERE}/backup.sh" /usr/local/bin/tankbear-backup
systemctl daemon-reload

echo "== Caddy (HTTPS)"
install -m 0644 "${HERE}/Caddyfile" /etc/caddy/Caddyfile
# Caddy receives only a hostname, never API keys or Fuel Finder credentials.
if [ ! -f /etc/caddy/tankbear.env ]; then
  install -o root -g caddy -m 0640 "${HERE}/caddy.env.example" /etc/caddy/tankbear.env
fi
install -d /etc/systemd/system/caddy.service.d
printf '[Service]\nEnvironmentFile=/etc/caddy/tankbear.env\n' > /etc/systemd/system/caddy.service.d/tankbear.conf
systemctl daemon-reload

echo "== Firewall (host): allow SSH, HTTP, HTTPS; the routing engine stays on localhost"
# Oracle's Ubuntu images ship iptables rules that reject new connections after SSH. Insert ours before that.
for port in 22 80 443; do
  iptables -C INPUT -p tcp --dport "${port}" -m conntrack --ctstate NEW -j ACCEPT 2>/dev/null \
    || iptables -I INPUT 1 -p tcp --dport "${port}" -m conntrack --ctstate NEW -j ACCEPT
done
netfilter-persistent save

cat <<MSG

Install finished. Docker is running; no Tank Bear services were started by this script.
Follow docs/server-deploy.md (fill ${ENV_DIR}/tankbear.env and /etc/caddy/tankbear.env,
start the API, verify a refresh, then enable its timer; routing is a separate stage).
Remember the cloud network (VCN security list) must also allow ports 80 and 443; port 8002 must stay closed.
MSG
