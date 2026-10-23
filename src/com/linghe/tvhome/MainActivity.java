package com.linghe.tvhome;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.text.*;
import java.util.*;

public final class MainActivity extends Activity {
 private static final int PAPER=Color.rgb(238,232,220), INK=Color.rgb(58,50,39), MUTED=Color.rgb(188,175,151), DARK=Color.rgb(36,32,27), BLUE=Color.rgb(180,201,221);
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ArrayDeque<String> events=new ArrayDeque<>();
 private final LinkedHashMap<String,ResolveInfo> launchables=new LinkedHashMap<>();
 private android.content.SharedPreferences prefs;
 private LinearLayout frame,body,nav;
 private TextView clock,diagnosticText,webStatus;
 private WebView web;
 private Bitmap wallpaper;
 private String page="home", configured="";
 private boolean resumed=false,safe=false,dirty=false;
 private long lastLaunch=0;
 private final Runnable tick=new Runnable(){public void run(){if(!resumed)return;if(clock!=null){String value=new SimpleDateFormat("HH:mm  ·  M月d日 EEEE",Locale.CHINA).format(new Date());if(!value.contentEquals(clock.getText()))clock.setText(value);}handler.postDelayed(this,60000-System.currentTimeMillis()%60000);}};

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  prefs=getSharedPreferences("home",MODE_PRIVATE);
  configured=prefs.getString("ha_url","");safe=prefs.getBoolean("web_safe_mode",false)||new File(getFilesDir(),"last-crash.json").exists();
  prefs.edit().putInt("config_schema",1).apply();
  final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
  Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
   try{JSONObject crash=new JSONObject();crash.put("versionCode",BuildInfo.CODE);crash.put("source",BuildInfo.SOURCE);crash.put("time",System.currentTimeMillis());crash.put("exception",error.getClass().getName());JSONArray stack=new JSONArray();for(StackTraceElement item:error.getStackTrace()){if(stack.length()>=16)break;stack.put(item.getClassName()+"."+item.getMethodName()+":"+item.getLineNumber());}crash.put("stack",stack);writeJson("last-crash.json",crash);writeDiagnostics();}catch(Throwable ignored){}
   if(previous!=null)previous.uncaughtException(thread,error);else android.os.Process.killProcess(android.os.Process.myPid());
  });
  getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
  try(InputStream in=getAssets().open("coast-wallpaper.png")){wallpaper=BitmapFactory.decodeStream(in);}catch(IOException ignored){}
  record("create b"+BuildInfo.CODE);show("home");
  if(Intent.ACTION_SEND.equals(getIntent().getAction())){String incoming=getIntent().getStringExtra(Intent.EXTRA_TEXT);if(WebPolicy.valid(incoming))handler.post(()->configureHA(incoming));}
 }
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private GradientDrawable fill(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private android.graphics.drawable.StateListDrawable buttonBackground(){
  android.graphics.drawable.StateListDrawable s=new android.graphics.drawable.StateListDrawable();
  GradientDrawable focused=fill(BLUE,9);focused.setStroke(dp(2),Color.rgb(246,240,228));
  s.addState(new int[]{android.R.attr.state_focused},focused);s.addState(new int[]{android.R.attr.state_pressed},fill(Color.rgb(199,183,155),9));s.addState(new int[]{},fill(Color.rgb(71,63,51),9));return s;
 }
 private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(4),0,dp(4));return t;}
 private Button button(String title,Runnable action){
  Button b=new Button(this);b.setText(title);b.setTextSize(16);b.setAllCaps(false);b.setTextColor(PAPER);b.setBackground(buttonBackground());b.setPadding(dp(18),dp(8),dp(18),dp(8));b.setMinimumHeight(dp(46));b.setMinHeight(dp(46));b.setFocusable(true);b.setTag(title);b.setId(View.generateViewId());
  b.setOnFocusChangeListener((v,focused)->b.setTextColor(focused?INK:PAPER));
  b.setOnClickListener(v->{if(resumed)action.run();});return b;
 }
 private void addButton(LinearLayout parent,String title,Runnable action){Button b=button(title,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(48));p.setMargins(0,dp(5),dp(10),dp(5));parent.addView(b,p);}
 private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
 private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
 private void addNote(String note){body.addView(text(note,15,MUTED));}
 private void disposeWeb(){if(web!=null){WebView old=web;web=null;old.stopLoading();old.setWebViewClient(new WebViewClient());if(old.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)old.getParent()).removeView(old);old.destroy();}}
 private void show(String next){
  disposeWeb();diagnosticText=null;webStatus=null;page=next;
  FrameLayout scene=new FrameLayout(this);scene.setBackgroundColor(DARK);
  String background=prefs.getString("wall","coast");
  if("coast".equals(background)&&wallpaper!=null){ImageView img=new ImageView(this);img.setImageBitmap(wallpaper);img.setScaleType(ImageView.ScaleType.CENTER_CROP);scene.addView(img,new FrameLayout.LayoutParams(-1,-1));}
  else scene.setBackgroundColor("sand".equals(background)?Color.rgb(125,113,93):Color.rgb(61,57,51));
  View shade=new View(this);shade.setBackgroundColor(Color.argb(174,16,15,13));scene.addView(shade,new FrameLayout.LayoutParams(-1,-1));
  frame=column();frame.setPadding(dp(34),dp(20),dp(34),dp(20));scene.addView(frame,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout header=row();header.addView(text("⌂  Home",25,PAPER));clock=text("",18,PAPER);clock.setGravity(Gravity.END);header.addView(clock,new LinearLayout.LayoutParams(0,-2,1));frame.addView(header);
  nav=row();addButton(nav,"家庭主页",()->show("home"));addButton(nav,"已安装应用",()->show("apps"));addButton(nav,"网页入口",()->show("services"));addButton(nav,"外观与连接",()->show("settings"));addButton(nav,"诊断",()->show("diagnostics"));addButton(nav,"退出",()->finish());frame.addView(nav);
  body=column();body.setPadding(0,dp(12),0,0);frame.addView(body,new LinearLayout.LayoutParams(-1,0,1));
  if("home".equals(next))home();else if("apps".equals(next))apps();else if("services".equals(next))services();else if("settings".equals(next))settings();else if("diagnostics".equals(next))diagnostics();else if("web".equals(next))webPage(false);else if("webtest".equals(next))webPage(true);
  setContentView(scene);record("page "+next);handler.removeCallbacks(tick);tick.run();
  nav.getChildAt("apps".equals(next)?1:"services".equals(next)?2:"settings".equals(next)?3:"diagnostics".equals(next)?4:0).requestFocus();
 }
 private void home(){
  if(!configured.isEmpty()&&!safe){webPage(false);return;}
  LinearLayout panel=column();panel.setPadding(dp(25),dp(20),dp(25),dp(20));panel.setBackground(fill(PAPER,12));body.addView(panel,new LinearLayout.LayoutParams(-1,0,1));
  panel.addView(text("Home Assistant",26,INK));
  TextView info=text(safe?"家庭页面已暂停加载\n上次异常记录已保留，可从诊断页检查。":configured.isEmpty()?"连接现有家庭页面，保留你原来的仪表盘。":"家庭页面已配置，按确认进入完整浏览。",19,Color.rgb(119,103,79));panel.addView(info,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout controls=row();addButton(controls,configured.isEmpty()?"连接 HA":"浏览家庭页面",()->{if(configured.isEmpty())configureHA();else if(safe)confirmResume();else show("web");});addButton(controls,"浏览器能力测试",()->show("webtest"));panel.addView(controls);
  addNote("方向键选择 · 确认进入 · 返回逐层退出；系统 Home、音量、信号源按键保留。");
 }
 private void apps(){
  LinkedHashMap<String,ResolveInfo> entries=new LinkedHashMap<>();PackageManager pm=getPackageManager();
  for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
   Intent q=new Intent(Intent.ACTION_MAIN).addCategory(category);
   for(ResolveInfo r:pm.queryIntentActivities(q,0))if(r.activityInfo!=null&&!r.activityInfo.packageName.equals(getPackageName()))entries.putIfAbsent(r.activityInfo.packageName,r);
  }
  ArrayList<ResolveInfo> sorted=new ArrayList<>(entries.values());sorted.sort((a,b)->a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));
  launchables.clear();for(ResolveInfo r:sorted)launchables.put(r.activityInfo.packageName,r);
  body.addView(text("已安装应用  ·  "+sorted.size(),22,PAPER));
  ScrollView scroll=new ScrollView(this);LinearLayout list=column();scroll.addView(list);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  if(sorted.isEmpty())list.addView(text("没有找到可启动的 App；可在原厂桌面继续使用。",18,MUTED));
  for(ResolveInfo r:sorted){String title=r.loadLabel(pm).toString();Button b=button(title,()->launchApp(r));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54));lp.setMargins(0,dp(4),0,dp(4));b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);list.addView(b,lp);}
  addNote("仅列出可启动 App，不读取它们的影片或播放记录。");
 }
 private void launchApp(ResolveInfo r){
  long now=SystemClock.elapsedRealtime();if(now-lastLaunch<800)return;lastLaunch=now;
  try{Intent i=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(new ComponentName(r.activityInfo.packageName,r.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);record("app launch");}catch(RuntimeException e){notice("应用暂时无法打开","请刷新应用列表，或通过原厂桌面打开。错误类型："+e.getClass().getSimpleName());}
 }
 private void services(){
  body.addView(text("网页入口",23,PAPER));addNote("地址保存在本 App 内；使用网页自己的登录，不复制其他 App 的内容。");
  addButton(body,"家庭主页",()->{if(configured.isEmpty())configureHA();else if(safe)confirmResume();else show("web");});
  String link=prefs.getString("service_url","");
  addButton(body,link.isEmpty()?"添加网页服务":prefs.getString("service_name","网页服务"),()->{if(link.isEmpty())configureService();else externalWeb(link);});
  if(!link.isEmpty())addButton(body,"编辑网页服务",this::configureService);
 }
 private void externalWeb(String url){
  try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));record("external browser");}catch(ActivityNotFoundException e){notice("未找到浏览器","请先在电视上安装浏览器；此入口没有修改系统。 ");}
 }
 private void configureService(){
  LinearLayout content=column();content.setPadding(dp(20),dp(10),dp(20),0);EditText name=new EditText(this);name.setSingleLine();name.setHint("入口名称");name.setText(prefs.getString("service_name",""));content.addView(name);
  EditText url=urlField(prefs.getString("service_url",""));content.addView(url);
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("网页服务").setView(content).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{String address=url.getText().toString().trim();if(!WebPolicy.valid(address)){url.setError("使用 HTTPS；家庭局域网 IP 可用 HTTP");return;}prefs.edit().putString("service_url",address).putString("service_name",name.getText().toString().trim().isEmpty()?"网页服务":name.getText().toString().trim()).apply();dialog.dismiss();show("services");}));dialog.show();
 }
 private EditText urlField(String current){EditText field=new EditText(this);field.setSingleLine();field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);field.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_FORCE_ASCII|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI|android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING|android.view.inputmethod.EditorInfo.IME_ACTION_DONE);field.setHint("https://home.example.com/dashboard/home");field.setText(current);return field;}
 private void configureHA(){configureHA(configured);}
 private void configureHA(String initial){
  EditText field=urlField(initial);LinearLayout content=column();content.setPadding(dp(20),dp(10),dp(20),0);content.addView(field);
  addButton(content,"粘贴网址",()->{android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);android.content.ClipData data=cb.getPrimaryClip();if(data!=null&&data.getItemCount()>0){String value=data.getItemAt(0).coerceToText(this).toString().trim();if(WebPolicy.valid(value)){field.setText(value);field.setSelection(value.length());}else field.setError("剪贴板中不是有效的网页地址");}});
  TextView note=text("保留现有 HA 页面。密码只在 HA 原页面中输入。",15,MUTED);content.addView(note);
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("家庭页面地址").setView(content).setNegativeButton("取消",null).setPositiveButton("保存并浏览",null).create();
  dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{String address=field.getText().toString().trim();if(!WebPolicy.valid(address)){field.setError("请用 HTTPS，或 http://局域网IP:端口/路径；不要在地址中放账号密码");return;}configured=address;prefs.edit().putString("ha_url",address).apply();dialog.dismiss();if(safe)confirmResume();else show("web");}));dialog.show();
 }
 private void settings(){
  body.addView(text("外观与连接",23,PAPER));LinearLayout walls=row();addButton(walls,"海岸壁纸",()->setWall("coast"));addButton(walls,"沙色",()->setWall("sand"));addButton(walls,"岩灰",()->setWall("stone"));body.addView(walls);
  LinearLayout connect=row();addButton(connect,"家庭页面地址",this::configureHA);addButton(connect,"网页服务",this::configureService);body.addView(connect);
  LinearLayout system=row();addButton(system,"原厂桌面",this::stockHome);addButton(system,"电视系统设置",()->systemSettings(Settings.ACTION_SETTINGS));addButton(system,"信号源设置",()->systemSettings("android.settings.TV_INPUT_SETTINGS"));body.addView(system);
  addNote("这只是普通电视 App：不接管默认 HOME、不注册开机启动、不拦截音量或信号源键。");
  addButton(body,"扩展与 Agent",()->notice("扩展预留","尚未启动任何控制服务。后续可按明确授权接入桌面动作；ADB 与其他整机控制能力分开验证。"));
 }
 private void setWall(String name){prefs.edit().putString("wall",name).apply();show("settings");}
 private void stockHome(){try{startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(RuntimeException e){notice("请使用系统 Home 键","未能打开系统桌面；没有更改默认桌面。");}}
 private void systemSettings(String action){try{startActivity(new Intent(action));}catch(RuntimeException e){notice("请用电视原设置","这个固件没有提供对应的标准设置入口；可用电视遥控器打开。 ");}}
 private void webPage(boolean demo){
  LinearLayout tools=row();addButton(tools,"返回桌面",()->show("home"));addButton(tools,"网页后退",()->{if(web!=null&&web.canGoBack())web.goBack();});addButton(tools,"刷新",()->{if(web!=null)web.reload();});body.addView(tools);
  webStatus=text("正在准备浏览器…",14,MUTED);body.addView(webStatus);
  try{
   web=new WebView(this);final WebView current=web;
   WebView.setWebContentsDebuggingEnabled(false);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSupportMultipleWindows(false);settings.setMediaPlaybackRequiresUserGesture(true);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setTextZoom(100);
   CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
   web.setBackgroundColor(PAPER);web.setFocusable(true);web.setFocusableInTouchMode(true);
   web.setDownloadListener((url,agent,disposition,type,size)->notice("不自动下载","请在电视原浏览器中处理下载。"));
   web.setWebViewClient(new WebViewClient(){
    @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){String url=req.getUrl().toString();if("about:blank".equals(url))return false;if(!demo&&WebPolicy.sameOrigin(configured,url))return false;status("已阻止离开配置的网页来源；外链可使用独立浏览器。");return true;}
    @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest req){String url=req.getUrl().toString();if("http".equals(req.getUrl().getScheme())&&!WebPolicy.sameOrigin(configured,url))return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));return null;}
    @Override public void onPageFinished(WebView v,String url){if(web!=current)return;status("加载结束 · 真实登录和遥控器操作仍需体验确认");CookieManager.getInstance().flush();record("web finished");}
    @Override public void onReceivedError(WebView v,WebResourceRequest req,WebResourceError error){if(req.isForMainFrame()&&web==current){status("页面暂时无法加载；可返回桌面或刷新。错误码 "+error.getErrorCode());record("web error "+error.getErrorCode());}}
    @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail){if(web==current){prefs.edit().putBoolean("web_safe_mode",true).apply();safe=true;record("web renderer stopped");show("home");}return true;}
   });
   body.addView(web,new LinearLayout.LayoutParams(-1,0,1));
   if(demo)web.loadDataWithBaseURL("https://preview.invalid/","<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width'><style>body{font:24px sans-serif;padding:24px;background:#f2efe8;color:#494034}button{font:24px sans-serif;padding:18px;background:#d6c4a4;border:0;border-radius:8px}button:focus{outline:4px solid #6e7f91}p{font-size:18px}</style><h2>WebView 能力测试</h2><p>这是本地测试页，没有连接 HA 或家庭设备。</p><button onclick='this.textContent=\"网页按钮已响应\"'>用方向键聚焦，再按确认</button><p id='cap'></p><script>document.getElementById('cap').textContent='JavaScript: 可用；Shadow DOM: '+(typeof HTMLElement.prototype.attachShadow==='function'?'可用':'不可用');</script>","text/html","UTF-8",null);
   else if(WebPolicy.valid(configured))web.loadUrl(configured);else status("还未配置有效地址。");
  }catch(RuntimeException e){disposeWeb();safe=true;prefs.edit().putBoolean("web_safe_mode",true).apply();status("浏览器暂不可用，桌面仍可使用。错误类型 "+e.getClass().getSimpleName());record("web creation failed");}
 }
 private void status(String message){runOnUiThread(()->{if(webStatus!=null)webStatus.setText(message);});}
 private void confirmResume(){new AlertDialog.Builder(this).setTitle("恢复家庭页面？").setMessage("上次异常记录会保留在本机。恢复后才重新加载已配置的网页。").setNegativeButton("暂不加载",null).setPositiveButton("恢复浏览",(d,w)->{safe=false;prefs.edit().putBoolean("web_safe_mode",false).apply();File crash=new File(getFilesDir(),"last-crash.json");if(crash.exists()){File previous=new File(getFilesDir(),"previous-crash.json");crash.renameTo(previous);}show("web");}).show();}
 private void diagnostics(){
  body.addView(text("本机诊断",23,PAPER));
  String webVersion="不可用";try{PackageInfo p=WebView.getCurrentWebViewPackage();if(p!=null)webVersion=p.packageName+" "+p.versionName;}catch(RuntimeException ignored){}
  body.addView(text("版本 "+BuildInfo.VERSION+" ("+BuildInfo.CODE+") · "+BuildInfo.SOURCE.substring(0,Math.min(10,BuildInfo.SOURCE.length()))+"\nAndroid "+Build.VERSION.RELEASE+" / SDK "+Build.VERSION.SDK_INT+"\nWebView "+webVersion,16,PAPER));
  ScrollView scroll=new ScrollView(this);diagnosticText=text(eventText(),14,MUTED);scroll.addView(diagnosticText);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  addNote("只记录按键编号、生命周期与版本；不记录网页、输入文本、账号或令牌。");
  if((getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0)addButton(body,"异常恢复演练",()->new AlertDialog.Builder(this).setTitle("仅测试 Home 预览的异常恢复").setMessage("只让这个测试 App 退出；保留配置和原厂桌面。下次打开将暂停家庭页面并显示恢复入口。").setNegativeButton("取消",null).setPositiveButton("确认演练",(d,w)->handler.post(()->{throw new IllegalStateException("Controlled TVHome recovery probe");})).show());
  if(safe)addButton(body,"恢复已配置的家庭页面",()->{if(configured.isEmpty())configureHA();else confirmResume();});
 }
 private void notice(String title,String message){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("知道了",null).show();}
 private void record(String value){String line=SystemClock.elapsedRealtime()+" "+value;if(events.size()>=40)events.removeFirst();events.addLast(line);dirty=true;if(diagnosticText!=null)diagnosticText.setText(eventText());}
 private String eventText(){StringBuilder b=new StringBuilder();for(String e:events)b.append(e).append('\n');return b.toString();}
 private void writeJson(String name,JSONObject data)throws IOException{AtomicFile f=new AtomicFile(new File(getFilesDir(),name));FileOutputStream out=null;try{out=f.startWrite();out.write(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));f.finishWrite(out);}catch(IOException error){if(out!=null)f.failWrite(out);throw error;}}
 private void writeDiagnostics(){try{JSONObject o=new JSONObject();o.put("versionCode",BuildInfo.CODE);o.put("source",BuildInfo.SOURCE);o.put("page",page);o.put("webSafeMode",safe);o.put("events",new JSONArray(events));writeJson("diagnostics.json",o);dirty=false;}catch(Exception ignored){}}
 @Override public boolean dispatchKeyEvent(KeyEvent event){
  int code=event.getKeyCode();if(event.getAction()==KeyEvent.ACTION_DOWN&&KeyPolicy.diagnosticKey(code))record("key "+code+" repeat "+event.getRepeatCount()+" device "+event.getDeviceId());
  if(KeyPolicy.systemOwned(code))return super.dispatchKeyEvent(event);
  if(event.getAction()==KeyEvent.ACTION_DOWN&&KeyPolicy.suppressRepeat(code,event.getRepeatCount()))return true;
  return super.dispatchKeyEvent(event);
 }
 @Override public void onBackPressed(){if(web!=null&&web.canGoBack()){web.goBack();return;}if(!"home".equals(page)){show("home");return;}finish();}
 @Override protected void onResume(){super.onResume();resumed=true;if(web!=null){web.onResume();web.resumeTimers();}record("resume");handler.removeCallbacks(tick);tick.run();}
 @Override protected void onPause(){resumed=false;handler.removeCallbacks(tick);if(web!=null){web.onPause();web.pauseTimers();}record("pause");writeDiagnostics();super.onPause();}
 @Override protected void onStop(){record("stop");if(web!=null)web.stopLoading();writeDiagnostics();super.onStop();}
 @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);disposeWeb();record("destroy");if(dirty)writeDiagnostics();super.onDestroy();}
}
