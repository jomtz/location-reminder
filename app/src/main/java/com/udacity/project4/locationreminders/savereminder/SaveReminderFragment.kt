package com.udacity.project4.locationreminders.savereminder

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.location.Geofence.NEVER_EXPIRE
import com.google.android.material.snackbar.Snackbar
import com.udacity.project4.BuildConfig
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSaveReminderBinding
import com.udacity.project4.locationreminders.geofence.GeofenceBroadcastReceiver
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.locationreminders.reminderslist.ReminderListFragmentDirections
import com.udacity.project4.locationreminders.savereminder.selectreminderlocation.BACKGROUND_LOCATION_PERMISSION_INDEX
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import org.koin.android.ext.android.inject

const val REQUEST_BACKGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 35
private const val GEOFENCE_RADIUS = 200f

@SuppressLint("UnspecifiedImmutableFlag")
class SaveReminderFragment : BaseFragment() {

    private val REQUEST_TURN_DEVICE_LOCATION_ON = 1

    private lateinit var binding: FragmentSaveReminderBinding
    private lateinit var reminderDataItem: ReminderDataItem
    private lateinit var getContext: Context
    private lateinit var registerForActivityResult: ActivityResultLauncher<Intent>
    lateinit var geofencingClient: GeofencingClient
    private val runningQOrLater = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q

    //Get the view model this time as a single to be shared with the another fragment
    override val _viewModel: SaveReminderViewModel by inject()

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(this.getContext, GeofenceBroadcastReceiver::class.java)
        intent.action = ACTION_GEOFENCE_EVENT
        PendingIntent.getBroadcast(this.getContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater, R.layout.fragment_save_reminder, container, false)

        binding.viewModel = _viewModel

        binding.lifecycleOwner = this

        setDisplayHomeAsUpEnabled(true)

        geofencingClient = LocationServices.getGeofencingClient(this.getContext)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        binding.selectLocation.setOnClickListener {
            _viewModel.navigationCommand.value =
                NavigationCommand.To(
                    SaveReminderFragmentDirections.actionSaveReminderFragmentToSelectLocationFragment()
            )
        }


