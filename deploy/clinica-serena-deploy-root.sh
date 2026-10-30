#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

readonly app_dir=/opt/clinica-serena
readonly backup_dir="$app_dir/backups"
readonly project=clinica-serena

fail() {
  printf 'deployment_error=%s\n' "$1" >&2
  exit 1
}

loaded_output=$(gzip -dc | docker load)
mapfile -t loaded_images < <(sed -n 's/^Loaded image: //p' <<< "$loaded_output")
[[ ${#loaded_images[@]} -eq 1 ]] || fail 'expected exactly one image in deployment stream'
image_tag=${loaded_images[0]}
[[ "$image_tag" =~ ^clinica-serena-(backend|frontend):([a-f0-9]{40})$ ]] \
  || fail 'image tag is not an approved component and full commit SHA'
component=${BASH_REMATCH[1]}
image_sha=${BASH_REMATCH[2]}
service=$component
docker image inspect "$image_tag" >/dev/null || fail 'loaded image is unavailable'

timestamp=$(date -u +%Y%m%dT%H%M%SZ)
backup="$backup_dir/predeploy-${component}-${image_sha}-${timestamp}.dump"
[[ ! -e "$backup" ]] || fail 'backup path already exists'
install -d -o root -g root -m 700 "$backup_dir"
docker exec clinica-serena-postgres-1 sh -lc \
  'pg_dump -Fc -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > "$backup"
[[ -s "$backup" ]] || fail 'database backup is empty'
chmod 600 "$backup"
pg_restore -l "$backup" >/dev/null || fail 'database backup failed validation'

container="${project}-${service}-1"
current_image=$(docker inspect --format '{{.Image}}' "$container") \
  || fail 'current production container was not found'
rollback_tag="clinica-serena-${component}:rollback-${timestamp}"
docker tag "$current_image" "$rollback_tag"
docker tag "$image_tag" "clinica-serena-${component}:latest"

cd "$app_dir"
if ! docker compose up -d --no-build --force-recreate "$service"; then
  docker tag "$rollback_tag" "clinica-serena-${component}:latest"
  docker compose up -d --no-build --force-recreate "$service" || true
  fail "compose start failed; prior image restored; backup=$backup"
fi

health_ok=false
for _ in $(seq 1 60); do
  if curl --fail --silent --show-error --max-time 6 https://clinicaserena.fyi/ >/dev/null \
    && curl --fail --silent --show-error --max-time 6 \
      https://clinicaserena.fyi/api/v1/health >/dev/null; then
    health_ok=true
    break
  fi
  sleep 2
done

if [[ "$health_ok" != true ]]; then
  docker tag "$rollback_tag" "clinica-serena-${component}:latest"
  docker compose up -d --no-build --force-recreate "$service" || true
  for _ in $(seq 1 30); do
    if curl --fail --silent --show-error --max-time 6 https://clinicaserena.fyi/ >/dev/null \
      && curl --fail --silent --show-error --max-time 6 \
        https://clinicaserena.fyi/api/v1/health >/dev/null; then
      fail "health check failed; prior image restored; database backup retained at $backup"
    fi
    sleep 2
  done
  fail "health check failed and app rollback did not restore health; database backup retained at $backup"
fi

printf 'deployment_component=%s\n' "$component"
printf 'deployment_commit=%s\n' "$image_sha"
printf 'database_backup=%s\n' "$backup"
printf 'web_health=200\napi_health=200\n'

