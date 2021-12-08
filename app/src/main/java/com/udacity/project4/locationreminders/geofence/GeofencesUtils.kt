package com.udacity.project4.locationreminders.geofence

import com.google.android.gms.maps.model.LatLng
import com.udacity.project4.R


data class LandmarkDataObject(val id: String, val hint: Int, val name: Int, val latLong: LatLng)

internal object GeofencingConstants {

    val LANDMARK_DATA = arrayOf(
        LandmarkDataObject(
            "golden_gate_bridge",
            R.string.golden_gate_bridge_hint,
            R.string.golden_gate_bridge_location,
            LatLng(37.819927, -122.478256)),

        LandmarkDataObject(
            "union_square",
            R.string.union_square_hint,
            R.string.union_square_location,
            LatLng(37.788151, -122.407570))
    )

    val NUM_LANDMARKS = LANDMARK_DATA.size
    const val GEOFENCE_RADIUS_IN_METERS = 100f
}