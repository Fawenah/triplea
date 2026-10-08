# Launch the locally built desktop client using JDK 25 without changing system Java.
[CmdletBinding()]
param([switch]$CheckLaunch)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$portableJdk = Get-ChildItem (Join-Path $projectRoot 'build/tools/jdk25') -Directory -ErrorAction SilentlyContinue |
    Select-Object -First 1
$taskJavaHome = if ($portableJdk) { $portableJdk.FullName } else { $env:JAVA_HOME }
if (-not $taskJavaHome -or -not (Test-Path (Join-Path $taskJavaHome 'bin/java.exe'))) {
    throw 'JDK 25 is required. Set JAVA_HOME to a JDK 25 installation before running this script.'
}
$javaVersion = & (Join-Path $taskJavaHome 'bin/java.exe') --version | Out-String
if ($javaVersion -notmatch '(?m)^(openjdk|java) 25(?:\.|\s)') {
    throw 'The selected Java installation is not JDK 25. Set JAVA_HOME to JDK 25.'
}
$previousJavaHome = $env:JAVA_HOME
$previousGradleHome = $env:GRADLE_USER_HOME
try {
    $env:JAVA_HOME = $taskJavaHome
    $env:GRADLE_USER_HOME = Join-Path $projectRoot 'build/gradle-home'
    $application = Join-Path $projectRoot 'game-app/game-headed/build/install/game-headed/bin/game-headed.bat'
    if (-not (Test-Path -LiteralPath $application)) {
        Push-Location $projectRoot
        try {
            & ./gradlew.bat :game-headed:installDist --console=plain
            if ($LASTEXITCODE -ne 0) { throw 'The desktop build failed.' }
        } finally { Pop-Location }
    }
    # Java expands the wildcard itself, avoiding cmd.exe's command-length limit
    # when the Gradle batch launcher lists every dependency by its full path.
    $applicationLib = Join-Path $projectRoot 'game-app/game-headed/build/install/game-headed/lib'
    $javaArguments = @('--class-path', (Join-Path $applicationLib '*'), 'org.triplea.game.client.HeadedGameRunner')
    if ($CheckLaunch) {
        $javaArguments = @('--dry-run') + $javaArguments
    } else {
        Write-Host 'In TripleA settings, set the maps folder to:'
        Write-Host (Join-Path $projectRoot 'custom_maps')
        Write-Host 'Then select Global 1940 MOD ECR v3.0.'
    }
    & (Join-Path $taskJavaHome 'bin/java.exe') @javaArguments
    if ($LASTEXITCODE -ne 0) { throw "The desktop client exited with code $LASTEXITCODE." }
    if ($CheckLaunch) { Write-Host 'Java launcher and desktop classpath validated.' }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:GRADLE_USER_HOME = $previousGradleHome
}
