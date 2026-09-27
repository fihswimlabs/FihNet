<#
FihNet dev helper.
Pretends to be a second FihNet player so a single Minecraft client can be tested.

Example:
  powershell -ExecutionPolicy Bypass -File server/fihnet-dev.ps1 -User Bob -Friend Alex -Watch
#>
param(
    [string]$Base = "http://127.0.0.1:8765",
    [string]$User = "Bob",
    [string]$Password = "secret456",
    [string]$Friend = "Alex",
    [string]$Message = "Hello from the dev helper",
    [string]$ServerName = "Bobs World",
    [string]$ServerAddress = "play.example.net:25565",
    [int]$Players = 3,
    [switch]$Watch,
    [int]$Seconds = 90
);

$ErrorActionPreference = "Continue"

function Post-Json([string]$Path, [string]$Body, [string]$Token) {
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    try {
        return Invoke-RestMethod -Method Post -Uri "$Base$Path" -ContentType "application/json" -Body $Body -Headers $headers
    } catch {
        return $null
    }
}

$body = "{`"username`":`"$User`",`"password`":`"$Password`"}"
$session = Post-Json "/api/auth/login" $body $null
if (-not $session) {
    $session = Post-Json "/api/auth/register" $body $null
}
if (-not $session) {
    Write-Host "Could not sign in as $User. Is the server running at $Base ?"
    exit 1
}
$token = $session.token
Write-Host "Signed in as $($session.username)"

$friend = Post-Json "/api/friends" "{`"username`":`"$Friend`"}" $token
if ($friend) {
    Write-Host "Now friends with $Friend"
} else {
    Write-Host "Friend step skipped (already friends, or $Friend does not exist yet)"
}

$presence = "{`"inGame`":true,`"serverAddress`":`"$ServerAddress`",`"serverName`":`"$ServerName`",`"playerCount`":$Players}"
Post-Json "/api/presence" $presence $token | Out-Null
Write-Host "Presence set: in game at $ServerName ($ServerAddress)"

$sent = Post-Json "/api/chat" "{`"to`":`"$Friend`",`"message`":`"$Message`"}" $token
if ($sent) {
    Write-Host "Sent to $Friend : $Message"
} else {
    Write-Host "Chat failed - $Friend must be on the friend list"
}

if (-not $Watch) {
    Write-Host "Done. Add -Watch to keep listening for replies."
    exit 0
}

Write-Host "Listening for incoming messages for $Seconds seconds..."
$since = 0
$deadline = (Get-Date).AddSeconds($Seconds)
while ((Get-Date) -lt $deadline) {
    try {
        $events = Invoke-RestMethod -Uri "$Base/api/events?since=$since" -Headers @{ "Authorization" = "Bearer $token" }
        if ($events.next) { $since = $events.next }
        foreach ($e in $events.events) {
            if ($e.type -eq "chat") {
                Write-Host "  <- $($e.from): $($e.message)"
            } elseif ($e.type -eq "presence") {
                Write-Host "  <- presence: $($e.username) online=$($e.online)"
            }
        }
    } catch {
        Start-Sleep -Seconds 2
    }
}
Write-Host "Stopped listening."
