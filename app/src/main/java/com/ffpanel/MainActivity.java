package com.ffpanel;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xFF111111);
        layout.setPadding(50,80,50,50);

        TextView title = new TextView(this);
        title.setText("FF ESP Panel");
        title.setTextColor(0xFF00FFFF);
        title.setTextSize(26f);
        title.setPadding(0,0,0,10);
        layout.addView(title);

        TextView sub = new TextView(this);
        sub.setText("v1.0 — No Root Required");
        sub.setTextColor(0xFF888888);
        sub.setTextSize(12f);
        sub.setPadding(0,0,0,30);
        layout.addView(sub);

        TextView info = new TextView(this);
        info.setText("Steps:\n1. Install patched FF APK (from repack.py)\n2. Open Free Fire → join a match\n3. Come back here → tap START\n4. Switch to FF — panel appears on screen\n5. Drag ⠿ to move panel anywhere\n6. Toggle ESP ON/OFF with the switch");
        info.setTextColor(0xFFCCCCCC);
        info.setTextSize(13f);
        info.setPadding(0,0,0,30);
        layout.addView(info);

        layout.addView(makeBtn("▶  START ESP", 0xFF006600, v -> checkPermAndStart()));
        layout.addView(makeBtn("■  STOP ESP",  0xFF660000, v -> {
            stopService(new Intent(this, OverlayService.class));
            Toast.makeText(this,"ESP stopped",Toast.LENGTH_SHORT).show();
        }));

        setContentView(layout);
    }

    private Button makeBtn(String text, int color, View.OnClickListener click){
        Button b = new Button(this);
        b.setText(text); b.setBackgroundColor(color);
        b.setTextColor(0xFFFFFFFF); b.setTextSize(15f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,10,0,10); b.setLayoutParams(lp);
        b.setOnClickListener(click);
        return b;
    }

    private void checkPermAndStart(){
        if(!Settings.canDrawOverlays(this)){
            startActivityForResult(
                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:"+getPackageName())), 1);
        } else { launch(); }
    }

    private void launch(){
        startForegroundService(new Intent(this, OverlayService.class));
        Toast.makeText(this,"ESP starting — switch to FF",Toast.LENGTH_SHORT).show();
        moveTaskToBack(true);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data){
        if(req==1 && Settings.canDrawOverlays(this)) launch();
    }
}
