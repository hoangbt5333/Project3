package com.example.testcamera.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build

data class UsbDeviceInfo(
    val device: UsbDevice,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val manufacturerName: String?,
    val productName: String?,
    val isUvcCamera: Boolean,
    val hasPermission: Boolean,
    val interfaceCount: Int,
) {
    fun getFormattedVidPid(): String {
        return String.format("VID: 0x%04X, PID: 0x%04X", vendorId, productId)
    }
}

object UsbDiagnosticHelper {

    const val ACTION_USB_PERMISSION = "com.example.testcamera.ACTION_USB_PERMISSION"

    fun getConnectedUsbDevices(context: Context): List<UsbDeviceInfo> {
        val usbManager = (context.getSystemService(Context.USB_SERVICE) as? UsbManager)
            ?: return emptyList()

        val deviceList = usbManager.deviceList
        val result = mutableListOf<UsbDeviceInfo>()

        for ((_, device) in deviceList) {
            val isUvc = checkIsUvcDevice(device)
            val hasPerm = usbManager.hasPermission(device)

            result.add(
                UsbDeviceInfo(
                    device = device,
                    deviceName = device.deviceName,
                    vendorId = device.vendorId,
                    productId = device.productId,
                    manufacturerName = try { device.manufacturerName } catch (ignored: Exception) { null },
                    productName = try { device.productName } catch (ignored: Exception) { null },
                    isUvcCamera = isUvc,
                    hasPermission = hasPerm,
                    interfaceCount = device.interfaceCount,
                )
            )
        }

        return result
    }

    fun autoRequestPermissionForUvcDevices(context: Context): Int {
        val usbManager = (context.getSystemService(Context.USB_SERVICE) as? UsbManager) ?: return 0
        var requestedCount = 0

        for ((_, device) in usbManager.deviceList) {
            if (checkIsUvcDevice(device) && !usbManager.hasPermission(device)) {
                requestUsbPermission(context, device)
                requestedCount++
            }
        }
        return requestedCount
    }

    fun requestUsbPermission(context: Context, usbDevice: UsbDevice) {
        val usbManager = (context.getSystemService(Context.USB_SERVICE) as? UsbManager) ?: return
        if (usbManager.hasPermission(usbDevice)) return

        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(context.packageName)
            putExtra(UsbManager.EXTRA_DEVICE, usbDevice)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val permissionIntent = PendingIntent.getBroadcast(
            context,
            usbDevice.deviceId,
            intent,
            flags
        )

        usbManager.requestPermission(usbDevice, permissionIntent)
    }

    private fun checkIsUvcDevice(device: UsbDevice): Boolean {
        // USB Class 14 (0x0E) is USB Video Class (UVC)
        if (device.deviceClass == UsbConstants.USB_CLASS_VIDEO) {
            return true
        }
        // Check interfaces if deviceClass is 0 (Interface-defined)
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_VIDEO) {
                return true
            }
        }
        return false
    }
}
