ps -eo pid,ppid,cmd | grep -E 'sbt.*run|sbt-launch.jar run' | grep -v grep
printf 'app='
curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:9663 || true
printf '\nws_http='
curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:9664 || true
printf '\nws log\n'
tail -n 18 /tmp/lila-ws-scan-run.log || true