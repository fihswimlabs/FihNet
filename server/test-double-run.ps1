# Starts the FihNet server and runs the ApiTest suite twice in a row against the
# same data file, to prove the suite is safe to re-run against a populated server.
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
Start-Process -FilePath "java" -ArgumentList "-cp","server/out","FihNetServer","--port","8765","--data","server/test-data.json" -NoNewWindow -RedirectStandardOutput "server/server.log" -RedirectStandardError "server/server.err"
Start-Sleep -Seconds 3
java -cp server/out ApiTest 2>&1 | Out-File "$env:TEMP/runA.txt" -Encoding utf8
java -cp server/out ApiTest 2>&1 | Out-File "$env:TEMP/runB.txt" -Encoding utf8
Get-CimInstance Win32_Process -Filter "Name=`"java.exe`"" | Where-Object { $_.CommandLine -like "*FihNetServer*" } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
Write-Host "double run complete"
