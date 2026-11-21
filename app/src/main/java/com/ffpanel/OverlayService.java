package com.ffpanel;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.PixelFormat;
import org.json.*;
import java.util.*;

public class OverlayService extends Service {

    private WindowManager  wm;
    private LinearLayout   rootView;
    private TextView       statusTv, headerTv, minimapTv;
    private LinearLayout   playerListLayout;
    private Switch         espToggle;
    private FridaBridge    frida;
    private Handler        handler;
    private Runnable       ticker;
    private boolean        espEnabled = true;
    private boolean        connected  = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, buildNotif());
        handler = new Handler(Looper.getMainLooper());
        setupOverlay();
        connectFrida();
        startTicker();
        return START_STICKY;
    }

    private void setupOverlay() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        rootView = new LinearLayout(this);
        rootView.setOrientation(LinearLayout.VERTICAL);
        rootView.setBackgroundColor(Color.argb(210,10,10,10));
        rootView.setPadding(10,8,10,8);

        // ── title bar ─────────────────────────────────────────────────────────
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("⠿ FF ESP");
        title.setTextColor(Color.CYAN);
        title.setTextSize(11f);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,1f);
        title.setLayoutParams(tp);
        titleBar.addView(title);
        setDraggable(title);

        // ON/OFF toggle
        espToggle = new Switch(this);
        espToggle.setChecked(true);
        espToggle.setText("ON");
        espToggle.setTextColor(Color.WHITE);
        espToggle.setTextSize(9f);
        espToggle.setOnCheckedChangeListener((btn, checked) -> {
            espEnabled = checked;
            playerListLayout.setVisibility(checked ? View.VISIBLE : View.GONE);
            minimapTv.setVisibility(checked ? View.VISIBLE : View.GONE);
            espToggle.setText(checked ? "ON" : "OFF");
        });
        titleBar.addView(espToggle);

        // close button
        Button closeBtn = new Button(this);
        closeBtn.setText("✕");
        closeBtn.setTextColor(Color.RED);
        closeBtn.setBackgroundColor(Color.TRANSPARENT);
        closeBtn.setTextSize(9f);
        closeBtn.setPadding(4,0,4,0);
        closeBtn.setOnClickListener(v -> stopSelf());
        titleBar.addView(closeBtn);
        rootView.addView(titleBar);

        // status
        statusTv = new TextView(this);
        statusTv.setText("Connecting...");
        statusTv.setTextColor(Color.YELLOW);
        statusTv.setTextSize(9f);
        statusTv.setTypeface(Typeface.MONOSPACE);
        rootView.addView(statusTv);

        // header
        headerTv = new TextView(this);
        headerTv.setTextColor(Color.LTGRAY);
        headerTv.setTextSize(8f);
        headerTv.setTypeface(Typeface.MONOSPACE);
        rootView.addView(headerTv);

        // player list
        playerListLayout = new LinearLayout(this);
        playerListLayout.setOrientation(LinearLayout.VERTICAL);
        rootView.addView(playerListLayout);

        // minimap
        minimapTv = new TextView(this);
        minimapTv.setTextSize(7.5f);
        minimapTv.setTypeface(Typeface.MONOSPACE);
        minimapTv.setTextColor(Color.WHITE);
        rootView.addView(minimapTv);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
              | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 10; params.y = 100;
        wm.addView(rootView, params);
    }

    private void setDraggable(View handle) {
        final WindowManager.LayoutParams[] lp = { (WindowManager.LayoutParams) rootView.getLayoutParams() };
        final float[] lx = {0}, ly = {0};
        handle.setOnTouchListener((v, e) -> {
            switch(e.getAction()) {
                case MotionEvent.ACTION_DOWN: lx[0]=e.getRawX(); ly[0]=e.getRawY(); break;
                case MotionEvent.ACTION_MOVE:
                    lp[0].x+=(int)(e.getRawX()-lx[0]); lp[0].y+=(int)(e.getRawY()-ly[0]);
                    lx[0]=e.getRawX(); ly[0]=e.getRawY();
                    wm.updateViewLayout(rootView, lp[0]); break;
            }
            return true;
        });
    }

    private void connectFrida() {
        new Thread(() -> {
            frida = new FridaBridge();
            for(int i=0;i<15;i++){
                if(frida.connect() && frida.injectScript()){
                    connected=true;
                    handler.post(()->statusTv.setText("Connected ✓"));
                    return;
                }
                try{Thread.sleep(2000);}catch(Exception ig){}
                int a=i+1;
                handler.post(()->statusTv.setText("Connecting... ("+a+"/15)"));
            }
            handler.post(()->statusTv.setText("Failed — open FF first"));
        }).start();
    }

    private void startTicker() {
        ticker = new Runnable() {
            @Override public void run() {
                if(connected && espEnabled){
                    new Thread(()->{
                        String raw = frida.callScript("getPlayers");
                        handler.post(()->renderPanel(raw));
                    }).start();
                }
                handler.postDelayed(this, 150);
            }
        };
        handler.postDelayed(ticker, 1500);
    }

    private void renderPanel(String raw) {
        if(raw==null) return;
        try {
            JSONObject root  = new JSONObject(raw);
            JSONObject local = root.getJSONObject("local");
            JSONArray  pArr  = root.getJSONArray("players");
            float lx=(float)local.getDouble("x"),ly=(float)local.getDouble("y"),lz=(float)local.getDouble("z");
            int lt=local.getInt("team");

            headerTv.setText(String.format("Pos: %.0f/%.0f/%.0f | Cnt: %d", lx,ly,lz, pArr.length()));
            playerListLayout.removeAllViews();
            playerListLayout.addView(makeText("  # Name         Dist  Dir  Hgt", Color.YELLOW));
            playerListLayout.addView(makeDivider());

            List<float[]> pos = new ArrayList<>();
            for(int i=0;i<pArr.length()&&i<12;i++){
                JSONObject p = pArr.getJSONObject(i);
                float px=(float)p.getDouble("x"),py=(float)p.getDouble("y"),pz=(float)p.getDouble("z");
                int team=p.getInt("team");
                String nm=p.optString("name","???");
                if(nm.length()>10) nm=nm.substring(0,10);
                float dist=dist3(lx,ly,lz,px,py,pz), hgt=py-ly;
                String dir=dir2(lx,lz,px,pz);
                boolean enemy=team!=lt;
                int color=enemy?Color.rgb(255,80,80):Color.rgb(80,255,80);
                playerListLayout.addView(makeText(String.format("  %-2d %-10s %4.0fm %-2s %+.0f",i+1,nm,dist,dir,hgt),color));
                pos.add(new float[]{px,pz,enemy?1:0});
            }
            minimapTv.setText(buildMinimap(pos,lx,lz));
        } catch(Exception e){ statusTv.setText("Parse err"); }
    }

    private String buildMinimap(List<float[]> pos, float lx, float lz){
        int W=22,H=10; char[] g=new char[W*H]; Arrays.fill(g,'·');
        for(float[] p:pos){
            int cx=W/2+(int)((p[0]-lx)/20f), cy=H/2+(int)((p[1]-lz)/20f);
            if(cx>=0&&cx<W&&cy>=0&&cy<H) g[cy*W+cx]=p[2]==1?'E':'T';
        }
        g[(H/2)*W+W/2]='@';
        StringBuilder sb=new StringBuilder("\n");
        for(int r=0;r<H;r++) sb.append(" ").append(new String(g,r*W,W)).append("\n");
        return sb.toString();
    }

    private float dist3(float x1,float y1,float z1,float x2,float y2,float z2){
        float dx=x2-x1,dy=y2-y1,dz=z2-z1; return (float)Math.sqrt(dx*dx+dy*dy+dz*dz);
    }
    private String dir2(float lx,float lz,float px,float pz){
        double a=Math.toDegrees(Math.atan2(px-lx,pz-lz)); if(a<0)a+=360;
        if(a<22.5||a>=337.5)return"N"; if(a<67.5)return"NE"; if(a<112.5)return"E";
        if(a<157.5)return"SE"; if(a<202.5)return"S"; if(a<247.5)return"SW"; if(a<292.5)return"W"; return"NW";
    }
    private TextView makeText(String s,int color){
        TextView tv=new TextView(this); tv.setText(s); tv.setTextColor(color);
        tv.setTextSize(8.5f); tv.setTypeface(Typeface.MONOSPACE); return tv;
    }
    private View makeDivider(){
        View v=new View(this); v.setBackgroundColor(Color.DKGRAY);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,1)); return v;
    }
    private Notification buildNotif(){
        NotificationChannel ch=new NotificationChannel("esp","FF ESP",NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        return new Notification.Builder(this,"esp").setContentTitle("FF ESP Active")
            .setSmallIcon(android.R.drawable.ic_menu_view).build();
    }
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){
        if(handler!=null) handler.removeCallbacks(ticker);
        if(wm!=null&&rootView!=null) try{wm.removeView(rootView);}catch(Exception ig){}
    }
}
