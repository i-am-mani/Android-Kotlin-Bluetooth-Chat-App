package com.omega.bluetoothchatapp

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.omega.bluetoothchatapp.databinding.ActivityMainBinding
import com.omega.bluetoothchatapp.databinding.ItemMessageBinding
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.viewbinding.BindableItem
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*


class MainActivity : AppCompatActivity() {

    private val TAG: String = "MAIN"
    private lateinit var binding: ActivityMainBinding
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val mUUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    private var mPairedDevices = listOf<BluetoothDevice>()
    private lateinit var mMessagesAdapter: GroupAdapter<GroupieViewHolder>

    private lateinit var mHandler: Handler


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setup()
        enableBluetooth()
    }

    private fun setup() {
        binding.apply {
            rvResponse.layoutManager =
                LinearLayoutManager(applicationContext, LinearLayoutManager.VERTICAL, false)
            mMessagesAdapter = GroupAdapter()
            mMessagesAdapter.add(ChatMessageItem("Begin Conversation By Connecting To Another Device.....", "Help", resources.getColor(R.color.white, null)))
            rvResponse.adapter = mMessagesAdapter
        }
    }

    private fun enableBluetooth() {
        // There's one Bluetooth adapter for the entire system, call getDefaultAdapter to get one
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Snackbar.make(
                binding.root,
                "Your Device Does Not Support Bluetooth.",
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        binding.tvDeviceName.text = bluetoothAdapter!!.name
        binding.tvDeviceAddress.text = bluetoothAdapter!!.address

        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        setupBluetoothClientConnection()

        AcceptThread().start()
    }

    private fun setupBluetoothClientConnection() {
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        val allPairs: MutableList<String> = pairedDevices?.map { device ->
            val deviceName = device.name
            return@map deviceName
        }?.toMutableList() ?: mutableListOf()

        allPairs.add(0, "Select Connection")
        mPairedDevices = pairedDevices?.toList() ?: listOf()

        val arrayAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, allPairs)
        binding.spinnerConnections.adapter = arrayAdapter
        binding.spinnerConnections.setSelection(0)
        binding.spinnerConnections.setOnItemSelectedListener(object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (position != 0) {
                    val selectedConnection: BluetoothDevice = pairedDevices!!.toList()[position - 1]

                    val connectionSocket = ConnectThread(selectedConnection)
                    connectionSocket.start()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

        })
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            Snackbar.make(binding.root, "Devices Bluetooth Enabled", Snackbar.LENGTH_LONG).show()
        }
    }

    private inner class AcceptThread : Thread() {

        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(
                bluetoothAdapter!!.name,
                mUUID
            )
        }

        override fun run() {
            // Keep listening until exception occurs or a socket is returned.
            var shouldLoop = true
            while (shouldLoop) {
                val socket: BluetoothSocket? = try {
                    Log.d(TAG, "Establishing new Connection")
                    mmServerSocket?.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Socket's accept() method failed", e)
                    shouldLoop = false
                    null
                }
                socket?.also { bluetoothSocket ->
                    val client = bluetoothSocket.remoteDevice.name
                    manageServerSocketConnection(bluetoothSocket, client)
                    mHandler.post {
                        val idx =
                            mPairedDevices.indexOfFirst { it.name == client }
                        if (idx != -1) {
                            binding.spinnerConnections.setSelection(idx + 1)
                            binding.tvConnectionLabel.text = "Connected To"
                        }
                        Snackbar.make(
                            binding.root,
                            "Connection Established With $client",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    mmServerSocket?.close()
                    shouldLoop = false
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        fun cancel() {
            try {
                mmServerSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }

    private inner class ConnectThread(val device: BluetoothDevice) : Thread() {

        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(mUUID)
        }

        public override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter?.cancelDiscovery()

            mmSocket?.let { socket ->
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                try {
                    socket.connect()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect", e)
                }

                // The connection attempt succeeded. Perform work associated with
                // the connection in a separate thread.
                val client = socket.remoteDevice.name
                manageServerSocketConnection(socket, client)
                Snackbar.make(
                    binding.root,
                    "Connection Established With $client",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }


    private inner class ConnectedThread(private val mmSocket: BluetoothSocket, val opName: String) :
        Thread() {

        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream

        override fun run() {
            var numBytes: Int // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                // Read from the InputStream.
                numBytes = try {
                    mmInStream.read(mmBuffer)
                } catch (e: IOException) {
                    Log.d(TAG, "Input stream was disconnected", e)
                    break
                }

                // Send the obtained bytes to the UI activity.
                val readMsg = mHandler.obtainMessage(
                    MESSAGE_READ, numBytes, -1,
                    opName to mmBuffer
                )
                readMsg.sendToTarget()
            }
        }

        // Call this from the main activity to send data to the remote device.
        fun write(bytes: ByteArray) {
            try {
                mmOutStream.write(bytes)
            } catch (e: IOException) {
                Log.e(TAG, "Error occurred when sending data", e)

                // Send a failure message back to the activity.
                val writeErrorMsg = mHandler.obtainMessage(MESSAGE_TOAST)
                val bundle = Bundle().apply {
                    putString("toast", "Couldn't send data to the other device")
                }
                writeErrorMsg.data = bundle
                mHandler.sendMessage(writeErrorMsg)
                return
            }

            // Share the sent message with the UI activity.
            val writtenMsg = mHandler.obtainMessage(
                MESSAGE_WRITE, -1, -1, mmBuffer
            )
            writtenMsg.sendToTarget()
        }

        // Call this method from the main activity to shut down the connection.
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }


    private fun manageServerSocketConnection(socket: BluetoothSocket, name: String) {
        mHandler = Handler(this.mainLooper, Handler.Callback {
            try {
                val response = it.obj as Pair<String, ByteArray>
                val from = response.first
                val msg = response.second.decodeToString()
                Toast.makeText(this, "New Message Received", Toast.LENGTH_SHORT).show()
                mMessagesAdapter.add(
                    ChatMessageItem(
                        msg,
                        from,
                        resources.getColor(R.color.reply, null)
                    )
                )

                return@Callback true
            } catch (e: Exception) {
                return@Callback false
            }
        })
        val communicationService = ConnectedThread(socket, name)
        communicationService.start()
        mHandler.post {
            binding.apply {
                etReply.isEnabled = true
                btnSendToConnected.setOnClickListener {
                    val text = etReply.text.toString()
                    communicationService.write(text.encodeToByteArray())
                    mMessagesAdapter.add(
                        ChatMessageItem(
                            text,
                            bluetoothAdapter!!.name,
                            resources.getColor(R.color.response, null)
                        )
                    )
                    etReply.setText("")
                }
            }
        }
    }


    companion object {
        const val REQUEST_ENABLE_BT = 100

        // Defines several constants used when transmitting messages between the
        // service and the UI.
        const val MESSAGE_READ: Int = 0
        const val MESSAGE_WRITE: Int = 1
        const val MESSAGE_TOAST: Int = 2
    }
}

class ChatMessageItem(
    private val message: String,
    private val name: String,
    private val color: Int
) : BindableItem<ItemMessageBinding>() {
    override fun bind(viewBinding: ItemMessageBinding, position: Int) {
        viewBinding.apply {
            tvFrom.text = name
            tvMessage.text = message
            cardRoot.setCardBackgroundColor(color)
        }
    }

    override fun getLayout(): Int {
        return R.layout.item_message
    }

    override fun initializeViewBinding(view: View): ItemMessageBinding {
        return ItemMessageBinding.bind(view)
    }
}