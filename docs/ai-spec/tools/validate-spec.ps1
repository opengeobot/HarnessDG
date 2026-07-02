Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$specRoot = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$errors = [System.Collections.Generic.List[string]]::new()

function Add-Error {
    param([string]$Message)
    $errors.Add($Message)
}

function Get-Ids {
    param(
        [string[]]$Paths,
        [string]$Pattern
    )

    $result = [System.Collections.Generic.List[string]]::new()
    foreach ($path in $Paths) {
        $content = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
        foreach ($match in [regex]::Matches($content, $Pattern, [Text.RegularExpressions.RegexOptions]::Multiline)) {
            $result.Add($match.Groups['id'].Value)
        }
    }
    return $result.ToArray()
}

function Assert-UniqueDefinitions {
    param(
        [string]$Name,
        [string[]]$Ids,
        [System.Collections.Generic.HashSet[string]]$AllDefinitions
    )

    if ($null -eq $Ids) {
        Add-Error "$Name has no definitions"
        return
    }

    foreach ($group in ($Ids | Group-Object)) {
        if ($group.Count -gt 1) {
            Add-Error "$Name duplicate definition: $($group.Name) ($($group.Count)x)"
        }
        [void]$AllDefinitions.Add($group.Name)
    }
}

# Read the fixed-indentation path entries. CI should add semantic YAML validation.
$manifestPath = Join-Path $specRoot 'manifest.yaml'
if (-not (Test-Path -LiteralPath $manifestPath)) {
    Add-Error 'manifest.yaml is missing'
} else {
    $manifestLines = Get-Content -LiteralPath $manifestPath -Encoding UTF8
    $manifestEntries = @(
        $manifestLines |
            Where-Object { $_ -match '^  - path: (.+)$' } |
            ForEach-Object { $Matches[1].Trim() }
    )

    if ($manifestEntries.Count -eq 0) {
        Add-Error 'manifest.yaml has no document entries'
    }

    foreach ($entry in $manifestEntries) {
        $target = Join-Path $specRoot $entry
        if (-not (Test-Path -LiteralPath $target)) {
            Add-Error "manifest path does not exist: $entry"
        }
    }

    foreach ($group in ($manifestEntries | Group-Object)) {
        if ($group.Count -gt 1) {
            Add-Error "manifest duplicate path: $($group.Name)"
        }
    }
}

$allDefinitions = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$requirementFiles = @(Get-ChildItem -LiteralPath (Join-Path $specRoot '01-requirements') -Filter '*.md' -File)
$acceptanceFiles = @(Get-ChildItem -LiteralPath (Join-Path $specRoot '05-acceptance') -Filter '*.md' -File)

