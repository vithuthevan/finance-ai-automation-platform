$ErrorActionPreference = 'Continue'
$Base = 'http://localhost:8080'
$Results = New-Object System.Collections.Generic.List[object]
$ts = Get-Date -Format 'yyyyMMddHHmmss'
$AppPwd = 'password1'
$Psql = 'c:\Users\HP\Downloads\finance-ai-automation-platform\.tools\pgsql\pgsql\bin\psql.exe'
$env:PGPASSWORD = 'postgres'
$Db = 'finance_platform_qa6'
$Tmp = Join-Path $env:TEMP "qa28-$ts"
New-Item -ItemType Directory -Force -Path $Tmp | Out-Null

function Add-Result($id, $result, $notes, $evidence) {
  $Results.Add([pscustomobject]@{ Id = $id; Result = $result; Notes = $notes; Evidence = $evidence })
  Write-Host ("[{0}] {1}: {2}" -f $id, $result, $notes)
}

function Invoke-Curl {
  param([string]$Method='GET',[string]$Url,[string]$Body=$null,[string]$Token=$null,[string[]]$ExtraHeaders=@())
  $bodyFile = Join-Path $Tmp ("b-" + [guid]::NewGuid() + ".txt")
  $args = @('-sS','-X',$Method,'-o',$bodyFile,'-w','%{http_code}','-m','60')
  if ($Token) { $args += @('-H',"Authorization: Bearer $Token") }
  foreach ($h in $ExtraHeaders) { $args += @('-H',$h) }
  if ($null -ne $Body) {
    $inFile = Join-Path $Tmp ("in-" + [guid]::NewGuid() + ".json")
    [System.IO.File]::WriteAllText($inFile, $Body, [Text.UTF8Encoding]::new($false))
    $args += @('-H','Content-Type: application/json','--data-binary',"@$inFile")
  }
  $args += $Url
  $code = & curl.exe @args 2>$null
  $content = if (Test-Path $bodyFile) { Get-Content -Raw $bodyFile -ErrorAction SilentlyContinue } else { '' }
  if (-not $content) { $content = '' }
  return [pscustomobject]@{ StatusCode = [int]$code; Content = $content }
}

function Parse-JsonSafe([string]$s) {
  if (-not $s) { return $null }
  try { return $s | ConvertFrom-Json } catch { return $null }
}

function Sql([string]$q) {
  return (& $Psql -h localhost -p 5432 -U postgres -d $Db -tAc $q).Trim()
}