        // checkPermissionsAndStartGeofencing()
        binding.saveReminder.setOnClickListener {

            val title = _viewModel.reminderTitle.value
            val description = _viewModel.reminderDescription.value
            val location = _viewModel.reminderSelectedLocationStr.value
            val latitude = _viewModel.latitude.value
            val longitude = _viewModel.longitude.value

            reminderDataItem = ReminderDataItem(
                title,
                description,
                location,
                latitude,
                longitude
            )

            if(!title.isNullOrEmpty() && !location.isNullOrEmpty()) {
                if (backgroundLocationPermissionGranted()) {
                    Log.e(TAG, "saveReminder.setOnClickListener checkDeviceLocationSettingsAndStartGeofence")
                    checkDeviceLocationSettingsAndStartGeofence()
                } else {
                    requestBackgroundLocationPermission()
                }
            }

        }

    }

    /*
     * When we get the result from asking the user to turn on device location, we call
     * checkDeviceLocationSettingsAndStartGeofence again to make sure it's actually on, but
     * we don't resolve the check to keep the user from seeing an endless loop
     */

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_TURN_DEVICE_LOCATION_ON) {
            checkDeviceLocationSettingsAndStartGeofence(false)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        getContext = context
    }

    /**
     * This will also destroy any saved state in the associated ViewModel, so we remove the
     * geofences here.
     */
    override fun onDestroy() {
        super.onDestroy()
        //make sure to clear the view model after destroy, as it's a single view model.
        _viewModel.onClear()
    }

    /*
     *  Determines whether the app has the appropriate permissions across Android 10+ and all other
     *  Android versions.
     */
    @TargetApi(29)
    private fun backgroundLocationPermissionGranted(): Boolean {
        val foregroundLocationApproved = (
                PackageManager.PERMISSION_GRANTED ==
                        ActivityCompat.checkSelfPermission(requireContext(),
                            Manifest.permission.ACCESS_FINE_LOCATION))
        val backgroundPermissionGranted =
            if (runningQOrLater) {
                PackageManager.PERMISSION_GRANTED ==
                        ActivityCompat.checkSelfPermission(
                            requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        )
            } else {
                true
            }
        return backgroundPermissionGranted
    }


    /*
     *  Requests ACCESS_FINE_LOCATION and (on Android 10+ (Q) ACCESS_BACKGROUND_LOCATION.
     */
    @TargetApi(29 )
    private fun requestBackgroundLocationPermission() {
        val permissionsArray = arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        val requestCode = REQUEST_BACKGROUND_ONLY_PERMISSIONS_REQUEST_CODE

        Log.d(TAG, "Request foreground only location permission")
        requestPermissions(
            permissionsArray,
            requestCode
        )
    }


    /*
     * In all cases, we need to have the location permission.  On Android 10+ (Q) we need to have
     * the background permission as well.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        Log.d(TAG, "onRequestPermissionResult")

        if (grantResults.isEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED)
        {

            Snackbar.make(
                this.requireView(),
                R.string.permission_denied_explanation,
                Snackbar.LENGTH_INDEFINITE
            )
                .setAction(R.string.settings) {
                    startActivity(Intent().apply {
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }.show()

        } else {

            checkDeviceLocationSettingsAndStartGeofence()
        }
    }


    /*
     *  Uses the Location Client to check the current state of location settings, and gives the user
     *  the opportunity to turn on location services within our app.
     */

    private fun checkDeviceLocationSettingsAndStartGeofence(resolve:Boolean = true) {
//        val locationRequest = LocationRequest.create().apply {
//            priority = LocationRequest.PRIORITY_LOW_POWER
//        }
        val locationRequest: LocationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
            maxWaitTime = 60
//        val locationRequest = LocationRequest.create().apply {
//            interval = 10000
//            fastestInterval = 5000
//            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
//        }
        }
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val settingsClient = LocationServices.getSettingsClient(requireActivity())
        val locationSettingsResponseTask = settingsClient.checkLocationSettings(builder.build())


        locationSettingsResponseTask.addOnCompleteListener {
            if ( it.isSuccessful ) {
                Log.e(TAG, "checkDeviceLocationSettingsAndStartGeofence > addGeofenceForReminder()")

                addGeofenceForReminder()
                showToast(buildToastMessage("Reminder Saved !"))

            }
        }

        locationSettingsResponseTask.addOnFailureListener { exception ->
            if (exception is ResolvableApiException && resolve){
                try {
                    startIntentSenderForResult(
                        exception.resolution.intentSender,
                        REQUEST_TURN_DEVICE_LOCATION_ON,
                        null,
                        0,
                        0,
                        0,
                        null)
                } catch (sendEx: IntentSender.SendIntentException) {
                    Log.d(TAG, "Error getting location settings resolution: " + sendEx.message)
                }
            } else {
                Snackbar.make(
                    binding.saveReminder,
                    R.string.location_required_error,
                    Snackbar.LENGTH_INDEFINITE
                ).setAction(android.R.string.ok) {
                    checkDeviceLocationSettingsAndStartGeofence()
                }.show()
            }
        }



    }





    @SuppressLint("MissingPermission", "LongLogTag")
    private fun addGeofenceForReminder() {

        val reminderLatitude = reminderDataItem.latitude ?: 0.0
        val reminderLongitude = reminderDataItem.longitude ?: 0.0
            //Create geofence objects
        Log.e(TAG, "addGeofenceForReminder() > Geofence.builder")

        val geofence = Geofence.Builder()

                .setRequestId(reminderDataItem.id)
                .setCircularRegion(reminderLatitude, reminderLongitude, GEOFENCE_RADIUS)
                .setExpirationDuration(NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()

        Log.e(TAG, "addGeofenceForReminder() > GeofenceRequest.Builder")

        val geofencingRequest = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build()

            Log.e(TAG, "addGeofenceForReminder() > geofencingClient.addGeofences")

            geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)?.run {
                addOnSuccessListener {
                    Log.e("SaveReminderFragment: Added Geofence", geofence.requestId)
                    Snackbar.make(
                        binding.saveReminder,
                        "Geofences Added",
                        Snackbar.LENGTH_SHORT
                    ).show()
                    /** TODO: Save Reminder after Geofence is successfully Added */
                    _viewModel.validateAndSaveReminder(reminderDataItem)
                }
                addOnFailureListener {
                    Toast.makeText(getContext, R.string.geofences_not_added, Toast.LENGTH_SHORT).show()
                    if ((it.message != null)) {
                        Log.w(TAG, it.message!!)
                    }
                }
            }
//        }
    }



    private fun showToast(message: String){
        Toast.makeText(getContext, message, Toast.LENGTH_SHORT).show()
    }

    companion object{
        fun buildToastMessage(message: String): String{
            return message
        }

        internal const val ACTION_GEOFENCE_EVENT =
            "SaveReminderFragment.reminder.action.ACTION_GEOFENCE_EVENT"
    }
}

