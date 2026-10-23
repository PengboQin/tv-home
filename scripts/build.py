#!/usr/bin/env python3
"""Build a small universal DEX TV APK with existing official tools; no Gradle/NDK."""
import hashlib,json,os,re,secrets,shutil,subprocess,zipfile
from pathlib import Path
import xml.etree.ElementTree as ET
R=Path(__file__).resolve().parents[1];REPO=R
sdk_path=os.environ.get('TVHOME_ANDROID_SDK') or os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
if not sdk_path: raise SystemExit('Set TVHOME_ANDROID_SDK or ANDROID_HOME to your installed Android SDK')
SDK=Path(sdk_path).expanduser()
jdk_path=os.environ.get('TVHOME_JDK') or os.environ.get('JAVA_HOME')
if not jdk_path: raise SystemExit('Set TVHOME_JDK or JAVA_HOME to a JDK 17 installation')
JDK=Path(jdk_path).expanduser()
TOOLS=SDK/'build-tools/36.0.0';ANDROID=SDK/'platforms/android-36/android.jar'
ENV=os.environ.copy();ENV['JAVA_HOME']=str(JDK);ENV['PATH']=str(JDK/'bin')+os.pathsep+ENV.get('PATH','')
def run(args,capture=False):
 return subprocess.run(list(map(str,args)),cwd=R,env=ENV,check=True,text=True,stdout=subprocess.PIPE if capture else None,stderr=subprocess.PIPE if capture else None)
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
version=json.loads((R/'version.json').read_text());code=version['versionCode'];name=version['versionName'];package=version['applicationId']
manifest=ET.parse(R/'AndroidManifest.xml').getroot();A='{http://schemas.android.com/apk/res/android}'
assert package=='com.pengboqin.tvhome.preview' and manifest.attrib['package']==package
assert manifest.attrib[A+'versionCode']==str(code) and manifest.attrib[A+'versionName']==name
permissions=[x.attrib[A+'name'] for x in manifest.findall('uses-permission')]
assert permissions==['android.permission.INTERNET']
assert manifest.find('application').attrib[A+'debuggable']=='false'
assert not manifest.findall('.//service') and not manifest.findall('.//receiver')
assert 'android.intent.category.HOME' not in (R/'AndroidManifest.xml').read_text()
assert not run(['git','-C',REPO,'status','--porcelain'],True).stdout.strip(),'Commit source first; refuse an untraceable build'
commit=run(['git','-C',REPO,'rev-parse','HEAD'],True).stdout.strip()
existing=sorted(int(p.stem) for p in (R/'releases').glob('[0-9][0-9][0-9][0-9].json'))
if (existing and code<=max(existing)) or (R/'artifacts'/f'tvhome-{name}-b{code}.apk').exists():raise SystemExit('Immutable candidate/version already exists; bump versionCode before building')
for tool in [JDK/'bin/javac',JDK/'bin/java',JDK/'bin/keytool',TOOLS/'aapt2',TOOLS/'d8',TOOLS/'zipalign',TOOLS/'apksigner',ANDROID]:
 if not tool.exists():raise SystemExit('Missing official tool or SDK platform: '+str(tool))
B=R/'build';B.mkdir(exist_ok=True)
for directory in ['classes','generated','dex','assets','test-classes']:
 p=B/directory
 if p.exists():shutil.rmtree(p)
 p.mkdir()
