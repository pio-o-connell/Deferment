# Check-Deferment-LocalChanges.ps1
# Purpose: Check whether the Spring configuration changes exist in your LOCAL
# Deferment working copy, and show any changes that have not been committed.
#
# Run this script from the Deferment project root in PowerShell:
#   .\Check-Deferment-LocalChanges.ps1
#
# This script is READ-ONLY. It does not edit files, stage changes, or commit.

$ErrorActionPreference = "Continue"

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " DEFERMENT - LOCAL CHANGE CHECK" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Confirm that the current directory is the Deferment Git repository.
$repoRoot = (Get-Location).Path
$insideRepo = git rev-parse --is-inside-work-tree 2>$null

if ($LASTEXITCODE -ne 0 -or $insideRepo -ne "true") {
    Write-Host ""
    Write-Host "ERROR: This directory is not inside a Git repository." -ForegroundColor Red
    Write-Host "Open PowerShell in your Deferment project folder, then run this script again."
    exit 1
}

Write-Host "`n[1] Repository location" -ForegroundColor Yellow
Write-Host $repoRoot

# 2. Show the current branch and latest local commit.
Write-Host "`n[2] Current branch and latest local commit" -ForegroundColor Yellow
git branch --show-current
git log -1 --oneline

# 3. Show all local changes, including untracked files.
Write-Host "`n[3] Git status (look for modified, staged, or untracked files)" -ForegroundColor Yellow
git status --short --branch

# 4. Summarise unstaged and staged changes.
Write-Host "`n[4] Unstaged change summary" -ForegroundColor Yellow
git diff --stat

Write-Host "`n[5] Staged change summary" -ForegroundColor Yellow
git diff --cached --stat

# 5. Show the actual unstaged and staged changes.
Write-Host "`n[6] Full unstaged diff" -ForegroundColor Yellow
git diff --no-ext-diff -- `
    WEB-INF/web.xml `
    src/main/webapp/WEB-INF/web.xml `
    src/main/webapp/WEB-INF/root-context.xml `
    src/main/resources/repository-context.xml `
    src/main/webapp/WEB-INF/mvc-dispatcher-servlet.xml `
    src/main/webapp/WEB-INF/spring-security.xml `
    .gitignore

Write-Host "`n[7] Full staged diff" -ForegroundColor Yellow
git diff --cached --no-ext-diff -- `
    WEB-INF/web.xml `
    src/main/webapp/WEB-INF/web.xml `
    src/main/webapp/WEB-INF/root-context.xml `
    src/main/resources/repository-context.xml `
    src/main/webapp/WEB-INF/mvc-dispatcher-servlet.xml `
    src/main/webapp/WEB-INF/spring-security.xml `
    .gitignore

# 6. Check whether the expected configuration files exist locally.
Write-Host "`n[8] Configuration file presence" -ForegroundColor Yellow
$files = @(
    "src/main/webapp/WEB-INF/web.xml",
    "src/main/webapp/WEB-INF/root-context.xml",
    "src/main/resources/repository-context.xml",
    "src/main/webapp/WEB-INF/mvc-dispatcher-servlet.xml",
    "src/main/webapp/WEB-INF/spring-security.xml",
    ".gitignore"
)

foreach ($file in $files) {
    if (Test-Path -LiteralPath $file) {
        Write-Host "[FOUND]   $file" -ForegroundColor Green
    }
    else {
        Write-Host "[MISSING] $file" -ForegroundColor Red
    }
}

# 7. Print the relevant configuration files so their current local contents
# can be compared with the intended Root/Child context arrangement.
Write-Host "`n[9] Local configuration contents" -ForegroundColor Yellow
$configFiles = @(
    "src/main/webapp/WEB-INF/web.xml",
    "src/main/webapp/WEB-INF/root-context.xml",
    "src/main/resources/repository-context.xml",
    "src/main/webapp/WEB-INF/mvc-dispatcher-servlet.xml",
    "src/main/webapp/WEB-INF/spring-security.xml"
)

foreach ($file in $configFiles) {
    if (Test-Path -LiteralPath $file) {
        Write-Host "`n---------------- $file ----------------" -ForegroundColor Cyan
        Get-Content -LiteralPath $file
    }
}

Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host " CHECK COMPLETE" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "No files were changed by this script."
Write-Host "Copy the output from sections 3, 4, 5, 6 and 7 back into ChatGPT."
Write-Host ""
