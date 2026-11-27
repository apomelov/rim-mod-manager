RimWorld Mod Manager
====================

A personal mod manager for RimWorld: sort your mods into folders, mark
translations, keep builds (mod sets) and activate one into the game.

Requirements: Java 21 or newer (https://adoptium.net).

Start:
  Linux, macOS:  ./run.sh
  Windows:       run.cmd (double-click)

It runs on http://127.0.0.1:8765/ and opens the browser. On the first
start it finds the game and mods folders and asks you to confirm them.

Your settings and library are stored in:
  Linux    ~/.config/rimmodmanager
  Windows  %APPDATA%\rimmodmanager
  macOS    ~/Library/Application Support/rimmodmanager

Activating a build rewrites the game's ModsConfig.xml; the previous one is
backed up into the folder above, under backups/.
