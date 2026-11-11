package ru.novatv.launcher;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.text.*;

public class MainActivity extends Activity {
  private LinearLayout page, content, menu; private TextView clock;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final ArrayList<App> installed = new ArrayList<>();
  private int theme = 0;
  private final int WHITE=0xfff7f9ff, MUTED=0xffb8c7df;
  static class App { String pkg, activity, label; Drawable icon; App(String p,String a,String l,Drawable d){pkg=p;activity=a;label=l;icon=d;} }
  int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+0.5f);}
  GradientDrawable shape(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
  GradientDrawable background(){int[][] colors={{0xff10172b,0xff1d365a,0xff090f20},{0xff171126,0xff4c284b,0xff0c1227},{0xff092c34,0xff164b57,0xff101c2c}};GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors[theme]);d.setCornerRadius(0);return d;}
  TextView label(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
  LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
  LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
  LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
  void gap(LinearLayout l,int h){View v=new View(this);l.addView(v,lp(1,h));}
  void marginAdd(LinearLayout parent,View child,int w,int h,int right){LinearLayout.LayoutParams p=lp(w,h);p.rightMargin=dp(right);parent.addView(child,p);}
  @Override public void onCreate(Bundle b){super.onCreate(b);theme=getPreferences(0).getInt("theme",0);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);refreshApps();render();}
  void refreshApps(){installed.clear();PackageManager pm=getPackageManager();Map<String,App> map=new LinkedHashMap<>();for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){Intent i=new Intent(Intent.ACTION_MAIN);i.addCategory(category);for(ResolveInfo r:pm.queryIntentActivities(i,0)){String pkg=r.activityInfo.packageName;if(pkg.equals(getPackageName())||map.containsKey(pkg))continue;map.put(pkg,new App(pkg,r.activityInfo.name,r.loadLabel(pm).toString(),r.loadIcon(pm)));}}installed.addAll(map.values());installed.sort((a,b)->a.label.compareToIgnoreCase(b.label));}
  void render(){ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackground(background());setContentView(sc);page=vertical();page.setPadding(dp(38),dp(20),dp(38),dp(28));sc.addView(page);header();gap(page,17);banner();gap(page,20);content=vertical();page.addView(content);home();}
  void header(){LinearLayout bar=horizontal();page.addView(bar,lp(-1,48));TextView logo=label("✦  NovaTV",26,WHITE);logo.setTypeface(null,1);marginAdd(bar,logo,170,-1,12);menu=horizontal();bar.addView(menu,new LinearLayout.LayoutParams(0,-1,1));nav("Главная",()->home());nav("Приложения",()->allApps());nav("Избранное",()->favorites());nav("Настройки",()->settings());clock=label("",17,WHITE);bar.addView(clock);updateClock();}
  void updateClock(){if(clock!=null)clock.setText(new SimpleDateFormat("EEE, d MMM  HH:mm",new Locale("ru","RU")).format(new Date()));handler.removeCallbacksAndMessages(null);handler.postDelayed(()->{if(!isFinishing())updateClock();},30000);}
  void nav(String s,Runnable r){TextView t=label(s,16,WHITE);t.setGravity(Gravity.CENTER);t.setPadding(dp(16),0,dp(16),0);t.setBackground(shape(0x553e587b,14));t.setFocusable(true);t.setOnClickListener(v->r.run());t.setOnFocusChangeListener((v,f)->{t.setBackground(shape(f?0xffd9eaff:0x553e587b,14));t.setTextColor(f?0xff10182a:WHITE);});marginAdd(menu,t,-2,42,9);}
  void banner(){LinearLayout b=vertical();b.setPadding(dp(30),dp(20),dp(25),dp(22));b.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xff172b52,0xff394d78,0xff192342}));page.addView(b,lp(-1,155));TextView eyebrow=label("ВАШ ДОМАШНИЙ ЭКРАН",13,0xffbcd9ff);b.addView(eyebrow);gap(b,5);TextView title=label("Добро пожаловать в NovaTV",29,WHITE);title.setTypeface(null,1);b.addView(title);gap(b,4);b.addView(label("Любимые приложения и развлечения — в одном месте",16,WHITE));gap(b,8);b.addView(label("✦  Быстрый доступ     •     Избранное     •     Темы оформления",13,0xffc8d8ef));}
  void clear(String title){content.removeAllViews();TextView t=label(title,22,WHITE);t.setTypeface(null,1);content.addView(t);gap(content,9);}
  void home(){clear("Избранные приложения");List<App> fav=getFavorites();if(fav.isEmpty())content.addView(label("Добавьте приложения в избранное кнопкой ☆",15,MUTED));else rowApps(fav);gap(content,18);content.addView(label("Все приложения",22,WHITE));gap(content,9);rowApps(installed);gap(content,15);content.addView(label("Погода: подключение погодного сервиса появится в следующем обновлении",14,MUTED));}
  void allApps(){clear("Все приложения");rowApps(installed);}
  void favorites(){clear("Избранное · порядок можно менять");List<App> fav=getFavorites();if(fav.isEmpty()){content.addView(label("Пока пусто. Откройте «Приложения» и нажмите ☆ на карточке.",16,MUTED));return;}rowApps(fav);gap(content,15);for(int n=0;n<fav.size();n++){final int index=n;App a=fav.get(n);LinearLayout l=horizontal();content.addView(l);TextView name=label(a.label,15,WHITE);l.addView(name,new LinearLayout.LayoutParams(0,dp(44),1));action(l,"↑",()->move(index,-1));action(l,"↓",()->move(index,1));}}
  List<String> favoriteIds(){String raw=getPreferences(0).getString("favorite_order","");return new ArrayList<>(raw.isEmpty()?Collections.emptyList():Arrays.asList(raw.split("\\|")));}
  void saveFavorites(List<String> ids){getPreferences(0).edit().putString("favorite_order",android.text.TextUtils.join("|",ids)).apply();}
  List<App> getFavorites(){List<App> list=new ArrayList<>();for(String id:favoriteIds())for(App a:installed)if(a.pkg.equals(id)){list.add(a);break;}return list;}
  void toggle(App a){List<String> ids=favoriteIds();if(ids.contains(a.pkg))ids.remove(a.pkg);else ids.add(a.pkg);saveFavorites(ids);if(content!=null)home();}
  void move(int index,int direction){List<String> ids=favoriteIds();int to=index+direction;if(to<0||to>=ids.size())return;Collections.swap(ids,index,to);saveFavorites(ids);favorites();}
  void action(LinearLayout parent,String s,Runnable r){TextView t=label(s,18,WHITE);t.setGravity(Gravity.CENTER);t.setBackground(shape(0x663d5376,12));t.setFocusable(true);t.setOnClickListener(v->r.run());t.setOnFocusChangeListener((v,f)->{t.setBackground(shape(f?0xffd9eaff:0x663d5376,12));t.setTextColor(f?0xff0b1425:WHITE);});marginAdd(parent,t,Math.max(52, s.length()*10+24),40,8);}
  void rowApps(List<App> list){HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);content.addView(scroll,lp(-1,145));LinearLayout row=horizontal();row.setGravity(Gravity.TOP);scroll.addView(row);for(App a:list){LinearLayout card=vertical();card.setGravity(Gravity.CENTER);card.setPadding(dp(6),dp(7),dp(6),dp(7));card.setBackground(shape(0xaa273b59,14));card.setFocusable(true);card.setOnFocusChangeListener((v,f)->{card.setBackground(shape(f?0xffdcecff:0xaa273b59,14));for(int j=0;j<card.getChildCount();j++)if(card.getChildAt(j) instanceof TextView)((TextView)card.getChildAt(j)).setTextColor(f?0xff10203a:WHITE);});marginAdd(row,card,152,132,13);ImageView icon=new ImageView(this);icon.setImageDrawable(a.icon);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);card.addView(icon,lp(55,55));TextView name=label(a.label,14,WHITE);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setGravity(Gravity.CENTER);card.addView(name,lp(135,28));TextView star=label(favoriteIds().contains(a.pkg)?"★ Избранное":"☆ В избранное",12,WHITE);star.setGravity(Gravity.CENTER);star.setFocusable(true);star.setOnClickListener(v->toggle(a));star.setOnFocusChangeListener((v,f)->star.setTextColor(f?0xffffdc7e:WHITE));card.addView(star,lp(135,29));card.setOnClickListener(v->launch(a));}}
  void launch(App a){try{Intent i=new Intent();i.setClassName(a.pkg,a.activity);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}catch(Exception e){Toast.makeText(this,"Не удалось открыть «"+a.label+"»",Toast.LENGTH_LONG).show();}}
  void settings(){clear("Настройки NovaTV");LinearLayout themes=horizontal();content.addView(themes);action(themes,"Ночная тема",()->changeNovaTheme(0));action(themes,"Закат",()->changeNovaTheme(1));action(themes,"Океан",()->changeNovaTheme(2));gap(content,16);LinearLayout opts=horizontal();content.addView(opts);action(opts,"Обновить приложения",()->{refreshApps();home();});action(opts,"Диагностика",this::diagnostics);gap(content,18);content.addView(label("Настройку лаунчера по умолчанию выполним отдельно. Google TV пока остаётся без изменений.",15,MUTED));}
  void changeNovaTheme(int value){theme=value;getPreferences(0).edit().putInt("theme",theme).apply();render();settings();}
  void diagnostics(){android.app.ActivityManager.MemoryInfo mem=new android.app.ActivityManager.MemoryInfo();((android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mem);String s="Устройство: "+Build.MANUFACTURER+" "+Build.MODEL+"\nAndroid: "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")\nОЗУ всего: "+(mem.totalMem/1024/1024)+" МБ\nОЗУ доступно: "+(mem.availMem/1024/1024)+" МБ\nНайдено приложений: "+installed.size()+"\nВерсия NovaTV: 0.2.0";new AlertDialog.Builder(this).setTitle("Диагностика").setMessage(s).setPositiveButton("Закрыть",null).show();}
}
