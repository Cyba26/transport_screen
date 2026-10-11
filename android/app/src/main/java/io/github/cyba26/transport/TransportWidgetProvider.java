package io.github.cyba26.transport;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** Widget d'écran d'accueil : prochains passages d'un arrêt. */
public class TransportWidgetProvider extends AppWidgetProvider {

    static final String ACTION_REFRESH = BuildConfig.APPLICATION_ID + ".action.REFRESH";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_REFRESH.equals(intent.getAction())) {
            int[] widgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
            if (widgetIds == null) widgetIds = WidgetUpdater.widgetIds(context);
            // Un clic sur le widget autorise le démarrage du service s'il avait été arrêté
            UnlockService.start(context);
            PendingResult result = goAsync();
            WidgetUpdater.refresh(context, widgetIds, true, result::finish);
            return;
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        // Relance le service s'il a été tué ; autorisé en arrière-plan avec l'exemption de batterie
        UnlockService.start(context);
        PendingResult result = goAsync();
        WidgetUpdater.refresh(context, widgetIds, false, result::finish);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int widgetId, Bundle options) {
        // Redimensionnement : avant Android 12 c'est à nous de choisir la mise en page
        WidgetUpdater.render(context, manager, widgetId);
    }

    @Override
    public void onDeleted(Context context, int[] widgetIds) {
        for (int widgetId : widgetIds) WidgetStore.removeWidget(context, widgetId);
    }

    @Override
    public void onEnabled(Context context) {
        UnlockService.start(context);
    }

    @Override
    public void onDisabled(Context context) {
        UnlockService.stop(context);
    }
}
