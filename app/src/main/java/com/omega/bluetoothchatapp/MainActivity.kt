package com.omega.bluetoothchatapp

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.omega.bluetoothchatapp.databinding.ActivityMainBinding
import java.time.Duration

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setup()
    }


    fun setup(){
        binding.btnEnableBluetooth.setOnClickListener {
            enableBluetooth()
        }
    }

    private fun enableBluetooth() {
        // There's one Bluetooth adapter for the entire system, call getDefaultAdapter to get one
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Snackbar.make(binding.root, "Your Device Does Not Support Bluetooth.", Snackbar.LENGTH_LONG)
        }

        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK){
            Snackbar.make(binding.root, "Devices Bluetooth Enabled", Snackbar.LENGTH_LONG)
        }
    }


    companion object {
        const val REQUEST_ENABLE_BT = 100
    }
}