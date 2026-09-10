$ErrorActionPreference = 'Continue'
$Base = 'http://localhost:8080'
$Results = New-Object System.Collections.Generic.List[object]
$ts = Get-Date -Format 'yyyyMMddHHmmss'
$AppPwd = 'password1'
$Psql = 'c:\Users\HP\Downloads\finance-ai-automation-platform\.tools\pgsql\pgsql\bin\psql.exe'
$env:PGPASSWORD = 'postgres'
$Db = 'finance_platform_qa6'
$Tmp = Join-Path $env:TEMP "qa51-$ts"
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

function Login-Token([string]$email) {
  $r = Invoke-Curl -Method POST -Url "$Base/api/v1/auth/login" -Body ("{`"email`":`"$email`",`"password`":`"$AppPwd`"}")
  return (Parse-JsonSafe $r.Content).accessToken
}

$today = (Get-Date).ToString('yyyy-MM-dd')
$lastMonth = (Get-Date).AddMonths(-1).ToString('yyyy-MM-dd')
$monthStart = (Get-Date -Day 1).ToString('yyyy-MM-dd')
$monthEnd = (Get-Date -Day 1).AddMonths(1).AddDays(-1).ToString('yyyy-MM-dd')

# ---------- Bootstrap ----------
$emailA = "admin-a-$ts@example.com"
$regA = Invoke-Curl -Method POST -Url "$Base/api/v1/auth/register" -Body ("{`"firmName`":`"FirmA $ts`",`"email`":`"$emailA`",`"password`":`"$AppPwd`",`"fullName`":`"Admin A`"}")
$regAObj = Parse-JsonSafe $regA.Content
$firmA = $regAObj.firmId
$tokenA = Login-Token $emailA
Write-Host "FirmA=$firmA reg=$($regA.StatusCode)"

# Detect DB if register landed elsewhere
$firmCount = Sql "SELECT count(*) FROM firms WHERE id='$firmA'"
if ($firmCount -ne '1') {
  $Db = 'finance_platform'
  $firmCount2 = Sql "SELECT count(*) FROM firms WHERE id='$firmA'"
  Write-Host "Switched DB to $Db firmCount=$firmCount2"
}

$clientA = Invoke-Curl -Method POST -Url "$Base/api/v1/clients" -Token $tokenA -Body ("{`"name`":`"Client A $ts`",`"businessRegNo`":`"A$ts`",`"contactEmail`":`"a@$ts.test`"}")
$clientAId = (Parse-JsonSafe $clientA.Content).id
$clientB = Invoke-Curl -Method POST -Url "$Base/api/v1/clients" -Token $tokenA -Body ("{`"name`":`"Client B $ts`",`"businessRegNo`":`"B$ts`",`"contactEmail`":`"b@$ts.test`"}")
$clientBId = (Parse-JsonSafe $clientB.Content).id

$expCat = Invoke-Curl -Method POST -Url "$Base/api/v1/categories" -Token $tokenA -Body '{"code":"6100","name":"Office Expenses","categoryType":"EXPENSE"}'
$expCatId = (Parse-JsonSafe $expCat.Content).id
$expCatB = Invoke-Curl -Method POST -Url "$Base/api/v1/categories" -Token $tokenA -Body ("{`"code`":`"CB$ts`",`"name`":`"ClientB Only`",`"categoryType`":`"EXPENSE`",`"clientId`":`"$clientBId`"}")
$expCatBId = (Parse-JsonSafe $expCatB.Content).id
$incCat = Invoke-Curl -Method POST -Url "$Base/api/v1/categories" -Token $tokenA -Body '{"code":"4100","name":"Sales Income","categoryType":"INCOME"}'
$incCatId = (Parse-JsonSafe $incCat.Content).id
$expCat2 = Invoke-Curl -Method POST -Url "$Base/api/v1/categories" -Token $tokenA -Body '{"code":"6200","name":"Utilities","categoryType":"EXPENSE"}'
$expCat2Id = (Parse-JsonSafe $expCat2.Content).id

# Raise plan user quota so role/security personas can be created
Sql "UPDATE firm_subscriptions SET max_users = 15 WHERE firm_id='$firmA'" | Out-Null

# Users
$accEmail = "acct-$ts@example.com"
$accCreate = Invoke-Curl -Method POST -Url "$Base/api/v1/users" -Token $tokenA -Body ("{`"email`":`"$accEmail`",`"password`":`"$AppPwd`",`"fullName`":`"Accountant`",`"role`":`"ACCOUNTANT`",`"clientAccess`":[{`"clientId`":`"$clientAId`",`"accessType`":`"FULL`"}]}")
$accId = (Parse-JsonSafe $accCreate.Content).id
$accToken = Login-Token $accEmail

$accRoEmail = "acct-ro-$ts@example.com"
$accRoCreate = Invoke-Curl -Method POST -Url "$Base/api/v1/users" -Token $tokenA -Body ("{`"email`":`"$accRoEmail`",`"password`":`"$AppPwd`",`"fullName`":`"Acct RO`",`"role`":`"ACCOUNTANT`",`"clientAccess`":[{`"clientId`":`"$clientAId`",`"accessType`":`"READ_ONLY`"}]}")
$accRoToken = Login-Token $accRoEmail

