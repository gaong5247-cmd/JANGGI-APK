#!/usr/bin/env python3
"""Package Java sources and prebuilt lib/<ABI>/libfairy.so into a signed APK."""
import os, pathlib, subprocess, zipfile
P=pathlib.Path(__file__).resolve().parents[1]; B=P/'build'
sdk=pathlib.Path(os.environ['ANDROID_SDK_ROOT']); java=pathlib.Path(os.environ['JAVA_HOME'])/'bin'
bt=next((sdk/'build-tools').rglob('aapt2')).parent
jar=next((sdk/'platforms'/'android-35').rglob('android.jar'))
def run(args):subprocess.run([str(x) for x in args],check=True)
for d in ['classes','dex']: (B/d).mkdir(parents=True,exist_ok=True)
run([java/'javac','--release','8','-encoding','UTF-8','-classpath',jar,'-d',B/'classes',*sorted((P/'app/src').rglob('*.java'))])
run([bt/'aapt2','compile','--dir',P/'app/res','-o',B/'resources.zip'])
run([bt/'aapt2','link','-o',B/'base.apk','-I',jar,'--manifest',P/'app/AndroidManifest.xml','-A',P/'app/assets',B/'resources.zip'])
run([bt/'d8','--lib',jar,'--min-api','26','--output',B/'dex',*sorted((B/'classes').rglob('*.class'))])
with zipfile.ZipFile(B/'base.apk','a',zipfile.ZIP_DEFLATED) as z:
 for f in sorted((B/'dex').glob('*.dex')):z.write(f,f.name)
 for f in sorted((B/'lib').rglob('*.so')):z.write(f,str(f.relative_to(B)))
key=B/'local-signing.p12'
if not key.exists():run([java/'keytool','-genkeypair','-keystore',key,'-storepass','localbuild','-alias','janggilab','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Janggi Lab Local Build'])
run([bt/'zipalign','-f','-P','16','4',B/'base.apk',B/'aligned.apk'])
run([bt/'apksigner','sign','--ks',key,'--ks-pass','pass:localbuild','--out',P/'JanggiLab.apk',B/'aligned.apk'])
run([bt/'apksigner','verify','--verbose',P/'JanggiLab.apk'])
print(P/'JanggiLab.apk')
