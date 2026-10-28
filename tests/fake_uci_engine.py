#!/usr/bin/env python3
"""Deterministic fake engine for Janggi Lab's crash/invalid-move regression tests.

Search movetime 777: exit without bestmove (native crash simulation)
Search movetime 779: emit an invalid coordinate
Ordinary searches: return a legal-looking Janggi pass move
"""
import os
import sys
import time

net = False
variant = "janggitraditional"

for line in sys.stdin:
    line=line.strip()
    if line=="uci":
        print("id name Janggi Lab Fault Injector")
        print("option name UCI_Variant type combo default janggitraditional var janggitraditional var janggimodern var jangginopass var janggiblitz")
        print("option name Hash type spin default 16 min 1 max 128")
        print("option name Threads type spin default 1 min 1 max 4")
        print("option name MultiPV type spin default 1 min 1 max 3")
        print("option name Skill Level type spin default 20 min 0 max 20")
        print("option name Use NNUE type check default false")
        print("option name EvalFile type string default <empty>")
        print("uciok",flush=True)
    elif line=="isready":
        print("readyok",flush=True)
    elif line.startswith("setoption name UCI_Variant value "):
        variant=line.split(" value ",1)[1]
    elif line=="setoption name Use NNUE value true":
        net=True
    elif line=="setoption name Use NNUE value false":
        net=False
    elif line.startswith("go "):
        if "movetime 777" in line:
            os._exit(23)
        if "movetime 779" in line:
            print("bestmove z1z2",flush=True)
            continue
        if net:
            print("info string NNUE evaluation using dummy.nnue enabled")
        print("info depth 1 score cp 30 pv e2e2")
        print("bestmove e2e2",flush=True)
    elif line=="eval":
        if net:
            print("info string NNUE evaluation using dummy.nnue enabled")
        print("Final evaluation: 0.30",flush=True)
    elif line=="stop":
        # Search is synchronous and already returned bestmove in this mock.
        pass
    elif line=="quit":
        break