# Bootstrap firm A
$emailA = "admin-a-$ts@example.com"
$regA = Invoke-Curl -Method POST -Url "$Base/api/v1/auth/register" -Body ("{`"firmName`":`"FirmA $ts`",`"email`":`"$emailA`",`"password`":`"$AppPwd`",`"fullName`":`"Admin A`"}")
$regAObj = Parse-JsonSafe $regA.Content
$firmA = $regAObj.firmId
$adminAId = $regAObj.userId
$loginA = Invoke-Curl -Method POST -Url "$Base/api/v1/auth/login" -Body ("{`"email`":`"$emailA`",`"password`":`"$AppPwd`"}")
$tokenA = (Parse-JsonSafe $loginA.Content).accessToken
Write-Host "FirmA=$firmA admin=$emailA reg=$($regA.StatusCode) login=$($loginA.StatusCode)"

# Firm B for cross-firm tests
$emailB = "admin-b-$ts@example.com"
$regB = Invoke-Curl -Method POST -Url "$Base/api/v1/auth/register" -Body ("{`"firmName`":`"FirmB $ts`",`"email`":`"$emailB`",`"password`":`"$AppPwd`",`"fullName`":`"Admin B`"}")
$firmB = (Parse-JsonSafe $regB.Content).firmId
$tokenB = (Parse-JsonSafe (Invoke-Curl -Method POST -Url "$Base/api/v1/auth/login" -Body ("{`"email`":`"$emailB`",`"password`":`"$AppPwd`"}")).Content).accessToken

# Client A on firm A
$clientA = Invoke-Curl -Method POST -Url "$Base/api/v1/clients" -Token $tokenA -Body ("{`"name`":`"Client A $ts`",`"businessRegNo`":`"A$ts`",`"contactEmail`":`"a@$ts.test`"}")
$clientAObj = Parse-JsonSafe $clientA.Content
$clientAId = $clientAObj.id
# Client B on firm A
$clientB = Invoke-Curl -Method POST -Url "$Base/api/v1/clients" -Token $tokenA -Body ("{`"name`":`"Client B $ts`",`"businessRegNo`":`"B$ts`",`"contactEmail`":`"b@$ts.test`"}")
$clientBId = (Parse-JsonSafe $clientB.Content).id
# Client on firm B
$clientFirmB = Invoke-Curl -Method POST -Url "$Base/api/v1/clients" -Token $tokenB -Body ("{`"name`":`"Other Firm Client $ts`",`"businessRegNo`":`"X$ts`"}")
$clientFirmBId = (Parse-JsonSafe $clientFirmB.Content).id
Write-Host "clients A=$clientAId B=$clientBId foreign=$clientFirmBId createA=$($clientA.StatusCode)"

# Firm-wide expense category for historical records
$cat = Invoke-Curl -Method POST -Url "$Base/api/v1/categories" -Token $tokenA -Body '{"code":"6100","name":"Office Expenses","categoryType":"EXPENSE"}'
$catId = (Parse-JsonSafe $cat.Content).id

# ==================== USERS TC-028..035 ====================

# TC-028 Create accountant with FULL access
$accEmail = "acct-$ts@example.com"
$accCreate = Invoke-Curl -Method POST -Url "$Base/api/v1/users" -Token $tokenA -Body ("{`"email`":`"$accEmail`",`"password`":`"$AppPwd`",`"fullName`":`"Accountant`",`"role`":`"ACCOUNTANT`",`"clientAccess`":[{`"clientId`":`"$clientAId`",`"accessType`":`"FULL`"}]}")
$accObj = Parse-JsonSafe $accCreate.Content
$accId = $accObj.id
$getAcc = Invoke-Curl -Url "$Base/api/v1/users/$accId" -Token $tokenA
$getAccObj = Parse-JsonSafe $getAcc.Content
$accessOk = $false
if ($getAccObj.clientAccess) {
  foreach ($a in $getAccObj.clientAccess) {
    if ($a.clientId -eq $clientAId -and $a.accessType -eq 'FULL') { $accessOk = $true }
  }
}
if ($accCreate.StatusCode -eq 201 -and $accObj.firmId -eq $firmA -and $accObj.role -eq 'ACCOUNTANT' -and $accessOk) {
  Add-Result 'TC-028' 'Pass' 'Accountant created in same firm with FULL client access' "id=$accId access=$($getAccObj.clientAccess | ConvertTo-Json -Compress)"
} else {
  Add-Result 'TC-028' 'Fail' "create=$($accCreate.StatusCode) role=$($accObj.role) accessOk=$accessOk" $accCreate.Content
}

# TC-029 Atomic rollback: assign foreign firm client
$orphanEmail = "orphan-$ts@example.com"
$beforeUsers = Sql "SELECT count(*) FROM users WHERE firm_id='$firmA' AND deleted_at IS NULL"
$crossCreate = Invoke-Curl -Method POST -Url "$Base/api/v1/users" -Token $tokenA -Body ("{`"email`":`"$orphanEmail`",`"password`":`"$AppPwd`",`"fullName`":`"Orphan`",`"role`":`"ACCOUNTANT`",`"clientAccess`":[{`"clientId`":`"$clientFirmBId`",`"accessType`":`"FULL`"}]}")
$afterUsers = Sql "SELECT count(*) FROM users WHERE firm_id='$firmA' AND deleted_at IS NULL"
$orphanCount = Sql "SELECT count(*) FROM users WHERE email='$orphanEmail'"
if ($crossCreate.StatusCode -in @(404,400,422) -and $beforeUsers -eq $afterUsers -and $orphanCount -eq '0') {
  Add-Result 'TC-029' 'Pass' 'Cross-firm client assignment fails; no orphan user' "status=$($crossCreate.StatusCode); users before/after=$beforeUsers/$afterUsers"
} else {
  Add-Result 'TC-029' 'Fail' "status=$($crossCreate.StatusCode) before=$beforeUsers after=$afterUsers orphan=$orphanCount" $crossCreate.Content
}

