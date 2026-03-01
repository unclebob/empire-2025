#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root_dir"

app_forbidden='empire\.atoms|empire\.ui|quil\.core|empire\.game-loop|empire\.test-utils|empire\.acceptance\.parser|empire\.acceptance\.generator'

app_hits="$(rg -n "$app_forbidden" src/empire/application --glob '!src/empire/application/runtime.cljc' || true)"
if [[ -n "$app_hits" ]]; then
  echo "Architecture boundary violation: application layer has forbidden dependencies:"
  printf '%s\n' "$app_hits"
  exit 1
fi

domain_forbidden='empire\.atoms|empire\.ui|quil\.core|empire\.game-loop|empire\.test-utils|empire\.acceptance\.parser|empire\.acceptance\.generator|empire\.application'
domain_hits="$(rg -n "$domain_forbidden" src/empire/domain || true)"
if [[ -n "$domain_hits" ]]; then
  echo "Architecture boundary violation: domain layer has forbidden dependencies:"
  printf '%s\n' "$domain_hits"
  exit 1
fi

adapter_forbidden='empire\.acceptance\.parser|empire\.acceptance\.generator'
adapter_hits="$(rg -n "$adapter_forbidden" src/empire/adapters || true)"
if [[ -n "$adapter_hits" ]]; then
  echo "Architecture boundary violation: adapters depend on acceptance parser/generator:"
  printf '%s\n' "$adapter_hits"
  exit 1
fi

inner_mutation_hits="$(rg -n '\bswap!\b|\breset!\b|\bcompare-and-set!\b' src/empire/application src/empire/domain || true)"
if [[ -n "$inner_mutation_hits" ]]; then
  echo "Architecture boundary violation: mutation primitives in application/domain:"
  printf '%s\n' "$inner_mutation_hits"
  exit 1
fi

echo "Architecture boundary check passed"
