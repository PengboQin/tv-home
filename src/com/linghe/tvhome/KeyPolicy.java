package com.linghe.tvhome;
final class KeyPolicy {
 static boolean systemOwned(int code) {
  return code==3 || code==24 || code==25 || code==26 || code==164 || code==177 || code==178 || (code>=243 && code<=246);
 }
 static boolean suppressRepeat(int code,int repeat) {return repeat>0 && (code==23 || code==66 || code==160 || code==96 || code==62);}
 static boolean diagnosticKey(int code) {return (code>=19 && code<=23)||code==4||code==66||systemOwned(code);}
}