# TC-030 Auditor FULL coerced to READ_ONLY
$audEmail = "aud-$ts@example.com"
$audCreate = Invoke-Curl -Method POST -Url "$Base/api/v1/users" -Token $tokenA -Body ("{`"email`":`"$audEmail`",`"password`":`"$AppPwd`",`"fullName`":`"Auditor`",`"role`":`"AUDITOR`",`"clientAccess`":[{`"clientId`":`"$clientAId`",`"accessType`":`"FULL`"}]}")
$audObj = Parse-JsonSafe $audCreate.Content
$audAccess = $null
if ($audObj.clientAccess) { $audAccess = $audObj.clientAccess[0].accessType }
if ($audCreate.StatusCode -eq 201 -and $audAccess -eq 'READ_ONLY') {
  Add-Result 'TC-030' 'Pass' 'AUDITOR+FULL coerced to READ_ONLY' "access=$audAccess"
} elseif ($audCreate.StatusCode -in @(400,422)) {
  Add-Result 'TC-030' 'Pass' 'AUDITOR+FULL rejected as invalid combination' "status=$($audCreate.StatusCode) $($audCreate.Content)"
} else {
  Add-Result 'TC-030' 'Fail' "status=$($audCreate.StatusCode) access=$audAccess" $audCreate.Content
}

# TC-031 ACCOUNTANT + UPLOAD_ONLY rejected
$upEmail = "uponly-$ts@example.com"
$upCreate = Invoke-Curl -Method POST -Url "$Base/api/v1/users" -Token $tokenA -Body ("{`"email`":`"$upEmail`",`"password`":`"$AppPwd`",`"fullName`":`"Up`",`"role`":`"ACCOUNTANT`",`"clientAccess`":[{`"clientId`":`"$clientAId`",`"accessType`":`"UPLOAD_ONLY`"}]}")
$upCount = Sql "SELECT count(*) FROM users WHERE email='$upEmail'"
if ($upCreate.StatusCode -eq 400 -and $upCount -eq '0') {
  Add-Result 'TC-031' 'Pass' 'ACCOUNTANT+UPLOAD_ONLY rejected' $upCreate.Content
} else {
  Add-Result 'TC-031' 'Fail' "status=$($upCreate.StatusCode) users=$upCount" $upCreate.Content
}

