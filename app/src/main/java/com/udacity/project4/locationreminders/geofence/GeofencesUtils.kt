package com.udacity.project4.locationreminders.geofence

import android.content.Context
import android.content.res.Resources
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.GeofenceStatusCodes
import com.udacity.project4.R


/**
 * Returns the error string for a geofencing error code.
 */
internal object GeofenceErrorMessages {
    /**
     * Returns the error string for a geofencing exception.
     */
    fun getErrorString(context: Context, e: Exception?): String {
        return if (e is ApiException) {
            getErrorString(context, (e as ApiException).statusCode)
        } else {
            context.resources.getString(R.string.unknown_geofence_error)
        }
    }

    /**
     * Returns the error string for a geofencing error code.
     */
    private fun getErrorString(context: Context, errorCode: Int): String {
        val mResources: Resources = context.resources
        return when (errorCode) {
            GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> mResources.getString(R.string.geofence_not_available)
            GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES -> mResources.getString(R.string.geofence_too_many_geofences)
            GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS -> mResources.getString(R.string.geofence_too_many_pending_intents)
            else -> mResources.getString(R.string.unknown_geofence_error)
        }
    }
}