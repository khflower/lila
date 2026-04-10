set -e
resp=$(curl -s -X POST 'http://127.0.0.1:9663/api/omok/create?ruleSet=taraguchi10')
echo "$resp"