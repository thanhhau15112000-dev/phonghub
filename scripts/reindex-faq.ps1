param(
    [string]$EnvFile = ".env",
    [string]$FaqFile = "ops/faq/assistant-faq.vi.json",
    [string]$OutputFile = "src/main/resources/faq/faq-index.json",
    [ValidateRange(128, 3072)]
    [int]$Dimensions = 768
)

$ErrorActionPreference = "Stop"

function Read-GeminiApiKey {
    if (-not [string]::IsNullOrWhiteSpace($env:GEMINI_API_KEY)) {
        $environmentKey = $env:GEMINI_API_KEY.Trim()
        if ($environmentKey -ne "your_gemini_api_key") {
            return $environmentKey
        }
    }
    if (-not (Test-Path -LiteralPath $EnvFile -PathType Leaf)) {
        throw "GEMINI_API_KEY is unset and the configured env file was not found."
    }
    foreach ($line in Get-Content -LiteralPath $EnvFile) {
        $parts = $line -split '=', 2
        if ($parts.Count -eq 2 -and $parts[0].Trim() -eq 'GEMINI_API_KEY') {
            $value = $parts[1].Trim()
            if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            if (-not [string]::IsNullOrWhiteSpace($value) -and $value -ne "your_gemini_api_key") {
                return $value
            }
            break
        }
    }
    throw "GEMINI_API_KEY was not found in the configured env file."
}

$apiKey = Read-GeminiApiKey
$faqs = Get-Content -LiteralPath $FaqFile -Raw -Encoding UTF8 | ConvertFrom-Json
if ($faqs.Count -lt 1) {
    throw "The FAQ source is empty."
}

$requests = @(
    foreach ($faq in $faqs) {
        $document = "title: $($faq.title) | text: Câu hỏi: $($faq.question) Câu trả lời: $($faq.answer)"
        @{
            model = "models/gemini-embedding-2"
            content = @{ parts = @(@{ text = $document }) }
            outputDimensionality = $Dimensions
        }
    }
)
$body = @{ requests = $requests } | ConvertTo-Json -Depth 12 -Compress
$response = Invoke-RestMethod `
    -Method Post `
    -Uri "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-2:batchEmbedContents" `
    -Headers @{ "x-goog-api-key" = $apiKey } `
    -ContentType "application/json" `
    -Body $body

if ($response.embeddings.Count -ne $faqs.Count) {
    throw "The embedding provider returned a different number of vectors than FAQ entries."
}

$entries = @()
for ($index = 0; $index -lt $faqs.Count; $index++) {
    $vector = @($response.embeddings[$index].values)
    if ($vector.Count -ne $Dimensions) {
        throw "The embedding provider returned a vector with an unexpected dimension count."
    }
    $entry = [ordered]@{
        id = $faqs[$index].id
        category = $faqs[$index].category
        title = $faqs[$index].title
        question = $faqs[$index].question
        answer = $faqs[$index].answer
        source = $faqs[$index].source
        embedding = $vector
    }
    $entries += $entry
}

$indexContent = [ordered]@{
    model = "gemini-embedding-2"
    dimensions = $Dimensions
    faqSourceSha256 = (Get-FileHash -LiteralPath $FaqFile -Algorithm SHA256).Hash.ToLowerInvariant()
    entries = $entries
} | ConvertTo-Json -Depth 12 -Compress

$outputPath = [System.IO.Path]::GetFullPath($OutputFile)
$outputDirectory = [System.IO.Path]::GetDirectoryName($outputPath)
[System.IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
[System.IO.File]::WriteAllText($outputPath, $indexContent, [System.Text.UTF8Encoding]::new($false))
Write-Output "Indexed $($entries.Count) FAQ entries with $Dimensions-dimensional vectors to $OutputFile."
