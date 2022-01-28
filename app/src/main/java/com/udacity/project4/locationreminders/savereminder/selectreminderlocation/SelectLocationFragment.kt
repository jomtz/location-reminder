package com.udacity.project4.locationreminders.savereminder.selectreminderlocation


import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.databinding.FragmentSelectLocationBinding
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import org.koin.android.ext.android.inject

import com.google.android.gms.maps.model.LatLng
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.material.snackbar.Snackbar
import com.udacity.project4.BuildConfig
import com.udacity.project4.locationreminders.savereminder.LOCATION_PERMISSION_INDEX
import com.udacity.project4.locationreminders.savereminder.REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE


private const val REQUEST_TURN_DEVICE_LOCATION_ON = 29
private const val REQUEST_LOCATION_PERMISSION = 1

private const val GEOFENCE_RADIUS = 200f


class SelectLocationFragment : BaseFragment() {

    private var permissionDenied = false

    private lateinit var googleMap: GoogleMap

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var binding: FragmentSelectLocationBinding
    lateinit var geofencingClient: GeofencingClient
    private lateinit var selectedPoi: PointOfInterest

    //Use Koin to get the view model of the SaveReminder
    override val _viewModel: SaveReminderViewModel by inject()


    private val callback = OnMapReadyCallback { gMap ->
        Log.e("SelectLocationFragment", "OnMapReadyCallback")

        googleMap = gMap
        enableLocationOrRequestPermission()
        zoomToLocation()
        setMapStyle(googleMap)
        setPoiClick(googleMap)
        setMapClick(googleMap)

    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        binding = DataBindingUtil.inflate(
            inflater, R.layout.fragment_select_location, container, false)

        binding.viewModel = _viewModel
        binding.lifecycleOwner = this

        setHasOptionsMenu(true)
        setDisplayHomeAsUpEnabled(true)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(callback)
        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireContext())

        return binding.root
    }



    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.map_options, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.normal_map -> {
            googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL
            true
        }
        R.id.hybrid_map -> {
            googleMap.mapType = GoogleMap.MAP_TYPE_HYBRID
            true
        }
        R.id.satellite_map -> {
            googleMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
            true
        }
        R.id.terrain_map -> {
            googleMap.mapType = GoogleMap.MAP_TYPE_TERRAIN
            true
        }
        else -> super.onOptionsItemSelected(item)
    }



    private fun onLocationSelected() {
        _viewModel.selectedPOI.value = selectedPoi
        _viewModel.latitude.value = selectedPoi.latLng.latitude
        _viewModel.longitude.value = selectedPoi.latLng.longitude
        _viewModel.reminderSelectedLocationStr.value = selectedPoi.name
        parentFragmentManager.popBackStack()
    }



//    @SuppressLint("MissingPermission")
//    private fun requestDeviceLocation(resolve:Boolean = true) {
//        val locationRequest = LocationRequest.create().apply {
//            priority = LocationRequest.PRIORITY_LOW_POWER
//        }
//        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
//        val settingsClient = LocationServices.getSettingsClient(requireActivity())
//        val locationSettingsResponseTask =
//            settingsClient.checkLocationSettings(builder.build())
//
//        locationSettingsResponseTask.addOnFailureListener {
//                exception ->
//            if (exception is ResolvableApiException && resolve){
//                try {
//                    exception.startResolutionForResult(requireActivity(),
//                        REQUEST_TURN_DEVICE_LOCATION_ON)
//                } catch (sendEx: IntentSender.SendIntentException) {
//                    Log.d(TAG, "Error getting location settings resolution: " + sendEx.message)
//                }
//            } else {
//                Snackbar.make(
//                    binding.constraintLayout,
//                    R.string.location_required_error, Snackbar.LENGTH_INDEFINITE
//                ).setAction(android.R.string.ok) {
//                    requestDeviceLocation()
//                }.show()
//            }
//        }
//        locationSettingsResponseTask.addOnCompleteListener {
//            if ( it.isSuccessful ) {
//                googleMap.isMyLocationEnabled = true
//            }
//        }
//    }

//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == REQUEST_TURN_DEVICE_LOCATION_ON) {
//            requestDeviceLocation(true)
//        }
//    }




    /**
     * Enables the My Location layer if the fine location permission has been granted.
     */

    private fun isForegroundPermissionGranted(): Boolean {
        return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
            this.requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    @SuppressLint("MissingPermission")
    private fun enableLocationOrRequestPermission() {
        if (isForegroundPermissionGranted()) {
            Log.d(TAG, "Request foreground only location permission")
            googleMap.isMyLocationEnabled = true
        } else {
            requestPermissions(
                arrayOf<String>(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
        }
    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray) {

        Log.d(TAG, "onRequestPermissionResult")
        if (
            (grantResults.isEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED)
        ) {
            Log.e("Created error", "granted location permission")
            Toast.makeText(requireContext(), "Permission is granted", Toast.LENGTH_SHORT)
                .show()
            enableLocationOrRequestPermission()

        } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    requireActivity(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                //Show permission explanation dialog...
                Log.e("Created error", "denied permission snackbar")
                Snackbar.make(
                    binding.constraintLayout,
                    R.string.permission_denied_explanation,
                    Snackbar.LENGTH_INDEFINITE
                )
                    .setAction(R.string.settings) {
                        startActivity(Intent().apply {
                            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            data =
                                Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }.show()
            }
        } else {
            Log.e("Created error", "denied permission snackbar")

        }

    }

    @SuppressLint("MissingPermission")
    fun zoomToLocation() {
        fusedLocationProviderClient.lastLocation.addOnSuccessListener(requireActivity()) { location ->
            if (location != null) {
                val userLatLng = LatLng(location.latitude, location.longitude)
                val zoomLevel = 17.0f
                googleMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        userLatLng,
                        zoomLevel
                    )
                )
            }
        }
    }


    private fun setMapClick(map: GoogleMap) {
        map.setOnMapClickListener {
            binding.saveLocation.visibility = View.VISIBLE
            binding.saveLocation.setOnClickListener { view ->
                _viewModel.latitude.value = it.latitude
                _viewModel.longitude.value = it.longitude
                _viewModel.reminderSelectedLocationStr.value = "Selected location"
                findNavController().popBackStack()
            }

            val cameraUpdate = CameraUpdateFactory.newLatLngZoom(it, 17f)
            map.moveCamera(cameraUpdate)
            val poiMarker = map.addMarker(MarkerOptions()
                .position(it)
            )
            poiMarker.showInfoWindow()

        }
    }




    private fun setPoiClick(map: GoogleMap) {
        map.setOnPoiClickListener { poi ->
            binding.saveLocation.visibility = View.VISIBLE
            binding.saveLocation.setOnClickListener {
                onLocationSelected()
            }
            val poiMarker = map.addMarker(
                MarkerOptions()
                    .title(poi.name)
                    .position(poi.latLng)
            )
            poiMarker.showInfoWindow()
        }

    }

    private fun setMapStyle(map: GoogleMap) {
        try {
            val success = map.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    requireContext(),
                    R.raw.map_style
                )
            )
            if (!success) {
                Log.e(TAG, "Style parsing failed.")
            }
        } catch (e: Resources.NotFoundException) {
            Log.e(TAG, "Can't find style. Error: ", e)
        }
    }


}
