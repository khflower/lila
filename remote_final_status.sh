cd /kh_code/lila || exit 1
grep -q taraguchi10 public/compiled/lobby.2AYZ3VJ7.js && echo lobby_ok
grep -q omok-candidates public/compiled/round.STSFNM5F.js && echo round_ok
ps -eo pid,ppid,cmd | grep -E 'sbt.*run|sbt-launch.jar run' | grep -v grep