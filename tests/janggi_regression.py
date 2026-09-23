"""Rule fixtures, score/state separation, and seeded all-legal-move transitions."""
import argparse
import json
import random
import re
from pathlib import Path
from engine_smoke import Uci


def board(fen):
    ranks = fen.split()[0].split('/')
    assert len(ranks) == 10, fen
    result = {}
    for rank, text in zip(range(10, 0, -1), ranks):
        file = 0
        for ch in text:
            if ch.isdigit():
                file += int(ch)
            else:
                result[chr(97 + file) + str(rank)] = ch
                file += 1
        assert file == 9, fen
    assert list(result.values()).count('K') == list(result.values()).count('k') == 1
    return result


def validate(s):
    for field in ('appfen', 'applegal', 'appcheck', 'appresult', 'appreason', 'appbikjang'):
        assert field in s, (field, s)
    board(s['appfen'])
    legal = s['applegal'].split()
    assert len(legal) == len(set(legal))
    assert s['appcheck'] in ('0', '1')
    assert s['appresult'] in ('ongoing', 'win', 'loss', 'draw')
    if s['appresult'] == 'ongoing':
        assert legal and s['appreason'] == 'none', s
    else:
        assert not legal and s['appreason'] != 'none', s
    if s['appreason'] == 'checkmate':
        assert s['appcheck'] == '1' and s['appresult'] == 'loss', s
    return legal


def run(exe, positions):
    e = Uci(exe)
    passed = []
    scores = []
    transitions = captures = 0
    try:
        cases = json.loads(Path(__file__).with_name('janggi_mate_cases.json').read_text())
        for c in cases:
            e.send('setoption name UCI_Variant value ' + c.get('variant', 'janggi'))
            e.send('ucinewgame')
            pos = 'fen ' + c['fen']
            # Assert every fixture history move is playable before applying it.
            for move in c.get('moves', []):
                before = e.state(pos)
                assert move in before['applegal'].split(), (c['name'], move, before)
                pos += (' ' if ' moves ' in pos else ' moves ') + move
            s = e.state(pos)
            legal = validate(s)
            assert s['appcheck'] == str(int(c['expectedCheck'])), (c['name'], s)
            assert s['appresult'] == c['expectedResult'], (c['name'], s)
            assert s['appreason'] == c['expectedReason'], (c['name'], s)
            assert len(legal) >= c['minimumLegalMoves'], (c['name'], s)
            if 'exactLegalMoves' in c:
                assert len(legal) == c['exactLegalMoves'], (c['name'], s)
            if 'expectedBikjang' in c:
                assert s['appbikjang'] == str(int(c['expectedBikjang'])), (c['name'], s)
            for m in c.get('mustContain', []):
                assert m in legal, (c['name'], m, s)
            for m in c.get('mustNotContain', []):
                assert m not in legal, (c['name'], m, s)
            if c.get('searchMate'):
                e.send('go depth 5')
                out = e.until('bestmove ')
                mate = [l for l in out if ' score mate ' in l]
                assert mate, (c['name'], out)
                # Query current position without resending position: search must
                # leave the root and the adjudication history untouched.
                e.send('appstate')
                after = dict((l.split(' ', 1) + [''])[:2] for l in e.until('appdone') if l != 'appdone' and l.startswith('app'))
                assert after == s and after['appresult'] == 'ongoing', (c['name'], after, s)
                scores.append({'case': c['name'], 'score': mate[-1]})
            passed.append(c['name'])

        e.send('setoption name UCI_Variant value janggi')
        rng = random.Random(20260923)
        pos = 'startpos'
        for _ in range(positions):
            s = e.state(pos)
            if s['appresult'] != 'ongoing':
                pos = 'startpos'
                s = e.state(pos)
            legal = validate(s)
            original = board(s['appfen'])
            next_positions = []
            for m in legal:
                src, dst = re.fullmatch(r'([a-i](?:10|[1-9]))([a-i](?:10|[1-9]))', m).groups()
                expected = original.copy()
                if src != dst:
                    captures += dst in expected
                    expected[dst] = expected.pop(src)
                child_pos = pos + (' ' if ' moves ' in pos else ' moves ') + m
                child = e.state(child_pos)
                validate(child)
                assert board(child['appfen']) == expected, (pos, m, child)
                assert child['appfen'].split()[1] != s['appfen'].split()[1]
                next_positions.append(child_pos)
                transitions += 1
            assert e.state(pos) == s, ('parent changed', pos)
            pos = rng.choice(next_positions)
        assert captures > 0
    finally:
        e.close()
    return dict(fixtures=len(passed), passed=passed, randomPositions=positions,
                legalTransitions=transitions, captures=captures, mateScores=scores)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('engine')
    parser.add_argument('--positions', type=int, default=120)
    args = parser.parse_args()
    print(json.dumps(run(args.engine, args.positions), indent=2))
