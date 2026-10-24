#!/usr/bin/env python3
"""Host-only checks; never connects to a TV."""
import os,shutil,subprocess,json
from pathlib import Path
import xml.etree.ElementTree as ET
R=Path(__file__).resolve().parents[1]
def check(ok,reason):
    if not ok:raise SystemExit(reason)
A='{http://schemas.android.com/apk/res/android}'
m=ET.parse(R/'AndroidManifest.xml').getroot();v=json.loads((R/'version.json').read_text())
check(m.attrib['package']==v['applicationId']=='com.pengboqin.tvhome.preview','Package mismatch')
check(m.attrib[A+'versionCode']==str(v['versionCode']) and m.attrib[A+'versionName']==v['versionName'],'Version mismatch')
check([p.attrib[A+'name'] for p in m.findall('uses-permission')]==['android.permission.INTERNET'],'Unexpected permission')
check(not m.findall('.//service') and not m.findall('.//receiver'),'Unexpected service or receiver')
check(not any(c.attrib.get(A+'name')=='android.intent.category.HOME' for c in m.findall('.//category')),'Must not own system HOME')
check(m.find('application').attrib[A+'debuggable']=='false','Public preview must not be debuggable')
jdk=os.environ.get('TVHOME_JDK') or os.environ.get('JAVA_HOME')
javac=str(Path(jdk)/'bin/javac') if jdk else shutil.which('javac')
java=str(Path(jdk)/'bin/java') if jdk else shutil.which('java')
check(javac and java,'Set TVHOME_JDK or JAVA_HOME to JDK17')
build=R/'build/test-classes';build.mkdir(parents=True,exist_ok=True)
sources=[R/'src/com/linghe/tvhome/KeyPolicy.java',R/'src/com/linghe/tvhome/WebPolicy.java',R/'src/com/linghe/tvhome/ShellState.java',R/'src/com/linghe/tvhome/ResourcePolicy.java',R/'tests/PolicyTest.java']
subprocess.run([javac,'--release','8','-encoding','UTF-8','-d',str(build),*map(str,sources)],check=True)
subprocess.run([java,'-cp',str(build),'com.linghe.tvhome.PolicyTest'],check=True)
print('Public manifest/identity/version checks passed; no device operation')
