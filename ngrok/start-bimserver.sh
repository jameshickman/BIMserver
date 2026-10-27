#!/usr/bin/env bash
# Starts a development BIMserver on 127.0.0.1 and publishes it through an ngrok tunnel.
# Settings: ngrok/bimserver-ngrok.env (see bimserver-ngrok.env.example and README.md).
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
DIR="$ROOT/ngrok"
ENV_FILE="$DIR/bimserver-ngrok.env"
SERVER_DIR="$ROOT/BimServerJar"

if [[ -f "$ENV_FILE" ]]; then
	set -a
	# shellcheck source=/dev/null
	source "$ENV_FILE"
	set +a
fi

export BIMSERVER_PORT=${BIMSERVER_PORT:-8080}
export BIMSERVER_ADMIN_USERNAME=${BIMSERVER_ADMIN_USERNAME:-admin@bimserver.org}
export BIMSERVER_HOME=${BIMSERVER_HOME:-home-public}
BIMSERVER_XMX=${BIMSERVER_XMX:-4g}
RUN_DIR="$SERVER_DIR/$BIMSERVER_HOME"
LOCAL_URL="http://127.0.0.1:$BIMSERVER_PORT"

die() { echo "error: $*" >&2; exit 1; }

command -v ngrok >/dev/null || die "ngrok is not installed"
command -v curl >/dev/null || die "curl is not installed"
command -v python3 >/dev/null || die "python3 is not installed"
ngrok config check >/dev/null 2>&1 || die "ngrok is not configured; run: ngrok config add-authtoken <token>"
if curl -s -o /dev/null "$LOCAL_URL"; then
	die "something is already listening on port $BIMSERVER_PORT"
fi

# A publicly reachable server must not use a guessable admin password
if [[ -z "${BIMSERVER_ADMIN_PASSWORD:-}" ]]; then
	BIMSERVER_ADMIN_PASSWORD=$(python3 -c 'import secrets; print(secrets.token_urlsafe(18))')
	export BIMSERVER_ADMIN_PASSWORD
	(umask 077 && printf 'BIMSERVER_ADMIN_PASSWORD=%s\n' "$BIMSERVER_ADMIN_PASSWORD" >> "$ENV_FILE")
	echo "Generated an admin password for $BIMSERVER_ADMIN_USERNAME, saved in $ENV_FILE"
fi
[[ -f "$ENV_FILE" ]] && chmod 600 "$ENV_FILE"

# Build and resolve the classpath
if [[ ! -d "$SERVER_DIR/target/classes" || "${1:-}" == "--build" ]]; then
	echo "Building BIMserver..."
	mvn -B -q -f "$ROOT/pom.xml" install -DskipTests -pl BimServerJar -am
fi
CP_FILE="$SERVER_DIR/target/classpath.txt"
mvn -B -q -f "$SERVER_DIR/pom.xml" dependency:build-classpath -Dmdep.outputFile="$CP_FILE" >/dev/null
# The JMX-over-HTTP agent is mapped automatically when present and must never be exposed
if grep -qi jolokia "$CP_FILE"; then
	die "the Jolokia agent is on the classpath; remove it before exposing the server"
fi

mkdir -p "$RUN_DIR"
SERVER_LOG="$RUN_DIR/server-console.log"
NGROK_LOG="$RUN_DIR/ngrok.log"
SERVER_PID=""
NGROK_PID=""
ATTACHED_TUNNEL=""
TUNNEL_NAME=${NGROK_TUNNEL_NAME:-bimserver}

