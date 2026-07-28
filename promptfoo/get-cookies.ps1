# chat-v2 eval session cookies — sign in 3 personas, set Cookie header into env
# Usage: start server(8081) first  ->  . .\promptfoo\get-cookies.ps1   (dot-source so env persists in this shell)
# Other port:  . .\promptfoo\get-cookies.ps1 -BaseUrl http://localhost:8080
# Note: Windows PowerShell 5.1 compatible (no -SkipHttpErrorCheck; messages in ASCII to dodge codepage issues).

param([string]$BaseUrl = "http://localhost:8081")

# ignore self-signed cert if https localhost (harmless on http)
try { [System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true } } catch {}

# seed 유저 (EvalDataSeeder). 비번 evaltest123.
$users = @{
  SHYOON_COOKIE = "leader"      # 이도경 — TEAM_LEADER + PM (팀 리더 + 프로젝트 PM)
  MEMBER_COOKIE = "member"      # 박서준 — 무역할 (본인 태스크만)
  ADMIN_COOKIE  = "admin"       # 김관리 — ADMIN (전체 스코프)
}

foreach ($envName in $users.Keys) {
  $username = $users[$envName]
  $body = @{ username = $username; password = "evaltest123" } | ConvertTo-Json
  try {
    $resp = Invoke-WebRequest -Uri "$BaseUrl/auth/sign-in" -Method Post `
      -ContentType "application/json" -Body $body -SessionVariable sess -UseBasicParsing
    # merge Set-Cookie into "name=value; name2=value2"
    $cookies = $sess.Cookies.GetCookies($BaseUrl)
    if ($cookies.Count -eq 0) {
      Write-Host "[$username] no cookie -- status=$($resp.StatusCode). check password/user" -ForegroundColor Yellow
      continue
    }
    $cookieHeader = ($cookies | ForEach-Object { "$($_.Name)=$($_.Value)" }) -join "; "
    Set-Item -Path "env:$envName" -Value $cookieHeader
    Write-Host "[$username] -> `$env:$envName set (status=$($resp.StatusCode), cookies=$($cookies.Count))" -ForegroundColor Green
  } catch {
    Write-Host "[$username] sign-in failed: $($_.Exception.Message)" -ForegroundColor Red
  }
}

Write-Host "`nenv set:" -ForegroundColor Cyan
$users.Keys | ForEach-Object { "  $_ = $([bool](Get-Item "env:$_" -ErrorAction SilentlyContinue))" }
Write-Host "`nnext (single run): npx promptfoo eval -c promptfoo/promptfooconfig.v2.yaml"
