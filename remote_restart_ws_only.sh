kill 3685680 3685641 916530 2>/dev/null || true
sleep 2
cd /tmp/lila-ws-scan
nohup sbt run >/tmp/lila-ws-scan-run.log 2>&1 &
echo ws_restarted
ps -eo pid,ppid,cmd | grep -E 'sbt.*run|sbt-launch.jar run' | grep -v grep