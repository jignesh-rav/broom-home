@echo off
rem Starts Broom Console. Needs Python 3: https://www.python.org/downloads/ (or: winget install Python.Python.3.12)
where py >nul 2>nul && (py -3 "%~dp0broom-console.py" %*) || (python "%~dp0broom-console.py" %*)
pause
