ps -eo pid,ppid,cmd | grep -E 'java.*lila|sbt|9663|reactivemongo' | grep -v grep
printf 'app='
curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:9663 || true
printf '\n'
tail -n 8 /tmp/lila-app.log || true