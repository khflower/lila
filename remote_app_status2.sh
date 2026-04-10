ps -eo pid,ppid,cmd | grep -E 'java @/tmp/sbt-args|sbt.*Dreactivemongo|sbt-launch.jar run' | grep -v grep
printf 'app='
curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:9663 || true
printf '\n'
tail -n 14 /tmp/lila-app.log || true