package io.github.cyba26.transport;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

/** Choix de l'arrêt affiché, à l'ajout du widget ou en touchant son nom. */
public class WidgetConfigActivity extends Activity {

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Si l'utilisateur quitte sans choisir, le launcher annule l'ajout du widget
        setResult(RESULT_CANCELED);

        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (!isOwnWidget(widgetId)) {
            finish();
            return;
        }

        setContentView(R.layout.activity_widget_config);
        String current = WidgetStore.stationFor(this, widgetId);
        ViewGroup list = findViewById(R.id.station_list);
        LayoutInflater inflater = getLayoutInflater();
        for (Station station : Station.ALL) {
            View item = inflater.inflate(R.layout.item_station, list, false);
            ((ImageView) item.findViewById(R.id.station_badge)).setImageResource(station.badgeRes);
            ((TextView) item.findViewById(R.id.station_name)).setText(station.name);
            ((TextView) item.findViewById(R.id.station_line)).setText(station.lineLabel);
            item.findViewById(R.id.station_selected)
                    .setVisibility(station.id.equals(current) ? View.VISIBLE : View.INVISIBLE);
            item.setOnClickListener(v -> select(station));
            list.addView(item);
        }

        findViewById(R.id.battery_button).setOnClickListener(v -> requestBatteryExemption());
    }

    @Override
    protected void onResume() {
        super.onResume();
        View battery = findViewById(R.id.battery_section);
        if (battery != null) {
            PowerManager power = getSystemService(PowerManager.class);
            boolean exempted = power.isIgnoringBatteryOptimizations(getPackageName());
            battery.setVisibility(exempted ? View.GONE : View.VISIBLE);
        }
    }

    private void select(Station station) {
        WidgetStore.setStation(this, widgetId, station.id);
        UnlockService.start(this);
        WidgetUpdater.refresh(this, new int[]{widgetId}, true, null);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        finish();
    }

    /** Sans exemption, certains constructeurs (Realme, Xiaomi…) tuent le service de déverrouillage. */
    private void requestBatteryExemption() {
        Intent request = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()));
        try {
            startActivity(request);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private boolean isOwnWidget(int id) {
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return false;
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
        return info != null && getPackageName().equals(info.provider.getPackageName());
    }
}
