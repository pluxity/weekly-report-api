# chat-v2 eval 세션 쿠키 획득 — 3 페르소나 로그인 후 Cookie 헤더를 env로 설정
# 사용: 서버 기동(8080) 후  →  . .\promptfoo\get-cookies.ps1   (점-소스로 실행해야 env가 현재 셸에 남음)
# 다른 포트면:  . .\promptfoo\get-cookies.ps1 -BaseUrl https://localhost:8081

param([string]$BaseUrl = "https://localhost:8080")

# 로컬 self-signed 인증서 무시 (https localhost)
try { [System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true } } catch {}

$users = @{
  SHYOON_COOKIE = "shyoon"      # 관리자(윤승현)
  MEMBER_COOKIE = "dkfkqpffk"   # 팀원(임우정)
  ADMIN_COOKIE  = "admin"       # admin(관리자)
}

foreach ($envName in $users.Keys) {
  $username = $users[$envName]
  $body = @{ username = $username; password = "evaltest123" } | ConvertTo-Json
  try {
    $resp = Invoke-WebRequest -Uri "$BaseUrl/auth/sign-in" -Method Post `
      -ContentType "application/json" -Body $body -SessionVariable sess -SkipHttpErrorCheck
    # Set-Cookie들을 "name=value; name2=value2" 형태로 병합
    $cookies = $sess.Cookies.GetCookies($BaseUrl)
    if ($cookies.Count -eq 0) {
      Write-Host "[$username] 쿠키 없음 — status=$($resp.StatusCode). 비번/유저 확인" -ForegroundColor Yellow
      continue
    }
    $cookieHeader = ($cookies | ForEach-Object { "$($_.Name)=$($_.Value)" }) -join "; "
    Set-Item -Path "env:$envName" -Value $cookieHeader
    Write-Host "[$username] -> `$env:$envName 설정됨 (status=$($resp.StatusCode), cookies=$($cookies.Count))" -ForegroundColor Green
  } catch {
    Write-Host "[$username] 로그인 실패: $($_.Exception.Message)" -ForegroundColor Red
  }
}

Write-Host "`n설정된 env:" -ForegroundColor Cyan
$users.Keys | ForEach-Object { "  $_ = $([bool](Get-Item "env:$_" -ErrorAction SilentlyContinue))" }
Write-Host "`n다음: npx promptfoo eval -c promptfoo/promptfooconfig.v2.yaml --repeat 3"
