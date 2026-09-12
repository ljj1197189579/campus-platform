param([string]$BaseUrl = "http://localhost:8080")
$ErrorActionPreference = "Stop"
$pass=0; $fail=0
function Check($name,$ok){ if($ok){$script:pass++; Write-Host "PASS $name" -ForegroundColor Green}else{$script:fail++; Write-Host "FAIL $name" -ForegroundColor Red} }
function Request([string]$method,[string]$path,$body=$null,[string]$token=$null){
    $headers=@{}
    if($token){$headers.Authorization="Bearer $token"}
    $params=@{Method=$method;Uri="$BaseUrl$path";Headers=$headers;UseBasicParsing=$true}
    if($null -ne $body){$params.ContentType="application/json";$params.Body=($body|ConvertTo-Json -Depth 5)}
    try{$response=Invoke-WebRequest @params; return @{Code=[int]$response.StatusCode;Data=($response.Content|ConvertFrom-Json)}}catch{
        if($_.Exception.Response){$response=$_.Exception.Response; $content=''; try{$content=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult()}catch{}; return @{Code=[int]$response.StatusCode;Data=if($content){$content|ConvertFrom-Json}else{$null}}}
        throw
    }
}
try{
    $u="test_$(Get-Random)"
    $r=Request -method 'POST' -path '/api/auth/register' -body @{username=$u;password='123456'}
    Check '注册成功' ($r.Code -eq 200)
    $r=Request -method 'POST' -path '/api/auth/login' -body @{username=$u;password='123456'}
    $token=$r.Data.token
    Check '登录返回JWT' ($r.Code -eq 200 -and -not [string]::IsNullOrWhiteSpace($token))
    $r=Request -method 'GET' -path '/api/goods'
    Check '公开搜索商品' ($r.Code -eq 200)
    $r=Request -method 'POST' -path '/api/goods' -body @{categoryId=1;title='联调测试商品';description='接口测试';price=10;condition='9成新'}
    Check '无Token禁止发布' ($r.Code -eq 401)
    $r=Request -method 'POST' -path '/api/goods' -body @{categoryId=1;title='联调测试商品';description='接口测试';price=10;condition='9成新'} -token $token
    Check '带Token发布商品' ($r.Code -eq 200)
    $r=Request -method 'GET' -path '/api/admin/audits' -token $token
    Check '普通用户禁止审核' ($r.Code -eq 403)
    $bad=Request -method 'POST' -path '/api/auth/login' -body @{username=$u;password='wrong'}
    Check '错误密码被拒绝' ($bad.Code -eq 400)
}catch{Write-Host "TEST ERROR: $($_.Exception.Message)" -ForegroundColor Red; $fail++}
Write-Host "RESULT pass=$pass fail=$fail"; if($fail -gt 0){exit 1}
