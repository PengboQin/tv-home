package com.linghe.tvhome;

/** Navigation owned by this app, deliberately independent of web history. */
final class ShellState {
 enum Back { EXIT_FULLSCREEN, CLOSE_MENU, WEB_TOOLS, HOME, EXIT }
 String page="home";
 boolean expanded=false, fullscreen=false;
 void show(String next) { page="web".equals(next)?"home":next;expanded=false;fullscreen=false; }
 void menu(boolean open) { expanded=open; if(open)fullscreen=false; }
 void full(boolean on) { fullscreen=on; if(on)expanded=false; }
 Back back(boolean webFocused) {
  if(fullscreen){fullscreen=false;return Back.EXIT_FULLSCREEN;}
  if(expanded){expanded=false;return Back.CLOSE_MENU;}
  if(webFocused)return Back.WEB_TOOLS;
  if(!"home".equals(page)){show("home");return Back.HOME;}
  return Back.EXIT;
 }
 static String restoredPage(String page,boolean safe){
  if(safe&&"webtest".equals(page))return "home";
  for(String allowed:new String[]{"home","apps","services","settings","diagnostics","webtest"})if(allowed.equals(page))return page;
  return "home";
 }
 static String webKey(boolean demo,String address){return demo?"local-test":"ha:"+address;}
 static boolean reuseWeb(String current,String wanted,boolean alive){return alive&&wanted.equals(current);}
}
