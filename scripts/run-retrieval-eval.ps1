param(
    [string]$Cases = "",
    [string]$Predictions = "",
    [int]$TopK = 5,
    [double]$MinHitRate = 1.0,
    [double]$MinMrr = 1.0,
    [double]$MinRecall = 1.0,
    [double]$MinNoAnswerPrecision = 1.0
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if ([string]::IsNullOrWhiteSpace($Cases)) {
    $Cases = (Get-ChildItem -Path $repoRoot -Recurse -Filter "retrieval-eval-cases.business.jsonl" | Select-Object -First 1).FullName
    if ([string]::IsNullOrWhiteSpace($Cases)) {
        $Cases = (Get-ChildItem -Path $repoRoot -Recurse -Filter "retrieval-eval-cases.seed.jsonl" | Select-Object -First 1).FullName
    }
}
if ([string]::IsNullOrWhiteSpace($Predictions)) {
    $Predictions = (Get-ChildItem -Path $repoRoot -Recurse -Filter "retrieval-eval-predictions.business-baseline.jsonl" | Select-Object -First 1).FullName
    if ([string]::IsNullOrWhiteSpace($Predictions)) {
        $Predictions = (Get-ChildItem -Path $repoRoot -Recurse -Filter "retrieval-eval-predictions.baseline.jsonl" | Select-Object -First 1).FullName
    }
}
if ([string]::IsNullOrWhiteSpace($Cases)) {
    throw "retrieval-eval-cases.business.jsonl or retrieval-eval-cases.seed.jsonl was not found"
}
if ([string]::IsNullOrWhiteSpace($Predictions)) {
    throw "retrieval-eval-predictions.business-baseline.jsonl or retrieval-eval-predictions.baseline.jsonl was not found"
}

$runnerArgs = @(
    "--cases", $Cases,
    "--predictions", $Predictions,
    "--topK", "$TopK",
    "--minHitRate", "$MinHitRate",
    "--minMrr", "$MinMrr",
    "--minRecall", "$MinRecall",
    "--minNoAnswerPrecision", "$MinNoAnswerPrecision"
)

mvn.cmd -s .mvn/settings.xml -pl agent-platform-core exec:java `
    "-Dexec.mainClass=com.ls.agent.core.evaluation.application.RetrievalEvaluationFileRunner" `
    "-Dexec.args=$($runnerArgs -join ' ')"
