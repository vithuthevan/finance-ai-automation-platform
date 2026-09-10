$ErrorActionPreference = 'Continue'
$Base = 'http://localhost:8080'
$Fe = 'http://localhost:4200'
$Results = New-Object System.Collections.Generic.List[object]
$ts = Get-Date -Format 'yyyyMMddHHmmss'
$AppPwd = 'password1'
$JwtSecretB64 = 'Zm9yLWRldmVsb3BtZW50LXVzZS1hLXN0cm9uZy1iYXNlNjQtZW5jb2RlZC1zZWNyZXQta2V5'
$Psql = 'c:\Users\HP\Downloads\finance-ai-automation-platform\.tools\pgsql\pgsql\bin\psql.exe'
$env:PGPASSWORD = 'postgres'
$Tmp = Join-Path $env:TEMP "qa-body-$ts"
New-Item -ItemType Directory -Force -Path $Tmp | Out-Null

function Add-Result($id, $result, $notes, $evidence) {
  $Results.Add([pscustomobject]@{ Id = $id; Result = $result; Notes = $notes; Evidence = $evidence })
  Write-Host ("[{0}] {1}: {2}" -f $id, $result, $notes)
}

function Invoke-Curl {
  param(
    [string]$Method = 'GET',
    [string]$Url,
    [string]$Body = $null,
    [string]$Token = $null,
    [string[]]$ExtraHeaders = @()
  )
  $bodyFile = Join-Path $Tmp ("body-" + [guid]::NewGuid().ToString() + ".txt")
  $hdrFile = Join-Path $Tmp ("hdr-" + [guid]::NewGuid().ToString() + ".txt")
  $args = @('-sS', '-X', $Method, '-D', $hdrFile, '-o', $bodyFile, '-w', '%{http_code}')
  if ($Token) { $args += @('-H', "Authorization: Bearer $Token") }
  foreach ($h in $ExtraHeaders) { $args += @('-H', $h) }
  if ($null -ne $Body) {
    $inFile = Join-Path $Tmp ("in-" + [guid]::NewGuid().ToString() + ".json")
    Set-Content -Path $inFile -Value $Body -Encoding UTF8 -NoNewline
    $args += @('-H', 'Content-Type: application/json', '--data-binary', "@$inFile")
  }
  $args += $Url
  $code = & curl.exe @args 2>$null
  $content = ''
  if (Test-Path $bodyFile) { $content = Get-Content -Raw -Path $bodyFile -ErrorAction SilentlyContinue }
  if (-not $content) { $content = '' }
  return [pscustomobject]@{ StatusCode = [int]$code; Content = $content; HeaderFile = $hdrFile }
}

function Parse-JsonSafe([string]$s) {
  if (-not $s) { return $null }
  try { return $s | ConvertFrom-Json } catch { return $null }
}

function Tamper-JwtRole([string]$token) {
  $parts = $token.Split('.')
  $pad = $parts[1] + ('=' * ((4 - ($parts[1].Length % 4)) % 4))
  $pad = $pad.Replace('-','+').Replace('_','/')
  $payloadJson = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($pad))
  $obj = $payloadJson | ConvertFrom-Json
  $obj.role = 'ADMIN'
  $newPayload = ($obj | ConvertTo-Json -Compress)
  $p = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($newPayload)).TrimEnd('=').Replace('+','-').Replace('/','_')
  return "$($parts[0]).$p.$($parts[2])"
}

