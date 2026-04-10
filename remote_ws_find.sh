cd /tmp/lila-ws-scan || exit 1
find . -type f \( -name '*.scala' -o -name '*.js' -o -name '*.ts' \) -print | xargs grep -n "place\|rematch\|r/do\|PlayerDo\|Round" | head -n 240