@echo off
rem Uruchamia aplikację z wbudowanym JRE (lub systemową Javą jako fallback).
rem Uruchamiane z własnego folderu, ponieważ config/, data/ i output/ to ścieżki względne.
cd /d "%~dp0"
if exist "jre\bin\javaw.exe" (
    start "" "jre\bin\javaw.exe" -jar document-generator.jar %*
) else (
    start "" javaw -jar document-generator.jar %*
)
