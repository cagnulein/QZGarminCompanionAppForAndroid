package org.cagnulein.qzgarmincompanionandroid

import android.app.Activity
import android.bluetooth.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*

class MainActivity : Activity() {

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeAdvertiser: BluetoothLeAdvertiser
    private lateinit var bluetoothGattServer: BluetoothGattServer
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "MainActivity"
        private val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
        private val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (!bluetoothAdapter.isMultipleAdvertisementSupported) {
            Log.e(TAG, "Bluetooth LE Advertising not supported on this device")
            finish()
            return
        }

        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        bluetoothGattServer = bluetoothManager.openGattServer(this, gattServerCallback)

        val service = BluetoothGattService(HEART_RATE_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val heartRateMeasurement = BluetoothGattCharacteristic(HEART_RATE_MEASUREMENT_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ)
        service.addCharacteristic(heartRateMeasurement)
        bluetoothGattServer.addService(service)

        startAdvertising()
    }

    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
            .build()

        bluetoothLeAdvertiser.startAdvertising(settings, data, advertisingCallback)
    }

    private val advertisingCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertising failed with error code $errorCode")
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to ${device.address}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from ${device.address}")
            }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)

            if (characteristic.uuid == HEART_RATE_MEASUREMENT_UUID) {
                val heartRateValue = generateHeartRateValue()
               
                handler.post {
                    characteristic.setValue(heartRateValue, BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, characteristic.value)
                }
            }
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)

            if (descriptor.uuid == BluetoothGattDescriptor.CLIENT_CHARACTERISTIC_CONFIGURATION_UUID) {
                if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Notifications enabled for ${descriptor.characteristic.uuid}")
                } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Notifications disabled for ${descriptor.characteristic.uuid}")
                }
            }

            if (responseNeeded) {
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }

    private fun generateHeartRateValue(): ByteArray {
        // Generate a random heart rate value between 60 and 100 beats per minute
        return byteArrayOf((60..100).random().toByte())
    }

    override fun onDestroy() {
        super.onDestroy()

        bluetoothGattServer.close()
        bluetoothLeAdvertiser.stopAdvertising(advertisingCallback)
    }
}
