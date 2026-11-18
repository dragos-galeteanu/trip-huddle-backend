#!/usr/bin/env bash
# Starts the whole system on this machine for the smoke tests, or stops it:
#   scripts/smoke-stack.sh start   # infrastructure in Docker, the fake OpenAI, then every service's jar
#   scripts/smoke-stack.sh stop
# It needs the jars built (mvn -DskipTests package) and, for sign-in, the Firebase test project:
#   SMOKE_FIREBASE_PROJECT_ID, and GOOGLE_APPLICATION_CREDENTIALS for api-user's Admin SDK.
# Optional: STRIPE_SECRET_KEY and STRIPE_WEBHOOK_SECRET (test mode) for the payment flow.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUN="$ROOT/target/smoke-stack"
SERVICES=(api-platform api-user api-group api-license api-chat api-destination notification openai)
# api-payment refuses to start without Stripe keys, so it joins only when test-mode keys are given.
if [[ -n "${STRIPE_SECRET_KEY:-}" && -n "${STRIPE_WEBHOOK_SECRET:-}" ]]; then
    SERVICES+=(api-payment)
fi

stop() {
    if [[ -d "$RUN" ]]; then
        for pid in "$RUN"/*.pid; do
            [[ -e "$pid" ]] && kill "$(cat "$pid")" 2>/dev/null || true
        done
        rm -f "$RUN"/*.pid
    fi
    docker compose -f "$ROOT/docker-compose.yml" --profile smoke down -v
}

# The datasource URL a service names, with the compose host name replaced by localhost.
datasource() {
    grep -oE 'jdbc:postgresql://[^ ]+' "$ROOT/$1/src/main/resources/application.yml" | head -1 \
        | sed 's#//postgres:#//localhost:#'
}

launch() {
    local module="$1" jar
    jar="$(ls "$ROOT/$module"/target/"$module".jar 2>/dev/null || true)"
    [[ -n "$jar" ]] || { echo "Build first: mvn -DskipTests package ($module.jar is missing)"; exit 1; }
    local env=(
        RABBITMQ_HOST=localhost RABBITMQ_USERNAME=triphuddle RABBITMQ_PASSWORD=triphuddle RABBITMQ_VIRTUAL_HOST=/
        MONGODB_HOST=localhost
        EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://localhost:8761/eureka/
        MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://localhost:9411/api/v2/spans
        SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI="https://securetoken.google.com/$SMOKE_FIREBASE_PROJECT_ID"
        CHAT_STOMP_RELAY_ENABLED=true
    )
    if grep -q 'jdbc:postgresql' "$ROOT/$module/src/main/resources/application.yml" 2>/dev/null; then
        env+=(SPRING_DATASOURCE_URL="$(datasource "$module")")
    fi
    case "$module" in
        notification)
            env+=(SPRING_MAIL_HOST=localhost SPRING_MAIL_PORT=1025 SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=false
                  SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=false SMTP_PASSWORD=unused FIREBASE_ENABLED=false) ;;
        openai)
            env+=(INTEGRATION_OPENAI_BASE_URL=http://localhost:9999/v1 OPENAI_API_KEY=sk-fake) ;;
    esac
    env "${env[@]}" java -jar "$jar" --firebase.project-id="$SMOKE_FIREBASE_PROJECT_ID" \
        > "$RUN/$module.log" 2>&1 &
    echo $! > "$RUN/$module.pid"
}

wait_for() {
    local url="$1" name="$2"
    for _ in $(seq 1 120); do
        curl -fsS "$url" > /dev/null 2>&1 && { echo "$name is up"; return; }
        sleep 2
    done
    echo "$name did not come up; see $RUN/$name.log"
    exit 1
}

start() {
    : "${SMOKE_FIREBASE_PROJECT_ID:?Set SMOKE_FIREBASE_PROJECT_ID to the Firebase test project}"
    mkdir -p "$RUN"
    docker compose -f "$ROOT/docker-compose.yml" --profile smoke up -d --wait postgres mongodb rabbitmq zipkin mailpit
    python3 "$ROOT/smoke-tests/fakes/fake_openai.py" 9999 > "$RUN/fake-openai.log" 2>&1 &
    echo $! > "$RUN/fake-openai.pid"

    launch eureka-server
    wait_for http://localhost:8761/actuator/health eureka-server
    for module in "${SERVICES[@]}"; do
        launch "$module"
    done
    launch api-gateway
    wait_for http://localhost:8080/actuator/health api-gateway
    # The gateway finds services through Eureka; wait until every one has registered.
    for module in "${SERVICES[@]}"; do
        local app
        app="$(echo "$module" | tr '[:lower:]' '[:upper:]')"
        wait_for "http://localhost:8761/eureka/apps/$app" "$module"
    done
}

case "${1:-}" in
    start) start ;;
    stop) stop ;;
    *) echo "Usage: $0 start|stop"; exit 2 ;;
esac
