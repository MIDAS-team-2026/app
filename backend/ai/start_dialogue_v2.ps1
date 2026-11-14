param(
    [string]$Model = 'claude-sonnet-5',
    [ValidateSet('llm', 'llm_reply')]
    [string]$Engine = 'llm_reply',
    [int]$Port = 8000
)
$ErrorActionPreference = 'Stop'
if (-not $env:YNU_API_KEY) { throw 'YNU_API_KEY is required. Do not put the key in this script.' }
$python = Join-Path $PSScriptRoot '.venv/Scripts/python.exe'
if (-not (Test-Path -LiteralPath $python)) { throw 'The AI virtual environment is missing.' }
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
    throw "Port $Port is already in use. Stop the old AI server before switching engines."
}
$previous = @{}
foreach ($name in @('MIDAS_DIALOGUE_ENGINE', 'MIDAS_DIALOGUE_MODEL', 'USE_LLM_FOLLOWUP_GENERATION')) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
Push-Location $PSScriptRoot
try {
    $env:MIDAS_DIALOGUE_ENGINE = $Engine
    $env:MIDAS_DIALOGUE_MODEL = $Model
    $env:USE_LLM_FOLLOWUP_GENERATION = 'false'
    Write-Host 'LLM dialogue pilot enabled; recall scoring and speech analysis are unchanged.'
    Write-Host "Dialogue engine=$Engine model=$Model port=$Port workers=1"
    & $python -m uvicorn dialogue_server_v2:create_app --factory --workers 1 --host 0.0.0.0 --port $Port
} finally {
    Pop-Location
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}
