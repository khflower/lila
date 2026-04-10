ss -ltnp | grep -E ':9663|:9664' || true
ps -eo pid,ppid,cmd | grep -E 'sbt.*run|sbt-launch.jar run' | grep -v grep
printf '\napp log tail\n'
tail -n 20 /tmp/lila-app.log || true
printf '\nws log tail\n'
tail -n 20 /tmp/lila-ws-scan-run.log || true