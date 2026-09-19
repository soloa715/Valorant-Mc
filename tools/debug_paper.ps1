$h = @{ "User-Agent" = "ValorantMC/1.0" }
$builds = Invoke-RestMethod -Uri "https://fill.papermc.io/v3/projects/paper/versions/1.21.4/builds" -Headers $h
$latest = $builds[$builds.Count - 1]
Write-Host "Build ID:" $latest.id
Write-Host "Downloads JSON:" ($latest.downloads | ConvertTo-Json -Depth 5)
