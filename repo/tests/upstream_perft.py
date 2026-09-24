"""Run the unchanged upstream largeboard perft cases without Tcl/expect."""
import json
from pathlib import Path
import shlex
import sys
from engine_smoke import Uci

source = Path(__file__).resolve().parents[1] / 'native/Fairy-Stockfish/tests/perft.sh'
section = source.read_text().split('if [[ $1 == "all" ||  $1 == "largeboard" ]]; then')[1].split('\nfi')[0]
e = Uci(sys.argv[1])
results = []
try:
    for line in section.splitlines():
        t = shlex.split(line, comments=True)
        if not t or t[:2] != ['expect', 'perft.exp']:
            continue
        variant, pos, depth, expected = t[2:6]
        e.send('setoption name UCI_Variant value ' + variant)
        e.send('position ' + pos)
        e.send('go perft ' + depth)
        out = e.until('Nodes searched:', 120)
        actual = int(out[-1].split(':')[1])
        assert actual == int(expected), (variant, pos, actual, expected)
        results.append(dict(variant=variant, depth=int(depth), nodes=actual))
finally:
    e.close()
print(json.dumps(dict(passed=len(results), cases=results), indent=2))
