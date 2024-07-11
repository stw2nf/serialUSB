package com.zing.terminal

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class MainActivity : AppCompatActivity() {

    private lateinit var consoleView: TextView
    private lateinit var usbManager: UsbManager
    private var usbSerialPort: UsbSerialPort? = null

    private val buffer = StringBuilder()

    private val ACTION_USB_PERMISSION = "com.zing.terminal.USB_PERMISSION"

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (ACTION_USB_PERMISSION == intent?.action) {
                synchronized(this) {
                    val device = intent?.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (intent?.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) == true) {
                        device?.let {
                            connectToDevice(it)
                        }
                    }
                }
            }
        }
    }

    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    @SuppressLint("MissingInflatedId")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize the UI
        setContentView(R.layout.activity_main)
        consoleView = findViewById(R.id.consoleView)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(usbPermissionReceiver, filter, RECEIVER_NOT_EXPORTED)

        probeAndConnect()
    }

    private fun probeAndConnect() {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            return
        }

        for (driver in availableDrivers) {
            val device = driver.device

            if (!usbManager.hasPermission(device)) {
                val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
                usbManager.requestPermission(device, permissionIntent)
            } else {
                connectToDevice(device)
            }
        }
    }

    private fun connectToDevice(device: UsbDevice) {
        val connection = usbManager.openDevice(device)
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        usbSerialPort = driver?.ports?.get(1)
        usbSerialPort?.let { port ->
            try {
                port.open(connection)
                port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

                val ioManager = SerialInputOutputManager(port, object : SerialInputOutputManager.Listener {
                    override fun onRunError(e: Exception?) {
                        e?.printStackTrace()
                    }

                    @SuppressLint("SetTextI18n")
                    override fun onNewData(data: ByteArray?) {
                        data?.let {
                            // Handle incoming data
                            // THIS IS FOR YOU SEAN
                            consoleView.text = data.toString()
                        }
                    }
                })
                Executors.newSingleThreadExecutor().submit(ioManager)

            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbPermissionReceiver)
        usbSerialPort?.close()
        scheduler.shutdown()
    }
}
