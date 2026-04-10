cd /kh_code/lila || exit 1
find modules app -type f -name '*.scala' -print | xargs grep -n "object OmokStoredState\|case class OmokStoredState\|OmokStoredState" | head -n 120
find modules app -type f -name '*.scala' -print | xargs grep -n "case class OmokGameSidecar\|OmokGameSidecar" | head -n 120