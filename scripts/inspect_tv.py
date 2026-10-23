#!/usr/bin/env python3
"""Opt-in, explicitly targeted read-only TV inspection. No discovery/connect/write."""
import argparse,json,os,re,shutil,subprocess

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial',required=True,help='Human-verified ADB serial; never auto-select')
    args=parser.parse_args()
    if not args.serial.strip() or args.serial.startswith('-') or any(c.isspace() for c in args.serial):
        parser.error('Use one explicit serial with no whitespace or leading dash')
    adb=os.environ.get('TVHOME_ADB') or shutil.which('adb')
    if not adb:parser.error('Install official Android Platform Tools and set PATH or TVHOME_ADB')
    def read(*command):
        p=subprocess.run([adb,'-s',args.serial,*command],capture_output=True,text=True,timeout=15)
        if p.returncode:raise RuntimeError('Read-only ADB query failed. Check identity, authorization and connectivity locally; no fallback device was selected.')
        return p.stdout.strip()
    try:
        if read('get-state')!='device':raise RuntimeError('Target is not an authorized online device')
        facts={name:read('shell','getprop',prop) for name,prop in {
            'model':'ro.product.model','brand':'ro.product.brand','android':'ro.build.version.release',
            'sdk':'ro.build.version.sdk','build':'ro.build.version.incremental','abi':'ro.product.cpu.abi'}.items()}
        home=read('shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')
        facts['homeComponent']=next((x for x in reversed(home.splitlines()) if re.fullmatch(r'[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+',x.strip())), 'unresolved; check original system UI')
        web=read('shell','dumpsys','webviewupdate')
        current=re.search(r'Current WebView package[^:]*:\s*\(([^)]+)\)',web)
        facts['webview']=current[1] if current else 'unresolved; inspect via original system settings'
        facts['scope']='read-only properties/HOME/WebView; not proof of TV identity or compatibility; human must compare'
        print(json.dumps(facts,ensure_ascii=False,indent=2))
    except (RuntimeError,subprocess.TimeoutExpired) as error:
        parser.exit(1,str(error)+'\n')

if __name__=='__main__':main()
