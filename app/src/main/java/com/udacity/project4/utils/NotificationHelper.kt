package com.udacity.project4.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import com.udacity.project4.BuildConfig
import com.udacity.project4.R
import com.udacity.project4.locationreminders.ReminderDescriptionActivity
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem


class NotificationHelper(context: Context) {

    private val NOTIFICATION_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel"
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val name = context.getString(R.string.app_name)

    fun getUniqueId() = ((System.currentTimeMillis() % 10000).toInt())

    fun createNotification(context: Context, reminderDataItem: ReminderDataItem) {
        createNotificationChannel()
        val intent = ReminderDescriptionActivity.newIntent(context.applicationContext, reminderDataItem)
        val stackBuilder = TaskStackBuilder.create(context)
            .addParentStack(ReminderDescriptionActivity::class.java)
            .addNextIntent(intent)

        val notificationPendingIntent = stackBuilder
            .getPendingIntent(getUniqueId(), PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(reminderDataItem.title)
            .setContentText(reminderDataItem.location)
            .setContentIntent(notificationPendingIntent)
            .setAutoCancel(true)
            .build()

//        notificationManager.notify(getUniqueId(), notification)
        NotificationManagerCompat.from(context).notify(getUniqueId(), notification)
    }

    private fun createNotificationChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_HIGH
            )

            notificationManager.createNotificationChannel(channel)
        }
    }



}