run([JDK/'bin/javac','--release','8','-encoding','UTF-8','-d',B/'test-classes',R/'src/com/linghe/tvhome/KeyPolicy.java',R/'src/com/linghe/tvhome/WebPolicy.java',R/'tests/PolicyTest.java'])
run([JDK/'bin/java','-cp',B/'test-classes','com.linghe.tvhome.PolicyTest'])
shutil.copy2(R/'assets/coast-wallpaper.png',B/'assets/coast-wallpaper.png')
build_info=B/'generated/com/linghe/tvhome/BuildInfo.java';build_info.parent.mkdir(parents=True,exist_ok=True)
build_info.write_text('package com.linghe.tvhome; final class BuildInfo {static final int CODE='+str(code)+';static final String VERSION='+json.dumps(name)+';static final String SOURCE='+json.dumps(commit)+';}')
run([TOOLS/'aapt2','compile','--dir',R/'res','-o',B/'resources.zip'])
run([TOOLS/'aapt2','link','-o',B/'resources.apk','-I',ANDROID,'--manifest',R/'AndroidManifest.xml','--java',B/'generated','-A',B/'assets',B/'resources.zip'])
sources=list((R/'src').rglob('*.java'))+list((B/'generated').rglob('*.java'))
run([JDK/'bin/javac','--release','8','-encoding','UTF-8','-classpath',ANDROID,'-d',B/'classes',*sources])
with zipfile.ZipFile(B/'classes.jar','w') as jar:
 for p in (B/'classes').rglob('*.class'):jar.write(p,str(p.relative_to(B/'classes')))
run([TOOLS/'d8','--min-api','26','--lib',ANDROID,'--output',B/'dex',B/'classes.jar'])
shutil.copy2(B/'resources.apk',B/'unsigned.apk')
with zipfile.ZipFile(B/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as apk:
 for p in (B/'dex').glob('*.dex'):apk.write(p,p.name)
run([TOOLS/'zipalign','-f','-P','16','4',B/'unsigned.apk',B/'aligned.apk'])
keys=R/'.private';keys.mkdir(exist_ok=True,mode=0o700);keys.chmod(0o700)
password=keys/'storepass';store=keys/'tvhome-dev.jks'
if store.exists() and not password.exists():raise SystemExit('Signing password missing; do not regenerate identity')
if not password.exists():password.write_text(secrets.token_urlsafe(36));password.chmod(0o600)
if not store.exists():
 run([JDK/'bin/keytool','-genkeypair','-noprompt','-keystore',store,'-storepass:file',password,'-keypass:file',password,'-alias','tvhome-local','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=TV Home Community Preview'],True);store.chmod(0o600)
artifacts=R/'artifacts';artifacts.mkdir(exist_ok=True);candidate=artifacts/f'tvhome-{name}-b{code}.apk'
if candidate.exists():raise SystemExit('Candidate already exists. Bump versionCode before another build.')
run([TOOLS/'apksigner','sign','--ks',store,'--ks-key-alias','tvhome-local','--ks-pass','file:'+str(password),'--out',candidate,B/'aligned.apk'])
verified=run([TOOLS/'apksigner','verify','--verbose','--print-certs',candidate],True).stdout
certificate=re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)',verified)
assert certificate,'Cannot verify signer'
run([TOOLS/'zipalign','-c','-P','16','4',candidate])
with zipfile.ZipFile(candidate) as apk:assert not any(p.startswith('lib/') for p in apk.namelist()),'Universal Java only'
meta={'applicationId':package,'versionName':name,'versionCode':code,'sourceCommit':commit,'apk':candidate.name,'apkSHA256':sha(candidate),'certificateSHA256':certificate[1],'abi':'universal DEX; no native libraries','minSdk':26,'targetSdk':30,'permissions':permissions,'homeCategory':False,'bootReceiver':False,'inputService':False,'sourceClean':True,'signing':'stable local development identity; not vendor/system key','hostTests':'input ownership and URL origin checks passed','installation':'not attempted'}
(R/'releases'/f'{code:04d}.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'candidate':str(candidate),'versionCode':code,'source':commit[:12],'sha256':meta['apkSHA256'],'signerVerified':True},ensure_ascii=False))

# Verified signed candidate and release JSON are retained; full APK intermediates are reproducible.
for transient in ['resources.apk','unsigned.apk','aligned.apk']:
 (B/transient).unlink(missing_ok=True)
shutil.rmtree(B/'assets')
