param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$ArgsFromCaller
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$runner = Join-Path $scriptDir 'omok_e2e_regression.js'

if (-not (Test-Path -LiteralPath $runner)) {
  Write-Error "Runner not found: $runner"
  exit 1
}

& node $runner @ArgsFromCaller
exit $LASTEXITCODE