$boEmail = "owner-$ts@example.com"
$boCreate = Invoke-Curl -Method POST -Url "$Base/api/v1/users" -Token $tokenA -Body ("{`"email`":`"$boEmail`",`"password`":`"$AppPwd`",`"fullName`":`"Owner`",`"role`":`"BUSINESS_OWNER`",`"clientAccess`":[{`"clientId`":`"$clientAId`",`"accessType`":`"FULL`"}]}")
$boToken = Login-Token $boEmail

$boUpEmail = "owner-up-$ts@example.com"
$boUpCreate = Invoke-Curl -Method POST -Url "$Base/api/v1/users" -Token $tokenA -Body ("{`"email`":`"$boUpEmail`",`"password`":`"$AppPwd`",`"fullName`":`"Upload Owner`",`"role`":`"BUSINESS_OWNER`",`"clientAccess`":[{`"clientId`":`"$clientAId`",`"accessType`":`"UPLOAD_ONLY`"}]}")
$boUpToken = Login-Token $boUpEmail

$audEmail = "aud-$ts@example.com"
$audCreate = Invoke-Curl -Method POST -Url "$Base/api/v1/users" -Token $tokenA -Body ("{`"email`":`"$audEmail`",`"password`":`"$AppPwd`",`"fullName`":`"Auditor`",`"role`":`"AUDITOR`",`"clientAccess`":[{`"clientId`":`"$clientAId`",`"accessType`":`"READ_ONLY`"}]}")
$audToken = Login-Token $audEmail

Write-Host "clients A=$clientAId B=$clientBId expCat=$expCatId incCat=$incCatId"
Write-Host "users acc=$($accCreate.StatusCode) ro=$($accRoCreate.StatusCode) bo=$($boCreate.StatusCode) up=$($boUpCreate.StatusCode) aud=$($audCreate.StatusCode)"
Write-Host "tokens acc=$([bool]$accToken) ro=$([bool]$accRoToken) bo=$([bool]$boToken) up=$([bool]$boUpToken) aud=$([bool]$audToken)"

# ==================== EXPENSES TC-051..063 ====================

# TC-051 Create draft expense
$c51Body = "{`"transactionDate`":`"$today`",`"categoryId`":`"$expCatId`",`"amount`":125.50,`"currencyCode`":`"LKR`",`"vendorName`":`"Vendor $ts`",`"description`":`"Draft smoke`"}"
$c51 = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses" -Token $accToken -Body $c51Body
$c51Obj = Parse-JsonSafe $c51.Content
$draftId = $c51Obj.id
$dbClient = Sql "SELECT client_id::text FROM expenses WHERE id='$draftId'"
$dbFirm = Sql "SELECT firm_id::text FROM expenses WHERE id='$draftId'"
if ($c51.StatusCode -eq 201 -and $c51Obj.status -eq 'DRAFT' -and $c51Obj.clientId -eq $clientAId -and $dbClient -eq $clientAId -and $dbFirm -eq $firmA) {
  Add-Result 'TC-051' 'Pass' 'Expense created as DRAFT for correct client/firm' "id=$draftId status=$($c51Obj.status) amount=$($c51Obj.amount)"
} else {
  Add-Result 'TC-051' 'Fail' "status=$($c51.StatusCode) apiStatus=$($c51Obj.status) dbClient=$dbClient" $c51.Content
}

# TC-052 Edit draft expense
$c52Body = "{`"transactionDate`":`"$today`",`"categoryId`":`"$expCat2Id`",`"amount`":200.00,`"currencyCode`":`"LKR`",`"vendorName`":`"Vendor $ts`",`"description`":`"Updated draft`"}"
$c52 = Invoke-Curl -Method PUT -Url "$Base/api/v1/clients/$clientAId/expenses/$draftId" -Token $accToken -Body $c52Body
$c52Obj = Parse-JsonSafe $c52.Content
$auditUpd = Sql "SELECT count(*) FROM audit_log WHERE resource_id='$draftId' AND action='EXPENSE_UPDATED'"
if ($c52.StatusCode -eq 200 -and $c52Obj.amount -eq 200.00 -and $c52Obj.description -eq 'Updated draft' -and $c52Obj.categoryId -eq $expCat2Id -and [int]$auditUpd -ge 1) {
  Add-Result 'TC-052' 'Pass' 'Draft updated; EXPENSE_UPDATED audit recorded' "amount=$($c52Obj.amount) cat=$($c52Obj.categoryId) audit=$auditUpd"
} else {
  Add-Result 'TC-052' 'Fail' "status=$($c52.StatusCode) amount=$($c52Obj.amount) audit=$auditUpd" $c52.Content
}

