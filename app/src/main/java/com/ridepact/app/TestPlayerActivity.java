package com.ridepact.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;

public class TestPlayerActivity extends AppCompatActivity {

    private FrameLayout mTargetView;
    private FrameLayout mContentView;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;
//    private View mCustomView;
//    private MyChromeClient mClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //Intent intent = getIntent();
        //String video = intent.getStringExtra("video");

        WebView webView = findViewById(R.id.webview);

        //mClient = new MyChromeClient();
        //webView.setWebChromeClient(mClient);

        mContentView = (FrameLayout) findViewById(R.id.main_content);
        mTargetView = (FrameLayout)findViewById(R.id.target_view);


        //webView.loadUrl("file:///" + video);
        webView.loadUrl("file:///android_asset/schedule13.html");

        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setJavaScriptEnabled(true);
        //webView.getSettings().setLoadWithOverviewMode(true);
        //webView.getSettings().setUseWideViewPort(true);

    }

//    @Override
//    public void onBackPressed(){
//        if (mCustomView != null){
//            mClient.onHideCustomView();
//        }else{
//            finish();
//        }
//    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {

            // Full screen
            hideSystemUI();

            // Change status bar color to dark
            setSystemBarTheme(this, false);

        }
    }

    private void hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        View decorView = getWindow().getDecorView();
        // Hide the status bar.
        int uiOptions = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
    }

    private void setFullScreenLayout(){
        //make translucent statusBar on kitkat devices
        if (Build.VERSION.SDK_INT >= 19 && Build.VERSION.SDK_INT < 21) {
            setWindowFlag(this, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, true);
        }

        //make fully Android Transparent Status bar
        if (Build.VERSION.SDK_INT >= 21) {
            setWindowFlag(this, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, false);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        }
    }

    public static void setWindowFlag(Activity activity, final int bits, boolean on) {
        Window win = activity.getWindow();
        WindowManager.LayoutParams winParams = win.getAttributes();
        if (on) {
            winParams.flags |= bits;
        } else {
            winParams.flags &= ~bits;
        }
        win.setAttributes(winParams);
    }

    private static final void setSystemBarTheme(final Activity pActivity, final boolean pIsDark) {

        // Fetch the current flags.
        final int lFlags = pActivity.getWindow().getDecorView().getSystemUiVisibility();

        // Update the SystemUiVisibility dependening on whether we want a Light or Dark theme.
        pActivity.getWindow().getDecorView().setSystemUiVisibility(pIsDark ? (lFlags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) : (lFlags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR));

    }

//    class MyChromeClient extends WebChromeClient {
//
//        @Override
//        public void onShowCustomView(View view, CustomViewCallback callback) {
//            mCustomViewCallback = callback;
//            mTargetView.addView(view);
//            mCustomView = view;
//            mContentView.setVisibility(View.GONE);
//            mTargetView.setVisibility(View.VISIBLE);
//            mTargetView.bringToFront();
//        }
//
//        @Override
//        public void onHideCustomView() {
//            if (mCustomView == null)
//                return;
//
//            mCustomView.setVisibility(View.GONE);
//            mTargetView.removeView(mCustomView);
//            mCustomView = null;
//            mTargetView.setVisibility(View.GONE);
//            mCustomViewCallback.onCustomViewHidden();
//            mContentView.setVisibility(View.VISIBLE);
//        }
//    }

}
