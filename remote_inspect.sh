cd /kh_code/lila || exit 1
git status --short
rg -n 'RuleSet|case class OmokRoundState|OmokStoredState|def play|HumanPlace|canPlaceOmok|omokRuleSet|placeOmok|data-omok|omok' modules/omok modules/round ui/round app modules/setup modules/lobby 2>/dev/null | head -n 320