#!/bin/sh
set -eu
export VAULT_ADDR="${VAULT_ADDR:-http://vault-kms:8200}"
export VAULT_TOKEN="${VAULT_TOKEN:?VAULT_TOKEN is required}"
until vault status >/dev/null 2>&1; do sleep 1; done
vault secrets enable transit 2>/dev/null || true
vault write -f transit/keys/vault-totp-key type=aes256-gcm96
