package com.mindosc.freenux

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/** Αυτόματο backup μία φορά την εβδομάδα, όταν το κινητό φορτίζει. */
class BackupJob : JobService() {
    companion object {
        private const val ID = 4242
        private const val WEEK = 7L * 24 * 60 * 60 * 1000

        fun schedule(ctx: Context, on: Boolean) {
            val js = ctx.getSystemService(JobScheduler::class.java)
            if (!on) { js.cancel(ID); return }
            js.schedule(JobInfo.Builder(ID, ComponentName(ctx, BackupJob::class.java))
                .setPeriodic(WEEK)
                .setRequiresCharging(true)
                .setRequiresBatteryNotLow(true)
                .setPersisted(true)
                .build())
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        if (LinuxService.prefs(this).getBoolean("autobackup", false) && Linux(this).setupDone) {
            LogBus.log(">>> Αυτόματο εβδομαδιαίο backup")
            LinuxService.send(this, LinuxService.ACTION_BACKUP)
        }
        return false
    }

    override fun onStopJob(params: JobParameters?) = false
}
