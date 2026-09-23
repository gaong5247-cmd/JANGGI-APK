#!/usr/bin/env python3
"""Compile Java once; sign per-ABI and universal APKs with the same local key."""
import hashlib
import os
import pathlib
import shutil
import subprocess
import sys
import zipfile

P = pathlib.Path(__file__).resolve().parents[1]
B = P / 'build'
D = P / 'dist'
D.mkdir(exist_ok=True)
sdk = pathlib.Path(os.environ['ANDROID_SDK_ROOT'])
java = pathlib.Path(os.environ['JAVA_HOME']) / 'bin'
bt = sdk / 'build-tools' / '35.0.0'
jar = sdk / 'platforms' / 'android-35' / 'android.jar'
abis = sys.argv[1:] or ['arm64-v8a', 'armeabi-v7a', 'x86_64']

def run(args):
    subprocess.run([str(x) for x in args], check=True)

for d in ['classes', 'dex']:
    shutil.rmtree(B / d, ignore_errors=True)
    (B / d).mkdir(parents=True)
run([java / 'javac', '--release', '8', '-encoding', 'UTF-8', '-classpath', jar,
     '-d', B / 'classes', *sorted((P / 'app/src').rglob('*.java'))])
run([bt / 'aapt2', 'compile', '--dir', P / 'app/res', '-o', B / 'resources.zip'])
run([bt / 'aapt2', 'link', '-o', B / 'base.apk', '-I', jar, '--manifest',
     P / 'app/AndroidManifest.xml', '-A', P / 'app/assets', B / 'resources.zip'])
run([bt / 'd8', '--lib', jar, '--min-api', '26', '--output', B / 'dex',
     *sorted((B / 'classes').rglob('*.class'))])
key = B / 'local-signing.p12'
if not key.exists():
    run([java / 'keytool', '-genkeypair', '-keystore', key, '-storepass', 'localbuild',
         '-alias', 'janggilab', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
         '-dname', 'CN=Janggi Lab Local Build'])
outputs = []
for name, members in [(a, [a]) for a in abis] + [('universal', abis)]:
    unsigned = B / ('unsigned-' + name + '.apk')
    aligned = B / ('aligned-' + name + '.apk')
    output = D / ('JanggiLab-' + name + '.apk')
    shutil.copy2(B / 'base.apk', unsigned)
    with zipfile.ZipFile(unsigned, 'a', zipfile.ZIP_DEFLATED) as z:
        for f in sorted((B / 'dex').glob('*.dex')):
            z.write(f, f.name)
        for abi in members:
            f = B / 'lib' / abi / 'libfairy.so'
            z.write(f, 'lib/' + abi + '/libfairy.so')
    run([bt / 'zipalign', '-f', '-P', '16', '4', unsigned, aligned])
    run([bt / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:localbuild', '--out', output, aligned])
    run([bt / 'apksigner', 'verify', '--verbose', output])
    run([bt / 'zipalign', '-c', '-P', '16', '4', output])
    outputs.append(output)
(D / 'APK-SHA256.txt').write_text(''.join(hashlib.sha256(f.read_bytes()).hexdigest() + '  ' + f.name + '\n' for f in outputs))
print('\n'.join(map(str, outputs)))
