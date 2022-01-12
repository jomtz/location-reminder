package com.udacity.project4.locationreminders.savereminder

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.PendingIntent
import android.content.ContentValues.TAG
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
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.firebase.ui.auth.IdpResponse
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.location.Geofence.NEVER_EXPIRE
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.udacity.project4.BuildConfig
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSaveReminderBinding
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.geofence.GeofenceBroadcastReceiver
import com.udacity.project4.locationreminders.geofence.GeofencingConstants
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.locationreminders.savereminder.selectreminderlocation.BACKGROUND_LOCATION_PERMISSION_INDEX
import com.udacity.project4.locationreminders.savereminder.selectreminderlocation.LOCATION_PERMISSION_INDEX
import com.udacity.project4.locationreminders.savereminder.selectreminderlocation.REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE
import com.udacity.project4.locationreminders.savereminder.selectreminderlocation.REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
import com.udacity.project4.utils.sendNotification
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import kotlinx.android.synthetic.main.fragment_save_reminder.*
import org.koin.android.ext.android.inject


@SuppressLint("UnspecifiedImmutableFlag")
class SaveReminderFragment : BaseFragment() {

    private val REQUEST_TURN_DEVICE_LOCATION_ON = 1

    private lateinit var binding: FragmentSaveReminderBinding
    private lateinit var registerForActivityResult: ActivityResultLauncher<Intent>
    lateinit var geofencingClient: GeofencingClient
    private val runningQOrLater = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q

    //Get the view model this time as a single to be shared with the another fragment
    override val _viewModel: SaveReminderViewModel by inject()

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(requireContext(), GeofenceBroadcastReceiver::class.java)
//        intent.action = ACTION_GEOFENCE_EVENT
        PendingIntent.getBroadcast(requireContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_save_reminder, container, false)

        geofencingClient = LocationServices.getGeofencingClient(requireActivity())

        setDisplayHomeAsUpEnabled(true)

        binding.viewModel = _viewModel

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = this
        binding.selectLocation.setOnClickListener {
        _viewModel.navigationCommand.value =
            NavigationCommand.To(SaveReminderFragmentDirections.actionSaveReminderFragmentToSelectLocationFragment())
        }

        checkPermissionsAndStartGeofencing()
    }

//    private fun registerForSignInResult() {
//        registerForActivityResult = registerForActivityResult(
//            ActivityResultContracts.StartActivityForResult()){ result: ActivityResult ->
//            val response = IdpResponse.fromResultIntent(result.data)
//            // Listen to the result of the sign - in process
//            if(result.resultCode == Activity.RESULT_OK){
//                Log.i(TAG, "User ${FirebaseAuth.getInstance().currentUser?.displayName} has signed in")
//            } else {
//                Log.i(TAG, "Sign in unsuccessful ${response?.error?.errorCode}")
//            }
//        }
//    }

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

        if (
            grantResults.isEmpty() ||
            grantResults[LOCATION_PERMISSION_INDEX] == PackageManager.PERMISSION_DENIED ||
            (requestCode == REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE &&
                    grantResults[BACKGROUND_LOCATION_PERMISSION_INDEX] ==
                    PackageManager.PERMISSION_DENIED) )
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

            binding.selectLocation.setOnClickListener {
                Toast.makeText(requireContext(), "Please go to settings to grant location permission",
                    Toast.LENGTH_SHORT).show()
            }

        } else {
            checkDeviceLocationSettingsAndStartGeofence()
        }
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

    /**
     * Starts the permission check and Geofence process only if the Geofence associated with the
     * current hint isn't yet active.
     */
    private fun checkPermissionsAndStartGeofencing() {
//        if (viewModel.geofenceIsActive()) return
        if (foregroundAndBackgroundLocationPermissionApproved()) {
            checkDeviceLocationSettingsAndStartGeofence()
        } else {
            requestForegroundAndBackgroundLocationPermissions()
        }
    }


    /*
     *  Uses the Location Client to check the current state of location settings, and gives the user
     *  the opportunity to turn on location services within our app.
     */

    private fun checkDeviceLocationSettingsAndStartGeofence(resolve:Boolean = true) {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_LOW_POWER
        }
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val settingsClient = LocationServices.getSettingsClient(requireActivity())
        val locationSettingsResponseTask = settingsClient.checkLocationSettings(builder.build())

        geofencingClient = LocationServices.getGeofencingClient(requireActivity())


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

        locationSettingsResponseTask.addOnCompleteListener {
            if ( it.isSuccessful ) {
                binding.saveReminder.setOnClickListener {
                    addGeofenceForReminder()
                    showToast(buildToastMessage("Reminder Saved !"))
                }
                /** Move to addGeofenceForClue add*/
//                binding.saveReminder.setOnClickListener {
//                    val title = _viewModel.reminderTitle.value
//                    val description = _viewModel.reminderDescription.value
//                    val location = _viewModel.reminderSelectedLocationStr.value
//                    val latitude = _viewModel.latitude.value
//                    val longitude = _viewModel.longitude.value
//                    val reminderDTO = _viewModel.validateAndSaveReminder(
//                        ReminderDataItem(
//                            title = title,
//                            description = description,
//                            latitude = latitude,
//                            longitude = longitude,
//                            location = location
//                        )
//                    )
//
//                    if (reminderDTO != null) {
//                        addGeofenceForClue(reminderDTO)
//                        showToast(buildToastMessage("Reminder Saved !"))
//                    }
//                }
            }
        }

    }

    /*
     *  Determines whether the app has the appropriate permissions across Android 10+ and all other
     *  Android versions.
     */
    @TargetApi(29)
    private fun foregroundAndBackgroundLocationPermissionApproved(): Boolean {
        val foregroundLocationApproved = (
                PackageManager.PERMISSION_GRANTED ==
                        ActivityCompat.checkSelfPermission(requireContext(),
                            Manifest.permission.ACCESS_FINE_LOCATION))
        val backgroundPermissionApproved =
            if (runningQOrLater) {
                PackageManager.PERMISSION_GRANTED ==
                        ActivityCompat.checkSelfPermission(
                            requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        )
            } else {
                true
            }
        return foregroundLocationApproved && backgroundPermissionApproved
    }


    /*
     *  Requests ACCESS_FINE_LOCATION and (on Android 10+ (Q) ACCESS_BACKGROUND_LOCATION.
     */
    @TargetApi(29 )
    private fun requestForegroundAndBackgroundLocationPermissions() {
        if (foregroundAndBackgroundLocationPermissionApproved())
            return
        var permissionsArray = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val resultCode = when {
            runningQOrLater -> {
                permissionsArray += Manifest.permission.ACCESS_BACKGROUND_LOCATION
                REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE
            }
            else -> REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
        }
        Log.d(TAG, "Request foreground only location permission")
        requestPermissions(
            permissionsArray,
            resultCode
        )
    }


