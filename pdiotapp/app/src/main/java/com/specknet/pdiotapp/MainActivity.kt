package com.specknet.pdiotapp

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.google.android.gms.appindexing.Thing
import com.google.android.material.snackbar.Snackbar
import com.specknet.pdiotapp.bluetooth.BluetoothSpeckService
import com.specknet.pdiotapp.bluetooth.ConnectingActivity
import com.specknet.pdiotapp.databinding.ActivityMain2Binding
import com.specknet.pdiotapp.databinding.ActivityMainBinding
import com.specknet.pdiotapp.live.LiveDataActivity
import com.specknet.pdiotapp.onboarding.OnBoardingActivity
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.Utils
import kotlinx.android.synthetic.main.activity_loginpage.*
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    // buttons and textviews
    lateinit var liveProcessingButton: Button
    lateinit var pairingButton: Button
    lateinit var recordButton: Button
    lateinit var quitButton:Button
    lateinit var ifConnected:TextView
    lateinit var userNameDisplay : TextView
    lateinit var historicButton : Button
    private lateinit var binding:ActivityMain2Binding

    // permissions
    lateinit var permissionAlertDialog: AlertDialog.Builder

    val permissionsForRequest = arrayListOf<String>()

    var locationPermissionGranted = false
    var cameraPermissionGranted = false
    var readStoragePermissionGranted = false
    var writeStoragePermissionGranted = false

    // broadcast receiver
    val filter = IntentFilter()

    private fun updateIfConnected() {
        val isServiceRunning =
            Utils.isServiceRunning(BluetoothSpeckService::class.java, applicationContext)
        if (isServiceRunning) {
            // get status of sensors
            val RespeckWorking =
                BluetoothSpeckService.mIsRESpeckFound && !BluetoothSpeckService.mIsRESpeckPaused &&BluetoothSpeckService.mIsRESpeckEnabled
            val ThingyWorking =
                BluetoothSpeckService.mIsThingyFound && !BluetoothSpeckService.mIsThingyPaused && BluetoothSpeckService.mIsThingyEnabled
//            Connection: Respeck ❌ Thingy ❌️
            var filledStr = "Connection: Respeck "
            filledStr = if (RespeckWorking) {
//                        val resourceID = this.resources.getIdentifier(
//                            "rounded_corner_blue",
//                            "drawable",
//                            this.packageName
//                        )
//                        liveProcessingButton.setBackgroundResource(resourceID)
                filledStr + "✔️ Thingy "
            } else {
                filledStr + "❌️ Thingy "
            }
            filledStr = if (ThingyWorking) {
                filledStr + "✔️"
            } else {
                filledStr + "❌️"
            }
            // concat the string to display
            ifConnected.setText(filledStr)
        }
        else{
            ifConnected.setText("Connection: Respeck ❌ Thingy ❌️")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMain2Binding.inflate(layoutInflater)
        setContentView(R.layout.activity_main2)
        val userName=intent.getStringExtra("userName")
        // check whether the onboarding screen should be shown
        val sharedPreferences = getSharedPreferences(Constants.PREFERENCES_FILE, Context.MODE_PRIVATE)
        val firstTime = intent.getStringExtra("firstTime")

        if (firstTime!= null) {
            // If it is the first login for an account, then show guides.
            sharedPreferences.edit().putBoolean(Constants.PREF_USER_FIRST_TIME, false).apply()
            val introIntent = Intent(this, OnBoardingActivity::class.java)
            startActivity(introIntent)
        }

        liveProcessingButton = findViewById(R.id.live_button)
        pairingButton = findViewById(R.id.ble_button)
        recordButton = findViewById(R.id.record_button)
        quitButton = findViewById(R.id.quitbutton)
        ifConnected=findViewById(R.id.ifConnected)
        userNameDisplay=findViewById(R.id.loginInfo)
        permissionAlertDialog = AlertDialog.Builder(this)
        historicButton = findViewById(R.id.viewhistory)
        userNameDisplay.setText("Welcome, "+userName.toString()+".")
        setupClickListeners(userName!!)

        setupPermissions()

        setupBluetoothService()


        // register a broadcast receiver for respeck status
        filter.addAction(Constants.ACTION_RESPECK_CONNECTED)
        filter.addAction(Constants.ACTION_RESPECK_DISCONNECTED)
        updateIfConnected() // update sensor status and display
    }

    fun setupClickListeners(userName:String) {
        liveProcessingButton.setOnClickListener {
            // go to live data graph activity
            val intent = Intent(this, LiveDataActivity::class.java)
            startActivity(intent)
        }

        pairingButton.setOnClickListener {
            // go to connect sensor activity
            val intent = Intent(this, ConnectingActivity::class.java)
            startActivity(intent)
        }

        recordButton.setOnClickListener {
            // go to live recognition activity
            val intent = Intent(this, PredictionActivity::class.java)
            intent.putExtra("username",userName)
            startActivity(intent)
        }

        quitButton.setOnClickListener {
            // go to log in activity
            val intent = Intent(this, Loginpage::class.java)
            startActivity(intent)
        }
        historicButton.setOnClickListener {
            // go to historic record view activity if the user logged in.
            if (userName=="guest"){
                Toast.makeText(this, "You need to log in to view your historic data.", Toast.LENGTH_SHORT).show()
            }
            else{
                val intent = Intent(this, HistoryRecord::class.java)
                intent.putExtra("username",userName)
                startActivity(intent)
            }
        }
    }

    fun setupPermissions() {
        // request permissions

        // location permission
        Log.i("Permissions", "Location permission = " + locationPermissionGranted)
        if (ActivityCompat.checkSelfPermission(applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsForRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsForRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        else {
            locationPermissionGranted = true
        }

        // camera permission
        Log.i("Permissions", "Camera permission = " + cameraPermissionGranted)
        if (ActivityCompat.checkSelfPermission(applicationContext,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.i("Permissions", "Camera permission = " + cameraPermissionGranted)
            permissionsForRequest.add(Manifest.permission.CAMERA)
        }
        else {
            cameraPermissionGranted = true
        }

        // read storage permission
        Log.i("Permissions", "Read st permission = " + readStoragePermissionGranted)
        if (ActivityCompat.checkSelfPermission(applicationContext,
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.i("Permissions", "Read st permission = " + readStoragePermissionGranted)
            permissionsForRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        else {
            readStoragePermissionGranted = true
        }

        // write storage permission
        Log.i("Permissions", "Write storage permission = " + writeStoragePermissionGranted)
        if (ActivityCompat.checkSelfPermission(applicationContext,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.i("Permissions", "Write storage permission = " + writeStoragePermissionGranted)
            permissionsForRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        else {
            writeStoragePermissionGranted = true
        }

        if (permissionsForRequest.size >= 1) {
            ActivityCompat.requestPermissions(this,
                permissionsForRequest.toTypedArray(),
                Constants.REQUEST_CODE_PERMISSIONS)
        }

    }

    fun setupBluetoothService() {
        val isServiceRunning = Utils.isServiceRunning(BluetoothSpeckService::class.java, applicationContext)
        Log.i("debug","isServiceRunning = " + isServiceRunning)

        // check sharedPreferences for an existing Respeck id
        val sharedPreferences = getSharedPreferences(Constants.PREFERENCES_FILE, Context.MODE_PRIVATE)
        if (sharedPreferences.contains(Constants.RESPECK_MAC_ADDRESS_PREF)) {
            Log.i("sharedpref", "Already saw a respeckID, starting service and attempting to reconnect")

            // launch service to reconnect
            // start the bluetooth service if it's not already running
            if(!isServiceRunning) {
                Log.i("service", "Starting BLT service")
                val simpleIntent = Intent(this, BluetoothSpeckService::class.java)
                this.startService(simpleIntent)
            }
        }
        else {
            Log.i("sharedpref", "No Respeck seen before, must pair first")
            // TODO then start the service from the connection activity
        }
    }

    override fun onDestroy() {
        super.onDestroy()
//        System.exit(0)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if(requestCode == Constants.REQUEST_CODE_PERMISSIONS) {
            if(grantResults.isNotEmpty()) {
                for (i in grantResults.indices) {
                    when(permissionsForRequest[i]) {
                        Manifest.permission.ACCESS_COARSE_LOCATION -> locationPermissionGranted = true
                        Manifest.permission.ACCESS_FINE_LOCATION -> locationPermissionGranted = true
                        Manifest.permission.CAMERA -> cameraPermissionGranted = true
                        Manifest.permission.READ_EXTERNAL_STORAGE -> readStoragePermissionGranted = true
                        Manifest.permission.WRITE_EXTERNAL_STORAGE -> writeStoragePermissionGranted = true
                    }

                }
            }
        }

        // count how many permissions need granting
        var numberOfPermissionsUngranted = 0
        if (!locationPermissionGranted) numberOfPermissionsUngranted++
        if (!cameraPermissionGranted) numberOfPermissionsUngranted++
        if (!readStoragePermissionGranted) numberOfPermissionsUngranted++
        if (!writeStoragePermissionGranted) numberOfPermissionsUngranted++

        // show a general message if we need multiple permissions
        if (numberOfPermissionsUngranted > 1) {
            val generalSnackbar = Snackbar
                .make(coordinatorLayout, "Several permissions are needed for correct app functioning", Snackbar.LENGTH_LONG)
                .setAction("SETTINGS") {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
                .show()
        }
        else if(numberOfPermissionsUngranted == 1) {
            var snackbar: Snackbar = Snackbar.make(coordinatorLayout, "", Snackbar.LENGTH_LONG)
            if (!locationPermissionGranted) {
                snackbar = Snackbar
                    .make(
                        coordinatorLayout,
                        "Location permission needed for Bluetooth to work.",
                        Snackbar.LENGTH_LONG
                    )
            }

            if(!cameraPermissionGranted) {
                snackbar = Snackbar
                    .make(
                        coordinatorLayout,
                        "Camera permission needed for QR code scanning to work.",
                        Snackbar.LENGTH_LONG
                    )
            }

            if(!readStoragePermissionGranted || !writeStoragePermissionGranted) {
                snackbar = Snackbar
                    .make(
                        coordinatorLayout,
                        "Storage permission needed to record sensor.",
                        Snackbar.LENGTH_LONG
                    )
            }

            snackbar.setAction("SETTINGS") {
                val settingsIntent = Intent(Settings.ACTION_SETTINGS)
                startActivity(settingsIntent)
            }
                .show()
        }

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.show_tutorial) {
            val introIntent = Intent(this, OnBoardingActivity::class.java)
            startActivity(introIntent)
            return true
        }

        return super.onOptionsItemSelected(item)
    }


    override fun onResume() {
        super.onResume()
        // update sensor status
        updateIfConnected()
        }
    }
