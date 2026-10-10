@echo off
cd /d "%~dp0"
if not exist .venv (
  echo First start: setting things up, this takes a minute...
  python -m venv .venv
  .venv\Scripts\python -m pip install -q -r requirements.txt
)
start "" http://localhost:5000
.venv\Scripts\python app.py
pause
