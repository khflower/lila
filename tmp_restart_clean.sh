set -e
pkill -f "sbt-launch.jar run" || true
pkill -f "/usr/local/bin/sbt -Dreactivemongo.api.bson.document.strict=false run" || true
sleep 2
cd /kh_code/lila
nohup /usr/local/bin/sbt -Dreactivemongo.api.bson.document.strict=false run >/tmp/lila-app.log 2>&1 &