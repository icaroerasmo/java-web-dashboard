#!/usr/bin/env bash
set -euo pipefail

# ============================================================================
# Teste local do java-web-dashboard com streams reais.
#
# A camera wall depende do go2rtc (porta 1984). Há duas formas de fornecê-lo:
#
#   A) (padrão) Encaminhar o go2rtc de um servidor remoto para localhost via túnel SSH.
#   B) (--local-go2rtc) Subir um go2rtc LOCAL via Docker/Podman, com um stream
#      de teste gerado pelo ffmpeg (padrão de barras de cor). Opcionalmente
#      injeta uma câmera real via GO2RTC_CAMERA_URL.
#
# Em ambos os casos o backend sobe apontando para http://localhost:1984.
#
# RabbitMQ (detecções/notificações) — duas formas de fornecê-lo:
#   - (--rabbitmq) Encaminha o RabbitMQ do servidor remoto via túnel SSH.
#   - (--local-rabbitmq) Sobe um RabbitMQ LOCAL via Docker/Podman.
#
# Sem RabbitMQ o dashboard funciona (camera wall, configs), mas detecções e
# notificações ficam sem dados e o backend loga tentativas de conexão a cada 5s.
#
# Modo full (padrão): compila o frontend p/ src/main/resources/static
# e serve tudo em http://localhost:8080.
# Modo --serve: hot-reload com 'ng serve' + proxy em http://localhost:4200.
#
# Uso:  ./dashboard-local-test.sh [--local-go2rtc] [--local-rabbitmq|--rabbitmq] [--serve] [--help]
#
# Env vars úteis:
#   GO2RTC_CAMERA_URL   URL RTSP de uma câmera real p/ publicar como stream "camera"
#                       no go2rtc local (só tem efeito com --local-go2rtc).
#   CONTAINER_RUNTIME   "docker" ou "podman" (padrão: autodetecção).
# ============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="${REPO_DIR:-$SCRIPT_DIR}"
REMOTE_HOST="${REMOTE_HOST:-}"
JAVA_HOME_VAL="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-4200}"

MODE="full"
WITH_LOCAL_GO2RTC=0
WITH_RABBITMQ=0
WITH_LOCAL_RABBITMQ=0
TUNNEL_PIDS=()
BACKEND_PID=""
GO2RTC_BIN=""
GO2RTC_CONTAINER="go2rtc-local"
LOCAL_GO2RTC_CFG_DIR=""
RABBITMQ_BIN=""
RABBITMQ_CONTAINER="rabbitmq-local"

usage() {
  cat <<EOF
Uso: $0 [opções]

Opções:
  --local-go2rtc  Sobe um go2rtc LOCAL (Docker/Podman) em localhost:1984 com um
                  stream de teste (ffmpeg testsrc). Dispensa o túnel SSH da 1984.
  --serve         Modo hot-reload: backend em background + 'ng serve' com proxy
                  (abre em http://localhost:${FRONTEND_PORT})
  --rabbitmq      Encaminha RabbitMQ do servidor remoto (5672/15672) via túnel SSH
  --local-rabbitmq Sobe um RabbitMQ LOCAL (Docker/Podman) em localhost:5672/15672
  --help          Mostra esta ajuda

Sem --local-go2rtc, encaminha o go2rtc do servidor remoto via túnel SSH (porta 1984).

Sem --serve, roda o modo "full": compila o frontend para src/main/resources/static
e sobe o backend (abre em http://localhost:${BACKEND_PORT}).

Env vars opcionais:
  REPO_DIR, REMOTE_HOST, JAVA_HOME, BACKEND_PORT, FRONTEND_PORT
  GO2RTC_CAMERA_URL (câmera real no go2rtc local), CONTAINER_RUNTIME (docker|podman)
EOF
}

for arg in "$@"; do
  case "$arg" in
    --local-go2rtc) WITH_LOCAL_GO2RTC=1 ;;
    --serve) MODE="serve" ;;
    --rabbitmq) WITH_RABBITMQ=1 ;;
    --local-rabbitmq) WITH_LOCAL_RABBITMQ=1 ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Opção desconhecida: $arg" >&2; usage >&2; exit 1 ;;
  esac
done

cd "$REPO_DIR"

