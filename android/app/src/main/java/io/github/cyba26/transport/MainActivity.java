package io.github.cyba26.transport;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Le tableau de bord web (index.html sur Vercel) dans une WebView plein écran. */
public class MainActivity extends Activity {

    private static final String HOST = Uri.parse(BuildConfig.BASE_URL).getHost();

    private FrameLayout webContainer;
    private View notificationBanner;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.app_background));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 15 dessine sous les barres système : on garde la page en dehors
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsets.CONSUMED;
            });
        }
        notificationBanner = getLayoutInflater().inflate(R.layout.view_notification_banner, root, false);
        notificationBanner.findViewById(R.id.notification_settings_button)
                .setOnClickListener(v -> openNotificationSettings());
        root.addView(notificationBanner);
        webContainer = new FrameLayout(this);
        root.addView(webContainer, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        createWebView();
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(BuildConfig.BASE_URL);
        }

        UnlockService.start(this);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createWebView() {
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        // localStorage : thème et temps de marche par station
        settings.setDomStorageEnabled(true);
        // Permet au site de savoir qu'il tourne dans l'appli (lien « mettre à jour l'appli »)
        settings.setUserAgentString(settings.getUserAgentString() + " TransportScreenApp/" + BuildConfig.VERSION_NAME);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (HOST.equals(url.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, url));
                } catch (ActivityNotFoundException ignored) {
                    // Aucun navigateur : on reste sur la page
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showOfflinePage();
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // Le moteur de rendu a planté : on repart sur une WebView neuve
                webContainer.removeView(view);
                view.destroy();
                createWebView();
                webView.loadUrl(BuildConfig.BASE_URL);
                return true;
            }
        });
        webContainer.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void showOfflinePage() {
        String html = "<html><body style=\"font-family:sans-serif;background:#f0f4ff;color:#0f172a;"
                + "display:flex;flex-direction:column;align-items:center;justify-content:center;height:90vh;"
                + "text-align:center\"><p>" + getString(R.string.offline_message) + "</p>"
                + "<p><a style=\"color:#2563eb\" href=\"" + BuildConfig.BASE_URL + "\">"
                + getString(R.string.offline_retry) + "</a></p></body></html>";
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        // Revérifié à chaque retour : le bandeau disparaît dès que l'utilisateur a activé les notifications
        notificationBanner.setVisibility(notificationsEnabled() ? View.GONE : View.VISIBLE);
        // Les minuteries JS sont gelées en arrière-plan : on rafraîchit dès le retour
        webView.evaluateJavascript("if (typeof fetchData === 'function') fetchData();", null);
    }

    /**
     * Sans notification visible, Android (et surtout Realme, Xiaomi…) arrête plus facilement le service
     * qui met le widget à jour au déverrouillage.
     */
    private boolean notificationsEnabled() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (!manager.areNotificationsEnabled()) return false;
        NotificationChannel channel = manager.getNotificationChannel(UnlockService.CHANNEL_ID);
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }
}
