$serverDir = "run\server"
if (-not (Test-Path $serverDir)) { New-Item -ItemType Directory -Path $serverDir -Force }

$paperJar = "$serverDir\paper-1.21.4.jar"
if (-not (Test-Path $paperJar) -or (Get-Item $paperJar).Length -lt 1000000) {
    Write-Host "Downloading Paper 1.21.4 server..."
    $headers = @{ "User-Agent" = "ValorantMC/1.0 (dev-launcher)" }
    $api = "https://fill.papermc.io/v3/projects/paper/versions/1.21.4/builds"
    $builds = Invoke-RestMethod -Uri $api -Headers $headers
    $latest = $builds[$builds.Count - 1]
    $downloadUrl = $latest.downloads.'server:default'.url
    $buildId = $latest.id
    Write-Host "Downloading Paper build $buildId from $downloadUrl..."
    Invoke-WebRequest -Uri $downloadUrl -OutFile $paperJar -Headers $headers
    Write-Host "Paper server saved to $paperJar"
} else {
    Write-Host "Paper 1.21.4 server already present."
}
