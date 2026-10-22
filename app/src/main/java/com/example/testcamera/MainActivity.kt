package com.example.testcamera

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.testcamera.util.UsbDiagnosticHelper

class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (!cameraGranted) {
            Toast.makeText(this, "Cần cấp quyền CAMERA để ứng dụng truy cập camera Kiosk!", Toast.LENGTH_LONG).show()
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }

            val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? KioskCameraFragment

            when (action) {
                UsbDiagnosticHelper.ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted && device != null) {
                        Toast.makeText(this@MainActivity, "Đã cấp quyền USB cho ${device.productName ?: device.deviceName}", Toast.LENGTH_SHORT).show()
                        fragment?.onUsbPermissionGranted(device)
                    } else if (device != null) {
                        Toast.makeText(this@MainActivity, "Từ chối cấp quyền USB cho ${device.productName ?: device.deviceName}", Toast.LENGTH_SHORT).show()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    device?.let { fragment?.onCameraConnected(it) }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    device?.let { fragment?.onCameraDisconnected(it) }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        checkAndRequestPermissions()
        registerUsbReceiver()

        if (savedInstanceState == null) {
            navigateToHome()
        }
    }

    fun navigateToFragment(fragment: Fragment, addToBackStack: Boolean = false) {
        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
        if (addToBackStack) {
            transaction.addToBackStack(null)
        }
        transaction.commitAllowingStateLoss()
    }

    fun navigateToHome() {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        navigateToFragment(KioskHomeFragment(), addToBackStack = false)
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissionsNeeded.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsNeeded.toTypedArray())
        }
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(UsbDiagnosticHelper.ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, RECEIVER_EXPORTED)
        } else {
            ContextCompat.registerReceiver(this, usbReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
