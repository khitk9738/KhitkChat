package com.akki.khitkchat.data.model

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.util.Log
import com.akki.khitkchat.data.model.BluetoothScanner.ScanningListener

class BluetoothScannerImpl(val context: Context) : BluetoothScanner {

    private var listener: ScanningListener? = null

    private var isDiscovering: Boolean = false

    private val handler: Handler = Handler()

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val foundDevices = HashMap<String, BluetoothDevice>()

    private val foundDeviceFilter: IntentFilter = IntentFilter(BluetoothDevice.ACTION_FOUND)
    private val foundDeviceReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {

            if (BluetoothDevice.ACTION_FOUND == intent.action) {
                val device = intent
                        .getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                foundDevices.put(device.address, device)
                listener?.onDeviceFind(device)
            }
        }
    }

    private val discoverableStateFilter: IntentFilter = IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
    private val discoverableStateReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {

            val scanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.SCAN_MODE_NONE)

            if (scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                listener?.onDiscoverableStart()
            } else {
                scanningFinishedTask.run()
            }
        }
    }

    private val scanningFinishedTask = Runnable {

        listener?.onDiscoveryFinish()
        isDiscovering = false

        try {
            context.unregisterReceiver(foundDeviceReceiver)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
        if (adapter != null && adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }
    }

    override fun scanForDevices(seconds: Int) {

        adapter?.startDiscovery()
        listener?.onDiscoveryStart(seconds)
        isDiscovering = true

        handler.postDelayed(scanningFinishedTask, seconds.toLong() * 1000)
        context.registerReceiver(foundDeviceReceiver, foundDeviceFilter)
    }

    override fun stopScanning() {
        handler.removeCallbacks(scanningFinishedTask)
        scanningFinishedTask.run()
    }

    override fun getBondedDevices(): List<BluetoothDevice> {
        return ArrayList<BluetoothDevice>(adapter?.bondedDevices)
    }

    override fun getDeviceByAddress(address: String): BluetoothDevice? {
        val pairedDevice = getBondedDevices()
                .filter { it.address.equals(address, ignoreCase = true) }
        return if (!pairedDevice.isEmpty()) pairedDevice.first() else foundDevices[address]
    }

    override fun isBluetoothAvailable(): Boolean {
        return adapter != null
    }

    override fun isBluetoothEnabled(): Boolean {
        return adapter?.isEnabled ?: false
    }

    override fun isDiscoverable(): Boolean {
        return adapter?.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
    }

    override fun isDiscovering(): Boolean {
        return isDiscovering
    }

    override fun listenForDevices() {
        context.registerReceiver(discoverableStateReceiver, discoverableStateFilter)
    }

    override fun setScanningListener(listener: ScanningListener) {
        this.listener = listener
    }
}
