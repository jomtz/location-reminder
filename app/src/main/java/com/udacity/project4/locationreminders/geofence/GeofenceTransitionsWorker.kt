package com.udacity.project4.locationreminders.geofence

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class GeofenceTransitionsWorker(context: Context, workerParams: WorkerParameters):
    Worker(context, workerParams) {

    override fun doWork(): Result {


        return Result.success()
    }
}