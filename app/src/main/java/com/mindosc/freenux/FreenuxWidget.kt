package com.mindosc.freenux

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Widget αρχικής οθόνης: κατάσταση + Άνοιγμα / Τερματισμός / Ρυθμίσεις. */
class FreenuxWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = update(ctx)

    companion object {
        fun update(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, FreenuxWidget::class.java))
            if (ids.isEmpty()) return
            val v = RemoteViews(ctx.packageName, R.layout.freenux_widget)
            v.setTextViewText(R.id.w_status, "Freenux · " + LogBus.state)

            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val open = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            v.setOnClickPendingIntent(R.id.w_open, PendingIntent.getActivity(ctx, 20, open, flags))
            val stop = Intent(ctx, LinuxService::class.java).setAction(LinuxService.ACTION_STOP)
            v.setOnClickPendingIntent(R.id.w_stop, PendingIntent.getForegroundService(ctx, 21, stop, flags))
            val settings = Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_SETTINGS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            v.setOnClickPendingIntent(R.id.w_settings, PendingIntent.getActivity(ctx, 22, settings, flags))
            mgr.updateAppWidget(ids, v)
        }
    }
}
