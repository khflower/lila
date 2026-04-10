set -e
pkill -f "/tmp/lila-ws-scan" || true
pkill -f "lila.ws.LilaWs" || true
pkill -f "/usr/local/bin/sbt run" || true
sleep 2
cd /tmp/lila-ws-scan
nohup /usr/local/bin/sbt run </dev/null >/tmp/lila-ws-scan-run.log 2>&1 &
echo $! >/tmp/lila-ws.pid