param([switch]$Integration, [switch]$Listeners, [switch]$Placeholders, [switch]$Migration, [string]$JavaHome = 'C:\Program Files\Java\jdk-21')
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
$repository = Join-Path $env:USERPROFILE '.m2\repository'
$output = Join-Path $workspace 'target\storage-check'
$patterns = @(
    'org\spigotmc\spigot-api\1.21.11-R0.1-SNAPSHOT\*.jar',
    'com\zaxxer\HikariCP\5.0.1\*.jar',
    'me\clip\placeholderapi\2.10.9\*.jar',
    'org\yaml\snakeyaml\2.2\*.jar',
    'com\google\guava\guava\33.5.0-jre\*.jar',
    'com\google\code\gson\gson\2.13.2\*.jar',
    'net\md-5\bungeecord-chat\1.21-R0.4\*.jar',
    'net\md-5\bungeecord-dialog\1.21-R0.4\*.jar',
    'net\md-5\bungeecord-serializer\1.21-R0.4\*.jar',
    'org\joml\joml\1.10.8\*.jar',
    'org\slf4j\slf4j-api\2.0.0-alpha1\*.jar',
    'org\jetbrains\annotations\24.0.1\*.jar',
    'com\mysql\mysql-connector-j\9.2.0\*.jar',
    'org\xerial\sqlite-jdbc\3.49.1.0\*.jar'
)
if ($Listeners -or $Placeholders) {
    $patterns += @(
        'org\mockito\mockito-core\5.19.0\*.jar',
        'net\bytebuddy\byte-buddy\1.17.6\*.jar',
        'net\bytebuddy\byte-buddy-agent\1.17.6\*.jar',
        'org\objenesis\objenesis\3.3\*.jar'
    )
}
$jars = foreach ($pattern in $patterns) {
    $matches = @(Get-ChildItem (Join-Path $repository $pattern) -ErrorAction Stop | Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' })
    if ($matches.Count -eq 0) { throw "Missing cached dependency: $pattern" }
    $matches.FullName
}
$jars += (Get-ChildItem (Join-Path $workspace 'libs\*.jar')).FullName
New-Item -ItemType Directory -Force -Path (Join-Path $output 'classes') | Out-Null
$classpath = $jars -join ';'
$sourceFiles = @(Get-ChildItem (Join-Path $workspace 'src\main\java') -Recurse -Filter '*.java').FullName
$sourceFiles += (Join-Path $workspace 'src\test\java\com\horizonkk\chuwudai\storage\MySqlRepositoryTest.java')
if ($Listeners) {
    $sourceFiles += (Join-Path $workspace 'src\test\java\com\horizonkk\chuwudai\storage\StorageListenerTest.java')
}
if ($Placeholders) {
    $sourceFiles += (Join-Path $workspace 'src\test\java\com\horizonkk\chuwudai\hook\PlaceholderAPITest.java')
}
if ($Migration) {
    $sourceFiles += (Join-Path $workspace 'src\test\java\com\horizonkk\chuwudai\storage\SqliteMigrationTest.java')
}
$arguments = @('-encoding','UTF-8','--release','21','-classpath',$classpath,'-d',(Join-Path $output 'classes')) + $sourceFiles
$argumentFile = Join-Path $output 'check.args'
[System.IO.File]::WriteAllLines($argumentFile, @($arguments | ForEach-Object { '"' + $_.Replace('\','/') + '"' }), [System.Text.UTF8Encoding]::new($false))
& (Join-Path $JavaHome 'bin\javac.exe') '-J-Duser.language=en' "@$argumentFile"
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
if ($Listeners) {
    $mockAgent = Join-Path $repository 'net\bytebuddy\byte-buddy-agent\1.17.6\byte-buddy-agent-1.17.6.jar'
    & (Join-Path $JavaHome 'bin\java.exe') "-javaagent:$mockAgent" '-cp' ((Join-Path $output 'classes') + ';' + $classpath) 'com.horizonkk.chuwudai.storage.StorageListenerTest'
    if ($LASTEXITCODE -ne 0) { throw 'Storage listener regression tests failed' }
}
if ($Placeholders) {
    $mockAgent = Join-Path $repository 'net\bytebuddy\byte-buddy-agent\1.17.6\byte-buddy-agent-1.17.6.jar'
    & (Join-Path $JavaHome 'bin\java.exe') "-javaagent:$mockAgent" '-cp' ((Join-Path $output 'classes') + ';' + $classpath) 'com.horizonkk.chuwudai.hook.PlaceholderAPITest'
    if ($LASTEXITCODE -ne 0) { throw 'Placeholder snapshot regression tests failed' }
}
if ($Integration) {
    # The test expects an isolated 5.7.44 instance on 127.0.0.1:33317, root with an empty test password.
    & (Join-Path $JavaHome 'bin\java.exe') '-cp' ((Join-Path $output 'classes') + ';' + $classpath) 'com.horizonkk.chuwudai.storage.MySqlRepositoryTest'
    if ($LASTEXITCODE -ne 0) { throw 'Storage integration tests failed' }
}
if ($Migration) {
    & (Join-Path $JavaHome 'bin\java.exe') '-cp' ((Join-Path $output 'classes') + ';' + $classpath) 'com.horizonkk.chuwudai.storage.SqliteMigrationTest'
    if ($LASTEXITCODE -ne 0) { throw 'SQLite migration tests failed' }
}