//    @SuppressLint("MissingPermission", "LongLogTag")
//        private fun addGeofenceForClue(reminder: ReminderDTO) {
//
//        val geofence = Geofence.Builder()
//            .setRequestId(reminder.id)
//            .setCircularRegion(
//                reminder.latitude!!,
//                reminder.longitude!!,
//                100f
//            )
//            .setExpirationDuration(NEVER_EXPIRE)
//            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
//            .build()
//
//
//        val geofencingRequest = GeofencingRequest.Builder()
//            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
//            .addGeofence(geofence)
//            .build()
//
//
//        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent).run {
//            addOnSuccessListener {
//                Log.e("Added Geofence", geofence.requestId)
//
//
//
//
//            }
//            addOnFailureListener {
//                Toast.makeText(requireContext(), R.string.geofences_not_added,
//                    Toast.LENGTH_SHORT).show()
//                if ((it.message != null)) {
//                    Log.w(TAG, it.message!!)
//                }
//            }
//        }
//    }

    @SuppressLint("MissingPermission", "LongLogTag")
    private fun addGeofenceForReminder() {

        val title = _viewModel.reminderTitle.value
        val description = _viewModel.reminderDescription.value
        val location = _viewModel.reminderSelectedLocationStr.value
        val latitude = _viewModel.latitude.value
        val longitude = _viewModel.longitude.value

        val reminderDataItem = ReminderDataItem(
            title,
            description,
            location,
            latitude,
            longitude
        )

        if (reminderDataItem.location == null) {
            Snackbar.make(
                binding.saveReminder,
                R.string.err_select_location,
                Snackbar.LENGTH_LONG
            ).show()
        }

        if (
//            reminderDataItem.title != null &&
            reminderDataItem.latitude != null &&
            reminderDataItem.longitude != null &&
            reminderDataItem.location != null
        ) {
            //Create geofence objects
            val geofence = Geofence.Builder()
                .setRequestId(reminderDataItem.id)
                .setCircularRegion(
                    reminderDataItem.latitude!!,
                    reminderDataItem.longitude!!,
                    200f
                )
                .setExpirationDuration(NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()


            val geofencingRequest = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build()



            geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent).run {
                addOnSuccessListener {
                    Log.e("SaveReminderFragment: Added Geofence", geofence.requestId)
                    _viewModel.validateAndSaveReminder(
                        ReminderDataItem(
                            title = title,
                            description = description,
                            latitude = latitude,
                            longitude = longitude,
                            location = location
                        )
                    )

                    /**
                     * Test notification to implement on worker
                     */
                    sendNotification(requireContext(),ReminderDataItem(
                        title = title,
                        description = description,
                        latitude = latitude,
                        longitude = longitude,
                        location = location
                    ))

                }
                addOnFailureListener {
                    Toast.makeText(
                        requireContext(), R.string.geofences_not_added,
                        Toast.LENGTH_SHORT
                    ).show()
                    if ((it.message != null)) {
                        Log.w(TAG, it.message!!)
                    }
                }
            }
        }
    }



    private fun showToast(message: String){
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    companion object{
        fun buildToastMessage(message: String): String{
            return message
        }
    }
}

