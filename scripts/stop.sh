#!/usr/bin/env sh
docker compose -f "$(dirname "$0")/../docker/docker-compose.yml" down
