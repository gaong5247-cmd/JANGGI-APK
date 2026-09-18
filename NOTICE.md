장기 연구실 1.0.0

Copyright (C) 2026 Janggi Lab contributors.
This program is free software under GPL-3.0-or-later, without warranty.

Contains modified Fairy-Stockfish, derived from Stockfish and Glaurung.
Upstream commit: 226c7f18c854372d5612be2a7d7f14449ae5a239
Copyright holders and contributors: native/Fairy-Stockfish/AUTHORS
License: LICENSE and native/Fairy-Stockfish/Copying.txt

Modification: src/uci.cpp adds the `appstate` command for the Android GUI.
It reports the current position, legal moves, terminal result and check state.
Game rules and search algorithms are otherwise unchanged.

The complete corresponding source is provided in the accompanying
JanggiLab-source.zip, including all app source, modified engine source,
license text, and the scripts required to produce the APK from those sources.
The standard Android SDK/NDK and JDK build tools are not included.

This is an independent GUI, not an official Fairy-Stockfish application.
