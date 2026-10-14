@echo off
REM yi-mark 启动脚本 (Windows)
REM 用法: yi-mark.bat [参数...]

set SCRIPT_DIR=%~dp0
set JAR_FILE=%SCRIPT_DIR%yi-mark.jar
set LIB_DIR=%SCRIPT_DIR%lib\

REM 检查 JAR 文件
if not exist "%JAR_FILE%" (
    echo 错误: 找不到 yi-mark.jar
    exit /b 1
)

REM 构建类路径
set CLASSPATH=%JAR_FILE%
for %%f in (%LIB_DIR%*.jar) do (
    set CLASSPATH=%CLASSPATH%;%%f
)

REM JavaFX 模块路径
set MODULE_PATH=
for %%f in (%LIB_DIR%javafx-*.jar) do (
    if "%MODULE_PATH%"=="" (
        set MODULE_PATH=%%f
    ) else (
        set MODULE_PATH=%MODULE_PATH%;%%f
    )
)

REM JVM 选项
set JAVA_OPTS=-Xmx512m

REM 如果有模块路径，添加 JavaFX 模块
if not "%MODULE_PATH%"=="" (
    set JAVA_OPTS=%JAVA_OPTS% --add-modules=javafx.controls,javafx.graphics,javafx.base -p %MODULE_PATH%
)

REM 启动应用
java %JAVA_OPTS% -cp "%CLASSPATH%" com.yimark.Main %*
