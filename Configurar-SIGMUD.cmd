@echo off
setlocal DisableDelayedExpansion
pushd "%~dp0" || exit /b 1
set "sigmudJava=java.exe"
if defined JAVA_HOME set "sigmudJava=%JAVA_HOME%\bin\java.exe"
if not exist "target\test-classes\pe\gob\sigmud\util\ConfigurarLocal.class" goto compilar
set "sigmudCP=target\test-classes;target\sigmud\WEB-INF\classes;target\sigmud\WEB-INF\lib\*"
if /I "%~1"=="importar-entorno" goto importar
if /I "%~1"=="cuentas" goto cuentas
if /I "%~1"=="comprobar" goto diagnostico
if not "%~1"=="" goto uso
"%sigmudJava%" -cp "%sigmudCP%" pe.gob.sigmud.util.ConfigurarLocal "%CD%"
goto fin
:importar
"%sigmudJava%" -cp "%sigmudCP%" pe.gob.sigmud.util.ConfigurarLocal "%CD%" --desde-entorno
goto fin
:diagnostico
"%sigmudJava%" -cp "%sigmudCP%" pe.gob.sigmud.util.ComprobarConexionBD
goto fin
:cuentas
"%sigmudJava%" -cp "%sigmudCP%" pe.gob.sigmud.util.InicializarDemo --todas
goto fin
:compilar
echo Primero ejecuta Clean and Build en NetBeans o mvn verify con el JDK 17.
goto fallo
:uso
echo Uso: Configurar-SIGMUD.cmd [comprobar, cuentas o importar-entorno]
:fallo
pause
popd
exit /b 1
:fin
set "sigmudResultado=%errorlevel%"
pause
popd
exit /b %sigmudResultado%