# TC-032 Deactivate preserves history
# create expense as accountant then approve as admin, then deactivate
$accLogin = Invoke-Curl -Method POST -Url "$Base/api/v1/auth/login" -Body ("{`"email`":`"$accEmail`",`"password`":`"$AppPwd`"}")
$accToken = (Parse-JsonSafe $accLogin.Content).accessToken
$today = (Get-Date).ToString('yyyy-MM-dd')
$expBody = "{`"transactionDate`":`"$today`",`"categoryId`":`"$catId`",`"amount`":100.00,`"currencyCode`":`"LKR`",`"vendorName`":`"Vendor $ts`",`"description`":`"hist`"}"
$exp = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses" -Token $accToken -Body $expBody
$expId = (Parse-JsonSafe $exp.Content).id
# approve if endpoint exists
$approve = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses/$expId/approve" -Token $tokenA
$deact = Invoke-Curl -Method POST -Url "$Base/api/v1/users/$accId/deactivate" -Token $tokenA
$reuse = Invoke-Curl -Url "$Base/api/v1/auth/me" -Token $accToken
$hist = Sql "SELECT count(*) FROM expenses WHERE id='$expId' AND created_by='$accId'"
$loginInactive = Invoke-Curl -Method POST -Url "$Base/api/v1/auth/login" -Body ("{`"email`":`"$accEmail`",`"password`":`"$AppPwd`"}")
if ($deact.StatusCode -eq 200 -and $reuse.StatusCode -eq 401 -and [int]$hist -ge 1 -and $loginInactive.StatusCode -in @(401,422)) {
  Add-Result 'TC-032' 'Pass' 'Deactivated user cannot auth; historical expense created_by preserved' "exp=$($exp.StatusCode)/$expId hist=$hist login=$($loginInactive.StatusCode) approve=$($approve.StatusCode)"
} else {
  Add-Result 'TC-032' 'Fail' "deact=$($deact.StatusCode) reuse=$($reuse.StatusCode) hist=$hist exp=$($exp.StatusCode) login=$($loginInactive.StatusCode)" "$($exp.Content) | $($deact.Content)"
}

# TC-033 Reactivate within quota
$react = Invoke-Curl -Method POST -Url "$Base/api/v1/users/$accId/activate" -Token $tokenA
$loginAgain = Invoke-Curl -Method POST -Url "$Base/api/v1/auth/login" -Body ("{`"email`":`"$accEmail`",`"password`":`"$AppPwd`"}")
if ($react.StatusCode -eq 200 -and $loginAgain.StatusCode -eq 200) {
  Add-Result 'TC-033' 'Pass' 'Reactivate within quota; user can authenticate' "react=$($react.StatusCode)"
} else {
  Add-Result 'TC-033' 'Fail' "react=$($react.StatusCode) login=$($loginAgain.StatusCode)" $react.Content
}

# TC-034 Reactivate over quota
# deactivate accountant again; set max_users to current active count
Invoke-Curl -Method POST -Url "$Base/api/v1/users/$accId/deactivate" -Token $tokenA | Out-Null
Sql "UPDATE firm_subscriptions SET max_users = (SELECT COUNT(*)::int FROM users WHERE firm_id='$firmA' AND deleted_at IS NULL AND active=true) WHERE firm_id='$firmA'" | Out-Null
$reactOver = Invoke-Curl -Method POST -Url "$Base/api/v1/users/$accId/activate" -Token $tokenA
$userStill = Sql "SELECT count(*) FROM users WHERE id='$accId'"
$activeFlag = Sql "SELECT active::text FROM users WHERE id='$accId'"
if ($reactOver.StatusCode -eq 422 -and ($reactOver.Content -match 'PLAN_USER_LIMIT|limit|quota|capacity' -or $reactOver.Content -match 'user') -and $activeFlag -eq 'false' -and $userStill -eq '1') {
  Add-Result 'TC-034' 'Pass' 'Reactivation blocked over user quota; history preserved' "status=$($reactOver.StatusCode) active=$activeFlag $($reactOver.Content)"
} else {
  Add-Result 'TC-034' 'Fail' "status=$($reactOver.StatusCode) active=$activeFlag" $reactOver.Content
}
# restore quota and reactivate for later tests
Sql "UPDATE firm_subscriptions SET max_users = 15 WHERE firm_id='$firmA'" | Out-Null
Invoke-Curl -Method POST -Url "$Base/api/v1/users/$accId/activate" -Token $tokenA | Out-Null

# TC-035 Replace client access A -> B
$accToken2 = (Parse-JsonSafe (Invoke-Curl -Method POST -Url "$Base/api/v1/auth/login" -Body ("{`"email`":`"$accEmail`",`"password`":`"$AppPwd`"}")).Content).accessToken
$replace = Invoke-Curl -Method PUT -Url "$Base/api/v1/users/$accId/client-access" -Token $tokenA -Body ("{`"assignments`":[{`"clientId`":`"$clientBId`",`"accessType`":`"FULL`"}]}")
$getA = Invoke-Curl -Url "$Base/api/v1/clients/$clientAId" -Token $accToken2
$getB = Invoke-Curl -Url "$Base/api/v1/clients/$clientBId" -Token $accToken2
$listAcc = Invoke-Curl -Url "$Base/api/v1/clients?page=0&size=50" -Token $accToken2
$listObj = Parse-JsonSafe $listAcc.Content
$ids = @()
if ($listObj.content) { $ids = @($listObj.content | ForEach-Object { $_.id }) }
$hasA = $ids -contains $clientAId
$hasB = $ids -contains $clientBId
if ($replace.StatusCode -eq 200 -and $getA.StatusCode -eq 403 -and $getB.StatusCode -eq 200 -and -not $hasA -and $hasB) {
  Add-Result 'TC-035' 'Pass' 'Access replaced A->B; no cross-client leakage' "getA=$($getA.StatusCode) getB=$($getB.StatusCode) listHasA=$hasA listHasB=$hasB"
} else {
  # maybe token needs refresh after access change - try fresh login
  $accToken3 = (Parse-JsonSafe (Invoke-Curl -Method POST -Url "$Base/api/v1/auth/login" -Body ("{`"email`":`"$accEmail`",`"password`":`"$AppPwd`"}")).Content).accessToken
  $getA2 = Invoke-Curl -Url "$Base/api/v1/clients/$clientAId" -Token $accToken3
  $getB2 = Invoke-Curl -Url "$Base/api/v1/clients/$clientBId" -Token $accToken3
  if ($replace.StatusCode -eq 200 -and $getA2.StatusCode -eq 403 -and $getB2.StatusCode -eq 200) {
    Add-Result 'TC-035' 'Pass' 'Access replaced A->B after fresh login' "getA=$($getA2.StatusCode) getB=$($getB2.StatusCode)"
  } else {
    Add-Result 'TC-035' 'Fail' "replace=$($replace.StatusCode) getA=$($getA.StatusCode)/$($getA2.StatusCode) getB=$($getB.StatusCode)/$($getB2.StatusCode)" $replace.Content
  }
}

# ==================== CLIENTS TC-036..042 ====================

# TC-036 Create client (already have clients; create another and list)
$c36 = Invoke-Curl -Method POST -Url "$Base/api/v1/clients" -Token $tokenA -Body ("{`"name`":`"SME Client $ts`",`"businessRegNo`":`"SME$ts`",`"contactEmail`":`"sme@$ts.test`"}")
$c36Obj = Parse-JsonSafe $c36.Content
$c36Get = Invoke-Curl -Url "$Base/api/v1/clients/$($c36Obj.id)" -Token $tokenA
$listAdmin = Invoke-Curl -Url "$Base/api/v1/clients?page=0&size=50" -Token $tokenA
if ($c36.StatusCode -eq 201 -and $c36Obj.firmId -eq $firmA -and $c36Get.StatusCode -eq 200 -and $listAdmin.Content -match [regex]::Escape($c36Obj.id)) {
  Add-Result 'TC-036' 'Pass' 'SME client created under firm and visible to admin' "id=$($c36Obj.id)"
} else {
  Add-Result 'TC-036' 'Fail' "create=$($c36.StatusCode) get=$($c36Get.StatusCode)" $c36.Content
}

# TC-037 Create client ignores supplied firmId
$c37 = Invoke-Curl -Method POST -Url "$Base/api/v1/clients" -Token $tokenA -Body ("{`"name`":`"MassAssign $ts`",`"businessRegNo`":`"MA$ts`",`"firmId`":`"$firmB`"}")
$c37Obj = Parse-JsonSafe $c37.Content
if ($c37.StatusCode -eq 201 -and $c37Obj.firmId -eq $firmA -and $c37Obj.firmId -ne $firmB) {
  Add-Result 'TC-037' 'Pass' 'Extra firmId ignored; client belongs to Firm A' "firmId=$($c37Obj.firmId)"
} elseif ($c37.StatusCode -in @(400,422)) {
  Add-Result 'TC-037' 'Pass' 'Mass-assignment firmId rejected' "status=$($c37.StatusCode)"
} else {
  Add-Result 'TC-037' 'Fail' "status=$($c37.StatusCode) firm=$($c37Obj.firmId)" $c37.Content
}

# TC-038 Accountant assigned-client list (restore access to A only)
Invoke-Curl -Method PUT -Url "$Base/api/v1/users/$accId/client-access" -Token $tokenA -Body ("{`"assignments`":[{`"clientId`":`"$clientAId`",`"accessType`":`"FULL`"}]}") | Out-Null
$accTok = (Parse-JsonSafe (Invoke-Curl -Method POST -Url "$Base/api/v1/auth/login" -Body ("{`"email`":`"$accEmail`",`"password`":`"$AppPwd`"}")).Content).accessToken
$listAcc2 = Invoke-Curl -Url "$Base/api/v1/clients?page=0&size=50" -Token $accTok
$listAcc2Obj = Parse-JsonSafe $listAcc2.Content
$ids2 = @()
if ($listAcc2Obj.content) { $ids2 = @($listAcc2Obj.content | ForEach-Object { $_.id }) }
$directB = Invoke-Curl -Url "$Base/api/v1/clients/$clientBId" -Token $accTok
if ($listAcc2.StatusCode -eq 200 -and ($ids2 -contains $clientAId) -and -not ($ids2 -contains $clientBId) -and $directB.StatusCode -eq 403) {
  Add-Result 'TC-038' 'Pass' 'Accountant lists only Client A; direct B denied' "list=$($ids2 -join ','); getB=$($directB.StatusCode)"
} else {
  Add-Result 'TC-038' 'Fail' "list=$($ids2 -join ',') getB=$($directB.StatusCode)" $listAcc2.Content
}

# TC-039 Business owner isolation
$boEmail = "owner-$ts@example.com"
$boCreate = Invoke-Curl -Method POST -Url "$Base/api/v1/users" -Token $tokenA -Body ("{`"email`":`"$boEmail`",`"password`":`"$AppPwd`",`"fullName`":`"Owner`",`"role`":`"BUSINESS_OWNER`",`"clientAccess`":[{`"clientId`":`"$clientAId`",`"accessType`":`"FULL`"}]}")
$boTok = (Parse-JsonSafe (Invoke-Curl -Method POST -Url "$Base/api/v1/auth/login" -Body ("{`"email`":`"$boEmail`",`"password`":`"$AppPwd`"}")).Content).accessToken
$boGetB = Invoke-Curl -Url "$Base/api/v1/clients/$clientBId" -Token $boTok
if ($boCreate.StatusCode -eq 201 -and $boGetB.StatusCode -eq 403) {
  Add-Result 'TC-039' 'Pass' 'Business owner denied Client B' "create=$($boCreate.StatusCode) getB=$($boGetB.StatusCode)"
} else {
  Add-Result 'TC-039' 'Fail' "create=$($boCreate.StatusCode) getB=$($boGetB.StatusCode)" $boGetB.Content
}

# TC-040 Deactivate client blocks new writes; history readable
$histExp = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses" -Token $tokenA -Body ("{`"transactionDate`":`"$today`",`"categoryId`":`"$catId`",`"amount`":50.00,`"currencyCode`":`"LKR`",`"vendorName`":`"Keep`"}")
$histExpId = (Parse-JsonSafe $histExp.Content).id
$deactClient = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/deactivate" -Token $tokenA
$newWrite = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses" -Token $tokenA -Body ("{`"transactionDate`":`"$today`",`"categoryId`":`"$catId`",`"amount`":51.00,`"currencyCode`":`"LKR`",`"vendorName`":`"Blocked`"}")
$assignInactive = Invoke-Curl -Method PUT -Url "$Base/api/v1/users/$accId/client-access" -Token $tokenA -Body ("{`"assignments`":[{`"clientId`":`"$clientAId`",`"accessType`":`"FULL`"}]}")
$readHist = Invoke-Curl -Url "$Base/api/v1/clients/$clientAId/expenses?page=0&size=20" -Token $tokenA
$readClient = Invoke-Curl -Url "$Base/api/v1/clients/$clientAId" -Token $tokenA
if ($deactClient.StatusCode -eq 200 -and $newWrite.StatusCode -eq 422 -and $readHist.StatusCode -eq 200 -and $readClient.StatusCode -eq 200 -and $assignInactive.StatusCode -in @(404,422,400)) {
  Add-Result 'TC-040' 'Pass' 'Inactive client blocks writes/assignments; history readable' "write=$($newWrite.StatusCode) assign=$($assignInactive.StatusCode) read=$($readHist.StatusCode)"
} else {
  Add-Result 'TC-040' 'Fail' "deact=$($deactClient.StatusCode) write=$($newWrite.StatusCode) assign=$($assignInactive.StatusCode) read=$($readHist.StatusCode)/$($readClient.StatusCode) histExp=$($histExp.StatusCode)" "$($newWrite.Content) | $($assignInactive.Content)"
}

# TC-041 Reactivate within quota
$reactClient = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/activate" -Token $tokenA
$writeOk = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses" -Token $tokenA -Body ("{`"transactionDate`":`"$today`",`"categoryId`":`"$catId`",`"amount`":52.00,`"currencyCode`":`"LKR`",`"vendorName`":`"After reactivate`"}")
if ($reactClient.StatusCode -eq 200 -and $writeOk.StatusCode -eq 201) {
  Add-Result 'TC-041' 'Pass' 'Client reactivated; new writes work' "react=$($reactClient.StatusCode) write=$($writeOk.StatusCode)"
} else {
  Add-Result 'TC-041' 'Fail' "react=$($reactClient.StatusCode) write=$($writeOk.StatusCode)" $writeOk.Content
}

# TC-042 Reactivate over client quota
Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/deactivate" -Token $tokenA | Out-Null
Sql "UPDATE firm_subscriptions SET max_clients = (SELECT COUNT(*)::int FROM clients WHERE firm_id='$firmA' AND deleted_at IS NULL AND active=true) WHERE firm_id='$firmA'" | Out-Null
$reactClientOver = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/activate" -Token $tokenA
$clientExists = Sql "SELECT count(*) FROM clients WHERE id='$clientAId'"
$clientActive = Sql "SELECT active::text FROM clients WHERE id='$clientAId'"
if ($reactClientOver.StatusCode -eq 422 -and $clientExists -eq '1' -and $clientActive -eq 'false') {
  Add-Result 'TC-042' 'Pass' 'Client reactivation blocked over quota; record preserved' $reactClientOver.Content
} else {
  Add-Result 'TC-042' 'Fail' "status=$($reactClientOver.StatusCode) active=$clientActive" $reactClientOver.Content
}
Sql "UPDATE firm_subscriptions SET max_clients = 50 WHERE firm_id='$firmA'" | Out-Null
Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/activate" -Token $tokenA | Out-Null

# ==================== CATEGORIES TC-043..050 ====================

# TC-043 Create firm-wide category (6100 may already exist from setup — use unique or reuse)
$cat43 = Invoke-Curl -Method POST -Url "$Base/api/v1/categories" -Token $tokenA -Body '{"code":"6200","name":"Utilities","categoryType":"EXPENSE"}'
$cat43Obj = Parse-JsonSafe $cat43.Content
$listCats = Invoke-Curl -Url "$Base/api/v1/categories" -Token $tokenA
# also verify 6100 from setup visible
$has6100 = $listCats.Content -match '6100'
$has6200 = $listCats.Content -match '6200'
if (($cat43.StatusCode -eq 201 -or ($cat.StatusCode -eq 201 -and $has6100)) -and ($has6100 -or $has6200)) {
  Add-Result 'TC-043' 'Pass' 'Firm-wide EXPENSE category created and listed' "create6100=$($cat.StatusCode) create6200=$($cat43.StatusCode) list has6100=$has6100 has6200=$has6200"
} else {
  Add-Result 'TC-043' 'Fail' "cat=$($cat.StatusCode) cat43=$($cat43.StatusCode)" $cat43.Content
}

# TC-044 Client-specific category visibility
$catA = Invoke-Curl -Method POST -Url "$Base/api/v1/categories" -Token $tokenA -Body ("{`"code`":`"CA$ts`",`"name`":`"ClientA Spec`",`"categoryType`":`"EXPENSE`",`"clientId`":`"$clientAId`"}")
$catAId = (Parse-JsonSafe $catA.Content).id
$listForA = Invoke-Curl -Url "$Base/api/v1/categories?clientId=$clientAId" -Token $tokenA
$listForB = Invoke-Curl -Url "$Base/api/v1/categories?clientId=$clientBId" -Token $tokenA
$aSees = $listForA.Content -match [regex]::Escape($catAId)
$bSees = $listForB.Content -match [regex]::Escape($catAId)
if ($catA.StatusCode -eq 201 -and $aSees -and -not $bSees) {
  Add-Result 'TC-044' 'Pass' 'Client A-specific category visible to A listing, not B' "id=$catAId"
} else {
  Add-Result 'TC-044' 'Fail' "create=$($catA.StatusCode) aSees=$aSees bSees=$bSees" "$($listForA.Content.Substring(0,[Math]::Min(200,$listForA.Content.Length)))"
}

# TC-045 Firm-wide duplicate code
$dupFirm = Invoke-Curl -Method POST -Url "$Base/api/v1/categories" -Token $tokenA -Body '{"code":"6100","name":"Dup Office","categoryType":"EXPENSE"}'
if ($dupFirm.StatusCode -eq 409 -and $dupFirm.Content -match 'DUPLICATE_CATEGORY|Duplicate|already') {
  Add-Result 'TC-045' 'Pass' 'Firm-wide duplicate code rejected' $dupFirm.Content
} else {
  Add-Result 'TC-045' 'Fail' "status=$($dupFirm.StatusCode)" $dupFirm.Content
}

# TC-046 Client-specific duplicate code
$dupClient = Invoke-Curl -Method POST -Url "$Base/api/v1/categories" -Token $tokenA -Body ("{`"code`":`"CA$ts`",`"name`":`"Dup`",`"categoryType`":`"EXPENSE`",`"clientId`":`"$clientAId`"}")
if ($dupClient.StatusCode -eq 409) {
  Add-Result 'TC-046' 'Pass' 'Client-specific duplicate code rejected' $dupClient.Content
} else {
  Add-Result 'TC-046' 'Fail' "status=$($dupClient.StatusCode)" $dupClient.Content
}

# TC-047 Same code across different clients
$codeX = "7777"
$cA = Invoke-Curl -Method POST -Url "$Base/api/v1/categories" -Token $tokenA -Body ("{`"code`":`"$codeX`",`"name`":`"A7777`",`"categoryType`":`"EXPENSE`",`"clientId`":`"$clientAId`"}")
$cB = Invoke-Curl -Method POST -Url "$Base/api/v1/categories" -Token $tokenA -Body ("{`"code`":`"$codeX`",`"name`":`"B7777`",`"categoryType`":`"EXPENSE`",`"clientId`":`"$clientBId`"}")
if ($cA.StatusCode -eq 201 -and $cB.StatusCode -eq 201) {
  Add-Result 'TC-047' 'Pass' 'Same client-specific code allowed on different clients' "A=$($cA.StatusCode) B=$($cB.StatusCode)"
} else {
  Add-Result 'TC-047' 'Fail' "A=$($cA.StatusCode) B=$($cB.StatusCode)" "$($cA.Content) | $($cB.Content)"
}

# TC-048 Category hierarchy self parent
$selfPut = Invoke-Curl -Method PUT -Url "$Base/api/v1/categories/$catId" -Token $tokenA -Body ("{`"code`":`"6100`",`"name`":`"Office Expenses`",`"categoryType`":`"EXPENSE`",`"parentId`":`"$catId`"}")
if ($selfPut.StatusCode -eq 400) {
  Add-Result 'TC-048' 'Pass' 'Self-parent rejected' $selfPut.Content
} else {
  Add-Result 'TC-048' 'Fail' "status=$($selfPut.StatusCode)" $selfPut.Content
}

# TC-049 Category hierarchy cycle A->B then B->A
$catP = Invoke-Curl -Method POST -Url "$Base/api/v1/categories" -Token $tokenA -Body '{"code":"P1","name":"Parent1","categoryType":"EXPENSE"}'
$catPId = (Parse-JsonSafe $catP.Content).id
$catC = Invoke-Curl -Method POST -Url "$Base/api/v1/categories" -Token $tokenA -Body ("{`"code`":`"C1`",`"name`":`"Child1`",`"categoryType`":`"EXPENSE`",`"parentId`":`"$catPId`"}")
$catCId = (Parse-JsonSafe $catC.Content).id
$cycle = Invoke-Curl -Method PUT -Url "$Base/api/v1/categories/$catPId" -Token $tokenA -Body ("{`"code`":`"P1`",`"name`":`"Parent1`",`"categoryType`":`"EXPENSE`",`"parentId`":`"$catCId`"}")
if ($cycle.StatusCode -eq 400) {
  Add-Result 'TC-049' 'Pass' 'Hierarchy cycle rejected' $cycle.Content
} else {
  Add-Result 'TC-049' 'Fail' "status=$($cycle.StatusCode) createP=$($catP.StatusCode) createC=$($catC.StatusCode)" $cycle.Content
}

# TC-050 Deactivate category: block new use; keep historical
$histWithCat = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses" -Token $tokenA -Body ("{`"transactionDate`":`"$today`",`"categoryId`":`"$catId`",`"amount`":77.00,`"currencyCode`":`"LKR`",`"vendorName`":`"CatHist`"}")
$histWithCatId = (Parse-JsonSafe $histWithCat.Content).id
$deactCat = Invoke-Curl -Method POST -Url "$Base/api/v1/categories/$catId/deactivate" -Token $tokenA
$newWithInactive = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses" -Token $tokenA -Body ("{`"transactionDate`":`"$today`",`"categoryId`":`"$catId`",`"amount`":78.00,`"currencyCode`":`"LKR`",`"vendorName`":`"BlockedCat`"}")
$oldStill = Sql "SELECT category_id::text FROM expenses WHERE id='$histWithCatId'"
if ($deactCat.StatusCode -eq 200 -and $newWithInactive.StatusCode -eq 422 -and $oldStill -eq $catId) {
  Add-Result 'TC-050' 'Pass' 'Inactive category blocks new txns; historical category_id unchanged' "new=$($newWithInactive.StatusCode) oldCat=$oldStill"
} else {
  Add-Result 'TC-050' 'Fail' "deact=$($deactCat.StatusCode) new=$($newWithInactive.StatusCode) old=$oldStill hist=$($histWithCat.StatusCode)" $newWithInactive.Content
}

# Write results
$outDir = 'c:\Users\HP\Downloads\finance-ai-automation-platform'
$md = Join-Path $outDir 'qa-results-tc028-050.md'
$pass = @($Results | Where-Object Result -eq 'Pass').Count
$fail = @($Results | Where-Object Result -eq 'Fail').Count
$blocked = @($Results | Where-Object Result -eq 'Blocked').Count
$lines = @(
  '# QA Results TC-028 – TC-050',
  '',
  "Run: $(Get-Date -Format o)",
  'Environment: local backend :8080, DB finance_platform_qa6',
  '',
  '| Case | Result | Notes | Evidence |',
  '|------|--------|-------|----------|'
)
foreach ($r in $Results) {
  $notes = ($r.Notes -replace '\|','/' -replace "`r?`n",' ')
  $evRaw = ($r.Evidence -replace '\|','/' -replace "`r?`n",' ')
  $ev = $evRaw.Substring(0, [Math]::Min(220, $evRaw.Length))
  $lines += "| $($r.Id) | **$($r.Result)** | $notes | $ev |"
}
$lines += ''
$lines += '## Summary'
$lines += "- Pass: $pass"
$lines += "- Fail: $fail"
$lines += "- Blocked: $blocked"
$lines += "- Total: $($Results.Count)"
$lines | Set-Content -Path $md -Encoding UTF8
$Results | Export-Csv (Join-Path $outDir 'qa-results-tc028-050.csv') -NoTypeInformation -Encoding UTF8
Write-Host "`nSUMMARY Pass=$pass Fail=$fail Blocked=$blocked Total=$($Results.Count)"
Write-Host "Wrote $md"