$definitionSpecs = @(
    @{
        Name = 'Question'
        Paths = @((Join-Path $specRoot '00-governance/open-questions.md'))
        Pattern = '\| `(?<id>Q-\d{3})` \|'
    },
    @{
        Name = 'Decision'
        Paths = @((Join-Path $specRoot '00-governance/decision-log.md'))
        Pattern = '^### (?<id>DEC-\d{3})\b'
    },
    @{
        Name = 'Audit finding'
        Paths = @((Join-Path $specRoot '00-governance/current-state-audit-2026-07-02.md'))
        Pattern = '\| `(?<id>AUD-\d{3})` \|'
    },
    @{
        Name = 'Term conflict'
        Paths = @((Join-Path $specRoot '01-requirements/glossary.md'))
        Pattern = '\| `(?<id>TERM-\d{3})` \|'
    },
    @{
        Name = 'Outcome'
        Paths = @((Join-Path $specRoot '01-requirements/product-scope.md'))
        Pattern = '\| `(?<id>OUT-\d{3})` \|'
    },
    @{
        Name = 'Persona'
        Paths = @((Join-Path $specRoot '01-requirements/personas-and-scope.md'))
        Pattern = '\| `(?<id>PER-\d{3})` \|'
    },
    @{
        Name = 'Requirement'
        Paths = @($requirementFiles.FullName)
        Pattern = '^## (?<id>REQ(?:-[A-Z0-9]+)+-\d{3})\b'
    },
    @{
        Name = 'Invariant'
        Paths = @((Join-Path $specRoot '02-domain/entity-invariants.md'))
        Pattern = '`(?<id>INV(?:-[A-Z0-9]+)+-\d{3})`'
    },
    @{
        Name = 'Capability'
        Paths = @((Join-Path $specRoot '02-delivery/traceability-matrix.md'))
        Pattern = '\| `(?<id>CAP-[A-Z0-9]+-\d{3})` \|'
    },
    @{
        Name = 'Journey'
        Paths = @((Join-Path $specRoot '03-use-cases/user-journey-catalog.md'))
        Pattern = '\| `(?<id>JRN(?:-[A-Z0-9]+)+-\d{3})` \|'
    },
    @{
        Name = 'Page'
        Paths = @((Join-Path $specRoot '04-ui/page-catalog.md'))
        Pattern = '\| `(?<id>PAGE(?:-[A-Z0-9]+)+-\d{3})` \|'
    },
    @{
        Name = 'Acceptance'
        Paths = @($acceptanceFiles.FullName)
        Pattern = '\| `(?<id>AC(?:-[A-Z0-9]+)+-\d{3})` \|'
    },
    @{
        Name = 'NFR'
        Paths = @((Join-Path $specRoot '01-requirements/non-functional-requirements.md'))
        Pattern = '\| `(?<id>NFR(?:-[A-Z0-9]+)+-\d{3})` \|'
    },
    @{
        Name = 'Task candidate'
        Paths = @((Join-Path $specRoot '02-delivery/work-breakdown.md'))
        Pattern = '\| `(?<id>TASK(?:-[A-Z0-9]+)+-\d{3})` \|'
    }
)

foreach ($spec in $definitionSpecs) {
    $ids = Get-Ids -Paths $spec.Paths -Pattern $spec.Pattern
    Assert-UniqueDefinitions -Name $spec.Name -Ids $ids -AllDefinitions $allDefinitions
}

# Real stable IDs end in three digits; template placeholders do not match.
$referencePattern = '\b(?<id>(?:(?:Q|DEC|AUD|TERM|OUT|PER)-\d{3}|(?:REQ|INV|AC|PAGE|JRN|TASK|NFR|CAP)(?:-[A-Z0-9]+)+-\d{3}))\b'
$referenceFiles = @(
    Get-ChildItem -LiteralPath $specRoot -Recurse -File |
        Where-Object { $_.Extension -in @('.md', '.yaml', '.ps1') -and $_.FullName -ne $PSCommandPath }
)

$unknown = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($file in $referenceFiles) {
    $content = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
    foreach ($match in [regex]::Matches($content, $referencePattern)) {
        $id = $match.Groups['id'].Value
        if ($id -match '-000$') {
            continue
        }
        if (-not $allDefinitions.Contains($id)) {
            [void]$unknown.Add($id)
        }
    }
}

foreach ($id in ($unknown | Sort-Object)) {
    Add-Error "unknown ID reference: $id"
}

