package com.udacity.project4.locationreminders.savereminder.selectreminderlocation


import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.navigation.fragment.findNavController
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PointOfInterest
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import com.udacity.project4.BuildConfig
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSelectLocationBinding
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import org.koin.android.ext.android.inject
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationCallback
import java.util.concurrent.TimeUnit


const val LOCATION_PERMISSION_INDEX = 0
const val BACKGROUND_LOCATION_PERMISSION_INDEX = 1
const val FOREGROUND_LOCATION_PERMISSION_INDEX = 3
private const val REQUEST_TURN_DEVICE_LOCATION_ON = 29
const val REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE = 33
private const val REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 34



class SelectLocationFragment : BaseFragment(), OnMapReadyCallback, GoogleMap.OnMyLocationButtonClickListener {

    private lateinit var googleMap: GoogleMap

    private lateinit var selectedLocation : LatLng

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private lateinit var locationCallback: LocationCallback

    private var currentLocation: Location? = null

    private val locationRequest: LocationRequest = LocationRequest.create().apply {
        interval = TimeUnit.SECONDS.toMillis(60)
        fastestInterval = TimeUnit.SECONDS.toMillis(30)
        maxWaitTime = TimeUnit.MINUTES.toMillis(2)
        priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
    }

    private lateinit var binding: FragmentSelectLocationBinding

    private lateinit var selectedPoi: PointOfInterest

    //Use Koin to get the view model of the SaveReminder
    override val _viewModel: SaveReminderViewModel by inject()



    @SuppressLint("VisibleForTests")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        binding = DataBindingUtil.inflate(
            inflater, R.layout.fragment_select_location, container, false)

        binding.viewModel = _viewModel
        binding.lifecycleOwner = this


        setHasOptionsMenu(true)
        setDisplayHomeAsUpEnabled(true)

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)

                // Normally, you want to save a new location to a database. We are simplifying
                // things a bit and just saving it as a local variable, as we only need it again
                // if a Notification is created (when the user navigates away from app).
                currentLocation = locationResult.lastLocation


            }
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment

        mapFragment.getMapAsync(this)

        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        binding.saveLocation.setOnClickListener {
            _viewModel.navigationCommand.value  = NavigationCommand.Back
        }

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

    @SuppressLint("MissingPermission")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
            if(!foregroundPermissionGranted() || !googleMap.isMyLocationEnabled ){
                findNavController().popBackStack()
            }
        Toast.makeText(requireContext(), "onActivityResult", Toast.LENGTH_SHORT).show()

    }

    override fun onMapReady(p0: GoogleMap) {
        Log.e("SelectLocationFragment", "OnMapReadyCallback")

        googleMap = p0

        setMapClick(googleMap)
        setMapStyle(googleMap)
        setPoiClick(googleMap)
        googleMap.setOnMyLocationButtonClickListener(this)

        enableMyLocation()

    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fusedLocationProviderClient.requestLocationUpdates(locationRequest,
            locationCallback,
            Looper.getMainLooper())
    }



    /** Request permissions and enable my location**/

    private fun foregroundPermissionGranted() : Boolean{
        Log.e(TAG, "check self permission")
        val accessFineLocationGranted = (
                PackageManager.PERMISSION_GRANTED ==
                        ActivityCompat.checkSelfPermission(requireContext(),
                            Manifest.permission.ACCESS_FINE_LOCATION))
        val accessCoarseLocationGranted =
                PackageManager.PERMISSION_GRANTED ==
                        ActivityCompat.checkSelfPermission(
                            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)

        return accessFineLocationGranted && accessCoarseLocationGranted
    }

    private fun requestForegroundPermissions(){
        requestPermissions(
            arrayOf<String>(Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
        )
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation(){
        if (foregroundPermissionGranted()) {
            Log.e(TAG, "enableMyLocation(): clear()")
            googleMap.getUiSettings().setMyLocationButtonEnabled(true)
            googleMap.setMyLocationEnabled(true)
            getLastLocation()
//            Toast.makeText(requireContext(), "Permission was granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Request permission", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "enableMyLocation(): request Permission")
            requestForegroundPermissions()
        }

    }


    override fun onMyLocationButtonClick(): Boolean {
        getLastLocation()
        Toast.makeText(requireContext(), "MyLocation button clicked", Toast.LENGTH_SHORT)
            .show()
        return false
    }



    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray) {
        // Check if location permissions are granted and if so enable the location data layer.
        if (requestCode == REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE) {
            Log.e(TAG, "onRequestPermissionsResult(): request Code")
            // Request for location permission.
            if (grantResults.isNotEmpty() && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Log.e(TAG, "onRequestPermissionsResult(): permission Granted")
                enableMyLocation()
            }
            else {
                // Permission denied.
                Snackbar.make(
                    this.requireView(),
                    R.string.permission_denied_explanation,
                    Snackbar.LENGTH_LONG
                )
                    .setAction(R.string.settings) {
                        startActivity(Intent().apply {
                            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }.show()


                binding.saveLocation.setOnClickListener {
                    findNavController().popBackStack()
                }
            }

        }

    }

    private fun currentLocationRequest() {


        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(this.requireActivity())
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())


        task.addOnSuccessListener {
            // All location settings are satisfied. The client can initialize
            // location requests here.
            // ...
            Toast
                .makeText(
                    requireActivity(),
                    "You enabled your location please select a location.",
                    Toast.LENGTH_LONG)
                .show()

        }
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException){
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    startIntentSenderForResult(
                        exception.resolution.intentSender,
                        REQUEST_TURN_DEVICE_LOCATION_ON,
                        null,
                        0,
                        0,
                        0,
                        null)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                    Log.d(TAG, "Error getting location settings resolution: " + sendEx.message)
                }
            }
            else {
                Snackbar.make(
                    binding.saveLocation,
                    R.string.location_required_error,
                    Snackbar.LENGTH_INDEFINITE
                ).setAction(android.R.string.ok) {
                    currentLocationRequest()
                }.show()
            }
        }

    }



    /** OnMapReady callback*/

    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        Log.e(TAG, "getLastLocation():")
        startLocationUpdates()
        fusedLocationProviderClient.lastLocation.addOnSuccessListener(requireActivity()) {  location : Location? ->
            if (location != null) {
                // Zoom to last location
                val userLatLng = LatLng(location.latitude, location.longitude)
                val zoomLevel = 17.0f
                googleMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        userLatLng,
                        zoomLevel
                    )
                )
                Log.e(TAG, "getLastLocation(): lastLocation")

            } else{
                Log.e(TAG, "getLastLocation(): currentLocationRequest()")
                currentLocationRequest()
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
            selectedLocation = poi.latLng
            map.clear()
            map.addMarker(
                MarkerOptions()
                    .position(poi.latLng)
                    .title(poi.name)
            )?.showInfoWindow()
            _viewModel.latitude.value = selectedLocation.latitude
            _viewModel.longitude.value = selectedLocation.longitude
            _viewModel.reminderSelectedLocationStr.value = poi.name
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
