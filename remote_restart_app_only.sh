kill 898607 2>/dev/null || true
pkill -f 'java @/tmp/sbt-args.*' || true
sleep 2
cd /kh_code/lila || exit 1
nohup /usr/local/bin/sbt -Dreactivemongo.api.bson.document.strict=false run >/tmp/lila-app.log 2>&1 &
echo app_restart_requested