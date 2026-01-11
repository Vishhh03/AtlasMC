$ErrorActionPreference = "Stop"

$project = "paper"
$version = "1.21.11" # Updated version

Write-Host "Fetching latest build for $project $version..."

# Get latest build
$buildsUrl = "https://api.papermc.io/v2/projects/$project/versions/$version/builds"
$buildsResponse = Invoke-RestMethod -Uri $buildsUrl
$latestBuild = $buildsResponse.builds[-1].build
$jarName = "paper-$version-$latestBuild.jar"
$downloadUrl = "https://api.papermc.io/v2/projects/$project/versions/$version/builds/$latestBuild/downloads/$jarName"

$serverDir = Join-Path $PSScriptRoot "test-server"
if (-not (Test-Path $serverDir)) {
    New-Item -ItemType Directory -Path $serverDir | Out-Null
}

$jarPath = Join-Path $serverDir "server.jar"

# Always download/overwrite for this update
Write-Host "Downloading $jarName to $jarPath..."
Invoke-WebRequest -Uri $downloadUrl -OutFile $jarPath

Write-Host "Accepting EULA..."
Set-Content -Path (Join-Path $serverDir "eula.txt") -Value "eula=true"

Write-Host "Copying plugin..."
# Need to rebuild first to ensure plugin is up to date, but assuming build exists or will be run.
# Ideally we should run the build command here too, but for now we copy what exists.
$pluginSource = Join-Path $PSScriptRoot "build/libs/ProjectAtlas-1.0-SNAPSHOT-all.jar"
$pluginsDir = Join-Path $serverDir "plugins"

if (-not (Test-Path $pluginsDir)) {
    New-Item -ItemType Directory -Path $pluginsDir | Out-Null
}

$pluginDest = Join-Path $pluginsDir "ProjectAtlas.jar"
Copy-Item -Path $pluginSource -Destination $pluginDest -Force

Write-Host "Creating start script..."
$startContent = "java -Xms2G -Xmx2G -jar server.jar nogui`npause"
Set-Content -Path (Join-Path $serverDir "start.bat") -Value $startContent

Write-Host "Setup complete for version $version!"
Write-Host "To run the server, execute: cd test-server; .\start.bat"
