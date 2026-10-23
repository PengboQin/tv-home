package com.linghe.tvhome;
import java.net.URI;
final class WebPolicy {
 static boolean valid(String address) {
  if(address==null || address.length()>2048)return false;
  try {URI u=new URI(address);String host=u.getHost(),scheme=u.getScheme();
   if(host==null||u.getRawUserInfo()!=null||u.getPort()>65535||u.getPort()==0)return false;
   if("https".equalsIgnoreCase(scheme))return true;
   return "http".equalsIgnoreCase(scheme)&&privateIPv4(host);
  }catch(Exception e){return false;}
 }
 private static boolean privateIPv4(String h) {
  String[] p=h.split("\\.");if(p.length!=4)return false;
  int[] n=new int[4];try{for(int i=0;i<4;i++){if(!p[i].matches("[0-9]{1,3}"))return false;n[i]=Integer.parseInt(p[i]);if(n[i]>255)return false;}}catch(Exception e){return false;}
  return n[0]==10 || (n[0]==172&&n[1]>=16&&n[1]<=31) || (n[0]==192&&n[1]==168);
 }
 static boolean sameOrigin(String configured,String candidate) {
  if(!valid(configured)||!valid(candidate))return false;
  try {URI a=new URI(configured),b=new URI(candidate);return a.getScheme().equalsIgnoreCase(b.getScheme())&&a.getHost().equalsIgnoreCase(b.getHost())&&port(a)==port(b);}catch(Exception e){return false;}
 }
 static int port(URI u){return u.getPort()==-1?("https".equalsIgnoreCase(u.getScheme())?443:80):u.getPort();}
}