cleanup() {
	if [[ -n "$ATTACHED_TUNNEL" ]]; then
		curl -s -X DELETE "$NGROK_API/api/tunnels/$TUNNEL_NAME" >/dev/null || true
	fi
	[[ -n "$NGROK_PID" ]] && kill "$NGROK_PID" 2>/dev/null || true
	[[ -n "$SERVER_PID" ]] && kill "$SERVER_PID" 2>/dev/null && wait "$SERVER_PID" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT TERM

json_call() { # $1 = JSON request body
	curl -s -X POST -H 'Content-Type: application/json' --data "$1" "$LOCAL_URL/json"
}
json_field() { # reads JSON on stdin, prints response.result (empty when absent)
	python3 -c 'import json,sys
try: print(json.load(sys.stdin)["response"].get("result") or "")
except Exception: print("")'
}
login_request() {
	python3 -c 'import json,os; print(json.dumps({"request": {"interface": "AuthInterface", "method": "login", "parameters": {"username": os.environ["BIMSERVER_ADMIN_USERNAME"], "password": os.environ["BIMSERVER_ADMIN_PASSWORD"]}}}))'
}

echo "Starting BIMserver on $LOCAL_URL (log: $SERVER_LOG)"
(cd "$SERVER_DIR" && exec java "-Xmx$BIMSERVER_XMX" -Dbimserver.host=127.0.0.1 -cp "target/classes:$(cat "$CP_FILE")" org.bimserver.PublicBimServerStarter) >"$SERVER_LOG" 2>&1 &
SERVER_PID=$!

# The tunnel only opens once setup is complete, so nobody else can claim the admin account first
TOKEN=""
for _ in $(seq 1 300); do
	kill -0 "$SERVER_PID" 2>/dev/null || { tail -n 20 "$SERVER_LOG" >&2; die "BIMserver exited during startup"; }
	# curl fails until the server accepts connections
	TOKEN=$(json_call "$(login_request)" 2>/dev/null | json_field) || true
	[[ -n "$TOKEN" ]] && break
	sleep 2
done
[[ -n "$TOKEN" ]] || die "could not log in as $BIMSERVER_ADMIN_USERNAME within 10 minutes; if this home was set up with another password, set BIMSERVER_ADMIN_PASSWORD in $ENV_FILE"

NGROK_API=${NGROK_API:-http://127.0.0.1:4040}
PUBLIC_URL=""
if curl -sf "$NGROK_API/api/tunnels" >/dev/null 2>&1; then
	# Free accounts allow one agent session, so add the tunnel to the agent that is already running
	echo "Adding tunnel '$TUNNEL_NAME' to the running ngrok agent at $NGROK_API"
	TUNNEL_REQUEST=$(python3 -c 'import json,os,sys
tunnel = {"name": sys.argv[1], "proto": "http", "addr": "127.0.0.1:" + os.environ["BIMSERVER_PORT"]}
if os.environ.get("NGROK_DOMAIN"): tunnel["domain"] = os.environ["NGROK_DOMAIN"]
print(json.dumps(tunnel))' "$TUNNEL_NAME")
	RESPONSE=$(curl -s -X POST -H 'Content-Type: application/json' --data "$TUNNEL_REQUEST" "$NGROK_API/api/tunnels")
	PUBLIC_URL=$(printf '%s' "$RESPONSE" | python3 -c 'import json,sys
try: print(json.load(sys.stdin).get("public_url") or "")
except ValueError: print("")')
	[[ -n "$PUBLIC_URL" ]] || die "the running ngrok agent refused the tunnel: $RESPONSE"
	ATTACHED_TUNNEL=1
else
	NGROK_ARGS=(http "127.0.0.1:$BIMSERVER_PORT" --log stdout --log-format json)
	[[ -n "${NGROK_DOMAIN:-}" ]] && NGROK_ARGS+=(--url "https://$NGROK_DOMAIN")
	echo "Starting ngrok (log: $NGROK_LOG)"
	ngrok "${NGROK_ARGS[@]}" >"$NGROK_LOG" 2>&1 &
	NGROK_PID=$!
	for _ in $(seq 1 30); do
		if ! kill -0 "$NGROK_PID" 2>/dev/null; then
			grep -q ERR_NGROK_108 "$NGROK_LOG" && die "your ngrok plan allows one agent session and another one is running elsewhere; stop it, or start this script on the machine running it"
			tail -n 5 "$NGROK_LOG" >&2
			die "ngrok exited"
		fi
		PUBLIC_URL=$(python3 -c 'import json,sys
for line in open(sys.argv[1]):
    try: entry = json.loads(line)
    except ValueError: continue
    if entry.get("msg") == "started tunnel" and str(entry.get("url", "")).startswith("https://"):
        print(entry["url"]); break' "$NGROK_LOG")
		[[ -n "$PUBLIC_URL" ]] && break
		sleep 1
	done
fi
[[ -n "$PUBLIC_URL" ]] || die "ngrok did not report a public URL"

# Links the server generates (downloads, e-mails) must use the public address
SET_ADDRESS=$(PUBLIC_URL="$PUBLIC_URL" TOKEN="$TOKEN" python3 -c 'import json,os; print(json.dumps({"token": os.environ["TOKEN"], "request": {"interface": "SettingsInterface", "method": "setSiteAddress", "parameters": {"siteAddress": os.environ["PUBLIC_URL"]}}}))')
if json_call "$SET_ADDRESS" | grep -q '"exception"'; then
	echo "warning: could not update the site address to $PUBLIC_URL" >&2
fi

cat <<EOF

BIMserver is online at $PUBLIC_URL
  admin user: $BIMSERVER_ADMIN_USERNAME (password in $ENV_FILE)
  JSON API:   $PUBLIC_URL/json
Press Ctrl-C to stop the tunnel and the server.
EOF

wait "$SERVER_PID"
