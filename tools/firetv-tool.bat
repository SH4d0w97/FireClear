@echo off
setlocal EnableExtensions
cd /d "%~dp0"
set "FALLBACK=n"
set "LIST=packages.txt"
where adb >nul 2>&1 || (echo adb not found. See docs\GUIDE.md & pause & exit /b 1)

set "TVIP="
set /p TVIP=Fire TV IP address (e.g. 192.168.1.50): 
set TVIP | findstr /r /x "TVIP=[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*" >nul || (echo That does not look like an IP address. & pause & exit /b 1)
set "DEV=%TVIP%:5555"
adb connect %DEV%
echo If the TV asks "Allow USB debugging?" tick Always allow and press OK, then run this tool again.

:menu
echo.
echo  1) Remove ads only (safest)
echo  2) Full debloat: ads, telemetry, junk (reversible)
echo  3) Full debloat, harder: also remove what can't be disabled
echo  4) Ad-blocking DNS ON
echo  5) Ad-blocking DNS OFF
echo  6) Undo debloat (bring everything back)
echo  7) Install FireClear APK
echo  8) Make the Home button open FireClear (disables Amazon home)
echo  9) Back to Amazon home
echo  0) Quit
set "C="
set /p C=Choose: 
if "%C%"=="1" (set "FALLBACK=n" & set "LIST=packages-ads.txt" & goto debloat)
if "%C%"=="2" (set "FALLBACK=n" & set "LIST=packages.txt" & goto debloat)
if "%C%"=="3" (set "FALLBACK=y" & set "LIST=packages.txt" & goto debloat)
if "%C%"=="4" goto dnson
if "%C%"=="5" goto dnsoff
if "%C%"=="6" goto undo
if "%C%"=="7" goto install
if "%C%"=="8" goto home
if "%C%"=="9" goto stock
if "%C%"=="0" goto quit
goto menu

:debloat
adb -s %DEV% shell pm list packages > backup-before.txt
echo Saved your package list to backup-before.txt
for /f "usebackq eol=# tokens=*" %%p in ("%LIST%") do call :off "%%p"
echo Done. Reboot the stick for the full effect.
goto menu

:undo
for /f "usebackq eol=# tokens=*" %%p in ("packages.txt") do call :on "%%p"
for /f "usebackq eol=# tokens=*" %%p in ("packages-ads.txt") do call :on "%%p"
goto menu

:dnson
set "TVH=dns.adguard-dns.com"
set /p TVH=DNS hostname, or press Enter for the default: 
set TVH | findstr /r /x "TVH=[A-Za-z0-9][A-Za-z0-9-]*\.[A-Za-z0-9.-]*[A-Za-z0-9]" >nul
if errorlevel 1 goto badhost
adb -s %DEV% shell settings put global private_dns_mode hostname
adb -s %DEV% shell settings put global private_dns_specifier %TVH%
echo Testing internet, please wait...
timeout /t 6 /nobreak >nul
adb -s %DEV% shell ping -c 2 example.com | findstr /c:"2 received" >nul
if errorlevel 1 goto dnsfail
echo OK: ad-blocking DNS is on. Reboot the stick for the full effect.
goto menu
:dnsfail
echo No internet with this DNS, switching it off again.
goto dnsoff
:badhost
echo Invalid hostname.
goto menu
:dnsoff
adb -s %DEV% shell settings put global private_dns_mode off
adb -s %DEV% shell settings delete global private_dns_specifier >nul 2>&1
echo Private DNS is off.
goto menu

:install
set "F="
set /p F=Path to the FireClear apk (you can drag the file here): 
adb -s %DEV% install -r "%F:"=%"
goto menu

:home
adb -s %DEV% shell pm list packages app.fireclear.launcher | findstr /c:"app.fireclear.launcher" >nul
if errorlevel 1 goto nolauncher
adb -s %DEV% shell pm disable-user --user 0 com.amazon.tv.launcher
adb -s %DEV% shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
echo If that was refused, use a Home-button remapper app instead. See docs\GUIDE.md
goto menu
:nolauncher
echo Install FireClear first, option 7.
goto menu

:stock
adb -s %DEV% shell pm enable --user 0 com.amazon.tv.launcher
goto menu

:quit
adb disconnect %DEV% >nul 2>&1
echo Now turn ADB debugging and Apps from Unknown Sources OFF on the Fire TV.
exit /b

:off
echo %1| findstr /r /x "\"[A-Za-z][A-Za-z0-9_]*\.[A-Za-z0-9_.]*\"" >nul
if errorlevel 1 (echo [bad]   invalid line in package list & exit /b)
adb -s %DEV% shell pm disable-user --user 0 %~1 2>nul | findstr /i "disabled" >nul
if not errorlevel 1 (echo [off]   %~1& exit /b)
if "%FALLBACK%"=="y" (
  adb -s %DEV% shell pm uninstall -k --user 0 %~1 2>nul | findstr /i "Success" >nul
  if not errorlevel 1 (echo [gone]  %~1& exit /b)
)
echo [skip]  %~1
exit /b

:on
echo %1| findstr /r /x "\"[A-Za-z][A-Za-z0-9_]*\.[A-Za-z0-9_.]*\"" >nul
if errorlevel 1 (echo [bad]   invalid line in package list & exit /b)
adb -s %DEV% shell pm enable --user 0 %~1 >nul 2>&1
adb -s %DEV% shell cmd package install-existing %~1 >nul 2>&1
echo [back]  %~1
exit /b
