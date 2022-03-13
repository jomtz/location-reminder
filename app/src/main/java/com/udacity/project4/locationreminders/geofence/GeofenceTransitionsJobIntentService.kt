package com.udacity.project4.locationreminders.geofence

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.app.JobIntentService
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.udacity.project4.R
import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.data.dto.Result
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.utils.sendNotification
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import kotlin.coroutines.CoroutineContext


class GeofenceTransitionsJobIntentService : JobIntentService(), CoroutineScope {

    private val TAG = "GeofenceTransitionsJobIntentService"
    // Get the local repository instance
    private val remindersLocalRepository: ReminderDataSource by inject()
    private var coroutineJob: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + coroutineJob

    companion object {
        private const val JOB_ID = 573
        fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(
                context,
                GeofenceTransitionsJobIntentService::class.java,
                JOB_ID,
                intent
            )
        }
    }


    @SuppressLint("LongLogTag", "StringFormatInvalid")
    override fun onHandleWork(@NonNull intent: Intent) {
        Log.e(TAG, "onHandleWork")

        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes
                .getStatusCodeString(geofencingEvent.errorCode)
            Log.e(TAG, errorMessage)
            return

        }
        // Get the transition type.
        val geofenceTransition = geofencingEvent.geofenceTransition

        // Test that the reported transition was of interest.
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
        geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {

            // Get the geofences that were triggered. A single event can trigger
            // multiple geofences.
            val triggeringGeofences = geofencingEvent.triggeringGeofences

            // Get the transition details as a String.
            val geofenceTransitionDetails = getGeofenceTransitionDetails(
                geofenceTransition,
                triggeringGeofences
            )

            ContextCompat.getSystemService(this.applicationContext, NotificationManager::class.java)
                    as NotificationManager
            // Send notification and log the transition details.
            sendNotification(triggeringGeofences)
            Log.e(TAG, triggeringGeofences.toString())
        } else {
            // Log the error.
            Log.e(TAG, getString(
                R.string.unknown_geofence_error,
                geofenceTransition))
        }


    }

    @SuppressLint("LongLogTag")
    private fun sendNotification(triggeringGeofences: List<Geofence>) {
        Log.e(TAG, "sendNotification()")


        /** TODO: Add a loop through all triggering Geofences
         *  TODO: Send a notification for each and every single one of them
         *  */

        triggeringGeofences.forEach {

            val requestId = it.requestId

            CoroutineScope(coroutineContext).launch(SupervisorJob()) {
                //get the reminder with the request id
                val result = remindersLocalRepository.getReminder(requestId)
                Log.e(TAG, "sendNotification() -> if statement")

                if (result is Result.Success<ReminderDTO>) {
                    val reminderDTO = result.data
                    //send a notification to the user with the reminder details
                    sendNotification(
                        this@GeofenceTransitionsJobIntentService,
                        ReminderDataItem(
                            reminderDTO.title,
                            reminderDTO.description,
                            reminderDTO.location,
                            reminderDTO.latitude,
                            reminderDTO.longitude,
                            reminderDTO.id
                        )
                    )
                }
            }
        }

    }

    private fun getGeofenceTransitionDetails(geofenceTransition: Int,
                                             triggeringGeofences: List<Geofence>):String {
        val geofenceTransitionString: String = getTransitionString(geofenceTransition)

        // Get the Ids of each geofence that was triggered.
        val triggeringGeofencesIdsList: ArrayList<String?> = ArrayList()

        for (geofence in triggeringGeofences) {
            triggeringGeofencesIdsList.add(geofence.requestId)
        }
        val triggeringGeofencesIdsString = TextUtils.join(", ", triggeringGeofencesIdsList)
        return "$geofenceTransitionString : $triggeringGeofencesIdsString"
    }

    private fun getTransitionString(geofenceTransition: Int): String {
        return when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> getString(R.string.geofence_transition_entered)
            Geofence.GEOFENCE_TRANSITION_EXIT -> getString(R.string.geofence_transition_exited)
            else -> getString(R.string.unknown_geofence_transition)
        }
    }



}