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
import android.util.LruCache;
import org.json.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
 private static final int PAPER=Color.rgb(238,232,220), INK=Color.rgb(58,50,39), MUTED=Color.rgb(188,175,151), DARK=Color.rgb(36,32,27), BLUE=Color.rgb(180,201,221);
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ShellState shell=new ShellState();
 private final ArrayDeque<String> events=new ArrayDeque<>();
 private final ExecutorService images=Executors.newSingleThreadExecutor();
 private final LruCache<String,Bitmap> icons=new LruCache<String,Bitmap>(ResourcePolicy.ICON_CACHE_BYTES){@Override protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}};
 private android.content.SharedPreferences prefs;
 private FrameLayout scene,contentViewport,pageHost,webPane,webSlot;
 private LinearLayout header,nav,body,webTools;
 private View scrim;
 private ScrollView navScroll;
 private ImageView backgroundImage;
 private TextView clock,date,diagnosticText,webStatus,webModeLabel;
 private Button webEnter,webBack,floatingExit;
 private LinearLayout navToggle;
 private final LinkedHashMap<String,LinearLayout> navItems=new LinkedHashMap<>();
 private volatile WebView web;
 private volatile int webGeneration=0;
 private volatile boolean destroyed=false;
 private Bitmap wallpaper;
 private Bundle restoredWeb;
 private String restoredWebKey="",webKey="",configured="",page="home",lastLaunched="";
 private boolean resumed=false,safe=false,dirty=false,webPaused=true,wallLoading=false,returningFromApp=false;
 private int webInstances=0,webLoads=0;
 private long lastLaunch=0;
 private GridView appList;
 private int appArtHeight,appCardHeight;
 private final ArrayList<ResolveInfo> appEntries=new ArrayList<>();
 private final HashSet<String> pendingIcons=new HashSet<>();
 private final ColorMatrixColorFilter iconTint=neutralIconFilter();
 private Thread.UncaughtExceptionHandler crashHandler;
 private static Thread.UncaughtExceptionHandler platformCrashHandler;
 private static boolean crashPlatformCaptured=false;
 private View menuReturnFocus,fullReturnFocus;
 private final Runnable tick=new Runnable(){public void run(){if(!resumed||destroyed)return;if(clock!=null){String value=new SimpleDateFormat("HH:mm",Locale.CHINA).format(new Date());if(!value.contentEquals(clock.getText()))clock.setText(value);if(date!=null)date.setText(new SimpleDateFormat("M月d日 EEEE",Locale.CHINA).format(new Date()));}handler.postDelayed(this,60000-System.currentTimeMillis()%60000);}};

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  prefs=getSharedPreferences("home",MODE_PRIVATE);
  configured=prefs.getString("ha_url","");
  lastLaunched=prefs.getString("last_app","");
  safe=prefs.getBoolean("web_safe_mode",false)||new File(getFilesDir(),"last-crash.json").exists()||(state!=null&&state.getBoolean("web_safe",false));
  prefs.edit().putInt("config_schema",1).apply();
  synchronized(MainActivity.class){if(!crashPlatformCaptured){platformCrashHandler=Thread.getDefaultUncaughtExceptionHandler();crashPlatformCaptured=true;}}
  crashHandler=(thread,error)->{
   try{JSONObject crash=new JSONObject();crash.put("versionCode",BuildInfo.CODE);crash.put("source",BuildInfo.SOURCE);crash.put("time",System.currentTimeMillis());crash.put("exception",error.getClass().getName());JSONArray stack=new JSONArray();for(StackTraceElement item:error.getStackTrace()){if(stack.length()>=16)break;stack.put(item.getClassName()+"."+item.getMethodName()+":"+item.getLineNumber());}crash.put("stack",stack);writeJson("last-crash.json",crash);writeDiagnostics();}catch(Throwable ignored){}
   if(platformCrashHandler!=null)platformCrashHandler.uncaughtException(thread,error);else android.os.Process.killProcess(android.os.Process.myPid());
  };Thread.setDefaultUncaughtExceptionHandler(crashHandler);
  getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
  if(state!=null){restoredWeb=state.getBundle("web_state");restoredWebKey=state.getString("web_key","");}
  buildShell();record("create b"+BuildInfo.CODE);
  String restoredPage=state==null?"home":state.getString("page","home");
  restoredPage=ShellState.restoredPage(restoredPage,safe);
  show(restoredPage);
  if(Intent.ACTION_SEND.equals(getIntent().getAction())){String incoming=getIntent().getStringExtra(Intent.EXTRA_TEXT);if(WebPolicy.valid(incoming))handler.post(()->{if(!destroyed)configureHA(incoming);});}
 }
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private GradientDrawable fill(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private android.graphics.drawable.StateListDrawable stateSurface(int normal,int selected){
  android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
  states.addState(new int[]{-android.R.attr.state_enabled},fill(Color.argb(12,235,235,235),12));
  GradientDrawable focus=fill(BLUE,12);focus.setStroke(dp(2),PAPER);states.addState(new int[]{android.R.attr.state_focused},focus);
  states.addState(new int[]{android.R.attr.state_pressed},fill(Color.rgb(164,183,205),12));states.addState(new int[]{android.R.attr.state_selected},fill(selected,12));states.addState(new int[]{},fill(normal,12));return states;
 }
 private android.graphics.drawable.StateListDrawable buttonBackground(){return stateSurface(Color.argb(25,240,239,233),Color.argb(36,240,239,233));}
 private android.graphics.drawable.StateListDrawable navigationBackground(){return stateSurface(Color.TRANSPARENT,Color.argb(30,240,239,233));}
 private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(4),0,dp(4));return t;}
 private Button button(String title,Runnable action){
  Button b=new Button(this);b.setText(title);b.setTextSize(16);b.setAllCaps(false);b.setTextColor(PAPER);b.setBackground(buttonBackground());b.setPadding(dp(16),dp(6),dp(16),dp(6));b.setMinimumHeight(dp(44));b.setMinHeight(dp(44));b.setFocusable(true);b.setStateListAnimator(null);b.setElevation(0);b.setTag(title);b.setId(View.generateViewId());
  b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_focused},new int[]{}},new int[]{Color.argb(90,238,232,220),INK,PAPER}));
  b.setOnClickListener(v->{if(resumed&&!destroyed)action.run();});return b;
 }
 private void addButton(LinearLayout parent,String title,Runnable action){Button b=button(title,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(46));p.setMargins(0,dp(4),dp(10),dp(4));parent.addView(b,p);}
 private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
 private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
 private void addNote(String note){body.addView(text(note,14,MUTED));}
 private void buildShell(){
  scene=new FrameLayout(this);scene.setBackgroundColor(DARK);
  backgroundImage=new ImageView(this);backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);scene.addView(backgroundImage,new FrameLayout.LayoutParams(-1,-1));
  View shade=new View(this);shade.setBackgroundColor(Color.argb(180,16,18,21));scene.addView(shade,new FrameLayout.LayoutParams(-1,-1));
  contentViewport=new FrameLayout(this);scene.addView(contentViewport);
  pageHost=new FrameLayout(this);contentViewport.addView(pageHost,new FrameLayout.LayoutParams(-1,-1));
  header=row();header.addView(text("Home",23,PAPER));View headerSpace=new View(this);header.addView(headerSpace,new LinearLayout.LayoutParams(0,1,1));date=text("",13,MUTED);header.addView(date);clock=text("",24,PAPER);LinearLayout.LayoutParams timeParams=new LinearLayout.LayoutParams(-2,-2);timeParams.leftMargin=dp(20);header.addView(clock,timeParams);
  FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-1,dp(48));hp.setMargins(dp(48),dp(22),dp(48),0);scene.addView(header,hp);
  scrim=new View(this);scrim.setBackgroundColor(Color.argb(95,5,8,12));scrim.setVisibility(View.GONE);scrim.setFocusable(false);scrim.setOnClickListener(v->setMenu(false));scene.addView(scrim,new FrameLayout.LayoutParams(-1,-1));
  navScroll=new ScrollView(this);navScroll.setFillViewport(true);navScroll.setVerticalScrollBarEnabled(false);nav=column();nav.setPadding(dp(8),dp(8),dp(8),dp(8));navScroll.addView(nav,new ScrollView.LayoutParams(-1,-1));
  navToggle=navItem("menu","展开导航",()->setMenu(!shell.expanded));
  String[] ids={"home","apps","services","settings","exit"};
  String[] labels={"家庭主页","应用","网页入口","外观与连接","退出桌面"};
  for(int i=0;i<ids.length;i++){if(i==3)nav.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));final String key=ids[i];navItems.put(key,navItem(key,labels[i],()->{if("exit".equals(key))finish();else show(key);}));}
  scene.addView(navScroll);
  floatingExit=button("‹ 退出全屏",()->setFull(false));floatingExit.setVisibility(View.GONE);
  FrameLayout.LayoutParams ep=new FrameLayout.LayoutParams(-2,dp(44));ep.setMargins(dp(20),dp(20),0,0);scene.addView(floatingExit,ep);
  setContentView(scene);applyShell();updateBackground();
 }
 private LinearLayout navItem(String glyph,String label,Runnable action){
  LinearLayout item=row();item.setFocusable(true);item.setId(View.generateViewId());item.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);item.setContentDescription(label);item.setBackground(navigationBackground());
  Glyph icon=new Glyph(glyph);item.addView(icon,new LinearLayout.LayoutParams(dp(40),dp(42)));
  TextView title=text(label,15,PAPER);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);item.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  item.setOnFocusChangeListener((v,on)->{icon.ink=on?INK:PAPER;icon.invalidate();title.setTextColor(on?INK:PAPER);});
  item.setOnClickListener(v->{if(resumed&&!destroyed)action.run();});
  item.setOnKeyListener((v,code,event)->{if(code==KeyEvent.KEYCODE_DPAD_RIGHT){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){setMenu(false);focusContent();}return true;}return false;});
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(46));lp.setMargins(0,0,0,dp(6));nav.addView(item,lp);return item;
 }
 private final class Glyph extends View {
  final String kind;int ink=PAPER;final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
  Glyph(String value){super(MainActivity.this);kind=value;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();float size=Math.min(getWidth(),getHeight())*.52f;canvas.translate((getWidth()-size)/2,(getHeight()-size)/2);canvas.scale(size/24,size/24);pen.setColor(ink);pen.setStrokeWidth(1.6f);pen.setStyle(Paint.Style.STROKE);pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);Path p=new Path();
   if("home".equals(kind)){p.moveTo(3,11);p.lineTo(12,3);p.lineTo(21,11);p.moveTo(6,9);p.lineTo(6,21);p.lineTo(10,21);p.lineTo(10,14);p.lineTo(14,14);p.lineTo(14,21);p.lineTo(18,21);p.lineTo(18,9);canvas.drawPath(p,pen);}
   else if("apps".equals(kind)){for(int x:new int[]{3,14})for(int y:new int[]{3,14})canvas.drawRoundRect(x,y,x+7,y+7,1,1,pen);}
   else if("services".equals(kind)){canvas.drawCircle(12,12,9,pen);canvas.drawLine(3,12,21,12,pen);canvas.drawOval(8,3,16,21,pen);}
   else if("diagnostics".equals(kind)){canvas.drawCircle(12,12,9,pen);canvas.drawLine(12,10,12,17,pen);canvas.drawPoint(12,6,pen);}
   else if("exit".equals(kind)){p.moveTo(10,4);p.lineTo(4,4);p.lineTo(4,20);p.lineTo(10,20);p.moveTo(9,12);p.lineTo(21,12);p.moveTo(16,7);p.lineTo(21,12);p.lineTo(16,17);canvas.drawPath(p,pen);}
   else{for(int y:new int[]{6,12,18})canvas.drawLine(4,y,20,y,pen);if("settings".equals(kind)){canvas.drawCircle(9,6,2,pen);canvas.drawCircle(15,12,2,pen);canvas.drawCircle(8,18,2,pen);}}
   canvas.restore();
  }
 }
 private void applyShell(){
  FrameLayout.LayoutParams vp=new FrameLayout.LayoutParams(-1,-1);if(!shell.fullscreen)vp.setMargins(dp(132),dp(96),dp(48),dp(36));contentViewport.setLayoutParams(vp);
  FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(dp(shell.expanded?224:60),-1);np.setMargins(dp(40),dp(96),0,dp(36));navScroll.setLayoutParams(np);
  if(shell.expanded){GradientDrawable panel=fill(Color.argb(250,29,32,37),16);panel.setStroke(dp(1),Color.argb(32,240,239,233));navScroll.setBackground(panel);}else navScroll.setBackgroundColor(Color.TRANSPARENT);
  contentViewport.setDescendantFocusability(shell.expanded?ViewGroup.FOCUS_BLOCK_DESCENDANTS:ViewGroup.FOCUS_AFTER_DESCENDANTS);
  header.setVisibility(shell.fullscreen?View.GONE:View.VISIBLE);navScroll.setVisibility(shell.fullscreen?View.GONE:View.VISIBLE);scrim.setVisibility(shell.expanded&&!shell.fullscreen?View.VISIBLE:View.GONE);floatingExit.setVisibility(shell.fullscreen?View.VISIBLE:View.GONE);
  if(webTools!=null)webTools.setVisibility(shell.fullscreen?View.GONE:View.VISIBLE);
  if(webPane!=null)webPane.setBackground(fill(PAPER,shell.fullscreen?0:16));
  if(shell.fullscreen&&webStatus!=null)webStatus.setVisibility(View.GONE);
  for(int i=0;i<nav.getChildCount();i++){View child=nav.getChildAt(i);if(child instanceof LinearLayout)((LinearLayout)child).getChildAt(1).setVisibility(shell.expanded?View.VISIBLE:View.GONE);}
  String active="webtest".equals(page)?"home":"diagnostics".equals(page)?"settings":page;for(Map.Entry<String,LinearLayout> entry:navItems.entrySet())entry.getValue().setSelected(entry.getKey().equals(active));
  ((TextView)navToggle.getChildAt(1)).setText(shell.expanded?"收起导航":"展开导航");navToggle.setContentDescription(shell.expanded?"收起导航":"展开导航");
 }
 private boolean requestNativeFocus(View target){return target!=null&&(target.requestFocus()||target.requestFocusFromTouch());}
 private void setMenu(boolean open){
  if(open){menuReturnFocus=getCurrentFocus();if(web!=null)web.clearFocus();}shell.menu(open);applyShell();if(open)requestNativeFocus(navToggle);else restoreMenuFocus();record(open?"menu open":"menu closed");
 }
 private void restoreMenuFocus(){View previous=menuReturnFocus;menuReturnFocus=null;if(previous!=null&&previous.isAttachedToWindow()&&previous.isShown()&&previous.isFocusable()&&requestNativeFocus(previous))return;requestNativeFocus(navToggle);}
 private void setFull(boolean full){
  if(full&&(web==null||webPane==null||webPane.getVisibility()!=View.VISIBLE))return;
  if(full)fullReturnFocus=getCurrentFocus();shell.full(full);applyShell();if(full){floatingExit.setNextFocusDownId(web.getId());requestNativeFocus(floatingExit);}else restoreFullFocus();record(full?"fullscreen":"windowed");
 }
 private void restoreFullFocus(){View previous=fullReturnFocus;fullReturnFocus=null;if(previous!=null&&previous.isAttachedToWindow()&&previous.isShown()&&previous.isFocusable()&&requestNativeFocus(previous))return;focusWebTools();}
 private void focusContent(){if(webPane!=null&&webPane.getVisibility()==View.VISIBLE){requestNativeFocus(webEnter);}else if(appList!=null&&"apps".equals(page)){restoreAppFocus();}else{ArrayList<View> targets=body==null?new ArrayList<>():body.getFocusables(View.FOCUS_FORWARD);if(!targets.isEmpty())requestNativeFocus(targets.get(0));}}
 private void focusWebTools(){if(webPane==null||webPane.getVisibility()!=View.VISIBLE){requestNativeFocus(navItems.get("home"));return;}if(webBack!=null&&webBack.isEnabled()&&webPane.getVisibility()==View.VISIBLE)requestNativeFocus(webBack);else if(webEnter!=null&&webPane.getVisibility()==View.VISIBLE)requestNativeFocus(webEnter);else requestNativeFocus(navItems.get("home"));}
 private void enterWeb(){if(web!=null&&webPane.getVisibility()==View.VISIBLE){shell.menu(false);applyShell();web.requestFocus(View.FOCUS_DOWN);}}
 private void show(String next){
  shell.show(next);menuReturnFocus=null;fullReturnFocus=null;page=shell.page;diagnosticText=null;appList=null;
  if(web!=null)web.clearFocus();
  if(webPane!=null)webPane.setVisibility(View.INVISIBLE);
  pageHost.removeAllViews();pageHost.setVisibility(View.VISIBLE);body=column();body.setPadding(dp(20),dp(14),dp(20),dp(14));pageHost.addView(body,new FrameLayout.LayoutParams(-1,-1));
  if("home".equals(page))home();else if("apps".equals(page))apps();else if("services".equals(page))services();else if("settings".equals(page))settings();else if("diagnostics".equals(page))diagnostics();else if("webtest".equals(page))webPage(true);
  updateWebLifecycle();applyShell();record("page "+page);
  View target=navItems.get("webtest".equals(page)?"home":"diagnostics".equals(page)?"settings":page);if(target!=null)requestNativeFocus(target);
  if("apps".equals(page))restoreAppFocus();
 }
 private void home(){
  if(WebPolicy.valid(configured)&&!safe){webPage(false);return;}
  FrameLayout welcome=new FrameLayout(this);body.addView(welcome,new LinearLayout.LayoutParams(-1,-1));
  LinearLayout panel=column();panel.setPadding(dp(28),dp(25),dp(28),dp(25));panel.setBackground(fill(PAPER,16));
  FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(Math.min(dp(560),getResources().getDisplayMetrics().widthPixels-dp(220)),-2,Gravity.CENTER_VERTICAL|Gravity.START);welcome.addView(panel,lp);
  TextView eyebrow=text("HOME ASSISTANT",12,Color.rgb(125,116,102));eyebrow.setLetterSpacing(.1f);panel.addView(eyebrow);
  panel.addView(text(safe?"家庭页面已暂停":"连接你的家庭页面",28,INK));TextView explanation=text(safe?"原来的地址与异常记录已保留。":"使用现有的 Home Assistant 页面。",16,Color.rgb(124,113,97));LinearLayout.LayoutParams note=new LinearLayout.LayoutParams(-1,-2);note.setMargins(0,dp(8),0,dp(24));panel.addView(explanation,note);
  LinearLayout controls=row();Button connect=button(!WebPolicy.valid(configured)?"连接 HA":"恢复浏览",()->{if(!WebPolicy.valid(configured))configureHA();else if(safe)confirmResume();else show("home");});connect.setBackground(stateSurface(Color.rgb(45,50,58),Color.rgb(45,50,58)));controls.addView(connect,new LinearLayout.LayoutParams(-2,dp(46)));
  Button preview=button("查看本地示意",()->show("webtest"));preview.setContentDescription("本地浏览测试");preview.setBackground(stateSurface(Color.rgb(218,211,198),Color.rgb(218,211,198)));preview.setTextColor(INK);preview.setOnFocusChangeListener((v,on)->preview.setTextColor(INK));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-2,dp(46));pp.leftMargin=dp(12);controls.addView(preview,pp);panel.addView(controls);
 }
 private void apps(){
  PackageManager pm=getPackageManager();LinkedHashMap<String,ResolveInfo> entries=new LinkedHashMap<>();
  for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER})for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0))if(r.activityInfo!=null&&!r.activityInfo.packageName.equals(getPackageName()))entries.putIfAbsent(r.activityInfo.packageName,r);
  appEntries.clear();appEntries.addAll(entries.values());appEntries.sort((a,b)->a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));
  LinearLayout heading=row();heading.addView(text("应用",27,PAPER));TextView count=text(appEntries.size()+" 个入口",13,MUTED);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.leftMargin=dp(18);heading.addView(count,cp);body.addView(heading);
  appArtHeight=dp(124);appCardHeight=appArtHeight+dp(44);
  appList=new GridView(this);appList.setNumColumns(4);appList.setHorizontalSpacing(dp(18));appList.setVerticalSpacing(dp(16));appList.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);appList.setPadding(0,dp(8),dp(12),0);appList.setVerticalScrollBarEnabled(true);appList.setScrollbarFadingEnabled(false);appList.setScrollBarSize(dp(4));if(Build.VERSION.SDK_INT>=29){appList.setVerticalScrollbarThumbDrawable(fill(Color.argb(150,230,230,230),4));appList.setVerticalScrollbarTrackDrawable(fill(Color.argb(22,230,230,230),4));}appList.setClipToPadding(false);appList.setNextFocusLeftId(navItems.get("apps").getId());appList.setCacheColorHint(Color.TRANSPARENT);appList.setSelector(gridSelector());appList.setDrawSelectorOnTop(true);appList.setAdapter(new AppAdapter());
  appList.setOnItemClickListener((parent,view,position,id)->launchApp(appEntries.get(position)));
  appList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View view,int position,long id){updateAppColors();}public void onNothingSelected(AdapterView<?> parent){updateAppColors();}});
  appList.setOnFocusChangeListener((v,on)->updateAppColors());appList.setOnScrollListener(new AbsListView.OnScrollListener(){public void onScrollStateChanged(AbsListView view,int state){}public void onScroll(AbsListView view,int first,int count,int total){updateAppColors();}});
  LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,0,1);gp.topMargin=dp(14);body.addView(appList,gp);
  final GridView grid=appList;grid.post(()->{if(destroyed||appList!=grid)return;int width=(grid.getWidth()-grid.getPaddingLeft()-grid.getPaddingRight()-dp(18)*3)/4;appArtHeight=Math.max(dp(88),Math.round((width-dp(8))*9f/16));appCardHeight=appArtHeight+dp(44);int rows=Math.max(1,Math.min(2,(grid.getHeight()-dp(8)+dp(16))/(appCardHeight+dp(16))));LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)grid.getLayoutParams();params.height=dp(8)+rows*appCardHeight+(rows-1)*dp(16);params.weight=0;grid.setLayoutParams(params);((BaseAdapter)grid.getAdapter()).notifyDataSetChanged();restoreAppFocus();});
  if(appEntries.isEmpty())addNote("没有找到可启动的应用，可从原厂桌面继续使用。");
 }
 private android.graphics.drawable.StateListDrawable gridSelector(){android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();GradientDrawable focus=fill(Color.TRANSPARENT,14);focus.setStroke(dp(2),Color.rgb(224,235,250));states.addState(new int[]{android.R.attr.state_focused},focus);GradientDrawable pressed=fill(Color.argb(30,224,235,250),14);pressed.setStroke(dp(2),PAPER);states.addState(new int[]{android.R.attr.state_pressed},pressed);states.addState(new int[]{},fill(Color.TRANSPARENT,14));return states;}
 private final class AppAdapter extends BaseAdapter {
  public int getCount(){return appEntries.size();}public Object getItem(int index){return appEntries.get(index);}public long getItemId(int index){return index;}
  public View getView(int position,View recycled,ViewGroup parent){
   LinearLayout item;FrameLayout art;ImageView image;TextView label;
   if(recycled instanceof LinearLayout){item=(LinearLayout)recycled;art=(FrameLayout)item.getChildAt(0);image=(ImageView)art.getChildAt(0);label=(TextView)item.getChildAt(1);}else{item=column();item.setPadding(dp(5),dp(5),dp(5),dp(5));art=new FrameLayout(MainActivity.this);image=new ImageView(MainActivity.this);image.setColorFilter(iconTint);art.addView(image,new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.CENTER));item.addView(art,new LinearLayout.LayoutParams(-1,appArtHeight));label=text("",15,PAPER);label.setSingleLine();label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setPadding(dp(2),dp(7),dp(2),0);item.addView(label,new LinearLayout.LayoutParams(-1,dp(34)));}
   art.getLayoutParams().height=appArtHeight;int[] colors={Color.rgb(209,204,192),Color.rgb(62,72,88),Color.rgb(161,148,127),Color.rgb(54,62,76)};GradientDrawable tile=fill(colors[position%colors.length],12);tile.setStroke(dp(1),Color.argb(30,244,239,230));art.setBackground(tile);item.setLayoutParams(new AbsListView.LayoutParams(-1,appCardHeight));
   ResolveInfo info=appEntries.get(position);String key=info.activityInfo.packageName+"/"+info.activityInfo.name;label.setText(info.loadLabel(getPackageManager()));label.setTextColor(appList!=null&&appList.hasFocus()&&position==appList.getSelectedItemPosition()?PAPER:Color.rgb(205,201,192));image.setTag(key);Bitmap cached=icons.get(key);image.setImageBitmap(cached);
   if(cached==null&&pendingIcons.add(key)){images.execute(()->{Bitmap result=null;try{Drawable drawable=info.loadIcon(getPackageManager()).mutate();int size=ResourcePolicy.iconPixels(dp(48));result=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);drawable.setBounds(0,0,size,size);drawable.draw(new Canvas(result));}catch(RuntimeException ignored){}final Bitmap loaded=result;handler.post(()->{pendingIcons.remove(key);if(destroyed||loaded==null)return;icons.put(key,loaded);bindVisibleIcon(key,loaded);});});}
   return item;
  }
 }
 private static ColorMatrixColorFilter neutralIconFilter(){ColorMatrix matrix=new ColorMatrix();matrix.setSaturation(0);return new ColorMatrixColorFilter(matrix);}
 private void bindVisibleIcon(String key,Bitmap icon){if(appList==null||!"apps".equals(page))return;for(int i=0;i<appList.getChildCount();i++){View row=appList.getChildAt(i);if(row instanceof LinearLayout){ImageView target=(ImageView)((FrameLayout)((LinearLayout)row).getChildAt(0)).getChildAt(0);if(key.equals(target.getTag()))target.setImageBitmap(icon);}}}
 private void updateAppColors(){if(appList==null)return;for(int i=0;i<appList.getChildCount();i++){View row=appList.getChildAt(i);if(row instanceof LinearLayout)((TextView)((LinearLayout)row).getChildAt(1)).setTextColor(appList.hasFocus()&&appList.getFirstVisiblePosition()+i==appList.getSelectedItemPosition()?PAPER:Color.rgb(205,201,192));}}
 private void restoreAppFocus(){
  if(appList==null||appEntries.isEmpty())return;final GridView list=appList;int position=0;for(int i=0;i<appEntries.size();i++)if(appEntries.get(i).activityInfo.packageName.equals(lastLaunched)){position=i;break;}
  list.requestFocus();list.setSelection(position);
 }
 private void launchApp(ResolveInfo info){
  long now=SystemClock.elapsedRealtime();if(now-lastLaunch<800)return;lastLaunch=now;
  try{startActivity(new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(info.activityInfo.packageName,info.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));lastLaunched=info.activityInfo.packageName;prefs.edit().putString("last_app",lastLaunched).apply();returningFromApp=true;record("app launch");}catch(RuntimeException error){notice("应用暂时无法打开","可刷新应用列表，或通过原厂桌面打开。错误类型："+error.getClass().getSimpleName());}
 }
 private void services(){
  body.addView(text("网页入口",23,PAPER));addButton(body,"家庭主页",()->{if(configured.isEmpty())configureHA();else if(safe)confirmResume();else show("home");});
  String link=prefs.getString("service_url","");addButton(body,link.isEmpty()?"添加网页服务":prefs.getString("service_name","网页服务"),()->{if(link.isEmpty())configureService();else externalWeb(link);});if(!link.isEmpty())addButton(body,"编辑网页服务",this::configureService);
 }
 private void externalWeb(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(RuntimeException error){notice("未能打开浏览器","请确认电视上已有可用浏览器。错误类型："+error.getClass().getSimpleName());}}
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
  dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{String address=field.getText().toString().trim();if(!WebPolicy.valid(address)){field.setError("请用 HTTPS，或 http://局域网IP:端口/路径；不要在地址中放账号密码");return;}if(!address.equals(configured)){discardWeb(false);restoredWeb=null;}configured=address;prefs.edit().putString("ha_url",address).apply();dialog.dismiss();if(safe)confirmResume();else show("web");}));dialog.show();
 }
 private void settings(){
  body.addView(text("外观与连接",23,PAPER));LinearLayout walls=row();addButton(walls,"海岸",()->setWall("coast"));addButton(walls,"沙色",()->setWall("sand"));addButton(walls,"岩灰",()->setWall("stone"));body.addView(walls);
  LinearLayout connect=row();addButton(connect,"家庭页面地址",this::configureHA);addButton(connect,"网页服务",this::configureService);body.addView(connect);
  LinearLayout system=row();addButton(system,"原厂桌面",this::stockHome);addButton(system,"系统设置",()->systemSettings(Settings.ACTION_SETTINGS));addButton(system,"信号源",()->systemSettings("android.settings.TV_INPUT_SETTINGS"));body.addView(system);
  addButton(body,"本地浏览测试",()->show("webtest"));addButton(body,"诊断与帮助",()->show("diagnostics"));addButton(body,"扩展与 Agent",()->notice("扩展预留","当前没有远程控制服务。配置导入和明确动作接口是后续工作。"));
 }
 private void setWall(String name){prefs.edit().putString("wall",name).apply();updateBackground();}
 private void updateBackground(){
  String choice=prefs.getString("wall","coast");backgroundImage.setVisibility("coast".equals(choice)?View.VISIBLE:View.GONE);scene.setBackgroundColor("sand".equals(choice)?Color.rgb(125,113,93):"stone".equals(choice)?Color.rgb(61,57,51):DARK);
  if(!"coast".equals(choice))return;if(wallpaper!=null){backgroundImage.setImageBitmap(wallpaper);return;}if(wallLoading)return;wallLoading=true;
  final int width=Math.min(getResources().getDisplayMetrics().widthPixels,1920),height=Math.min(getResources().getDisplayMetrics().heightPixels,1080);
  images.execute(()->{Bitmap result=null;try{BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;try(InputStream in=getAssets().open("coast-wallpaper.png")){BitmapFactory.decodeStream(in,null,options);}options.inSampleSize=ResourcePolicy.sampleSize(options.outWidth,options.outHeight,width,height);options.inJustDecodeBounds=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;try(InputStream in=getAssets().open("coast-wallpaper.png")){result=BitmapFactory.decodeStream(in,null,options);}}catch(IOException|RuntimeException ignored){}final Bitmap loaded=result;handler.post(()->{if(destroyed)return;wallLoading=false;wallpaper=loaded;if("coast".equals(prefs.getString("wall","coast")))backgroundImage.setImageBitmap(loaded);});});
 }
 private void stockHome(){try{startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(RuntimeException error){notice("请使用系统 Home 键","原厂桌面未更改，可使用系统 Home 键返回。");}}
 private void systemSettings(String action){try{startActivity(new Intent(action));}catch(RuntimeException error){notice("请用电视原设置","当前固件没有提供这个标准入口。");}}
 private boolean live(WebView current,int generation){return !destroyed&&current==web&&generation==webGeneration;}
 private void discardWeb(boolean rendererGone){
  WebView old=web;web=null;webKey="";webGeneration++;webPaused=true;if(old==null)return;
  if(!rendererGone){try{old.stopLoading();old.setWebViewClient(new WebViewClient());}catch(RuntimeException ignored){}}
  if(old.getParent() instanceof ViewGroup)((ViewGroup)old.getParent()).removeView(old);old.destroy();
 }
 private Button toolButton(String title,String label,Runnable action){Button control=button(title,action);control.setContentDescription(label);control.setTextSize(13);control.setMinHeight(0);control.setMinimumHeight(0);control.setMinWidth(dp(38));control.setMinimumWidth(dp(38));control.setPadding(dp(12),0,dp(12),0);return control;}
 private void createWebPane(){
  webPane=new FrameLayout(this);webPane.setBackground(fill(PAPER,12));webPane.setClipToOutline(true);contentViewport.addView(webPane,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout layout=column();webPane.addView(layout,new FrameLayout.LayoutParams(-1,-1));
  webTools=row();webTools.setPadding(dp(14),0,dp(10),0);webTools.setBackgroundColor(Color.rgb(37,41,47));
  webModeLabel=text("家庭页面",14,PAPER);webTools.addView(webModeLabel,new LinearLayout.LayoutParams(0,-2,1));
  webEnter=toolButton("浏览","进入网页",this::enterWeb);webEnter.setNextFocusLeftId(navItems.get("home").getId());webBack=toolButton("‹","网页后退",()->{if(web!=null&&web.canGoBack())web.goBack();});
  for(Button b:new Button[]{webEnter,webBack,toolButton("↻","刷新",()->{if(web!=null){webLoads++;web.reload();}}),toolButton("⤢","全屏",()->setFull(true))}){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(36));lp.setMargins(0,dp(5),dp(8),dp(5));webTools.addView(b,lp);}
  layout.addView(webTools);webStatus=text("",14,INK);webStatus.setPadding(dp(12),dp(4),dp(12),dp(4));layout.addView(webStatus);
  webSlot=new FrameLayout(this);layout.addView(webSlot,new LinearLayout.LayoutParams(-1,0,1));
 }
 private void webPage(boolean demo){
  String wanted=ShellState.webKey(demo,configured);
  if(webPane==null)createWebPane();webModeLabel.setText(demo?"本地示意":"家庭页面");
  if(!ShellState.reuseWeb(webKey,wanted,web!=null)){
   discardWeb(false);webKey=wanted;final String allowedAddress=demo?"https://preview.invalid/":configured;
   try{
    WebView current=new WebView(this);web=current;webInstances++;current.setId(View.generateViewId());final int generation=webGeneration;
    WebView.setWebContentsDebuggingEnabled(false);WebSettings settings=current.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSupportMultipleWindows(false);settings.setMediaPlaybackRequiresUserGesture(true);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    CookieManager.getInstance().setAcceptThirdPartyCookies(current,false);current.setBackgroundColor(PAPER);current.setFocusable(true);current.setFocusableInTouchMode(true);
    current.setDownloadListener((url,agent,disposition,type,size)->{if(live(current,generation))notice("不自动下载","请在电视原浏览器中处理下载。");});
    current.setWebViewClient(new WebViewClient(){
     @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){if(!live(current,generation))return true;String url=req.getUrl().toString();if("about:blank".equals(url)||WebPolicy.sameOrigin(allowedAddress,url))return false;status("已阻止离开配置的网页来源。");return true;}
     @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest req){String url=req.getUrl().toString();if("http".equals(req.getUrl().getScheme())&&!WebPolicy.sameOrigin(allowedAddress,url))return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));return null;}
     @Override public void onPageStarted(WebView v,String url,Bitmap favicon){if(live(current,generation)){status("正在加载…");updateWebControls();}}
     @Override public void onPageFinished(WebView v,String url){if(!live(current,generation))return;status("");updateWebControls();CookieManager.getInstance().flush();record("web finished");}
     @Override public void doUpdateVisitedHistory(WebView v,String url,boolean reload){if(live(current,generation))updateWebControls();}
     @Override public void onReceivedError(WebView v,WebResourceRequest req,WebResourceError error){if(req.isForMainFrame()&&live(current,generation)){status("网页加载失败，可刷新或返回。错误码 "+error.getErrorCode());record("web error "+error.getErrorCode());}}
     @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail){if(live(current,generation)){boolean wasVisible=webPane.getVisibility()==View.VISIBLE;safe=true;prefs.edit().putBoolean("web_safe_mode",true).apply();discardWeb(true);restoredWeb=null;record("web renderer stopped");if(wasVisible)show("home");else updateWebControls();}return true;}
    });
    webSlot.addView(current,new FrameLayout.LayoutParams(-1,-1));webPaused=false;
    boolean restored=false;if(!demo&&restoredWeb!=null&&wanted.equals(restoredWebKey))try{WebBackForwardList recovered=current.restoreState(restoredWeb);restored=recovered!=null&&recovered.getSize()>0&&recovered.getCurrentItem()!=null&&WebPolicy.sameOrigin(allowedAddress,recovered.getCurrentItem().getUrl());}catch(RuntimeException ignored){}restoredWeb=null;restoredWebKey="";
    if(!restored){webLoads++;if(demo){String html;try(InputStream in=getAssets().open("session-probe.html")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int read;while((read=in.read(buffer))!=-1)bytes.write(buffer,0,read);html=bytes.toString("UTF-8");}current.loadDataWithBaseURL("https://preview.invalid/",html,"text/html","UTF-8",null);}else if(WebPolicy.valid(configured))current.loadUrl(configured);}
    record("web instance "+webInstances);
   }catch(IOException|RuntimeException error){discardWeb(false);safe=true;prefs.edit().putBoolean("web_safe_mode",true).apply();record("web creation failed");show("home");return;}
  }
  pageHost.setVisibility(View.GONE);webPane.setVisibility(View.VISIBLE);updateWebControls();
 }
 private void updateWebControls(){if(webBack!=null)webBack.setEnabled(web!=null&&web.canGoBack());}
 private void status(String message){runOnUiThread(()->{if(destroyed||webStatus==null)return;webStatus.setText(message);webStatus.setVisibility(message.isEmpty()||shell.fullscreen?View.GONE:View.VISIBLE);});}
 private void updateWebLifecycle(){if(web==null)return;boolean visible=resumed&&webPane!=null&&webPane.getVisibility()==View.VISIBLE;if(visible&&webPaused){web.onResume();webPaused=false;}else if(!visible&&!webPaused){web.onPause();webPaused=true;}}
 private void confirmResume(){new AlertDialog.Builder(this).setTitle("恢复家庭页面？").setMessage("保留上次异常记录，恢复后重新加载已配置的网页。").setNegativeButton("暂不加载",null).setPositiveButton("恢复浏览",(dialog,which)->{safe=false;prefs.edit().putBoolean("web_safe_mode",false).apply();File crash=new File(getFilesDir(),"last-crash.json");if(crash.exists())crash.renameTo(new File(getFilesDir(),"previous-crash.json"));show("home");}).show();}
 private void diagnostics(){
  body.addView(text("本机诊断",23,PAPER));String webVersion="不可用";try{PackageInfo info=WebView.getCurrentWebViewPackage();if(info!=null)webVersion=info.packageName+" "+info.versionName;}catch(RuntimeException ignored){}
  body.addView(text("版本 "+BuildInfo.VERSION+" ("+BuildInfo.CODE+") · "+BuildInfo.SOURCE.substring(0,Math.min(10,BuildInfo.SOURCE.length()))+"\nAndroid "+Build.VERSION.RELEASE+" / SDK "+Build.VERSION.SDK_INT+"\nWebView "+webVersion,15,PAPER));
  String stats="网页实例创建 "+webInstances+" · 显式加载 "+webLoads+"\n当前实例 "+(web==null?"无":System.identityHashCode(web))+" · "+(web!=null&&web.isAttachedToWindow()?"已挂载":"未挂载")+"\n网页视口 "+(web==null?"无":web.getWidth()+" × "+web.getHeight())+"\n图标缓存 "+icons.size()/1024+" KiB / 2048 KiB";
  body.addView(text(stats,15,PAPER));ScrollView scroll=new ScrollView(this);diagnosticText=text(eventText(),13,MUTED);scroll.addView(diagnosticText);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  if(safe)addButton(body,"恢复家庭页面",()->{if(configured.isEmpty())configureHA();else confirmResume();});
 }
 private void notice(String title,String message){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("知道了",null).show();}
 private void record(String value){String line=SystemClock.elapsedRealtime()+" "+value;if(events.size()>=40)events.removeFirst();events.addLast(line);dirty=true;if(diagnosticText!=null)diagnosticText.setText(eventText());}
 private String eventText(){StringBuilder b=new StringBuilder();for(String e:events)b.append(e).append('\n');return b.toString();}
 private void writeJson(String name,JSONObject data)throws IOException{AtomicFile f=new AtomicFile(new File(getFilesDir(),name));FileOutputStream out=null;try{out=f.startWrite();out.write(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));f.finishWrite(out);}catch(IOException error){if(out!=null)f.failWrite(out);throw error;}}
 private void writeDiagnostics(){try{JSONObject o=new JSONObject();o.put("versionCode",BuildInfo.CODE);o.put("source",BuildInfo.SOURCE);o.put("page",page);o.put("webSafeMode",safe);o.put("events",new JSONArray(events));writeJson("diagnostics.json",o);dirty=false;}catch(Exception ignored){}}
 @Override public boolean dispatchKeyEvent(KeyEvent event){
  int code=event.getKeyCode();if(event.getAction()==KeyEvent.ACTION_DOWN&&KeyPolicy.diagnosticKey(code))record("key "+code+" repeat "+event.getRepeatCount()+" device "+event.getDeviceId());
  if(KeyPolicy.systemOwned(code))return super.dispatchKeyEvent(event);
  if(code==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)setMenu(!shell.expanded);return true;}
  if(event.getAction()==KeyEvent.ACTION_DOWN&&KeyPolicy.suppressRepeat(code,event.getRepeatCount()))return true;
  return super.dispatchKeyEvent(event);
 }
 @Override public void onBackPressed(){
  ShellState.Back action=shell.back(web!=null&&web.hasFocus());
  switch(action){case EXIT_FULLSCREEN:applyShell();restoreFullFocus();break;case CLOSE_MENU:applyShell();restoreMenuFocus();break;case WEB_TOOLS:web.clearFocus();focusWebTools();break;case HOME:show("home");break;case EXIT:finish();break;}
 }
 @Override protected void onSaveInstanceState(Bundle state){
  state.putString("page",page);state.putBoolean("web_safe",safe);
  if(web!=null&&!safe&&webKey.startsWith("ha:")){Bundle saved=new Bundle();if(web.saveState(saved)!=null){state.putBundle("web_state",saved);state.putString("web_key",webKey);}}
  super.onSaveInstanceState(state);
 }
 @Override protected void onResume(){super.onResume();Thread.setDefaultUncaughtExceptionHandler(crashHandler);resumed=true;updateWebLifecycle();record("resume");handler.removeCallbacks(tick);tick.run();if(returningFromApp){returningFromApp=false;if("apps".equals(page))restoreAppFocus();}}
 @Override protected void onPause(){resumed=false;handler.removeCallbacks(tick);updateWebLifecycle();record("pause");writeDiagnostics();super.onPause();}
 @Override protected void onStop(){record("stop");writeDiagnostics();super.onStop();}
 @Override public void onTrimMemory(int level){super.onTrimMemory(level);if(level>=TRIM_MEMORY_RUNNING_LOW)icons.evictAll();}
 @Override protected void onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);images.shutdownNow();icons.evictAll();discardWeb(false);record("destroy");if(dirty)writeDiagnostics();if(Thread.getDefaultUncaughtExceptionHandler()==crashHandler)Thread.setDefaultUncaughtExceptionHandler(platformCrashHandler);super.onDestroy();}
}
