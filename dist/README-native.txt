RimWorld Mod Manager
====================

A personal mod manager for RimWorld: sort your mods into folders, mark
translations, keep builds (mod sets) and activate one into the game.
Java is included; nothing else needs to be installed.

Start:
  Windows:  RimWorldModManager\RimWorldModManager.exe
            Windows may warn "Windows protected your PC" because the app is
            not signed: click "More info", then "Run anyway".
            A console window opens with the address; close it to stop the app.
  macOS:    RimWorldModManager.app (first time: right-click > Open, because
            the app is not signed)
  Linux:    RimWorldModManager/bin/RimWorldModManager

It runs on http://127.0.0.1:8765/ and opens the browser. On the first
start it finds the game and mods folders and asks you to confirm them.

Your settings and library are stored in:
  Windows  %APPDATA%\rimmodmanager
  macOS    ~/Library/Application Support/rimmodmanager
  Linux    ~/.config/rimmodmanager

Activating a build rewrites the game's ModsConfig.xml; the previous one is
backed up into the folder above, under backups/.
