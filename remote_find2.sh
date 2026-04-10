cd /kh_code/lila || exit 1
find modules app -type f -name '*.scala' -print | xargs grep -n "PositionSnapshot\|OmokPositionDto\|boardRows" | head -n 160