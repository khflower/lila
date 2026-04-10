set -e
kill -9 1741384 2>/dev/null || true
sleep 2
cd /kh_code/lila
nohup /usr/local/bin/sbt -Dreactivemongo.api.bson.document.strict=false run >/tmp/lila-app.log 2>&1 &