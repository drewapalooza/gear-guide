<# Local bridge used by the assistant. Never prints the authentication token. #>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('status','snapshot','setups','push','select','delete','task','match','preferences')]
    [string]$Action,
    [string]$File,
    [string]$Id,
    [ValidateSet('melee','ranged','magic','hybrid')][string]$Style = 'melee',
    [bool]$AutoSelect = $true,
    [string]$ConnectionFile = (Join-Path $env:USERPROFILE '.runelite\gear-guide\connection.json')
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $ConnectionFile)) {
    throw 'Gear Guide is not running. Start the development client and enable the plugin.'
}
$connection = Get-Content -LiteralPath $ConnectionFile -Raw | ConvertFrom-Json
$baseUri = [Uri]$connection.baseUrl
if ($baseUri.Scheme -ne 'http' -or $baseUri.Host -ne '127.0.0.1' -or $baseUri.AbsolutePath -ne '/' -or $baseUri.Query -or $baseUri.UserInfo) {
    throw 'Invalid connection address. Only the local Gear Guide endpoint is allowed.'
}
$request = @{
    Headers = @{ Authorization = 'Bearer ' + $connection.token }
    TimeoutSec = 15
    MaximumRedirection = 0
}
switch ($Action) {
    'status'   { $request.Uri = "$($connection.baseUrl)/v1/status"; $request.Method = 'Get' }
    'snapshot' { $request.Uri = "$($connection.baseUrl)/v1/snapshot"; $request.Method = 'Get' }
    'setups'   { $request.Uri = "$($connection.baseUrl)/v1/setups"; $request.Method = 'Get' }
    'task' { $request.Uri = "$($connection.baseUrl)/v1/task"; $request.Method = 'Get' }
    'match' { $request.Uri = "$($connection.baseUrl)/v1/task/select"; $request.Method = 'Post' }
    'preferences' {
        $request.Uri = "$($connection.baseUrl)/v1/preferences"; $request.Method = 'Put'
        $request.Body = @{combatStyle=$Style;autoSelect=$AutoSelect} | ConvertTo-Json -Compress
        $request.ContentType = 'application/json'
    }
    'push' {
        if (-not $File) { throw 'Use -File with the setup JSON file.' }
        $request.Uri = "$($connection.baseUrl)/v1/setups"; $request.Method = 'Put'
        $request.Body = [Text.Encoding]::UTF8.GetBytes((Get-Content -LiteralPath $File -Raw))
        $request.ContentType = 'application/json; charset=utf-8'
    }
    'select' {
        $request.Uri = "$($connection.baseUrl)/v1/active"; $request.Method = 'Put'
        $request.Body = @{id = $(if ($Id) { $Id } else { $null })} | ConvertTo-Json -Compress
        $request.ContentType = 'application/json'
    }
    'delete' {
        if ($Id -notmatch '^[a-z0-9][a-z0-9-]{0,63}$') { throw 'Supply a valid setup -Id.' }
        $request.Uri = "$($connection.baseUrl)/v1/setups/$Id"; $request.Method = 'Delete'
    }
}
$result = Invoke-RestMethod @request
$json = ConvertTo-Json -InputObject $result -Depth 30
if ($Action -eq 'snapshot' -and $File) {
    [IO.File]::WriteAllText($ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($File), $json, [Text.UTF8Encoding]::new($false))
    Write-Output "Bank snapshot saved to $File"
} else { Write-Output $json }
