"""Generate original deterministic PCM effects (GPL-3.0-or-later)."""
from pathlib import Path
import math, random, struct, wave
out=Path(__file__).resolve().parents[1]/'app/res/raw'
out.mkdir(parents=True,exist_ok=True)
rate=22050
for name,duration in [('move',.10),('capture',.19),('check',.44)]:
    rng=random.Random(17)
    samples=[]
    for i in range(int(rate*duration)):
        t=i/rate
        if name=='check':
            local=t if t<.20 else t-.22
            value=0 if local<0 else .45*math.sin(2*math.pi*(660 if t<.20 else 880)*local)*math.exp(-12*local)
        else:
            freq=900 if name=='move' else 350
            value=(.32*math.sin(2*math.pi*freq*t)+.28*rng.uniform(-1,1))*math.exp(-(45 if name=='move' else 25)*t)
        samples.append(struct.pack('<h',int(32767*value*min(t/.002,1))))
    with wave.open(str(out/(name+'.wav')),'wb') as f:
        f.setparams((1,2,rate,0,'NONE','not compressed'));f.writeframes(b''.join(samples))
