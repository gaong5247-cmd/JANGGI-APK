"""Check APK native membership, ELF machine/bitness, and 16 KB LOAD alignment."""
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

expected = {'arm64-v8a': (2, 183), 'armeabi-v7a': (1, 40), 'x86_64': (2, 62)}
sdk = Path(os.environ['ANDROID_SDK_ROOT'])
ndk = Path(os.environ.get('ANDROID_NDK_HOME', str(sdk / 'ndk/28.2.13676358')))
readelf = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf'
for name, abis in [(a, [a]) for a in expected] + [('universal', list(expected))]:
    apk = Path('dist') / ('JanggiLab-' + name + '.apk')
    with zipfile.ZipFile(apk) as z:
        members = [n for n in z.namelist() if n.startswith('lib/')]
        assert set(members) == {'lib/' + a + '/libfairy.so' for a in abis}
        assert 'classes.dex' in z.namelist()
        for abi in abis:
            data = z.read('lib/' + abi + '/libfairy.so')
            assert data[:4] == b'\x7fELF' and data[5] == 1
            assert (data[4], int.from_bytes(data[18:20], 'little')) == expected[abi]
            with tempfile.NamedTemporaryFile() as f:
                f.write(data)
                f.flush()
                headers = subprocess.check_output([str(readelf), '-lW', f.name], text=True)
                loads = [line.split() for line in headers.splitlines() if line.strip().startswith('LOAD ')]
                assert loads and all(int(line[-1], 16) >= 16384 for line in loads), headers
    print('PASS', apk, ','.join(abis))
