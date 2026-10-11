#!/usr/bin/env bash
# Copies the backend code to the server. Run from your computer: deploy/oracle/push.sh user@host [ssh-key]
# No secrets are sent: the environment file lives only on the server.
set -euo pipefail
target="${1:?usage: push.sh user@host [ssh-key]}"
key="${2:-$HOME/.ssh/tankbear_oracle}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ssh_cmd="ssh -i ${key} -o StrictHostKeyChecking=accept-new"

rsync -az --delete -e "${ssh_cmd}" \
  --include='backend/' --include='backend/package.json' \
  --include='backend/src/***' --include='backend/server/***' --include='backend/migrations/***' \
  --exclude='*' "${root}/" "${target}:/tmp/tankbear-push/"
${ssh_cmd} "${target}" 'sudo rsync -a --delete /tmp/tankbear-push/ /opt/tankbear/ && rm -rf /tmp/tankbear-push && sudo chown -R root:root /opt/tankbear'
rsync -az -e "${ssh_cmd}" "${root}/deploy/oracle/" "${target}:/tmp/tankbear-deploy/"
echo "Pushed code to /opt/tankbear and the deploy kit to /tmp/tankbear-deploy on the server."
