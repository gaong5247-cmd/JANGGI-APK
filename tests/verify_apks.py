"""Check APK native membership, ELF machine/bitness, and 16 KB LOAD alignment."""
import os
import io
import wave
from pathlib import Path
import subprocess
import tempfile
import zipfile

expected = {'arm64-v8a': (2, 183), 'armeabi-v7a': (1, 40), 'x86_64': (2, 62)}
sdk = Path(os.environ['ANDROID_SDK_ROOT'])
ndk = Path(os.environ.get('ANDROID_NDK_HOME', str(sdk / 'ndk/28.2.13676358')))
host = 'windows-x86_64' if os.name == 'nt' else 'linux-x86_64'
readelf = ndk / 'toolchains/llvm/prebuilt' / host / 'bin' / ('llvm-readelf.exe' if os.name == 'nt' else 'llvm-readelf')
for name, abis in [(a, [a]) for a in expected] + [('universal', list(expected))]:
    apk = Path('dist') / ('JanggiLab-' + name + '.apk')
    with zipfile.ZipFile(apk) as z:
        members = [n for n in z.namelist() if n.startswith('lib/')]
        assert set(members) == {'lib/' + a + '/libfairy.so' for a in abis}
        assert 'classes.dex' in z.namelist()
        for sound in ('move','capture','check'):
            with wave.open(io.BytesIO(z.read('res/raw/'+sound+'.wav'))) as pcm:
                assert pcm.getnchannels()==1 and pcm.getsampwidth()==2 and pcm.getnframes()>0
        for abi in abis:
            data = z.read('lib/' + abi + '/libfairy.so')
            for variant in (b'janggitraditional',b'janggimodern',b'jangginopass',b'janggiblitz'):
                assert b'['+variant+b':janggi]' in data, (abi,variant)
            assert b'nnueAlias = janggi' not in data
            assert data[:4] == b'\x7fELF' and data[5] == 1
            assert (data[4], int.from_bytes(data[18:20], 'little')) == expected[abi]
            with tempfile.TemporaryDirectory() as tmp:
                f=Path(tmp)/"engine.so"
                f.write_bytes(data)
                headers = subprocess.check_output([str(readelf), '-lW', str(f)], text=True)
                loads = [line.split() for line in headers.splitlines() if line.strip().startswith('LOAD ')]
                assert loads and all(int(line[-1], 16) >= 16384 for line in loads), headers
    print('PASS', apk, ','.join(abis))
