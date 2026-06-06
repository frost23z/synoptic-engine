#!/usr/bin/env bash
set -euo pipefail

echo "==> Installing CLI tooling (advanced search/find utilities)"
sudo apt-get update -qq
sudo apt-get install -y -qq ripgrep fd-find fzf bat jq tree
# Ubuntu ships these under alternate binary names; expose the canonical ones.
sudo ln -sf "$(command -v fdfind)" /usr/local/bin/fd
sudo ln -sf "$(command -v batcat)" /usr/local/bin/bat

echo "==> Making gradlew executable"
chmod +x api/gradlew

echo "==> Installing backend dependencies"
cd api
./gradlew dependencies

echo "==> Installing frontend dependencies"
cd ../web
pnpm install

echo "==> Installing Playwright browsers with system dependencies"
pnpm exec playwright install --with-deps chromium

cd ..

echo "==> Starting background services (postgres, mailpit)"
docker compose -f api/compose.yaml up -d

echo "==> Setup complete"
