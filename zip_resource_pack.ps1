$sourceDir = "resource-pack"
$zipFile = "test-server\resource-pack.zip"

if (Test-Path $zipFile) { Remove-Item $zipFile }

# Ensure we are zipping the contents of resource-pack folder directly, not the folder itself
Compress-Archive -Path "$sourceDir\*" -DestinationPath $zipFile -Force

Write-Host "Resource pack zipped to $zipFile"
