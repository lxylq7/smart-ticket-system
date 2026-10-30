$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    & mvn -B -ntp clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Maven verification failed.' }
    [xml]$report = Get-Content -LiteralPath 'target/surefire-reports/TEST-com.example.ticket.ticket.TicketApiIntegrationTest.xml' -Raw
    $classpath = ($report.testsuite.properties.property | Where-Object { $_.name -eq 'java.class.path' }).value
    if (-not $classpath) { throw 'Test runtime classpath not found.' }
    & java '-Dfile.encoding=UTF-8' --class-path $classpath tools/MySqlHttpSmoke.java
    if ($LASTEXITCODE -ne 0) { throw 'Real MySQL HTTP smoke test failed. See target/mysql-http-smoke-report.txt.' }
} finally {
    Pop-Location
}