# TC-053 Delete draft expense (separate draft so main chain continues)
$delDraft = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses" -Token $accToken -Body ("{`"transactionDate`":`"$today`",`"categoryId`":`"$expCatId`",`"amount`":10.00,`"currencyCode`":`"LKR`",`"vendorName`":`"ToDelete`"}")
$delDraftId = (Parse-JsonSafe $delDraft.Content).id
# keep an approved untouched for "finalized unaffected"
$keepApproved = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses" -Token $accToken -Body ("{`"transactionDate`":`"$today`",`"categoryId`":`"$expCatId`",`"amount`":11.00,`"currencyCode`":`"LKR`",`"vendorName`":`"KeepApproved`"}")
$keepApprovedId = (Parse-JsonSafe $keepApproved.Content).id
Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses/$keepApprovedId/approve" -Token $accToken | Out-Null
$c53 = Invoke-Curl -Method DELETE -Url "$Base/api/v1/clients/$clientAId/expenses/$delDraftId" -Token $accToken
$gone = Invoke-Curl -Url "$Base/api/v1/clients/$clientAId/expenses/$delDraftId" -Token $accToken
$keepStill = Invoke-Curl -Url "$Base/api/v1/clients/$clientAId/expenses/$keepApprovedId" -Token $accToken
$dbGone = Sql "SELECT count(*) FROM expenses WHERE id='$delDraftId'"
if ($c53.StatusCode -eq 204 -and $gone.StatusCode -eq 404 -and $keepStill.StatusCode -eq 200 -and (Parse-JsonSafe $keepStill.Content).status -eq 'APPROVED' -and $dbGone -eq '0') {
  Add-Result 'TC-053' 'Pass' 'Draft hard-deleted per design; approved record unaffected' "delete=$($c53.StatusCode) get=$($gone.StatusCode) keep=$($keepStill.StatusCode) db=$dbGone"
} else {
  Add-Result 'TC-053' 'Fail' "del=$($c53.StatusCode) get=$($gone.StatusCode) keep=$($keepStill.StatusCode) db=$dbGone" "$($c53.Content) | $($gone.Content)"
}

# TC-054 Approve expense
$c54 = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses/$draftId/approve" -Token $accToken
$c54Obj = Parse-JsonSafe $c54.Content
$reload = Invoke-Curl -Url "$Base/api/v1/clients/$clientAId/expenses/$draftId" -Token $accToken
$reloadObj = Parse-JsonSafe $reload.Content
$approvedBy = Sql "SELECT approved_by_user_id::text FROM expenses WHERE id='$draftId'"
$approvedAtDb = Sql "SELECT CASE WHEN approved_at IS NULL THEN 'null' ELSE 'set' END FROM expenses WHERE id='$draftId'"
if ($c54.StatusCode -eq 200 -and $reloadObj.status -eq 'APPROVED' -and $reloadObj.approvedAt -and $approvedBy -eq $accId -and $approvedAtDb -eq 'set') {
  Add-Result 'TC-054' 'Pass' 'Status APPROVED; approvedBy/approvedAt recorded' "status=$($reloadObj.status) approvedAt=$($reloadObj.approvedAt) by=$approvedBy"
} else {
  Add-Result 'TC-054' 'Fail' "approve=$($c54.StatusCode) status=$($reloadObj.status) by=$approvedBy at=$approvedAtDb" $c54.Content
}

