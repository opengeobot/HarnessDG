param(
    [Parameter(Mandatory = $true)]
    [string]$TaskPath,

    [switch]$CheckChangedPaths,

    [switch]$CheckCompletion
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$errors = [System.Collections.Generic.List[string]]::new()

function Add-Error {
    param([string]$Message)
    $errors.Add($Message)
}

function Get-Scalar {
    param(
        [string]$FrontMatter,
        [string]$Key
    )
    $match = [regex]::Match(
        $FrontMatter,
        "(?m)^$([regex]::Escape($Key)):\s*(?<value>[^\r\n]+?)\s*$"
    )
    if (-not $match.Success) {
        return $null
    }
    return $match.Groups['value'].Value.Trim().Trim("'`"")
}

function Get-List {
    param(
        [string[]]$Lines,
        [string]$Key
    )
    $values = [System.Collections.Generic.List[string]]::new()
    $start = -1
    for ($index = 0; $index -lt $Lines.Count; $index++) {
        if ($Lines[$index] -match "^$([regex]::Escape($Key)):\s*$") {
            $start = $index + 1
            break
        }
    }
    if ($start -lt 0) {
        return $values.ToArray()
    }
    for ($index = $start; $index -lt $Lines.Count; $index++) {
        $line = $Lines[$index]
        if ($line -match '^\s{2}-\s+(?<value>.+?)\s*$') {
            $values.Add($Matches['value'].Trim().Trim("'`""))
            continue
        }
        if ($line -match '^\S') {
            break
        }
    }
    return $values.ToArray()
}

function Test-Placeholder {
    param([string]$Value)
    return [string]::IsNullOrWhiteSpace($Value) -or
        $Value.Contains('<') -or
        $Value.Contains('...') -or
        $Value -eq 'null'
}

function Get-LevelNumber {
    param([string]$Level)
    if ($Level -match '^E(?<number>[1-5])$') {
        return [int]$Matches['number']
    }
    return 0
}

function Get-YamlBlock {
    param(
        [string]$Yaml,
        [string]$Key
    )
    $match = [regex]::Match(
        $Yaml,
        "(?ms)^$([regex]::Escape($Key)):\s*\r?\n(?<body>.*?)(?=^\S|\z)"
    )
    if (-not $match.Success) {
        return ''
    }
    return $match.Groups['body'].Value
}

function Get-IndentedScalar {
    param(
        [string]$Block,
        [string]$Key
    )
    $match = [regex]::Match(
        $Block,
        "(?m)^\s+$([regex]::Escape($Key)):\s*(?<value>[^\r\n]+?)\s*$"
    )
    if (-not $match.Success) {
        return $null
    }
    return $match.Groups['value'].Value.Trim().Trim("'`"")
}

function Get-IndentedList {
    param(
        [string]$Block,
        [string]$Key
    )
    $values = [System.Collections.Generic.List[string]]::new()
    $match = [regex]::Match(
        $Block,
        "(?ms)^\s+$([regex]::Escape($Key)):\s*\r?\n(?<body>(?:^\s+-\s+.*(?:\r?\n|\z))*)"
    )
    if (-not $match.Success) {
        return $values.ToArray()
    }
    foreach ($line in ($match.Groups['body'].Value -split '\r?\n')) {
        if ($line -match '^\s+-\s+(?<value>.+?)\s*$') {
            $values.Add($Matches['value'].Trim().Trim("'`""))
        }
    }
    return $values.ToArray()
}

$specRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $specRoot '..\..')).Path
$taskRoot = Join-Path $specRoot 'tasks'

if (-not (Test-Path -LiteralPath $TaskPath -PathType Leaf)) {
    throw "Task Card not found: $TaskPath"
}

$taskItem = Get-Item -LiteralPath $TaskPath
$taskFullPath = $taskItem.FullName
$taskRootFullPath = (Resolve-Path -LiteralPath $taskRoot).Path
if (-not $taskFullPath.StartsWith(
        $taskRootFullPath + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase
    )) {
    Add-Error "Task Card must be under docs/ai-spec/tasks: $taskFullPath"
}

$content = [IO.File]::ReadAllText($taskFullPath, [Text.Encoding]::UTF8)
$frontMatch = [regex]::Match($content, '\A---\r?\n(?<body>.*?)\r?\n---\r?\n', 'Singleline')
if (-not $frontMatch.Success) {
    Add-Error 'Task Card must begin with YAML Front Matter'
    $frontMatter = ''
} else {
    $frontMatter = $frontMatch.Groups['body'].Value
}
$frontLines = @($frontMatter -split '\r?\n')

$schemaVersion = Get-Scalar -FrontMatter $frontMatter -Key 'schemaVersion'
$taskId = Get-Scalar -FrontMatter $frontMatter -Key 'taskId'
$status = Get-Scalar -FrontMatter $frontMatter -Key 'status'
$implementationAuthorized = Get-Scalar -FrontMatter $frontMatter -Key 'implementationAuthorized'
$phase = Get-Scalar -FrontMatter $frontMatter -Key 'phase'
$baseCommit = Get-Scalar -FrontMatter $frontMatter -Key 'baseCommit'
$stageGatePassed = Get-Scalar -FrontMatter $frontMatter -Key 'stageGatePassed'
$stageGateEvidence = Get-Scalar -FrontMatter $frontMatter -Key 'stageGateEvidence'
$requiredEvidenceLevel = Get-Scalar -FrontMatter $frontMatter -Key 'requiredEvidenceLevel'
$evidenceTemplate = Get-Scalar -FrontMatter $frontMatter -Key 'evidenceTemplate'
$approvedBy = Get-Scalar -FrontMatter $frontMatter -Key 'approvedBy'
$approvedAt = Get-Scalar -FrontMatter $frontMatter -Key 'approvedAt'
$currentCommit = ''

if ($schemaVersion -ne 'harnessdg.task/v1') {
    Add-Error 'schemaVersion must be harnessdg.task/v1'
}
if ($taskId -notmatch '^TASK(?:-[A-Z0-9]+)+-\d{3}$') {
    Add-Error 'taskId must be a stable TASK-...-NNN ID'
} elseif ($taskItem.BaseName -ne $taskId) {
    Add-Error "Task filename must equal taskId: expected $taskId.md"
}
if ($status -ne 'READY') {
    Add-Error 'Task status must be READY'
}
if ($implementationAuthorized -ne 'true') {
    Add-Error 'implementationAuthorized must be true'
}
if ($phase -notin @('P0-B', 'P1', 'P2', 'P3', 'P4', 'P5')) {
    Add-Error 'phase must be one of P0-B/P1/P2/P3/P4/P5'
}
if ($baseCommit -notmatch '^[0-9a-fA-F]{40}$') {
    Add-Error 'baseCommit must be a full 40-character Git SHA'
} else {
    $currentCommit = (& git -C $repoRoot rev-parse HEAD 2>$null).Trim()
    if ($LASTEXITCODE -ne 0) {
        Add-Error 'Unable to read current Git HEAD'
    } elseif ($CheckChangedPaths -or $CheckCompletion) {
        & git -C $repoRoot merge-base --is-ancestor $baseCommit $currentCommit 2>$null
        if ($LASTEXITCODE -ne 0) {
            Add-Error "baseCommit is not an ancestor of current HEAD ($currentCommit)"
        }
    } elseif ($currentCommit -ne $baseCommit.ToLowerInvariant()) {
        Add-Error "baseCommit does not match current HEAD ($currentCommit)"
    }
}
if ($stageGatePassed -ne 'true') {
    Add-Error 'stageGatePassed must be true'
}
if (Test-Placeholder $stageGateEvidence) {
    Add-Error 'stageGateEvidence must identify the accepted upstream evidence'
}
if ($requiredEvidenceLevel -notin @('E1', 'E2', 'E3', 'E4', 'E5')) {
    Add-Error 'requiredEvidenceLevel must be E1-E5'
}
if (Test-Placeholder $evidenceTemplate) {
    Add-Error 'evidenceTemplate must identify the repository Evidence schema template'
} else {
    $normalizedTemplate = $evidenceTemplate.Replace('\', '/').Trim()
    if ([IO.Path]::IsPathRooted($normalizedTemplate) -or
        $normalizedTemplate -match '(^|/)\.\.(/|$)' -or
        -not (Test-Path -LiteralPath (Join-Path $repoRoot $normalizedTemplate) -PathType Leaf)) {
        Add-Error 'evidenceTemplate must be an existing repository-relative file'
    }
}
if (Test-Placeholder $approvedBy) {
    Add-Error 'approvedBy must identify a human or controlled approval authority'
} elseif ($approvedBy -match '(?i)\b(ai|self|codex|trae|claude|cursor|qwen)\b') {
    Add-Error 'approvedBy cannot identify the implementing AI or self-approval'
}
if (Test-Placeholder $approvedAt) {
    Add-Error 'approvedAt must be a UTC timestamp'
} else {
    $parsedApproval = [DateTimeOffset]::MinValue
    if (-not [DateTimeOffset]::TryParse($approvedAt, [ref]$parsedApproval)) {
        Add-Error 'approvedAt is not a valid timestamp'
    }
}

$requirements = @(Get-List -Lines $frontLines -Key 'requirements')
$scenarios = @(Get-List -Lines $frontLines -Key 'acceptanceScenarios')
$decisions = @(Get-List -Lines $frontLines -Key 'decisions')
$stageGateEvidenceRefs = @(Get-List -Lines $frontLines -Key 'stageGateEvidenceRefs')
$allowedPaths = @(Get-List -Lines $frontLines -Key 'allowedPaths')
$forbiddenPaths = @(Get-List -Lines $frontLines -Key 'forbiddenPaths')
$preExistingDirtyPaths = @(Get-List -Lines $frontLines -Key 'preExistingDirtyPaths')
$scenarioEvidencePlan = @(Get-List -Lines $frontLines -Key 'scenarioEvidencePlan')
$crossCuttingPlan = @(Get-List -Lines $frontLines -Key 'crossCuttingPlan')
$requiredValidationCommands = @(Get-List -Lines $frontLines -Key 'requiredValidationCommands')
$evidenceManifests = @(Get-List -Lines $frontLines -Key 'evidenceManifests')

if ($requirements.Count -eq 0) {
    Add-Error 'requirements must contain at least one REQ ID'
}
if ($scenarios.Count -eq 0) {
    Add-Error 'acceptanceScenarios must contain at least one AC ID'
}
if ($decisions.Count -eq 0) {
    Add-Error 'decisions must contain at least one DEC ID'
}
if ($stageGateEvidenceRefs.Count -eq 0) {
    Add-Error 'stageGateEvidenceRefs must contain at least one accepted DEC or repository evidence path'
}
if ($allowedPaths.Count -eq 0) {
    Add-Error 'allowedPaths must contain at least one narrow path'
}
if ($scenarioEvidencePlan.Count -eq 0) {
    Add-Error 'scenarioEvidencePlan must map every acceptance scenario to an evidence level and artifact'
}
if ($crossCuttingPlan.Count -eq 0) {
    Add-Error 'crossCuttingPlan must explicitly address every platform concern'
}
if ($requiredValidationCommands.Count -eq 0) {
    Add-Error 'requiredValidationCommands must contain exact runnable commands'
}
if ($evidenceManifests.Count -eq 0) {
    Add-Error 'evidenceManifests must predeclare at least one repository Evidence path'
}

$requirementFiles = @(Get-ChildItem -LiteralPath (Join-Path $specRoot '01-requirements') -Filter '*.md' -File)
foreach ($requirementId in $requirements) {
    if ($requirementId -notmatch '^REQ(?:-[A-Z0-9]+)+-\d{3}$') {
        Add-Error "Invalid requirement ID: $requirementId"
        continue
    }
    $found = $false
    foreach ($file in $requirementFiles) {
        $requirementContent = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
        $section = [regex]::Match(
            $requirementContent,
            "(?ms)^##\s+$([regex]::Escape($requirementId))\b(?<body>.*?)(?=^##\s+|\z)"
        )
        if ($section.Success) {
            $found = $true
            if ($section.Groups['body'].Value -notmatch '(?m)^status:\s*READY\s*$') {
                Add-Error "Requirement is not READY: $requirementId"
            }
            break
        }
    }
    if (-not $found) {
        Add-Error "Unknown requirement: $requirementId"
    }
}

$acceptanceContent = (
    Get-ChildItem -LiteralPath (Join-Path $specRoot '05-acceptance') -Filter '*.md' -File |
        ForEach-Object { [IO.File]::ReadAllText($_.FullName, [Text.Encoding]::UTF8) }
) -join "`n"
foreach ($scenarioId in $scenarios) {
    if ($scenarioId -notmatch '^AC(?:-[A-Z0-9]+)+-\d{3}$') {
        Add-Error "Invalid acceptance scenario ID: $scenarioId"
    } else {
        $scenarioPattern = '\|\s*`' + [regex]::Escape($scenarioId) + '`\s*\|'
        if ($acceptanceContent -notmatch $scenarioPattern) {
        Add-Error "Unknown acceptance scenario: $scenarioId"
        }
    }
}

$decisionContent = [IO.File]::ReadAllText(
    (Join-Path $specRoot '00-governance/decision-log.md'),
    [Text.Encoding]::UTF8
)
foreach ($decisionId in $decisions) {
    if ($decisionId -notmatch '^DEC-\d{3}$') {
        Add-Error "Invalid decision ID: $decisionId"
        continue
    }
    $section = [regex]::Match(
        $decisionContent,
        "(?ms)^###\s+$([regex]::Escape($decisionId))\b(?<body>.*?)(?=^###\s+|\z)"
    )
    if (-not $section.Success) {
        Add-Error "Unknown decision: $decisionId"
    } elseif ($section.Groups['body'].Value -notmatch '(?m)^status:\s*ACCEPTED\s*$') {
        Add-Error "Decision is not ACCEPTED: $decisionId"
    }
}

foreach ($evidenceRef in $stageGateEvidenceRefs) {
    if ($evidenceRef -match '^DEC-\d{3}$') {
        if ($evidenceRef -notin $decisions) {
            Add-Error "stageGateEvidenceRefs decision is not declared in decisions: $evidenceRef"
        }
        continue
    }
    $normalizedRef = $evidenceRef.Replace('\', '/').Trim()
    if (Test-Placeholder $normalizedRef) {
        Add-Error "stageGateEvidenceRefs contains a placeholder: $evidenceRef"
        continue
    }
    if ([IO.Path]::IsPathRooted($normalizedRef) -or
        $normalizedRef -match '(^|/)\.\.(/|$)') {
        Add-Error "stageGateEvidenceRefs path must be repository-relative and non-escaping: $evidenceRef"
        continue
    }
    $resolvedRef = [IO.Path]::GetFullPath((Join-Path $repoRoot $normalizedRef))
    if (-not $resolvedRef.StartsWith(
            $repoRoot + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase
        ) -or -not (Test-Path -LiteralPath $resolvedRef -PathType Leaf)) {
        Add-Error "stageGateEvidenceRefs evidence path does not exist in the repository: $evidenceRef"
    }
}

$plannedScenarioIds = [System.Collections.Generic.List[string]]::new()
foreach ($planEntry in $scenarioEvidencePlan) {
    $parts = @($planEntry -split '\|', 3)
    if ($parts.Count -ne 3) {
        Add-Error "scenarioEvidencePlan must use AC-ID|E1-E5|expected-artifact: $planEntry"
        continue
    }
    $plannedScenarioId = $parts[0].Trim()
    $plannedLevel = $parts[1].Trim()
    $plannedArtifact = $parts[2].Trim()
    $plannedScenarioIds.Add($plannedScenarioId)
    if ($plannedScenarioId -notin $scenarios) {
        Add-Error "scenarioEvidencePlan references a scenario outside acceptanceScenarios: $plannedScenarioId"
    }
    if ((Get-LevelNumber $plannedLevel) -lt (Get-LevelNumber $requiredEvidenceLevel)) {
        Add-Error "scenarioEvidencePlan level is below requiredEvidenceLevel: $planEntry"
    }
    if (Test-Placeholder $plannedArtifact) {
        Add-Error "scenarioEvidencePlan expected artifact is missing or placeholder: $planEntry"
    }
}
foreach ($scenarioId in $scenarios) {
    $count = @($plannedScenarioIds | Where-Object { $_ -eq $scenarioId }).Count
    if ($count -ne 1) {
        Add-Error "acceptance scenario must have exactly one scenarioEvidencePlan entry: $scenarioId ($count found)"
    }
}

$taskRelativePath = $taskFullPath.Substring($repoRoot.Length).TrimStart('\', '/').Replace('\', '/')
$hasSpecValidation = $false
$hasTaskValidation = $false
$hasChangedPathValidation = $false
$deliveryValidationCount = 0
foreach ($command in $requiredValidationCommands) {
    if (Test-Placeholder $command) {
        Add-Error "requiredValidationCommands contains a placeholder: $command"
        continue
    }
    if ($command -match '(?:^|\s)(?:\./)?docs/ai-spec/tools/validate-spec\.ps1(?:\s|$)') {
        $hasSpecValidation = $true
        continue
    }
    if ($command -match '(?:^|\s)(?:\./)?docs/ai-spec/tools/validate-task-card\.ps1(?:\s|$)') {
        if ($command -notmatch [regex]::Escape($taskRelativePath)) {
            Add-Error "Task validator command must target this Task Card: $command"
        }
        if ($command -match '(?:^|\s)-CheckChangedPaths(?:\s|$)') {
            $hasChangedPathValidation = $true
        } else {
            $hasTaskValidation = $true
        }
        continue
    }
    $deliveryValidationCount++
}
if (-not $hasSpecValidation) {
    Add-Error 'requiredValidationCommands must include validate-spec.ps1'
}
if (-not $hasTaskValidation) {
    Add-Error 'requiredValidationCommands must include validate-task-card.ps1 for this Task'
}
if (-not $hasChangedPathValidation) {
    Add-Error 'requiredValidationCommands must include validate-task-card.ps1 -CheckChangedPaths'
}
if ($deliveryValidationCount -eq 0) {
    Add-Error 'requiredValidationCommands must include at least one task-specific validation command'
}

$validationCommandText = $requiredValidationCommands -join "`n"
$allowedPathText = ($allowedPaths | ForEach-Object {
        $_.Replace('\', '/').Trim().TrimEnd('/')
    }) -join "`n"
if ($allowedPathText -match '(?m)^backend(?:/|$)' -and
    $validationCommandText -notmatch '(?i)mvnw(?:\.cmd)?\s+.*\bverify\b') {
    Add-Error 'Backend scope requires a Maven wrapper verify command'
}
if ($allowedPathText -match '(?m)^frontend(?:/|$)') {
    foreach ($frontendCommand in @('lint', 'typecheck', 'test', 'build')) {
        if ($validationCommandText -notmatch "(?i)\bpnpm(?:@\S+)?\s+$frontendCommand\b") {
            Add-Error "Frontend scope requires pnpm $frontendCommand"
        }
    }
}
if ($allowedPathText -match '(?m)^contracts/openapi(?:/|$)') {
    if ($validationCommandText -notmatch '(?i)redocly.+\blint\b') {
        Add-Error 'OpenAPI scope requires Redocly lint'
    }
    if ($validationCommandText -notmatch '(?i)redocly.+\bdiff\b') {
        Add-Error 'OpenAPI scope requires a blocking Redocly breaking-change diff'
    }
}
if ($allowedPathText -match '(?m)^deploy/compose(?:/|$)') {
    if ($validationCommandText -notmatch '(?i)docker\s+compose.+\bconfig\b') {
        Add-Error 'Compose scope requires docker compose config'
    }
    if ($validationCommandText -notmatch '(?i)deploy/compose/scripts/verify\.(?:ps1|sh)') {
        Add-Error 'Compose scope requires the repository functional verification script'
    }
}

$requiredCrossCuttingConcerns = @(
    'AUTHN',
    'AUTHZ',
    'DB_FILTER',
    'STATE',
    'IDEMPOTENCY',
    'CONSISTENCY',
    'ERRORS',
    'AUDIT',
    'NOTIFICATION',
    'TAXONOMY_I18N',
    'CONFIG',
    'OBSERVABILITY',
    'SECRETS'
)
$crossCuttingKeys = [System.Collections.Generic.List[string]]::new()
foreach ($entry in $crossCuttingPlan) {
    $parts = @($entry -split '\|', 2)
    if ($parts.Count -ne 2) {
        Add-Error "crossCuttingPlan must use CONCERN|requirement-or-N/A-with-reason: $entry"
        continue
    }
    $key = $parts[0].Trim()
    $value = $parts[1].Trim()
    $crossCuttingKeys.Add($key)
    if ($key -notin $requiredCrossCuttingConcerns) {
        Add-Error "crossCuttingPlan contains an unknown concern: $key"
    }
    if (Test-Placeholder $value -or $value -eq 'N/A') {
        Add-Error "crossCuttingPlan must be explicit or N/A with a reason: $key"
    }
}
foreach ($concern in $requiredCrossCuttingConcerns) {
    $count = @($crossCuttingKeys | Where-Object { $_ -eq $concern }).Count
    if ($count -ne 1) {
        Add-Error "crossCuttingPlan must contain each concern exactly once: $concern ($count found)"
    }
}

$taskCatalog = [IO.File]::ReadAllText(
    (Join-Path $specRoot '02-delivery/work-breakdown.md'),
    [Text.Encoding]::UTF8
)
if (-not [string]::IsNullOrWhiteSpace($taskId)) {
    $taskPattern = '\|\s*`' + [regex]::Escape($taskId) + '`\s*\|'
    if ($taskCatalog -notmatch $taskPattern) {
        Add-Error "taskId is not registered in work-breakdown.md: $taskId"
    }
}

$forbiddenBroadPaths = @(
    '.', '/', 'backend', 'frontend', 'docs', 'contracts', 'deploy',
    'backend/src/main/resources/db/migration'
)
foreach ($allowedPath in $allowedPaths) {
    $normalized = $allowedPath.Trim().TrimEnd('/', '\')
    if (Test-Placeholder $normalized) {
        Add-Error "allowedPaths contains a placeholder: $allowedPath"
        continue
    }
    if ($normalized.Contains('*') -or $normalized.Contains('?')) {
        Add-Error "allowedPaths cannot contain wildcards: $allowedPath"
    }
    if ($normalized -in $forbiddenBroadPaths) {
        Add-Error "allowedPaths is too broad: $allowedPath"
    }
    if ([IO.Path]::IsPathRooted($normalized) -or $normalized -match '(^|[\\/])\.\.([\\/]|$)') {
        Add-Error "allowedPaths must be a repository-relative non-escaping path: $allowedPath"
        continue
    }
    $resolvedCandidate = [IO.Path]::GetFullPath((Join-Path $repoRoot $normalized))
    if (-not $resolvedCandidate.StartsWith(
            $repoRoot + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase
        )) {
        Add-Error "allowedPaths escapes the repository: $allowedPath"
    }
    if ($normalized -in @(
            'backend/src/main/resources/db/migration/V1__baseline.sql',
            'backend/src/main/resources/db/migration/V2__asset_catalog.sql'
        )) {
        Add-Error "allowedPaths includes an immutable applied migration: $allowedPath"
    }
}

$allowedEvidencePrefixes = @(
    $allowedPaths |
        ForEach-Object { $_.Replace('\', '/').Trim().TrimEnd('/') }
)
foreach ($evidencePath in $evidenceManifests) {
    $normalizedEvidencePath = $evidencePath.Replace('\', '/').Trim()
    if ((Test-Placeholder $normalizedEvidencePath) -or
        ([IO.Path]::IsPathRooted($normalizedEvidencePath)) -or
        $normalizedEvidencePath -match '(^|/)\.\.(/|$)') {
        Add-Error "evidenceManifests path must be concrete, repository-relative and non-escaping: $evidencePath"
        continue
    }
    $evidenceAllowed = $false
    foreach ($prefix in $allowedEvidencePrefixes) {
        if ($normalizedEvidencePath -eq $prefix -or
            $normalizedEvidencePath.StartsWith("$prefix/")) {
            $evidenceAllowed = $true
            break
        }
    }
    if (-not $evidenceAllowed) {
        Add-Error "evidenceManifests path must be included by allowedPaths: $evidencePath"
    }
}

$preExistingDigestMap = @{}
foreach ($entry in $preExistingDirtyPaths) {
    $parts = @($entry -split '\|', 2)
    if ($parts.Count -ne 2 -or
        $parts[0].Trim() -eq '' -or
        $parts[1].Trim() -notmatch '^[0-9a-fA-F]{64}$') {
        Add-Error "preExistingDirtyPaths must use repository-path|SHA-256: $entry"
        continue
    }
    $path = $parts[0].Replace('\', '/').Trim().TrimEnd('/')
    if ([IO.Path]::IsPathRooted($path) -or $path -match '(^|/)\.\.(/|$)') {
        Add-Error "preExistingDirtyPaths path must be repository-relative and non-escaping: $path"
        continue
    }
    if ($preExistingDigestMap.ContainsKey($path)) {
        Add-Error "preExistingDirtyPaths contains a duplicate path: $path"
        continue
    }
    $preExistingDigestMap[$path] = $parts[1].Trim().ToLowerInvariant()
}

if (($CheckChangedPaths -or $CheckCompletion) -and $baseCommit -match '^[0-9a-fA-F]{40}$') {
    $changedPaths = [System.Collections.Generic.HashSet[string]]::new(
        [StringComparer]::OrdinalIgnoreCase
    )
    foreach ($path in @(& git -c core.autocrlf=false -c core.quotepath=false -C $repoRoot diff --name-only $baseCommit -- 2>$null)) {
        if (-not [string]::IsNullOrWhiteSpace($path)) {
            [void]$changedPaths.Add($path.Replace('\', '/').Trim())
        }
    }
    foreach ($path in @(& git -c core.autocrlf=false -c core.quotepath=false -C $repoRoot ls-files --others --exclude-standard 2>$null)) {
        if (-not [string]::IsNullOrWhiteSpace($path)) {
            [void]$changedPaths.Add($path.Replace('\', '/').Trim())
        }
    }

    $allowedNormalized = @(
        $allowedPaths |
            ForEach-Object { $_.Replace('\', '/').Trim().TrimEnd('/') }
    )
    $forbiddenNormalized = @(
        $forbiddenPaths |
            ForEach-Object { $_.Replace('\', '/').Trim().TrimEnd('/') }
    )
    foreach ($changedPath in ($changedPaths | Sort-Object)) {
        $preExisting = $false
        if ($preExistingDigestMap.ContainsKey($changedPath)) {
            $candidate = Join-Path $repoRoot $changedPath
            if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                $currentDigest = (Get-FileHash -LiteralPath $candidate -Algorithm SHA256).Hash.ToLowerInvariant()
                if ($currentDigest -eq $preExistingDigestMap[$changedPath]) {
                    $preExisting = $true
                } else {
                    Add-Error "Pre-existing dirty path changed after approval: $changedPath"
                }
            } else {
                Add-Error "Pre-existing dirty path no longer exists: $changedPath"
            }
        }
        if ($preExisting) {
            continue
        }

        $allowed = $false
        foreach ($prefix in $allowedNormalized) {
            if ($changedPath -eq $prefix -or $changedPath.StartsWith("$prefix/")) {
                $allowed = $true
                break
            }
        }
        if (-not $allowed) {
            Add-Error "Changed path is outside allowedPaths: $changedPath"
            continue
        }

        foreach ($prefix in $forbiddenNormalized) {
            if ($changedPath -eq $prefix -or $changedPath.StartsWith("$prefix/")) {
                Add-Error "Changed path is forbidden by the Task Card: $changedPath"
                break
            }
        }
    }
}

if ($CheckCompletion) {
    if ($evidenceManifests.Count -eq 0) {
        Add-Error 'CheckCompletion requires at least one evidenceManifests entry'
    }

    $provenScenarioIds = [System.Collections.Generic.HashSet[string]]::new(
        [StringComparer]::Ordinal
    )
    foreach ($evidencePath in $evidenceManifests) {
        $normalizedEvidencePath = $evidencePath.Replace('\', '/').Trim()
        if ((Test-Placeholder $normalizedEvidencePath) -or
            ([IO.Path]::IsPathRooted($normalizedEvidencePath)) -or
            $normalizedEvidencePath -match '(^|/)\.\.(/|$)') {
            Add-Error "evidenceManifests path must be a concrete repository-relative path: $evidencePath"
            continue
        }
        $evidenceFullPath = [IO.Path]::GetFullPath((Join-Path $repoRoot $normalizedEvidencePath))
        if (-not $evidenceFullPath.StartsWith(
                $repoRoot + [IO.Path]::DirectorySeparatorChar,
                [StringComparison]::OrdinalIgnoreCase
            ) -or -not (Test-Path -LiteralPath $evidenceFullPath -PathType Leaf)) {
            Add-Error "Evidence Manifest does not exist: $evidencePath"
            continue
        }

        $evidenceContent = [IO.File]::ReadAllText($evidenceFullPath, [Text.Encoding]::UTF8)
        if ((Get-Scalar -FrontMatter $evidenceContent -Key 'schemaVersion') -ne
            'harnessdg.evidence/v1') {
            Add-Error "Evidence Manifest schemaVersion is invalid: $evidencePath"
        }
        if ((Get-Scalar -FrontMatter $evidenceContent -Key 'status') -ne 'PASS') {
            Add-Error "Evidence Manifest status must be PASS: $evidencePath"
        }

        $taskBlock = Get-YamlBlock -Yaml $evidenceContent -Key 'task'
        if ((Get-IndentedScalar -Block $taskBlock -Key 'taskId') -ne $taskId) {
            Add-Error "Evidence Manifest taskId does not match: $evidencePath"
        }
        foreach ($scenario in @(Get-IndentedList -Block $taskBlock -Key 'scenarios')) {
            if ($scenario -notin $scenarios) {
                Add-Error "Evidence Manifest contains a scenario outside the Task: $scenario"
            }
        }

        $governanceBlock = Get-YamlBlock -Yaml $evidenceContent -Key 'governance'
        foreach ($validationName in @(
                'specValidation',
                'taskValidation',
                'changedPathValidation'
            )) {
            $validationPattern = "(?ms)^\s+${validationName}:\s*\r?\n" +
                "(?:^\s{4}.*\r?\n?)*?^\s{4}result:\s*PASS\s*$"
            if ($governanceBlock -notmatch $validationPattern) {
                Add-Error "Evidence governance check is not PASS ($validationName): $evidencePath"
            }
        }

        $sourceBlock = Get-YamlBlock -Yaml $evidenceContent -Key 'source'
        $sourceCommit = Get-IndentedScalar -Block $sourceBlock -Key 'commitSha'
        if ($sourceCommit -ne $currentCommit) {
            Add-Error "Evidence source commit must equal current HEAD: $evidencePath"
        }
        if ((Get-IndentedScalar -Block $sourceBlock -Key 'dirtyWorktree') -ne 'false') {
            Add-Error "Evidence cannot prove completion with dirtyWorktree=true: $evidencePath"
        }

        $executionBlock = Get-YamlBlock -Yaml $evidenceContent -Key 'execution'
        if ((Get-IndentedScalar -Block $executionBlock -Key 'result') -ne 'PASS') {
            Add-Error "Evidence execution result must be PASS: $evidencePath"
        }
        if ((Get-IndentedScalar -Block $executionBlock -Key 'exitCode') -ne '0') {
            Add-Error "Evidence execution exitCode must be 0: $evidencePath"
        }
        $evidenceLevel = Get-IndentedScalar -Block $executionBlock -Key 'evidenceLevel'
        if ((Get-LevelNumber $evidenceLevel) -lt (Get-LevelNumber $requiredEvidenceLevel)) {
            Add-Error "Evidence level is below Task requirement: $evidencePath"
        }

        $coverageBlock = Get-YamlBlock -Yaml $evidenceContent -Key 'coverage'
        foreach ($match in [regex]::Matches(
                $coverageBlock,
                '(?m)^\s+-\s+scenarioId:\s*(?<id>AC(?:-[A-Z0-9]+)+-\d{3})\s*$'
            )) {
            $provenScenarioId = $match.Groups['id'].Value
            if ($provenScenarioId -in $scenarios) {
                [void]$provenScenarioIds.Add($provenScenarioId)
            } else {
                Add-Error "Evidence coverage proves a scenario outside the Task: $provenScenarioId"
            }
        }
        if ($coverageBlock -notmatch '(?m)^\s+notProven:\s*\[\]\s*$') {
            Add-Error "Evidence still declares notProven items: $evidencePath"
        }
        $secretsBlock = Get-YamlBlock -Yaml $evidenceContent -Key 'secrets'
        if ((Get-IndentedScalar -Block $secretsBlock -Key 'redactionChecked') -ne 'true') {
            Add-Error "Evidence secret redaction check must be true: $evidencePath"
        }
        $limitationsBlock = Get-YamlBlock -Yaml $evidenceContent -Key 'limitations'
        if ($limitationsBlock -notmatch '(?m)^\s+skippedTests:\s*\[\]\s*$') {
            Add-Error "Evidence contains skipped or unreported tests: $evidencePath"
        }
        $reviewBlock = Get-YamlBlock -Yaml $evidenceContent -Key 'review'
        $reviewedBy = Get-IndentedScalar -Block $reviewBlock -Key 'reviewedBy'
        $reviewedAt = Get-IndentedScalar -Block $reviewBlock -Key 'reviewedAt'
        if ((Get-IndentedScalar -Block $reviewBlock -Key 'accepted') -ne 'true') {
            Add-Error "Evidence review must be accepted before completion: $evidencePath"
        }
        if ((Test-Placeholder $reviewedBy) -or
            $reviewedBy -match '(?i)\b(ai|self|codex|trae|claude|cursor|qwen)\b') {
            Add-Error "Evidence reviewedBy must identify the verification authority: $evidencePath"
        }
        $parsedReview = [DateTimeOffset]::MinValue
        if ((Test-Placeholder $reviewedAt) -or
            -not ([DateTimeOffset]::TryParse($reviewedAt, [ref]$parsedReview))) {
            Add-Error "Evidence reviewedAt must be a valid timestamp: $evidencePath"
        }
    }

    foreach ($scenarioId in $scenarios) {
        if (-not $provenScenarioIds.Contains($scenarioId)) {
            Add-Error "No completion Evidence proves acceptance scenario: $scenarioId"
        }
    }
}

if ($errors.Count -gt 0) {
    Write-Host "AI Task Card validation FAILED ($($errors.Count) issue(s))" -ForegroundColor Red
    foreach ($item in $errors) {
        Write-Host " - $item" -ForegroundColor Red
    }
    exit 1
}

Write-Host 'AI Task Card validation PASS' -ForegroundColor Green
Write-Host " - task: $taskId"
Write-Host " - phase: $phase"
Write-Host " - requirements: $($requirements.Count)"
Write-Host " - acceptance scenarios: $($scenarios.Count)"
Write-Host " - allowed paths: $($allowedPaths.Count)"
if ($CheckChangedPaths) {
    Write-Host ' - changed paths checked against Task scope'
}
if ($CheckCompletion) {
    Write-Host " - completion evidence manifests: $($evidenceManifests.Count)"
}
exit 0
