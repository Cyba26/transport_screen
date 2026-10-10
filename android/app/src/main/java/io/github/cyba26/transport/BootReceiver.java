package io.github.cyba26.transport;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Relance l'actualisation au déverrouillage après un redémarrage ou une mise à jour de l'appli. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            UnlockService.start(context);
        }
    }
}