# TC-055 Edit approved expense (negative)
$c55 = Invoke-Curl -Method PUT -Url "$Base/api/v1/clients/$clientAId/expenses/$draftId" -Token $accToken -Body ("{`"transactionDate`":`"$today`",`"categoryId`":`"$expCatId`",`"amount`":999.00,`"currencyCode`":`"LKR`",`"vendorName`":`"ShouldFail`",`"description`":`"nope`"}")
$amtUnchanged = Sql "SELECT amount::text FROM expenses WHERE id='$draftId'"
if ($c55.StatusCode -in @(400,409,422) -and [decimal]$amtUnchanged -ne 999.00) {
  Add-Result 'TC-055' 'Pass' 'Approved expense update rejected; amount unchanged' "status=$($c55.StatusCode) amount=$amtUnchanged $($c55.Content)"
} else {
  Add-Result 'TC-055' 'Fail' "status=$($c55.StatusCode) amount=$amtUnchanged" $c55.Content
}

# TC-056 Void approved expense
$c56 = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses/$draftId/void" -Token $accToken -Body '{"reason":"Duplicate entry"}'
$c56Obj = Parse-JsonSafe $c56.Content
$voidBy = Sql "SELECT voided_by_user_id::text FROM expenses WHERE id='$draftId'"
$voidReason = Sql "SELECT void_reason FROM expenses WHERE id='$draftId'"
$rowExists = Sql "SELECT count(*) FROM expenses WHERE id='$draftId'"
if ($c56.StatusCode -eq 200 -and $c56Obj.status -eq 'VOID' -and $c56Obj.voidReason -eq 'Duplicate entry' -and $c56Obj.voidedAt -and $voidBy -eq $accId -and $voidReason -eq 'Duplicate entry' -and $rowExists -eq '1') {
  Add-Result 'TC-056' 'Pass' 'VOID with actor/time/reason; row retained' "status=$($c56Obj.status) by=$voidBy reason=$voidReason"
} else {
  Add-Result 'TC-056' 'Fail' "status=$($c56.StatusCode) api=$($c56Obj.status) by=$voidBy reason=$voidReason row=$rowExists" $c56.Content
}

# TC-057 Void -> approve invalid transition
$c57 = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses/$draftId/approve" -Token $accToken
if ($c57.StatusCode -in @(400,409,422) -and ($c57.Content -match 'VOID|ALREADY_VOID|cannot be approved|invalid' -or $c57.StatusCode -ne 200)) {
  Add-Result 'TC-057' 'Pass' 'VOID->APPROVE rejected' "status=$($c57.StatusCode) $($c57.Content)"
} else {
  Add-Result 'TC-057' 'Fail' "status=$($c57.StatusCode)" $c57.Content
}

# TC-058 Business owner cannot approve
$boDraft = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses" -Token $boToken -Body ("{`"transactionDate`":`"$today`",`"categoryId`":`"$expCatId`",`"amount`":33.00,`"currencyCode`":`"LKR`",`"vendorName`":`"OwnerDraft`"}")
$boDraftId = (Parse-JsonSafe $boDraft.Content).id
$c58 = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses/$boDraftId/approve" -Token $boToken
if ($c58.StatusCode -eq 403 -and (Parse-JsonSafe (Invoke-Curl -Url "$Base/api/v1/clients/$clientAId/expenses/$boDraftId" -Token $accToken).Content).status -eq 'DRAFT') {
  Add-Result 'TC-058' 'Pass' 'BUSINESS_OWNER approve forbidden; draft unchanged' "create=$($boDraft.StatusCode) approve=$($c58.StatusCode)"
} else {
  Add-Result 'TC-058' 'Fail' "create=$($boDraft.StatusCode) approve=$($c58.StatusCode)" $c58.Content
}