cleanup() {
  for pid in "${TUNNEL_PIDS[@]:-}"; do
    kill "$pid" 2>/dev/null || true
  done
  if [[ -n "$BACKEND_PID" ]]; then
    kill "$BACKEND_PID" 2>/dev/null || true
  fi
  if (( WITH_LOCAL_GO2RTC )) && [[ -n "$GO2RTC_BIN" ]]; then
    "$GO2RTC_BIN" rm -f "$GO2RTC_CONTAINER" >/dev/null 2>&1 || true
    if [[ -n "$LOCAL_GO2RTC_CFG_DIR" ]]; then
      rm -rf "$LOCAL_GO2RTC_CFG_DIR" 2>/dev/null || true
    fi
  fi
  if (( WITH_LOCAL_RABBITMQ )) && [[ -n "$RABBITMQ_BIN" ]]; then
    "$RABBITMQ_BIN" rm -f "$RABBITMQ_CONTAINER" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

port_open() {
  local port="$1"
  (exec 3<>"/dev/tcp/127.0.0.1/${port}") 2>/dev/null && { exec 3>&- 3<&- || true; return 0; }
  return 1
}

ensure_tunnel() {
  local port="$1"
  if [[ -z "$REMOTE_HOST" ]]; then
    echo "ERRO: defina REMOTE_HOST (host do servidor remoto) para usar túnel SSH." >&2
    exit 1
  fi
  if port_open "$port"; then
    echo "   porta ${port} já acessível localmente — pulando túnel"
    return 0
  fi
  ssh -N -o ExitOnForwardFailure=yes -o ConnectTimeout=10 \
      -L "${port}:127.0.0.1:${port}" "$REMOTE_HOST" &
  TUNNEL_PIDS+=("$!")
  echo "   túnel 127.0.0.1:${port} -> ${REMOTE_HOST}:${port} (pid $!)"
}

wait_for_port() {
  local port="$1" timeout="${2:-15}"
  local i=0
  while (( i < timeout )); do
    if port_open "$port"; then return 0; fi
    sleep 1
    ((i++))
  done
  return 1
}

detect_runtime() {
  if [[ -n "${CONTAINER_RUNTIME:-}" ]]; then
    echo "$CONTAINER_RUNTIME"; return
  fi
  if docker ps >/dev/null 2>&1; then echo docker; else echo podman; fi
}

start_local_go2rtc() {
  GO2RTC_BIN="$(detect_runtime)"
  echo "==> Subindo go2rtc local (${GO2RTC_BIN}) com stream de teste ..."
  LOCAL_GO2RTC_CFG_DIR="$(mktemp -d)"

  local camera_line=""
  if [[ -n "${GO2RTC_CAMERA_URL:-}" ]]; then
    camera_line="  camera: '${GO2RTC_CAMERA_URL}'"
    echo "   stream 'camera' <- ${GO2RTC_CAMERA_URL}"
  fi

  cat > "$LOCAL_GO2RTC_CFG_DIR/go2rtc.yaml" <<EOF
api:
  origin: "*"
rtsp:
  listen: ":8554"
streams:
  test: "exec:ffmpeg -hide_banner -re -f lavfi -i testsrc=size=1280x720:rate=30 -c:v libx264 -g 50 -preset superfast -tune zerolatency -pix_fmt yuv420p -rtsp_transport tcp -f rtsp {output}"
${camera_line}
EOF

  "$GO2RTC_BIN" run -d --rm --name "$GO2RTC_CONTAINER" \
    -p 1984:1984 -p 8554:8554 -p 8555:8555/tcp -p 8555:8555/udp \
    -v "$LOCAL_GO2RTC_CFG_DIR/go2rtc.yaml:/config/go2rtc.yaml:ro" \
    ghcr.io/alexxit/go2rtc:latest
}

start_local_rabbitmq() {
  RABBITMQ_BIN="$(detect_runtime)"
  echo "==> Subindo RabbitMQ local (${RABBITMQ_BIN}) em localhost:5672/15672 ..."
  "$RABBITMQ_BIN" run -d --rm --name "$RABBITMQ_CONTAINER" \
    -p 5672:5672 -p 15672:15672 \
    docker.io/library/rabbitmq:3.13-management
}

if (( WITH_LOCAL_GO2RTC )); then
  start_local_go2rtc
else
  echo "==> Iniciando túneis SSH para '${REMOTE_HOST}' ..."
  ensure_tunnel 1984          # go2rtc
fi

if (( WITH_LOCAL_RABBITMQ )); then
  start_local_rabbitmq
elif (( WITH_RABBITMQ )); then
  ensure_tunnel 5672          # rabbitmq amqp
  ensure_tunnel 15672         # rabbitmq management
fi

echo "==> Aguardando go2rtc (porta 1984) ..."
wait_for_port 1984 20 || { echo "ERRO: porta 1984 não ficou acessível." >&2; exit 1; }
echo "==> go2rtc acessível em http://127.0.0.1:1984"

export JAVA_HOME="$JAVA_HOME_VAL"
export DASHBOARD_GO2RTC_BASE_URL="http://127.0.0.1:1984"

if (( WITH_LOCAL_RABBITMQ || WITH_RABBITMQ )); then
  wait_for_port 5672 60 || { echo "ERRO: RabbitMQ (porta 5672) não ficou acessível." >&2; exit 1; }
  export RABBITMQ_HOST="127.0.0.1"
  export RABBITMQ_PORT="5672"
  export DASHBOARD_RABBITMQ_MANAGEMENT_URL="http://127.0.0.1:15672"
  echo "==> RabbitMQ acessível em 127.0.0.1:5672 / management 127.0.0.1:15672"
fi

if [[ "$MODE" == "serve" ]]; then
  echo "==> Subindo backend em background (porta ${BACKEND_PORT}) ..."
  mvn spring-boot:run &
  BACKEND_PID=$!
  wait_for_port "$BACKEND_PORT" 90 || { echo "ERRO: backend não subiu na porta ${BACKEND_PORT}." >&2; exit 1; }
  echo "==> Backend OK em http://localhost:${BACKEND_PORT}"
  echo "==> Iniciando 'ng serve' com proxy (http://localhost:${FRONTEND_PORT}) ..."
  cd frontend
  npx ng serve --proxy-config proxy.conf.json --port "$FRONTEND_PORT"
else
  echo "==> Compilando frontend ..."
  ( cd frontend && npm run build )
  echo "==> Copiando build para src/main/resources/static ..."
  mkdir -p src/main/resources/static
  rm -rf src/main/resources/static/*
  cp -r frontend/dist/frontend/browser/* src/main/resources/static/
  echo "==> Subindo backend (http://localhost:${BACKEND_PORT}) ..."
  mvn spring-boot:run
fi
