package com.clevertap.android.sdk;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.net.URLDecoder;

public abstract class CTInAppBasePartialHtmlFragment extends CTInAppBasePartialFragment implements  View.OnTouchListener, View.OnLongClickListener{

    private CTInAppWebView webView;
    private final GestureDetector gd = new GestureDetector(new GestureListener());

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        return displayHTMLView(inflater,container);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        reDrawInApp();
    }

    abstract View getView(LayoutInflater inflater, ViewGroup container);
    abstract ViewGroup getLayout(View view);

    private View displayHTMLView(LayoutInflater inflater, ViewGroup container){
        View inAppView;
        ViewGroup layout;
        try {
            inAppView = getView(inflater, container);
            layout = getLayout(inAppView);
            webView = new CTInAppWebView(getActivity().getBaseContext(), inAppNotification.getWidth(), inAppNotification.getHeight(), inAppNotification.getWidthPercentage(), inAppNotification.getHeightPercentage());
            InAppWebViewClient webViewClient = new InAppWebViewClient();
            webView.setWebViewClient(webViewClient);
            webView.setOnTouchListener(CTInAppBasePartialHtmlFragment.this);
            webView.setOnLongClickListener(CTInAppBasePartialHtmlFragment.this);

            if(layout != null) {
                layout.addView(webView);
            }
        }catch (Throwable t){
            config.getLogger().verbose(config.getAccountId(),"Fragment view not created",t);
            return null;
        }
        return inAppView;
    }

    private class InAppWebViewClient extends WebViewClient {
        InAppWebViewClient() {
            super();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            final Bundle formData;
            try {
                formData = UriHelper.getAllKeyValuePairs(url, false);

                if (formData != null && formData.containsKey(Constants.KEY_C2A)) {
                    final String c2a = formData.getString(Constants.KEY_C2A);
                    if (c2a != null) {
                        final String[] parts = c2a.split("__dl__");
                        if (parts.length == 2) {
                            // Decode it here as wzrk_c2a is not decoded by UriHelper
                            formData.putString("wzrk_c2a", URLDecoder.decode(parts[0], "UTF-8"));
                            url = parts[1];
                        }
                    }
                }

                didClick(formData);
                Logger.d("Executing call to action for in-app: " + url);
                fireUrlThroughIntent(url,formData);
            } catch (Throwable t) {
                Logger.v("Error parsing the in-app notification action!", t);
            }
            return true;
        }
    }

    private void reDrawInApp() {
        webView.updateDimension();

        int mHeight = webView.dim.y;
        int mWidth = webView.dim.x;

        float d = getResources().getDisplayMetrics().density;
        mHeight /= d;
        mWidth /= d;

        String html = inAppNotification.getHtml();

        String style = "<style>body{width:" + mWidth + "px; height: " + mHeight + "px; margin: 0; padding:0;}</style>";
        html = html.replaceFirst("<head>", "<head>" + style);
        Logger.v("Density appears to be " + d);

        webView.setInitialScale((int) (d * 100));
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    @Override
    public boolean onLongClick(View v) {
        return true;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return gd.onTouchEvent(event) || (event.getAction() == MotionEvent.ACTION_MOVE);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        reDrawInApp();
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        private final int SWIPE_MIN_DISTANCE = 120;
        private final int SWIPE_THRESHOLD_VELOCITY = 200;

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                // Right to left
                return remove(e1, e2, false);
            } else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                // Left to right
                return remove(e1, e2, true);
            }
            return false;
        }

        @SuppressWarnings("UnusedParameters")
        private boolean remove(MotionEvent e1, MotionEvent e2, boolean ltr) {
            AnimationSet animSet = new AnimationSet(true);
            TranslateAnimation anim;
            if (ltr)
                anim = new TranslateAnimation(0, getScaledPixels(50), 0, 0);
            else
                anim = new TranslateAnimation(0, -getScaledPixels(50), 0, 0);
            animSet.addAnimation(anim);
            animSet.addAnimation(new AlphaAnimation(1, 0));
            animSet.setDuration(300);
            animSet.setFillAfter(true);
            animSet.setFillEnabled(true);
            animSet.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    didDismiss(null);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            webView.startAnimation(animSet);
            return true;
        }
    }
}