# TC-059 Auditor draft hidden even with filter
# seed approved + void already exist; ensure a draft exists
$audDraft = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses" -Token $accToken -Body ("{`"transactionDate`":`"$today`",`"categoryId`":`"$expCatId`",`"amount`":44.00,`"currencyCode`":`"LKR`",`"vendorName`":`"HiddenDraft`"}")
$audDraftId = (Parse-JsonSafe $audDraft.Content).id
$c59 = Invoke-Curl -Url "$Base/api/v1/clients/$clientAId/expenses?status=DRAFT&page=0&size=50" -Token $audToken
$c59Obj = Parse-JsonSafe $c59.Content
$draftIds = @()
if ($c59Obj.content) { $draftIds = @($c59Obj.content | ForEach-Object { $_.id }) }
$hasDraft = $draftIds -contains $audDraftId
$anyDraftStatus = $false
if ($c59Obj.content) { foreach ($row in $c59Obj.content) { if ($row.status -eq 'DRAFT') { $anyDraftStatus = $true } } }
if ($c59.StatusCode -eq 200 -and -not $hasDraft -and -not $anyDraftStatus) {
  Add-Result 'TC-059' 'Pass' 'Auditor status=DRAFT returns no drafts' "count=$($c59Obj.content.Count) ids=$($draftIds -join ',')"
} else {
  Add-Result 'TC-059' 'Fail' "status=$($c59.StatusCode) hasDraft=$hasDraft anyDraft=$anyDraftStatus" $c59.Content.Substring(0, [Math]::Min(300, $c59.Content.Length))
}

# TC-060 READ_ONLY accountant cannot create
$c60 = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses" -Token $accRoToken -Body ("{`"transactionDate`":`"$today`",`"categoryId`":`"$expCatId`",`"amount`":55.00,`"currencyCode`":`"LKR`",`"vendorName`":`"ROBlocked`"}")
if ($c60.StatusCode -eq 403) {
  Add-Result 'TC-060' 'Pass' 'READ_ONLY accountant create forbidden' $c60.Content
} else {
  Add-Result 'TC-060' 'Fail' "status=$($c60.StatusCode)" $c60.Content
}

# TC-061 UPLOAD_ONLY cannot list ledger
$c61 = Invoke-Curl -Url "$Base/api/v1/clients/$clientAId/expenses?page=0&size=20" -Token $boUpToken
if ($c61.StatusCode -eq 403) {
  Add-Result 'TC-061' 'Pass' 'UPLOAD_ONLY owner expense list forbidden' $c61.Content
} else {
  Add-Result 'TC-061' 'Fail' "status=$($c61.StatusCode)" $c61.Content
}

# TC-062 Cross-client category rejected
$c62 = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses" -Token $accToken -Body ("{`"transactionDate`":`"$today`",`"categoryId`":`"$expCatBId`",`"amount`":66.00,`"currencyCode`":`"LKR`",`"vendorName`":`"ForeignCat`"}")
if ($c62.StatusCode -in @(400,404,422) -and ($c62.Content -match 'INVALID_CATEGORY|belong|client|Category')) {
  Add-Result 'TC-062' 'Pass' 'Cross-client category rejected' "status=$($c62.StatusCode) $($c62.Content)"
} else {
  Add-Result 'TC-062' 'Fail' "status=$($c62.StatusCode) catB=$expCatBId" $c62.Content
}

# TC-063 Negative/invalid amount
$c63 = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses" -Token $accToken -Body ("{`"transactionDate`":`"$today`",`"categoryId`":`"$expCatId`",`"amount`":-100,`"currencyCode`":`"LKR`",`"vendorName`":`"NegAmt`"}")
if ($c63.StatusCode -in @(400,422) -and ($c63.Content -match 'amount|Amount|VALIDATION')) {
  Add-Result 'TC-063' 'Pass' 'Negative amount rejected with validation error' "status=$($c63.StatusCode) $($c63.Content)"
} else {
  Add-Result 'TC-063' 'Fail' "status=$($c63.StatusCode)" $c63.Content
}

# ==================== INCOME TC-064..067 ====================

