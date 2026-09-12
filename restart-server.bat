@echo off
rem Windows counterpart of restart-server.sh.
rem
rem UhcGameManager.regenerateTerrain() launches this (via `cmd /c`) with the
rem current server PID as %1, then stops the server. We wait for that process to
rem die, then hand off to start-server.bat.
rem
rem Place this next to the world folder (the server's working directory).
rem Without it, /uhc regen throws:
rem     IllegalStateException: Missing regen restart helper
rem
rem You must also provide start-server.bat in the same directory - the launch
rem command is environment-specific. For a Loom dev server that is typically:
rem     @echo off
rem     cd /d "%~dp0.."
rem     call "%~dp0..\gradlew.bat" runServer   (explicit path: cmd may not search .)
rem and for a real server:
rem     @echo off
rem     cd /d "%~dp0"
rem     java -Xmx4G -jar fabric-server-launch.jar nogui

setlocal
set "DIR=%~dp0"
set "PID_FILE=%DIR%server.pid"
set "LOG_FILE=%DIR%regen-restart.log"
set "OLD_PID=%~1"

echo [%DATE% %TIME%] regen restart helper started (old pid=%OLD_PID%)>>"%LOG_FILE%"

if "%OLD_PID%"=="" (
    rem No PID handed over - fall back to a fixed pause.
    timeout /t 2 /nobreak >nul
    goto :restart
)

:waitloop
tasklist /FI "PID eq %OLD_PID%" 2>nul | find "%OLD_PID%" >nul
if errorlevel 1 goto :restart
timeout /t 1 /nobreak >nul
goto :waitloop

:restart
if exist "%PID_FILE%" del /q "%PID_FILE%"

if not exist "%DIR%start-server.bat" (
    echo [%DATE% %TIME%] ERROR: start-server.bat not found in %DIR%>>"%LOG_FILE%"
    echo The server was stopped for terrain regeneration but cannot be restarted:>>"%LOG_FILE%"
    echo create start-server.bat next to this file.>>"%LOG_FILE%"
    exit /b 1
)

echo [%DATE% %TIME%] starting server>>"%LOG_FILE%"
call "%DIR%start-server.bat" >>"%LOG_FILE%" 2>&1
endlocal
