package ru.novatv.launcher;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
public class MainActivity extends Activity {
 LinearLayout root,apps; TextView clock; boolean favoritesOnly=false; int bg=Color.rgb(15,23,42);
 Handler handler=new Handler(Looper.getMainLooper());
 @Override public void onCreate(Bundle b){super.onCreate(b);render();}
 TextView text(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(Color.WHITE);v.setPadding(18,15,18,15);return v;}
 void render(){root=new LinearLayout(this);root.setOrientation(1);root.setPadding(28,20,28,20);root.setBackgroundColor(bg);setContentView(root);
 clock=text("",24);root.addView(clock);updateClock();root.addView(text("NovaTV • Главная",32));
 LinearLayout menu=new LinearLayout(this);root.addView(menu);addButton(menu,"Все приложения",()->{favoritesOnly=false;loadApps();});addButton(menu,"Избранное",()->{favoritesOnly=true;loadApps();});addButton(menu,"Настройки",this::settings);addButton(menu,"Диагностика",this::diagnostics);
 ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));apps=new LinearLayout(this);apps.setOrientation(1);scroll.addView(apps);loadApps();}
 void addButton(LinearLayout parent,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setFocusable(true);parent.addView(b);b.setOnClickListener(v->action.run());}
 void updateClock(){clock.setText(new SimpleDateFormat("EEEE, d MMMM • HH:mm",new Locale("ru","RU")).format(new Date()));handler.postDelayed(()->{if(!isFinishing())updateClock();},60000);}
 void loadApps(){apps.removeAllViews();PackageManager pm=getPackageManager();Intent intent=new Intent(Intent.ACTION_MAIN);intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);List<ResolveInfo> list=pm.queryIntentActivities(intent,0);if(list.isEmpty()){intent.removeCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);intent.addCategory(Intent.CATEGORY_LAUNCHER);list=pm.queryIntentActivities(intent,0);}list.sort((a,b)->a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));Set<String> fav=getPreferences(0).getStringSet("favorites",new HashSet<>());
 for(ResolveInfo info:list){String pkg=info.activityInfo.packageName;String name=info.loadLabel(pm).toString();if(pkg.equals(getPackageName())||(favoritesOnly&&!fav.contains(pkg)))continue;LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);apps.addView(line);Button launch=new Button(this);launch.setText(name);launch.setAllCaps(false);launch.setFocusable(true);line.addView(launch,new LinearLayout.LayoutParams(0,85,1));launch.setOnClickListener(v->{try{Intent i=new Intent();i.setClassName(pkg,info.activityInfo.name);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}catch(Exception e){Toast.makeText(this,"Не удалось открыть приложение",Toast.LENGTH_SHORT).show();}});Button star=new Button(this);star.setText(fav.contains(pkg)?"★":"☆");line.addView(star);star.setOnClickListener(v->{Set<String> next=new HashSet<>(getPreferences(0).getStringSet("favorites",new HashSet<>()));if(!next.add(pkg))next.remove(pkg);getPreferences(0).edit().putStringSet("favorites",next).apply();loadApps();});}}
 void settings(){String[] choices={"Тёмная тема","Синяя тема","Как сделать лаунчером по умолчанию","Открыть системные настройки"};new AlertDialog.Builder(this).setTitle("Настройки").setItems(choices,(d,n)->{if(n<2){bg=n==0?Color.rgb(15,23,42):Color.rgb(12,52,100);render();}else if(n==2)new AlertDialog.Builder(this).setTitle("Лаунчер по умолчанию").setMessage("Откройте Настройки → Приложения → Приложения по умолчанию → Главный экран и выберите NovaTV. На некоторых TCL выбор HOME заблокирован производителем.").setPositiveButton("Понятно",null).show();else{try{startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));}catch(Exception e){Toast.makeText(this,"Настройки недоступны",Toast.LENGTH_SHORT).show();}}}).show();}
 void diagnostics(){String message="Устройство: "+Build.MANUFACTURER+" "+Build.MODEL+"\nAndroid: "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")\nПакет: "+getPackageName();new AlertDialog.Builder(this).setTitle("Диагностика").setMessage(message).setPositiveButton("Закрыть",null).show();}
}