# TC-064 Create draft income
$c64 = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/income" -Token $accToken -Body ("{`"transactionDate`":`"$today`",`"categoryId`":`"$incCatId`",`"amount`":500.00,`"currencyCode`":`"LKR`",`"customerName`":`"Customer $ts`",`"description`":`"Draft income`"}")
$c64Obj = Parse-JsonSafe $c64.Content
$incomeId = $c64Obj.id
if ($c64.StatusCode -eq 201 -and $c64Obj.status -eq 'DRAFT') {
  Add-Result 'TC-064' 'Pass' 'Income created as DRAFT' "id=$incomeId status=$($c64Obj.status)"
} else {
  Add-Result 'TC-064' 'Fail' "status=$($c64.StatusCode)" $c64.Content
}

# TC-065 Approve income without payment method
$c65 = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/income/$incomeId/approve" -Token $accToken
$stillDraft = (Parse-JsonSafe (Invoke-Curl -Url "$Base/api/v1/clients/$clientAId/income/$incomeId" -Token $accToken).Content).status
if ($c65.StatusCode -in @(400,422) -and ($c65.Content -match 'paymentMethod|Payment method') -and $stillDraft -eq 'DRAFT') {
  Add-Result 'TC-065' 'Pass' 'Approval rejected without payment method' "status=$($c65.StatusCode) still=$stillDraft"
} else {
  Add-Result 'TC-065' 'Fail' "status=$($c65.StatusCode) still=$stillDraft" $c65.Content
}

# TC-066 Approve income with payment method
$c66Upd = Invoke-Curl -Method PUT -Url "$Base/api/v1/clients/$clientAId/income/$incomeId" -Token $accToken -Body ("{`"transactionDate`":`"$today`",`"categoryId`":`"$incCatId`",`"amount`":500.00,`"currencyCode`":`"LKR`",`"customerName`":`"Customer $ts`",`"description`":`"With PM`",`"paymentMethod`":`"BANK_TRANSFER`"}")
$c66 = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/income/$incomeId/approve" -Token $accToken
$c66Obj = Parse-JsonSafe $c66.Content
$incApprovedBy = Sql "SELECT approved_by_user_id::text FROM income WHERE id='$incomeId'"
if ($c66Upd.StatusCode -eq 200 -and $c66.StatusCode -eq 200 -and $c66Obj.status -eq 'APPROVED' -and $c66Obj.approvedAt -and $incApprovedBy -eq $accId) {
  Add-Result 'TC-066' 'Pass' 'Income APPROVED with payment method and approval metadata' "status=$($c66Obj.status) pm=$($c66Obj.paymentMethod) by=$incApprovedBy"
} else {
  Add-Result 'TC-066' 'Fail' "upd=$($c66Upd.StatusCode) approve=$($c66.StatusCode) status=$($c66Obj.status) by=$incApprovedBy" "$($c66Upd.Content) | $($c66.Content)"
}

# TC-067 Void approved income
$c67 = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/income/$incomeId/void" -Token $accToken -Body '{"reason":"Correction"}'
$c67Obj = Parse-JsonSafe $c67.Content
$c67Get = Invoke-Curl -Url "$Base/api/v1/clients/$clientAId/income/$incomeId" -Token $accToken
$c67List = Invoke-Curl -Url "$Base/api/v1/clients/$clientAId/income?status=VOID&page=0&size=20" -Token $accToken
$visible = $c67List.Content -match [regex]::Escape($incomeId)
if ($c67.StatusCode -eq 200 -and $c67Obj.status -eq 'VOID' -and $c67Get.StatusCode -eq 200 -and $visible) {
  Add-Result 'TC-067' 'Pass' 'Income VOID and historically visible' "status=$($c67Obj.status) get=$($c67Get.StatusCode) listed=$visible"
} else {
  Add-Result 'TC-067' 'Fail' "void=$($c67.StatusCode) status=$($c67Obj.status) get=$($c67Get.StatusCode) listed=$visible" $c67.Content
}

# ==================== LEDGER TC-068..069 ====================

