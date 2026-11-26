package io.github.cyba26.transport;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Après un redémarrage ou une mise à jour de l'appli : relance le service et recharge les widgets. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            UnlockService.start(context);
            // Recharge tout de suite avec le nouveau code : sans ça, le widget gardait le résultat
            // de l'ancienne version (par ex. « Aucun passage ») jusqu'au prochain déverrouillage
            int[] widgetIds = WidgetUpdater.widgetIds(context);
            if (widgetIds.length > 0) {
                PendingResult result = goAsync();
                WidgetUpdater.refresh(context, widgetIds, true, result::finish);
            }
        }
    }
}
