package com.linghe.tvhome;
public final class PolicyTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
  for(int key:new int[]{3,24,25,26,164,177,178,243,244,245,246}){check(KeyPolicy.systemOwned(key),"system key "+key);check(!KeyPolicy.suppressRepeat(key,8),"never suppress system repeats");}
  check(KeyPolicy.suppressRepeat(96,3),"gamepad confirmation repeat");check(KeyPolicy.suppressRepeat(23,2),"prevent repeated activation");check(!KeyPolicy.suppressRepeat(20,6),"allow held Dpad scrolling");
  check(WebPolicy.valid("https://home.example.com/family-control/home"),"https");check(WebPolicy.valid("http://192.168.50.10:8123/family-control/home"),"private http");
  for(String u:new String[]{"http://example.com","http://1.1.1.1","file:///etc/passwd","javascript:alert(1)","https://user:pass@example.com","http://192.168.999.1","https://example.com:99999"})check(!WebPolicy.valid(u),"reject "+u);
  check(WebPolicy.sameOrigin("https://ha.example.com/home","https://ha.example.com:443/auth/authorize?code=example"),"same origin auth");
  check(!WebPolicy.sameOrigin("https://ha.example.com/home","https://other.example.com"),"external origin");check(!WebPolicy.sameOrigin("https://ha.example.com/home","http://ha.example.com"),"no downgrade");
  System.out.println("Input and WebView policy checks passed");
 }
}