# Seed date/category variety
$inRange1 = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses" -Token $accToken -Body ("{`"transactionDate`":`"$today`",`"categoryId`":`"$expCatId`",`"amount`":71.00,`"currencyCode`":`"LKR`",`"vendorName`":`"RangeIn1`"}")
$inRange1Id = (Parse-JsonSafe $inRange1.Content).id
$outRange = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses" -Token $accToken -Body ("{`"transactionDate`":`"$lastMonth`",`"categoryId`":`"$expCatId`",`"amount`":72.00,`"currencyCode`":`"LKR`",`"vendorName`":`"RangeOut`"}")
$outRangeId = (Parse-JsonSafe $outRange.Content).id
$otherCat = Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientAId/expenses" -Token $accToken -Body ("{`"transactionDate`":`"$today`",`"categoryId`":`"$expCat2Id`",`"amount`":73.00,`"currencyCode`":`"LKR`",`"vendorName`":`"OtherCat`"}")
$otherCatId = (Parse-JsonSafe $otherCat.Content).id

# TC-068 Date-range filtering (API params: from/to)
$c68 = Invoke-Curl -Url "$Base/api/v1/clients/$clientAId/expenses?from=$monthStart&to=$monthEnd&page=0&size=100" -Token $accToken
$c68Obj = Parse-JsonSafe $c68.Content
$ids68 = @()
if ($c68Obj.content) { $ids68 = @($c68Obj.content | ForEach-Object { $_.id }) }
$hasIn = $ids68 -contains $inRange1Id
$hasOut = $ids68 -contains $outRangeId
$allInRange = $true
if ($c68Obj.content) {
  foreach ($row in $c68Obj.content) {
    if ($row.transactionDate -lt $monthStart -or $row.transactionDate -gt $monthEnd) { $allInRange = $false }
  }
}
if ($c68.StatusCode -eq 200 -and $hasIn -and -not $hasOut -and $allInRange) {
  Add-Result 'TC-068' 'Pass' 'Date from/to filter returns only in-range transactions' "in=$hasIn out=$hasOut count=$($ids68.Count)"
} else {
  Add-Result 'TC-068' 'Fail' "status=$($c68.StatusCode) in=$hasIn out=$hasOut createIn=$($inRange1.StatusCode) createOut=$($outRange.StatusCode)" "from=$monthStart to=$monthEnd"
}

# TC-069 Category filtering
$c69 = Invoke-Curl -Url "$Base/api/v1/clients/$clientAId/expenses?categoryId=$expCatId&page=0&size=100" -Token $accToken
$c69Obj = Parse-JsonSafe $c69.Content
$ids69 = @()
$badCat = $false
if ($c69Obj.content) {
  $ids69 = @($c69Obj.content | ForEach-Object { $_.id })
  foreach ($row in $c69Obj.content) { if ($row.categoryId -ne $expCatId) { $badCat = $true } }
}
if ($c69.StatusCode -eq 200 -and ($ids69 -contains $inRange1Id) -and -not ($ids69 -contains $otherCatId) -and -not $badCat) {
  Add-Result 'TC-069' 'Pass' 'categoryId filter returns only matching transactions' "hasTarget=$($ids69 -contains $inRange1Id) hasOther=$($ids69 -contains $otherCatId)"
} else {
  Add-Result 'TC-069' 'Fail' "status=$($c69.StatusCode) hasTarget=$($ids69 -contains $inRange1Id) hasOther=$($ids69 -contains $otherCatId) badCat=$badCat" ''
}

# ==================== AUDIT TC-070..072 ====================

# Ensure client B has audit activity
Invoke-Curl -Method POST -Url "$Base/api/v1/clients/$clientBId/expenses" -Token $tokenA -Body ("{`"transactionDate`":`"$today`",`"categoryId`":`"$expCatId`",`"amount`":88.00,`"currencyCode`":`"LKR`",`"vendorName`":`"ClientBExp`"}") | Out-Null

# TC-070 Audit log query
$c70 = Invoke-Curl -Url "$Base/api/v1/audit?action=EXPENSE_CREATED&page=0&size=20" -Token $tokenA
$c70Obj = Parse-JsonSafe $c70.Content
$c70Date = Invoke-Curl -Url "$Base/api/v1/audit?resourceType=EXPENSE&page=0&size=10" -Token $tokenA
$c70DateObj = Parse-JsonSafe $c70Date.Content
if ($c70.StatusCode -eq 200 -and $c70Obj.content.Count -ge 1 -and $null -ne $c70Obj.totalElements -and $c70Date.StatusCode -eq 200) {
  Add-Result 'TC-070' 'Pass' 'Admin audit query with action/resource filters and pagination' "actionCount=$($c70Obj.content.Count) total=$($c70Obj.totalElements) resourceTotal=$($c70DateObj.totalElements)"
} else {
  Add-Result 'TC-070' 'Fail' "action=$($c70.StatusCode) resource=$($c70Date.StatusCode)" $c70.Content.Substring(0, [Math]::Min(250, $c70.Content.Length))
}

