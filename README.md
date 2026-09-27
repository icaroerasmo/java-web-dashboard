# java-web-dashboard

Dashboard web (Spring Boot BFF + Angular) para gerenciar os módulos do cafofo.

## Testando localmente

A camera wall depende do **go2rtc** (porta `1984`). Para testar localmente, o
backend precisa alcançar um go2rtc e o frontend conecta em `ws://<host>:1984`.
Há duas formas de fornecer esse go2rtc.

### Opção A — túnel SSH para o go2rtc do servidor remoto

Encaminha o go2rtc já existente no servidor remoto para `localhost:1984`.
Defina o host remoto via `REMOTE_HOST` (alias SSH usado pelo túnel):

```bash
REMOTE_HOST=meu-servidor ./dashboard-local-test.sh            # full: build frontend + backend em :8080
REMOTE_HOST=meu-servidor ./dashboard-local-test.sh --serve    # hot-reload: ng serve + proxy em :4200
REMOTE_HOST=meu-servidor ./dashboard-local-test.sh --rabbitmq # + túnel do RabbitMQ (detecções/notificações)
```

### Opção B — go2rtc local (sem depender do servidor remoto)

Sobe um go2rtc local com um stream de teste (barras de cor via ffmpeg):

```bash
./dashboard-local-test.sh --local-go2rtc
```

Para injetar uma câmera real no go2rtc local, use a env var `GO2RTC_CAMERA_URL`:

```bash
GO2RTC_CAMERA_URL='rtsp://admin:senha@<CAMERA_IP>:554/onvif1' \
  ./dashboard-local-test.sh --local-go2rtc --serve
```

#### Rodando o go2rtc local manualmente (sem o script)

Usa **Podman** (ou Docker) com o mesmo container de produção:

```bash
mkdir -p /tmp/go2rtc-local && cat > /tmp/go2rtc-local/go2rtc.yaml <<'YAML'
api:
  origin: "*"
rtsp:
  listen: ":8554"
streams:
  test: "exec:ffmpeg -hide_banner -re -f lavfi -i testsrc=size=1280x720:rate=30 -c:v libx264 -g 50 -preset superfast -tune zerolatency -pix_fmt yuv420p -rtsp_transport tcp -f rtsp {output}"
  camera: "rtsp://admin:senha@<CAMERA_IP>:554/onvif1"   # opcional: câmera real
YAML

podman run -d --rm --name go2rtc-local \
  -p 1984:1984 -p 8554:8554 -p 8555:8555/tcp -p 8555:8555/udp \
  -v /tmp/go2rtc-local/go2rtc.yaml:/config/go2rtc.yaml:ro \
  ghcr.io/alexxit/go2rtc:latest
```

Com o go2rtc de pé em `localhost:1984`, suba o backend (sem túnel):

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
DASHBOARD_GO2RTC_BASE_URL=http://localhost:1984 \
mvn spring-boot:run
```

Para um stream de teste "de verdade", troque a fonte do `exec` por qualquer
entrada lavfi do ffmpeg: `testsrc`, `testsrc2`, `smptebars`, `color=c=blue`, etc.
Ex.: `-i smptebars=size=1280x720:rate=30`.

Para parar: `podman rm -f go2rtc-local`.

## RabbitMQ (detecções e notificações)

O backend declara duas filas (`dashboard.detection.events` e
`dashboard.notifications`) e fica escutando-as para alimentar `/ws/detections` e
`/ws/notifications`. O host padrão é `rabbitmq:5672` (nome interno do Docker),
que **não resolve fora do compose** — por isso, ao rodar localmente sem
configurar, o log repete `Attempting to connect to: [rabbitmq:5672]` a cada 5s.

Isso é **não-fatal**: a camera wall e os configs funcionam; só detecções e
notificações ficam sem dados. Para resolver, forneça um RabbitMQ alcançável:

```bash
./dashboard-local-test.sh --local-rabbitmq   # sobe um RabbitMQ local (Docker/Podman)
./dashboard-local-test.sh --rabbitmq         # ou encaminha o RabbitMQ do servidor remoto via túnel SSH
```

Rodando o RabbitMQ local manualmente:

```bash
podman run -d --rm --name rabbitmq-local -p 5672:5672 -p 15672:15672 \
  docker.io/library/rabbitmq:3.13-management
```

Então suba o backend apontando para ele:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
RABBITMQ_HOST=127.0.0.1 \
DASHBOARD_RABBITMQ_MANAGEMENT_URL=http://127.0.0.1:15672 \
mvn spring-boot:run
```

> Use `127.0.0.1` (não `localhost`): o podman rootless publica as portas em IPv4
> e `localhost` pode resolver para `::1` (IPv6) antes, fazendo a conexão falhar.

### Verificação

```bash
curl http://localhost:1984/api/streams        # deve listar "test" (e "camera")
curl http://localhost:8080/api/go2rtc/streams # backend deve repassar as streams
```

Depois abra `http://localhost:8080` (full) ou `http://localhost:4200` (serve).

## Proxy de desenvolvimento

`frontend/proxy.conf.json` redireciona `/api` e `/ws` para o backend em
`localhost:8080` durante o `ng serve`.
