"""All four embedded Janggi rules, using real UCI processes and legal histories."""
import json, re, sys, subprocess, tempfile
from pathlib import Path
from engine_smoke import Uci
from janggi_regression import validate

IDS=['janggitraditional','janggimodern','jangginopass','janggiblitz']
CYCLE=['b1c3','b10c8','c3b1','c8b10']
FACING='9/4k4/9/9/9/9/9/9/4K4/9 w - - 0 1'
CHECK='9/3k5/9/9/9/9/9/9/r3K4/9 w - - 0 1'

def walk(e,base,moves):
    pos=base
    for m in moves:
        s=e.state(pos)
        assert m in validate(s),(pos,m,s)
        pos+=(' ' if ' moves ' in pos else ' moves ')+m
    return e.state(pos)

def run(exe):
    root=Path(__file__).resolve().parents[1]
    subprocess.run([sys.executable,str(root/'scripts/embed_variants.py'),'--check'],check=True)
    block=(root/'native/Fairy-Stockfish/src/variants.ini').read_text().split('# BEGIN JANGGI LAB VARIANTS',1)[1]
    with tempfile.TemporaryDirectory() as tmp:
        config=Path(tmp)/'janggi.ini';config.write_text(block)
        validation=subprocess.run([str(Path(exe).resolve()),'check',str(config)],capture_output=True,text=True,check=True)
    assert not re.search(r'Invalid|already exists|does not exist|Unable to open',validation.stdout+validation.stderr),validation.stderr
    e=Uci(exe);report={}
    try:
        e.send('uci');uci=e.until('uciok')
        advertised=next(x for x in uci if x.startswith('option name UCI_Variant '))
        fixtures=json.loads(Path(__file__).with_name('janggi_mate_cases.json').read_text())
        for v in IDS:
            assert ' var '+v in advertised
            e.send('stop');e.send('setoption name UCI_Variant value '+v)
            e.send('isready');e.until('readyok');e.send('ucinewgame')
            initial=e.state();legal=validate(initial)
            assert initial['appvariant']==v and initial['appfen']==initial['appstartfen']
            assert len(legal)==(31 if v=='jangginopass' else 32)
            assert ('e2e2' in legal)==(v!='jangginopass')
            e.send('go depth 1');best=e.until('bestmove ')[-1].split()[1]
            assert best in legal
            e.send('appstate');after=dict((x.split(' ',1)+[''])[:2] for x in e.until('appdone') if x!='appdone' and x.startswith('app'))
            assert after==initial,'search must not change adjudication'
            # Geometry/checkmate fixtures shared by every rule (no pass assumptions).
            count=0
            for c in fixtures:
                if c.get('variant') or c.get('moves') or c['expectedReason'] not in ('none','checkmate') or c['name'].startswith(('bikjang','pass_only')):
                    continue
                s=e.state('fen '+c['fen']);validate(s)
                assert s['appcheck']==str(int(c['expectedCheck'])),(v,c['name'],s)
                assert s['appresult']==c['expectedResult'],(v,c['name'],s)
                for m in c.get('mustContain',[]):
                    src,dst=re.fullmatch(r'([a-i](?:10|[1-9]))([a-i](?:10|[1-9]))',m).groups()
                    if src!=dst or v!='jangginopass':assert m in s['applegal'].split(),(v,c['name'],m,s)
                for m in c.get('mustNotContain',[]):assert m not in s['applegal'].split()
                count+=1
            facing=e.state('fen '+FACING)
            assert facing['appbikjang']==str(int(v in ('janggitraditional','jangginopass')))
            assert facing['appcheck']=='0' and facing['appresult']=='ongoing'
            if v!='jangginopass':
                accepted=walk(e,'fen '+FACING,['e2e2'])
                assert accepted['appreason']==('bikjang' if v=='janggitraditional' else 'none')
                double=walk(e,'startpos',['e2e2','e9e9'])
                assert double['appreason']=='double_pass'
                assert double['appresult']==('draw' if v=='janggiblitz' else 'loss')
            else:
                assert 'e2e2' not in facing['applegal'].split()
                e.send('position startpos moves e2e2');e.send('appstate')
                rejected=dict((x.split(' ',1)+[''])[:2] for x in e.until('appdone') if x!='appdone' and x.startswith('app'))
                assert rejected['appfen']==initial['appfen'],'illegal pass changed board'
            # Only-pass positions are stalemate, not checkmate, in no-pass.
            only=e.state('fen 4k4/c7R/9/3R1R3/9/9/9/9/9/3K5 b - - 0 1')
            assert only['appcheck']=='0'
            assert only['appreason']==('stalemate' if v=='jangginopass' else 'none')
            # Walk each repetition; never feed moves after terminal state.
            history=[];terminal=None
            for m in CYCLE*3:
                s=walk(e,'startpos',history+[m]);history.append(m)
                if s['appresult']!='ongoing':terminal=s;break
            assert terminal is not None
            expected={'janggitraditional':(8,'repetition','loss'),'jangginopass':(8,'repetition','loss'),
                      'janggiblitz':(4,'repetition','draw'),'janggimodern':(9,'move_repetition','win')}[v]
            assert (len(history),terminal['appreason'],terminal['appresult'])==expected,(v,len(history),terminal)
            # Perpetual check loses for checker, except blitz's unconditional draw.
            cycle=['e2e3','a2a3','e3e2','a3a2']
            n=1 if v=='janggiblitz' else 3 if v=='janggimodern' else 2
            history_check=[]
            for m in cycle*n:
                perpetual=walk(e,'fen '+CHECK,history_check+[m]);history_check.append(m)
                if perpetual['appresult']!='ongoing':break
            assert perpetual['appreason']==('repetition' if v=='janggiblitz' else 'move_repetition' if v=='janggimodern' else 'perpetual_check')
            assert perpetual['appresult']==('draw' if v=='janggiblitz' else 'win')
            # Alternating two horses avoids the modern single-piece rule.
            long_cycle=['b1c3','b10c8','h1g3','h10g8','c3b1','c8b10','g3h1','g8h10']
            repetitions=1 if v=='janggiblitz' else 3 if v=='janggimodern' else 2
            long_history=[]
            for m in long_cycle*repetitions:
                long_result=walk(e,'startpos',long_history+[m]);long_history.append(m)
                if len(long_history)<len(long_cycle)*repetitions:assert long_result['appresult']=='ongoing',(v,long_history,long_result)
            assert long_result['appreason']=='repetition'
            assert long_result['appresult']==('draw' if v=='janggiblitz' else 'loss')
            # Material counting must flip with the actual pieces, not search scores.
            if v!='jangginopass':
                material=walk(e,'fen 9/3k5/9/9/9/9/R8/9/4K4/9 w - - 0 1',['e2e2','d9d9'])
                assert material['appresult']==('draw' if v=='janggiblitz' else 'win')
            report[v]={'geometryFixtures':count,'startLegalMoves':len(legal),'bestmove':best,
                       'repetitionPly':len(history),'repetitionReason':terminal['appreason'],
                       'pass':v!='jangginopass','bikjang':facing['appbikjang']=='1',
                       'perpetualResult':perpetual['appresult']}
    finally:e.close()
    return report

if __name__=='__main__':print(json.dumps(run(sys.argv[1]),indent=2))