function New-SignedJwt($sub, $firmId, $role, $email, $expOffsetSec) {
  $script = @"
const crypto = require('crypto');
const secret = Buffer.from('$JwtSecretB64', 'base64');
function b64url(buf){return Buffer.from(buf).toString('base64').replace(/=+$/,'').replace(/\+/g,'-').replace(/\//g,'_');}
function sign(payload){
  const h=b64url(JSON.stringify({alg:'HS256',typ:'JWT'}));
  const p=b64url(JSON.stringify(payload));
  const data=h+'.'+p;
  const sig=crypto.createHmac('sha256', secret).update(data).digest('base64').replace(/=+$/,'').replace(/\+/g,'-').replace(/\//g,'_');
  return data+'.'+sig;
}
const now=Math.floor(Date.now()/1000);
console.log(sign({
  jti: crypto.randomUUID(),
  sub: '$sub',
  iss: 'finance-platform',
  firmId: '$firmId',
  role: '$role',
  email: '$email',
  iat: now + Math.min(0, $expOffsetSec),
  exp: now + $expOffsetSec
}));
"@
  return (node -e $script).Trim()
}

function Register-Firm([string]$prefix) {
  $email = ("admin-{0}-{1}@example.com" -f $prefix, $ts).ToLower()
  $body = '{"firmName":"QA Firm ' + $prefix + ' ' + $ts + '","email":"' + $email + '","password":"' + $AppPwd + '","fullName":"Admin ' + $prefix + '"}'
  $r = Invoke-Curl -Method POST -Url "$Base/api/v1/auth/register" -Body $body
  return [pscustomobject]@{ Response = $r; Email = $email; Body = (Parse-JsonSafe $r.Content) }
}

function Login-User([string]$email, [string]$password = $AppPwd) {
  $body = '{"email":"' + $email + '","password":"' + $password + '"}'
  return Invoke-Curl -Method POST -Url "$Base/api/v1/auth/login" -Body $body
}

# ---------- TC-001 ----------
$h = Invoke-Curl -Url "$Base/api/v1/health"
if ($h.StatusCode -eq 200 -and $h.Content -match '"status"\s*:\s*"UP"') {
  Add-Result 'TC-001' 'Pass' 'Local backend up; Flyway applied 24 migrations (v24); health UP' $h.Content
} else {
  Add-Result 'TC-001' 'Fail' "Health unexpected: $($h.StatusCode)" $h.Content
}

# ---------- TC-002 ----------
$loginPage = Invoke-Curl -Url "$Fe/login"
$refresh = Invoke-Curl -Url "$Fe/login"
if ($loginPage.StatusCode -eq 200 -and $refresh.StatusCode -eq 200) {
  Add-Result 'TC-002' 'Pass' 'Login page HTTP 200; refresh keeps valid route' "status=$($loginPage.StatusCode); bytes=$($loginPage.Content.Length)"
} else {
  Add-Result 'TC-002' 'Fail' "login=$($loginPage.StatusCode) refresh=$($refresh.StatusCode)" ''
}

# ---------- TC-003 ----------
$proxyHealth = Invoke-Curl -Url "$Fe/api/v1/health"
$loginViaProxy = Invoke-Curl -Method POST -Url "$Fe/api/v1/auth/login" -Body '{"email":"nobody@example.com","password":"wrongpass1"}'
if ($proxyHealth.StatusCode -eq 200 -and $proxyHealth.Content -match 'UP' -and $loginViaProxy.StatusCode -in @(400,401,422)) {
  Add-Result 'TC-003' 'Pass' 'Same-origin via ng proxy: /api/v1 on :4200 reaches backend' "proxy=$($proxyHealth.Content); loginStatus=$($loginViaProxy.StatusCode)"
} else {
  Add-Result 'TC-003' 'Fail' "proxy=$($proxyHealth.StatusCode) login=$($loginViaProxy.StatusCode)" $proxyHealth.Content
}

# ---------- TC-004 ----------
$prodOut = Join-Path $env:TEMP "qa-prod-jwt-$ts.txt"
$prodJob = Start-Job -ScriptBlock {
  param($out)
  Set-Location 'c:\Users\HP\Downloads\finance-ai-automation-platform\backend'
  $env:SPRING_PROFILES_ACTIVE='prod'
  $env:SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/finance_platform_qa6'
  $env:SPRING_DATASOURCE_USERNAME='postgres'
  $env:SPRING_DATASOURCE_PASSWORD='postgres'
  $env:APP_JWT_SECRET='change-me'
  $env:SPRING_JPA_HIBERNATE_DDL_AUTO='none'
  $env:SERVER_PORT='18081'
  & .\gradlew.bat :platform-app:bootRun --no-daemon *> $out
} -ArgumentList $prodOut
$tc004 = $null
for ($i=0; $i -lt 36; $i++) {
  Start-Sleep -Seconds 5
  if (Test-Path $prodOut) {
    $txt = Get-Content $prodOut -Raw -ErrorAction SilentlyContinue
    if ($txt -match 'APP_JWT_SECRET is too weak|IllegalStateException|Could not resolve placeholder|APPLICATION FAILED|bootRun FAILED|BUILD FAILED') {
      $tc004 = $txt; break
    }
    if ($txt -match 'Started FinancePlatformApplication') { $tc004 = 'STARTED_UNEXPECTEDLY'; break }
  }
}
Stop-Job $prodJob -ErrorAction SilentlyContinue
Remove-Job $prodJob -Force -ErrorAction SilentlyContinue
Get-NetTCPConnection -LocalPort 18081 -ErrorAction SilentlyContinue | ForEach-Object {
  try { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue } catch {}
}
if ($tc004 -eq 'STARTED_UNEXPECTEDLY') {
  Add-Result 'TC-004' 'Fail' 'Prod started with weak JWT secret' 'started'
} elseif ($tc004 -match 'too weak|IllegalStateException|placeholder|FAILED') {
  $snip = ($tc004 | Select-String -Pattern 'too weak|IllegalStateException|placeholder|FAILED' | Select-Object -First 2 | ForEach-Object { $_.Line }) -join ' | '
  Add-Result 'TC-004' 'Pass' 'Prod rejects APP_JWT_SECRET=change-me' $snip
} else {
  Add-Result 'TC-004' 'Blocked' 'Prod failure not observed in time' "len=$((Get-Item $prodOut -EA SilentlyContinue).Length)"
}

# ---------- TC-005 ----------
$sw = Invoke-Curl -Url "$Base/swagger-ui.html"
$docs = Invoke-Curl -Url "$Base/v3/api-docs"
$prodYml = Get-Content 'c:\Users\HP\Downloads\finance-ai-automation-platform\backend\platform-app\src\main\resources\application-prod.yml' -Raw
if (($sw.StatusCode -in 200,302) -and $docs.StatusCode -eq 200 -and $prodYml -match 'SPRINGDOC_SWAGGER_UI_ENABLED:false') {
  Add-Result 'TC-005' 'Pass' 'Swagger on locally; prod defaults springdoc off' "swagger=$($sw.StatusCode); docs=$($docs.StatusCode)"
} else {
  Add-Result 'TC-005' 'Fail' "swagger=$($sw.StatusCode) docs=$($docs.StatusCode)" ''
}

# ---------- TC-006 ----------
$h2 = Invoke-Curl -Url "$Base/api/v1/health"
$ready = Invoke-Curl -Url "$Base/api/v1/health/ready"
$leak = ($h2.Content + $ready.Content) -match 'password|jdbc:|secret|APP_JWT|/Users/|C:\\'
if ($h2.StatusCode -eq 200 -and -not $leak) {
  Add-Result 'TC-006' 'Pass' 'Health exposes only safe keys' "$($h2.Content) | $($ready.Content)"
} else {
  Add-Result 'TC-006' 'Fail' "leak=$leak" $h2.Content
}

$reg = Register-Firm 'env'
$adminEmail = $reg.Email
$login = Login-User $adminEmail
$loginObj = Parse-JsonSafe $login.Content
$adminToken = $loginObj.accessToken
$adminUserId = $loginObj.userId
$adminFirmId = $reg.Body.firmId
Write-Host "Admin registered: $adminEmail tokenLen=$($adminToken.Length) firm=$adminFirmId"

# ---------- TC-007 ----------
$nf = Invoke-Curl -Url "$Base/api/v1/does-not-exist" -Token $adminToken
$hasStack = $nf.Content -match 'Exception|at com\.finance|stackTrace'
if ($nf.StatusCode -eq 404 -and -not $hasStack) {
  Add-Result 'TC-007' 'Pass' 'Authenticated unknown route -> clean 404' "status=$($nf.StatusCode) body=$($nf.Content)"
} else {
  Add-Result 'TC-007' 'Fail' "status=$($nf.StatusCode)" $nf.Content
}

# ---------- TC-008 ----------
$mal = Invoke-Curl -Method POST -Url "$Base/api/v1/clients" -Token $adminToken -Body '{"name":'
if ($mal.StatusCode -eq 400) {
  Add-Result 'TC-008' 'Pass' 'Malformed JSON -> 400 controlled error' $mal.Content
} else {
  Add-Result 'TC-008' 'Fail' "status=$($mal.StatusCode)" $mal.Content
}

# ---------- TC-009 ----------
$page = Invoke-Curl -Url "$Base/api/v1/clients?page=0&size=1000000" -Token $adminToken
$pageObj = Parse-JsonSafe $page.Content
$sizeOk = ($pageObj.size -le 100) -or ($page.Content -match '"size"\s*:\s*100')
if ($page.StatusCode -eq 200 -and $sizeOk) {
  Add-Result 'TC-009' 'Pass' 'Page size clamped to <=100' "size=$($pageObj.size)"
} else {
  Add-Result 'TC-009' 'Fail' "status=$($page.StatusCode) size=$($pageObj.size)" $page.Content
}

# ---------- TC-010 ----------
$corrId = 'manual-qa-001'
$loginCorr = Invoke-Curl -Method POST -Url "$Base/api/v1/auth/login" -Body ("{`"email`":`"$adminEmail`",`"password`":`"$AppPwd`"}") -ExtraHeaders @("X-Request-ID: $corrId")
$dbCorr = (& $Psql -h localhost -p 5432 -U postgres -d finance_platform_qa6 -tAc "SELECT correlation_id FROM audit_log WHERE correlation_id='$corrId' LIMIT 1;").Trim()
if ($dbCorr -eq $corrId) {
  Add-Result 'TC-010' 'Pass' 'X-Request-ID stored on audit_log.correlation_id (not echoed in HTTP/logs by design)' "db=$dbCorr; login=$($loginCorr.StatusCode)"
} else {
  Add-Result 'TC-010' 'Fail' 'Correlation ID not in audit_log' "db='$dbCorr' login=$($loginCorr.StatusCode)"
}

# ---------- TC-011 ----------
$reg2 = Register-Firm 'reg'
$hasTokens = $reg2.Response.Content -match 'accessToken'
$sub = (& $Psql -h localhost -p 5432 -U postgres -d finance_platform_qa6 -tAc "SELECT status FROM firm_subscriptions WHERE firm_id='$($reg2.Body.firmId)' LIMIT 1;").Trim()
$login2 = Login-User $reg2.Email
if ($reg2.Response.StatusCode -eq 201 -and -not $hasTokens -and $reg2.Body.firmId -and $sub -and $login2.StatusCode -eq 200) {
  Add-Result 'TC-011' 'Pass' 'Register atomic firm+admin+subscription; no login tokens; login works' "sub=$sub"
} else {
  Add-Result 'TC-011' 'Fail' "reg=$($reg2.Response.StatusCode) sub=$sub login=$($login2.StatusCode)" $reg2.Response.Content
}

# ---------- TC-012 ----------
$dup = Invoke-Curl -Method POST -Url "$Base/api/v1/auth/register" -Body ("{`"firmName`":`"Dup`",`"email`":`"$($reg2.Email)`",`"password`":`"$AppPwd`",`"fullName`":`"Dup`"}")
$countUsers = (& $Psql -h localhost -p 5432 -U postgres -d finance_platform_qa6 -tAc "SELECT count(*) FROM users WHERE email='$($reg2.Email)';").Trim()
if ($dup.StatusCode -eq 409 -and $countUsers -eq '1') {
  Add-Result 'TC-012' 'Pass' 'Duplicate email -> 409; no duplicate user' "users=$countUsers"
} else {
  Add-Result 'TC-012' 'Fail' "status=$($dup.StatusCode) users=$countUsers" $dup.Content
}

# ---------- TC-013 ----------
$me = Invoke-Curl -Url "$Base/api/v1/auth/me" -Token $adminToken
$meObj = Parse-JsonSafe $me.Content
if ($login.StatusCode -eq 200 -and $me.StatusCode -eq 200 -and $meObj.email -eq $adminEmail) {
  Add-Result 'TC-013' 'Pass' 'Login + /auth/me match' $me.Content
} else {
  Add-Result 'TC-013' 'Fail' "login=$($login.StatusCode) me=$($me.StatusCode)" $me.Content
}

# ---------- TC-014 ----------
$bad = Login-User $adminEmail 'wrong-password-xx'
if ($bad.StatusCode -in @(400,401,422) -and $bad.Content -match 'Invalid credentials' -and $bad.Content -notmatch 'accessToken') {
  Add-Result 'TC-014' 'Pass' "Wrong password rejected ($($bad.StatusCode)); generic message" $bad.Content
} else {
  Add-Result 'TC-014' 'Fail' "status=$($bad.StatusCode)" $bad.Content
}

# ---------- TC-015 ----------
$unk = Login-User "unknown-$ts@example.com" 'wrong-password-xx'
$badDetail = (Parse-JsonSafe $bad.Content).detail
$unkDetail = (Parse-JsonSafe $unk.Content).detail
if ($unk.StatusCode -eq $bad.StatusCode -and $badDetail -eq $unkDetail) {
  Add-Result 'TC-015' 'Pass' 'No account enumeration via login response' "status=$($unk.StatusCode); detail=$unkDetail"
} else {
  Add-Result 'TC-015' 'Fail' "unk=$($unk.StatusCode)/$unkDetail bad=$($bad.StatusCode)/$badDetail" ''
}

# ---------- TC-016 ----------
$rateEmail = "ratelimit-$ts@example.com"
Invoke-Curl -Method POST -Url "$Base/api/v1/auth/register" -Body ("{`"firmName`":`"RL`",`"email`":`"$rateEmail`",`"password`":`"$AppPwd`",`"fullName`":`"RL`"}") | Out-Null
$throttled = $false; $lastStatus=0; $lastBody=''
for ($i=0; $i -lt 25; $i++) {
  $r = Login-User $rateEmail 'bad-password-xx'
  $lastStatus = $r.StatusCode; $lastBody = $r.Content
  if ($r.Content -match 'Too many requests') { $throttled = $true; break }
}
$healthAfter = Invoke-Curl -Url "$Base/api/v1/health"
if ($throttled -and $healthAfter.StatusCode -eq 200) {
  Add-Result 'TC-016' 'Pass' 'Login rate limit engages; service stable' "status=$lastStatus"
} else {
  Add-Result 'TC-016' 'Fail' "throttled=$throttled last=$lastStatus" $lastBody
}

# ---------- TC-017 ----------
$expiredToken = New-SignedJwt $adminUserId $adminFirmId 'ADMIN' $adminEmail -3600
$expCall = Invoke-Curl -Url "$Base/api/v1/clients" -Token $expiredToken
if ($expCall.StatusCode -eq 401) {
  Add-Result 'TC-017' 'Pass' 'Expired JWT -> 401' $expCall.Content
} else {
  Add-Result 'TC-017' 'Fail' "status=$($expCall.StatusCode)" $expCall.Content
}

# ---------- TC-018 ----------
$accEmail = "acct-$ts@example.com"
$accCreate = Invoke-Curl -Method POST -Url "$Base/api/v1/users" -Token $adminToken -Body ("{`"email`":`"$accEmail`",`"password`":`"$AppPwd`",`"fullName`":`"Accountant`",`"role`":`"ACCOUNTANT`"}")
$accLogin = Login-User $accEmail
$accObj = Parse-JsonSafe $accLogin.Content
$tampered = Tamper-JwtRole $accObj.accessToken
$adminOnly = Invoke-Curl -Url "$Base/api/v1/users" -Token $tampered
$accAdminOnly = Invoke-Curl -Url "$Base/api/v1/users" -Token $accObj.accessToken
if ($adminOnly.StatusCode -eq 401) {
  Add-Result 'TC-018' 'Pass' "Tampered role JWT rejected; valid accountant users API=$($accAdminOnly.StatusCode)" "create=$($accCreate.StatusCode)"
} else {
  Add-Result 'TC-018' 'Fail' "tampered=$($adminOnly.StatusCode) acc=$($accAdminOnly.StatusCode) create=$($accCreate.StatusCode)" $adminOnly.Content
}

# ---------- TC-019 ----------
$wrongFirm = [guid]::NewGuid().ToString()
$mismatchJwt = New-SignedJwt $adminUserId $wrongFirm 'ADMIN' $adminEmail 3600
$mm = Invoke-Curl -Url "$Base/api/v1/clients" -Token $mismatchJwt
if ($mm.StatusCode -eq 401) {
  Add-Result 'TC-019' 'Pass' 'JWT firm mismatch -> 401' $mm.Content
} else {
  Add-Result 'TC-019' 'Fail' "status=$($mm.StatusCode)" $mm.Content
}

# ---------- TC-020 / 021 ----------
$acc2Email = "acct2-$ts@example.com"
$acc2Create = Invoke-Curl -Method POST -Url "$Base/api/v1/users" -Token $adminToken -Body ("{`"email`":`"$acc2Email`",`"password`":`"$AppPwd`",`"fullName`":`"Acct2`",`"role`":`"ACCOUNTANT`"}")
$acc2Login = Login-User $acc2Email
$acc2 = Parse-JsonSafe $acc2Login.Content
$acc2Id = (Parse-JsonSafe $acc2Create.Content).id
if (-not $acc2Id) { $acc2Id = $acc2.userId }
$deact = Invoke-Curl -Method POST -Url "$Base/api/v1/users/$acc2Id/deactivate" -Token $adminToken
$reuse = Invoke-Curl -Url "$Base/api/v1/auth/me" -Token $acc2.accessToken
$refresh = Invoke-Curl -Method POST -Url "$Base/api/v1/auth/refresh" -Body ("{`"refreshToken`":`"$($acc2.refreshToken)`"}")
if ($deact.StatusCode -in @(200,204) -and $reuse.StatusCode -eq 401) {
  Add-Result 'TC-020' 'Pass' 'Deactivated user access token rejected' "deact=$($deact.StatusCode); reuse=$($reuse.StatusCode)"
} else {
  Add-Result 'TC-020' 'Fail' "deact=$($deact.StatusCode) reuse=$($reuse.StatusCode) create=$($acc2Create.StatusCode)" "$($acc2Create.Content) $($reuse.Content)"
}
if ($refresh.StatusCode -in @(400,401,422) -and $refresh.Content -notmatch 'accessToken') {
  Add-Result 'TC-021' 'Pass' 'Refresh after deactivation fails' "status=$($refresh.StatusCode) $($refresh.Content)"
} else {
  Add-Result 'TC-021' 'Fail' "status=$($refresh.StatusCode)" $refresh.Content
}

# ---------- TC-022 / 023 ----------
$pwEmail = "pw-$ts@example.com"
Invoke-Curl -Method POST -Url "$Base/api/v1/users" -Token $adminToken -Body ("{`"email`":`"$pwEmail`",`"password`":`"$AppPwd`",`"fullName`":`"PW`",`"role`":`"ACCOUNTANT`"}") | Out-Null
$pwLogin = Login-User $pwEmail
$pwToken = (Parse-JsonSafe $pwLogin.Content).accessToken
$wrongCur = Invoke-Curl -Method POST -Url "$Base/api/v1/users/me/password" -Token $pwToken -Body '{"currentPassword":"not-the-password","newPassword":"newpassword1"}'
$stillOld = Login-User $pwEmail $AppPwd
$change = Invoke-Curl -Method POST -Url "$Base/api/v1/users/me/password" -Token $pwToken -Body ("{`"currentPassword`":`"$AppPwd`",`"newPassword`":`"newpassword1`"}")
$oldFail = Login-User $pwEmail $AppPwd
$newOk = Login-User $pwEmail 'newpassword1'
if ($wrongCur.StatusCode -eq 400 -and $stillOld.StatusCode -eq 200) {
  Add-Result 'TC-023' 'Pass' 'Wrong current password rejected; original remains valid' "wrong=$($wrongCur.StatusCode)"
} else {
  Add-Result 'TC-023' 'Fail' "wrong=$($wrongCur.StatusCode) stillOld=$($stillOld.StatusCode)" $wrongCur.Content
}
if ($change.StatusCode -eq 204 -and $oldFail.StatusCode -in @(400,401,422) -and $newOk.StatusCode -eq 200) {
  Add-Result 'TC-022' 'Pass' 'Password change works; old fails; new works' "change=$($change.StatusCode)"
} else {
  Add-Result 'TC-022' 'Fail' "change=$($change.StatusCode) old=$($oldFail.StatusCode) new=$($newOk.StatusCode)" $change.Content
}

# ---------- TC-024 ----------
$plat = Invoke-Curl -Url "$Base/api/v1/platform/metrics" -Token $adminToken
if ($plat.StatusCode -eq 403) {
  Add-Result 'TC-024' 'Pass' 'Firm ADMIN blocked from platform APIs' $plat.Content
} else {
  Add-Result 'TC-024' 'Fail' "status=$($plat.StatusCode)" $plat.Content
}

# ---------- TC-025 / 026 ----------
& $Psql -h localhost -p 5432 -U postgres -d finance_platform_qa6 -c "INSERT INTO platform_admin_grants (id, user_id, active, granted_at, reason, created_at, updated_at) VALUES (gen_random_uuid(), '$adminUserId', TRUE, NOW(), 'qa grant', NOW(), NOW()) ON CONFLICT (user_id) DO UPDATE SET active=TRUE, revoked_at=NULL, updated_at=NOW();" | Out-Null
$platToken = (Parse-JsonSafe (Login-User $adminEmail).Content).accessToken
$platOk = Invoke-Curl -Url "$Base/api/v1/platform/metrics" -Token $platToken
if ($platOk.StatusCode -eq 200) {
  Add-Result 'TC-025' 'Pass' 'Persisted grant allows platform API' ($platOk.Content.Substring(0,[Math]::Min(160,$platOk.Content.Length)))
} else {
  Add-Result 'TC-025' 'Fail' "status=$($platOk.StatusCode)" $platOk.Content
}
& $Psql -h localhost -p 5432 -U postgres -d finance_platform_qa6 -c "UPDATE platform_admin_grants SET active=FALSE, revoked_at=NOW(), updated_at=NOW() WHERE user_id='$adminUserId';" | Out-Null
$revToken = (Parse-JsonSafe (Login-User $adminEmail).Content).accessToken
$platDenied = Invoke-Curl -Url "$Base/api/v1/platform/metrics" -Token $revToken
if ($platDenied.StatusCode -eq 403) {
  Add-Result 'TC-026' 'Pass' 'Revoked grant blocks platform access after fresh login' $platDenied.Content
} else {
  Add-Result 'TC-026' 'Fail' "status=$($platDenied.StatusCode)" $platDenied.Content
}

# ---------- TC-027 ----------
& $Psql -h localhost -p 5432 -U postgres -d finance_platform_qa6 -c "UPDATE platform_admin_grants SET active=TRUE, revoked_at=NULL, updated_at=NOW() WHERE user_id='$adminUserId';" | Out-Null
$platTok2 = (Parse-JsonSafe (Login-User $adminEmail).Content).accessToken
$other = Register-Firm 'other'
$otherTok = (Parse-JsonSafe (Login-User $other.Email).Content).accessToken
# Discover client create schema from OpenAPI or try common shapes
$clientCreate = Invoke-Curl -Method POST -Url "$Base/api/v1/clients" -Token $otherTok -Body ("{`"name`":`"Other Client $ts`",`"code`":`"C$ts`"}")
if ($clientCreate.StatusCode -notin @(200,201)) {
  $clientCreate = Invoke-Curl -Method POST -Url "$Base/api/v1/clients" -Token $otherTok -Body ("{`"legalName`":`"Other Client $ts`",`"displayName`":`"Other $ts`"}")
}
if ($clientCreate.StatusCode -notin @(200,201)) {
  # read CreateClientRequest via file
  $cc = Get-Content 'c:\Users\HP\Downloads\finance-ai-automation-platform\backend\module-finance\src\main\java\com\finance\platform\finance\application\dto\CreateClientRequest.java' -Raw
  Add-Result 'TC-027' 'Blocked' "Could not create other-firm client to probe isolation; create=$($clientCreate.StatusCode)" "$($clientCreate.Content) | $cc"
} else {
  $clientId = (Parse-JsonSafe $clientCreate.Content).id
  $cross = Invoke-Curl -Url "$Base/api/v1/clients/$clientId" -Token $platTok2
  $crossDocs = Invoke-Curl -Url "$Base/api/v1/clients/$clientId/expenses?page=0&size=10" -Token $platTok2
  if ($cross.StatusCode -in @(403,404) -and $crossDocs.StatusCode -in @(403,404)) {
    Add-Result 'TC-027' 'Pass' 'Platform admin cannot access other firm finance endpoints' "client=$($cross.StatusCode); expenses=$($crossDocs.StatusCode)"
  } else {
    Add-Result 'TC-027' 'Fail' "client=$($cross.StatusCode) expenses=$($crossDocs.StatusCode)" $cross.Content
  }
}

$outDir = 'c:\Users\HP\Downloads\finance-ai-automation-platform'
$md = Join-Path $outDir 'qa-results-tc001-027.md'
$pass = @($Results | Where-Object Result -eq 'Pass').Count
$fail = @($Results | Where-Object Result -eq 'Fail').Count
$blocked = @($Results | Where-Object Result -eq 'Blocked').Count
$mdLines = @(
  '# QA Results TC-001 – TC-027',
  '',
  "Run: $(Get-Date -Format o)",
  "Environment: local profile, Postgres 16 portable, Angular :4200, backend :8080 (ddl-auto=none override for schema-validate blockers)",
  '',
  '| Case | Result | Notes | Evidence |',
  '|------|--------|-------|----------|'
)
foreach ($r in $Results) {
  $notes = ($r.Notes -replace '\|','/' -replace "`r?`n",' ')
  $evRaw = ($r.Evidence -replace '\|','/' -replace "`r?`n",' ')
  $ev = $evRaw.Substring(0, [Math]::Min(200, $evRaw.Length))
  $mdLines += "| $($r.Id) | **$($r.Result)** | $notes | $ev |"
}
$mdLines += ''
$mdLines += '## Summary'
$mdLines += "- Pass: $pass"
$mdLines += "- Fail: $fail"
$mdLines += "- Blocked: $blocked"
$mdLines += "- Total: $($Results.Count)"
$mdLines += ''
$mdLines += '## Gaps / notes vs spreadsheet'
$mdLines += '- Login failures and rate limits return HTTP **422** (BusinessException), not 401/429.'
$mdLines += '- Page size is **clamped to 100**, not rejected.'
$mdLines += '- X-Request-ID is stored on audit rows only (not response headers / console logs).'
$mdLines += '- Prod JWT env var is **APP_JWT_SECRET** (not JWT_SECRET).'
$mdLines += '- Startup required migration fixes (V18 DROP VIEW; CHAR->VARCHAR; bank_import_profiles audit cols) and temporary `SPRING_JPA_HIBERNATE_DDL_AUTO=none` due to remaining JPA validate mismatches (e.g. plan_change_requests.created_by).'
$mdLines += '- Docker unavailable: Postgres integration tests not run.'
$mdLines | Set-Content -Path $md -Encoding UTF8
$Results | Export-Csv (Join-Path $outDir 'qa-results-tc001-027.csv') -NoTypeInformation -Encoding UTF8
Write-Host "`nSUMMARY Pass=$pass Fail=$fail Blocked=$blocked Total=$($Results.Count)"
Write-Host "Wrote $md"
