cd /kh_code/lila || exit 1
./ui/build round
latest_round=$(ls -t public/compiled/round.*.js | head -n 1)
cp "$latest_round" public/compiled/round.STSFNM5F.js
grep -q data-omok-candidate-selection public/compiled/round.STSFNM5F.js && echo round_candidate_attrs_ok