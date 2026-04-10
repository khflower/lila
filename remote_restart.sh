set -e
ps -eo pid,cmd | grep -E 'sbt.*run|lila-ws-scan' | grep -v grep || true
pkill -f '/tmp/lila-ws-scan.*sbt.*run' || true
pkill -f 'Dreactivemongo.api.bson.document.strict=false.*sbt-launch.jar run' || true
pkill -f 'sbt.*Dreactivemongo.api.bson.document.strict=false.*run' || true
cd /tmp/lila-ws-scan
nohup sbt run >/tmp/lila-ws-scan-run.log 2>&1 &
cd /kh_code/lila
nohup /usr/local/bin/sbt -Dreactivemongo.api.bson.document.strict=false run >/tmp/lila-app.log 2>&1 &
echo restarted
ps -eo pid,cmd | grep -E 'sbt.*run|lila-ws-scan' | grep -v grep || true