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
  ShellState state=new ShellState();String key=ShellState.webKey(false,"https://ha.example.com");
  state.menu(true);check(state.expanded&&!state.fullscreen&&state.page.equals("home"),"menu overlays same page");
  check(ShellState.reuseWeb(key,key,true),"menu and app pages reuse session");
  state.full(true);check(state.fullscreen&&!state.expanded,"fullscreen closes menu");
  check(state.back(true)==ShellState.Back.EXIT_FULLSCREEN&&!state.fullscreen,"back exits app fullscreen first");
  state.menu(true);check(state.back(true)==ShellState.Back.CLOSE_MENU,"overlay owns back before web focus");
  check(state.back(true)==ShellState.Back.WEB_TOOLS,"web history never owns back");
  check(state.back(false)==ShellState.Back.EXIT,"root back always terminates");
  state.show("apps");check(state.back(false)==ShellState.Back.HOME&&state.page.equals("home"),"child page returns home");
  check(state.back(false)==ShellState.Back.EXIT,"child route reaches exit");
  check(!ShellState.reuseWeb(key,ShellState.webKey(true,""),true),"test page cannot reuse live HA session");
  check(!ShellState.reuseWeb(key,ShellState.webKey(false,"https://other.example.com"),true),"address change replaces session");
  check(!ShellState.reuseWeb(key,key,false),"dead renderer never reused");
  check(ResourcePolicy.sampleSize(7680,4320,1920,1080)==4,"8K photo decoded at bounded dimensions");
  check(ResourcePolicy.sampleSize(1672,941,1920,1080)==1,"current wallpaper not needlessly enlarged");
  check(ResourcePolicy.sampleSize(Integer.MAX_VALUE,Integer.MAX_VALUE,1,1)>0,"sample computation cannot overflow negative");
  check(ResourcePolicy.iconPixels(300)==128&&ResourcePolicy.ICON_CACHE_BYTES==2097152,"bounded icon decode/cache");
  check(ShellState.restoredPage("webtest",true).equals("home"),"safe restart never auto-loads test renderer");
  check(ShellState.restoredPage("apps",true).equals("apps"),"safe restart preserves native app page");
  check(ShellState.restoredPage(null,false).equals("home"),"unknown saved route cannot strand startup");
  System.out.println("Input/URL, shell/back/session and image-memory policy checks passed");
 }
}
