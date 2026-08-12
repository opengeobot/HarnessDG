$env:JAVA_HOME = "D:\tools\jdks\temurin-21.0.9"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location d:\workspace\opengeobot\modelscope\HarnessDG
# clean 防止 IDE 后台编译器写入 target/classes（不带 -parameters）后被 Maven 增量编译误判为最新
mvn -pl modules/app -am clean test
exit $LASTEXITCODE