# TC-071 Auditor audit scope
$c71All = Invoke-Curl -Url "$Base/api/v1/audit?page=0&size=100" -Token $audToken
$c71A = Invoke-Curl -Url "$Base/api/v1/audit?clientId=$clientAId&page=0&size=50" -Token $audToken
$c71B = Invoke-Curl -Url "$Base/api/v1/audit?clientId=$clientBId&page=0&size=50" -Token $audToken
$c71AllObj = Parse-JsonSafe $c71All.Content
$sawB = $false
if ($c71AllObj.content) {
  foreach ($row in $c71AllObj.content) {
    if ($row.clientId -eq $clientBId) { $sawB = $true }
  }
}
if ($c71All.StatusCode -eq 200 -and $c71A.StatusCode -eq 200 -and $c71B.StatusCode -eq 403 -and -not $sawB) {
  Add-Result 'TC-071' 'Pass' 'Auditor sees only assigned-client audit; Client B denied' "all=$($c71All.StatusCode) A=$($c71A.StatusCode) B=$($c71B.StatusCode) sawB=$sawB"
} else {
  Add-Result 'TC-071' 'Fail' "all=$($c71All.StatusCode) A=$($c71A.StatusCode) B=$($c71B.StatusCode) sawB=$sawB" $c71B.Content
}

# TC-072 No secrets in audit
$recent = Invoke-Curl -Url "$Base/api/v1/audit?page=0&size=100" -Token $tokenA
$blob = $recent.Content.ToLower()
$secretHits = @()
foreach ($pat in @('password','passwordhash','accessToken','accesstoken','refreshToken','refreshtoken','bearer ','apiKey','apikey','jwt')) {
  if ($blob -match [regex]::Escape($pat.ToLower())) { $secretHits += $pat }
}
# also scan DB JSON for password/token literals in recent firm rows
$dbSecrets = Sql @"
SELECT count(*) FROM audit_log
WHERE firm_id='$firmA'
  AND (
    coalesce(before_state::text,'') ~* '(password|refresh.?token|access.?token|api.?key|"jwt")'
    OR coalesce(after_state::text,'') ~* '(password|refresh.?token|access.?token|api.?key|"jwt")'
    OR coalesce(metadata::text,'') ~* '(password|refresh.?token|access.?token|api.?key|"jwt")'
  )
"@
if ($recent.StatusCode -eq 200 -and $secretHits.Count -eq 0 -and $dbSecrets -eq '0') {
  Add-Result 'TC-072' 'Pass' 'No password/JWT/refresh token/API key in recent audit payloads' "apiHits=0 dbHits=$dbSecrets rows=$($recent.Content.Length)"
} else {
  Add-Result 'TC-072' 'Fail' "apiHits=$($secretHits -join ',') dbHits=$dbSecrets status=$($recent.StatusCode)" ''
}

# Write results
$outDir = 'c:\Users\HP\Downloads\finance-ai-automation-platform'
$md = Join-Path $outDir 'qa-results-tc051-072.md'
$pass = @($Results | Where-Object Result -eq 'Pass').Count
$fail = @($Results | Where-Object Result -eq 'Fail').Count
$blocked = @($Results | Where-Object Result -eq 'Blocked').Count
$lines = @(
  '# QA Results TC-051 – TC-072',
  '',
  "Run: $(Get-Date -Format o)",
  "Environment: local backend :8080, DB $Db",
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
$Results | Export-Csv (Join-Path $outDir 'qa-results-tc051-072.csv') -NoTypeInformation -Encoding UTF8
Write-Host "`nSUMMARY Pass=$pass Fail=$fail Blocked=$blocked Total=$($Results.Count)"
Write-Host "Wrote $md"
