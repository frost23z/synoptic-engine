#!/usr/bin/env bash
set -euo pipefail

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
