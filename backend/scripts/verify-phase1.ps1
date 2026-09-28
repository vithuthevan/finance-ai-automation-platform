# Phase 1 final verification — single Gradle invocation at a time
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

function Invoke-GradleTest {
    param(
        [string]$Label,
        [string[]]$GradleArgs
    )
    Write-Host "`n========== $Label ==========" -ForegroundColor Cyan
    & .\gradlew --stop 2>&1 | Out-Null
    Start-Sleep -Seconds 2
    Remove-Item -Recurse -Force "platform-app\build\test-results", "platform-app\build\tmp\test" -ErrorAction SilentlyContinue
    $allArgs = @("--no-daemon", "--console=plain") + $GradleArgs + @("-x", "jacocoTestReport")
    & .\gradlew @allArgs
    return $LASTEXITCODE
}

function Get-TestStats {
    param([string]$ResultsDir)
    $dir = Join-Path (Get-Location) $ResultsDir
    if (-not (Test-Path $dir)) {
        return @{ executed = 0; passed = 0; failed = 0; skipped = 0 }
    }
    $files = Get-ChildItem -Path $dir -Recurse -Filter "TEST-*.xml" -ErrorAction SilentlyContinue
    $executed = 0; $failed = 0; $skipped = 0; $errors = 0
    foreach ($f in $files) {
        [xml]$x = Get-Content $f.FullName
        $executed += [int]$x.testsuite.tests
        $failed += [int]$x.testsuite.failures
        $errors += [int]$x.testsuite.errors
        $skipped += [int]$x.testsuite.skipped
    }
    $passed = $executed - $failed - $errors - $skipped
    return @{ executed = $executed; passed = $passed; failed = ($failed + $errors); skipped = $skipped }
}

$results = @{}

$code = Invoke-GradleTest "SessionRevocationIntegrationTest" @(
    ":platform-app:test",
    "--tests", "com.finance.platform.security.SessionRevocationIntegrationTest"
)
$results["session"] = Get-TestStats "platform-app\build\test-results\test"
$results["session_exit"] = $code

$code = Invoke-GradleTest "ClosedPeriod concurrency" @(
    ":platform-app:test",
    "--tests", "com.finance.platform.finance.ClosedPeriodIntegrityIntegrationTest.closeVersusVoidExpense_serializesWithoutPostCloseMutation"
)
$results["closed_period_exit"] = $code

$code = Invoke-GradleTest "module-auth" @(":module-auth:test", "--rerun-tasks")
$results["auth"] = Get-TestStats "module-auth\build\test-results\test"
$results["auth_exit"] = $code

$code = Invoke-GradleTest "module-finance" @(":module-finance:test", "--rerun-tasks")
$results["finance"] = Get-TestStats "module-finance\build\test-results\test"
$results["finance_exit"] = $code

$code = Invoke-GradleTest "flyway" @(":platform-app:flywayIntegrationTest", "--rerun-tasks", "--no-parallel")
$results["flyway"] = Get-TestStats "platform-app\build\test-results\flywayIntegrationTest"
$results["flyway_exit"] = $code

$code = Invoke-GradleTest "postgres integration" @(":platform-app:postgresIntegrationTest", "--rerun-tasks", "--no-parallel")
$results["postgres"] = Get-TestStats "platform-app\build\test-results\postgresIntegrationTest"
$results["postgres_exit"] = $code

$code = Invoke-GradleTest "tenant security" @(":platform-app:tenantSecurityIntegrationTest", "--rerun-tasks", "--no-parallel")
$results["tenant"] = Get-TestStats "platform-app\build\test-results\tenantSecurityIntegrationTest"
$results["tenant_exit"] = $code

$outFile = Join-Path (Get-Location) "phase1-verification-results.json"
$results | ConvertTo-Json -Depth 5 | Set-Content $outFile
Write-Host "`nWrote $outFile" -ForegroundColor Green
$results | ConvertTo-Json -Depth 5