# AI execution policy is a mandatory machine gate under DEC-009.
if (Test-Path -LiteralPath $manifestPath) {
    $manifestContent = [IO.File]::ReadAllText($manifestPath, [Text.Encoding]::UTF8)
    $requiredPolicyPatterns = @(
        '(?m)^approvedSlices:\s*$',
        '(?m)^\s+approvedSlicesNormative:\s+true\s*$',
        '(?m)^\s+- id:\s+dataset-experience-and-agent-delivery\s*$',
        '(?m)^\s+decision:\s+DEC-008\s*$',
        '(?m)^\s+implementationStatus:\s+GATED_BY_P0B_AND_PHASE_ORDER\s*$',
        '(?m)^aiExecutionPolicy:\s*$',
        '(?m)^\s+decision:\s+DEC-009\s*$',
        '(?m)^\s+requireTaskCardReady:\s+true\s*$',
        '(?m)^\s+requireRequirementsReady:\s+true\s*$',
        '(?m)^\s+requireAcceptedDecisions:\s+true\s*$',
        '(?m)^\s+requireBaseCommit:\s+true\s*$',
        '(?m)^\s+requireAllowedPaths:\s+true\s*$',
        '(?m)^\s+requireStageGate:\s+true\s*$',
        '(?m)^\s+requireAcceptanceEvidenceMap:\s+true\s*$',
        '(?m)^\s+requireCrossCuttingPlan:\s+true\s*$',
        '(?m)^\s+requireTaskSpecificValidation:\s+true\s*$',
        '(?m)^\s+requirePathTriggeredValidationProfiles:\s+true\s*$',
        '(?m)^\s+requireCompletionEvidenceValidation:\s+true\s*$',
        '(?m)^\s+protectedCiEnforcementStatus:\s+MISSING_TASK_GOV_008\s*$',
        '(?m)^\s+denyImplementationOnValidationFailure:\s+true\s*$'
    )
    foreach ($pattern in $requiredPolicyPatterns) {
        if ($manifestContent -notmatch $pattern) {
            Add-Error "manifest AI execution policy missing: $pattern"
        }
    }

    $acceptedBlock = [regex]::Match(
        $manifestContent,
        '(?ms)^acceptedDecisions:\s*(?<body>.*?)(?=^\S|\z)'
    )
    if (-not $acceptedBlock.Success) {
        Add-Error 'manifest acceptedDecisions block is missing'
    } else {
        $decisionLogPath = Join-Path $specRoot '00-governance/decision-log.md'
        $decisionLogContent = [IO.File]::ReadAllText($decisionLogPath, [Text.Encoding]::UTF8)
        foreach ($match in [regex]::Matches($acceptedBlock.Groups['body'].Value, '(?m)^\s+-\s+(?<id>DEC-\d{3})\s*$')) {
            $decisionId = $match.Groups['id'].Value
            $section = [regex]::Match(
                $decisionLogContent,
                "(?ms)^###\s+$([regex]::Escape($decisionId))\b(?<body>.*?)(?=^###\s+|\z)"
            )
            if (-not $section.Success -or
                $section.Groups['body'].Value -notmatch '(?m)^status:\s*ACCEPTED\s*$') {
                Add-Error "manifest accepted decision is not ACCEPTED: $decisionId"
            }
        }
    }
}

$taskValidatorPath = Join-Path $specRoot 'tools/validate-task-card.ps1'
if (-not (Test-Path -LiteralPath $taskValidatorPath -PathType Leaf)) {
    Add-Error 'tools/validate-task-card.ps1 is missing'
}

# Validate relative Markdown links inside the AI specification package.
$markdownFiles = @(Get-ChildItem -LiteralPath $specRoot -Recurse -Filter '*.md' -File)
foreach ($file in $markdownFiles) {
    $content = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
    foreach ($match in [regex]::Matches($content, '\[[^\]]*\]\((?<target>[^)]+)\)')) {
        $target = $match.Groups['target'].Value.Trim().Trim('<', '>')
        if ([string]::IsNullOrWhiteSpace($target) -or
            $target -match '^(https?://|mailto:|#)') {
            continue
        }
        $pathPart = ($target -split '#', 2)[0]
        if ($pathPart -match '\s+"') {
            $pathPart = ($pathPart -split '\s+"', 2)[0]
        }
        $resolved = [IO.Path]::GetFullPath((Join-Path $file.DirectoryName $pathPart))
        if (-not (Test-Path -LiteralPath $resolved)) {
            Add-Error "broken Markdown link in $($file.Name): $target"
        }
    }
}

if ($errors.Count -gt 0) {
    Write-Host "AI spec validation FAILED ($($errors.Count) issue(s))" -ForegroundColor Red
    foreach ($item in $errors) {
        Write-Host " - $item" -ForegroundColor Red
    }
    exit 1
}

Write-Host "AI spec validation PASS" -ForegroundColor Green
Write-Host " - manifest documents: $($manifestEntries.Count)"
Write-Host " - stable ID definitions: $($allDefinitions.Count)"
exit 0
