cd /kh_code/lila || exit 1
find modules -type f -name '*.scala' -print | xargs grep -n "case class HumanPlace\|HumanPlace(" | head -n 80