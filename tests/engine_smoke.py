"""Real-engine integration checks; no mocked engine or move generator."""
import subprocess,sys,queue,threading,time,json
from pathlib import Path
class Uci:
 def __init__(self,path):
  self.p=subprocess.Popen([str(Path(path).resolve())],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,bufsize=1)
  self.q=queue.Queue()
  def pump():
   for line in self.p.stdout:self.q.put(line.strip())
  threading.Thread(target=pump,daemon=True).start()
  self.send('uci');self.until('uciok');self.send('setoption name UCI_Variant value janggi');self.send('setoption name Use NNUE value false');self.send('isready');self.until('readyok')
 def send(self,line):self.p.stdin.write(line+'\n');self.p.stdin.flush()
 def until(self,prefix,timeout=30):
  end=time.monotonic()+timeout;result=[]
  while time.monotonic()<end:
   line=self.q.get(timeout=max(.01,end-time.monotonic()));result.append(line)
   if line.startswith(prefix):return result
  raise TimeoutError(prefix)
 def state(self,pos='startpos'):
  self.send('position '+pos);self.send('appstate');out=self.until('appdone');return dict(s.split(' ',1) for s in out if ' ' in s and s.startswith('app'))
 def close(self):self.send('quit');self.p.wait(timeout=5)
p=sys.argv[1];a=Uci(p);b=Uci(p);checks=[]
s=a.state();assert len(s['applegal'].split())==32;assert 'e2e2' in s['applegal'].split();checks.append('Initial legal moves: 32 including pass')
for pos,expected in [('startpos',1065277),('fen 1n1kaabn1/cr2N4/5C1c1/p1pNp3p/9/9/P1PbP1P1P/3r1p3/4A4/R1BA1KB1R b - - 0 1',76763),('fen 1Pbcka3/3nNn1c1/N2CaC3/1pB6/9/9/5P3/9/4K4/9 w - - 0 23',151202)]:
 a.send('position '+pos);a.send('go perft 4');out=a.until('Nodes searched:');actual=int(out[-1].split(':')[1]);assert actual==expected,(actual,expected);checks.append(f'Janggi perft depth 4: {actual}')
s=a.state('fen 4k4/c7R/9/3R1R3/9/9/9/9/9/3K5 b - - 0 1');assert s['applegal']=='e10e10';checks.append('Pass-only position (rank 10)')
# Alternating isolated processes must both return legal moves for the same history.
moves=[]
for ply in range(60):
 e=a if ply%2==0 else b;s=e.state('startpos'+(' moves '+' '.join(moves) if moves else ''))
 if s['appresult']!='ongoing':break
 e.send('go movetime 20');best=e.until('bestmove ')[-1].split()[1];assert best in s['applegal'].split(),(ply,best);moves.append(best)
checks.append(f'Two-engine match: {len(moves)} legal plies')
a.state();a.send('setoption name MultiPV value 3');a.send('go infinite');time.sleep(.2);t=time.monotonic();a.send('stop');out=a.until('bestmove ',5);assert time.monotonic()-t<5;assert any('multipv 3' in l for l in out);a.send('isready');a.until('readyok');checks.append('MultiPV 3, stop, and ready synchronization')
a.close();b.close();print(json.dumps({'passed':checks,'moves':moves},indent=2))
