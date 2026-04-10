cd /kh_code/lila || exit 1
set -e
latest_lobby=$(ls -t public/compiled/lobby.*.js | head -n 1)
latest_round=$(ls -t public/compiled/round.*.js | head -n 1)
latest_lobby_setup_css=$(ls -t public/css/lobby.setup*.css | head -n 1)
latest_round_css=$(ls -t public/css/round*.css | grep -v 'round.zh' | grep -v 'round.tour-standing' | grep -v 'round.nvui' | head -n 1)
latest_analyse_round_css=$(ls -t public/css/analyse.round*.css | head -n 1)
[ "$latest_lobby" = public/compiled/lobby.2AYZ3VJ7.js ] || cp "$latest_lobby" public/compiled/lobby.2AYZ3VJ7.js
[ "$latest_round" = public/compiled/round.STSFNM5F.js ] || cp "$latest_round" public/compiled/round.STSFNM5F.js
[ "$latest_round" = public/compiled/round.3OKELKM3.js ] || cp "$latest_round" public/compiled/round.3OKELKM3.js
[ "$latest_round" = public/compiled/round.GYB7ZO6T.js ] || cp "$latest_round" public/compiled/round.GYB7ZO6T.js
[ "$latest_lobby_setup_css" = public/css/lobby.setup.66953964.css ] || cp "$latest_lobby_setup_css" public/css/lobby.setup.66953964.css
[ "$latest_round_css" = public/css/round.b9596191.css ] || cp "$latest_round_css" public/css/round.b9596191.css
[ "$latest_analyse_round_css" = public/css/analyse.round.ef7335aa.css ] || cp "$latest_analyse_round_css" public/css/analyse.round.ef7335aa.css
echo lobby=$latest_lobby
echo round=$latest_round
echo lobby_setup_css=$latest_lobby_setup_css
echo round_css=$latest_round_css
echo analyse_round_css=$latest_analyse_round_css
grep -q taraguchi10 public/compiled/lobby.2AYZ3VJ7.js && echo lobby_has_taraguchi
grep -q omok-candidates public/compiled/round.STSFNM5F.js && echo round_has_candidates
