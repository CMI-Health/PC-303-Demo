
package com.example.lpdemo

import android.Manifest
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lpdemo.utils.DeviceAdapter
import com.example.lpdemo.utils._bleState
import com.example.lpdemo.utils.bleState
import com.jeremyliao.liveeventbus.LiveEventBus
import com.lepu.blepro.ext.BleServiceHelper
import com.lepu.blepro.constants.Ble
import com.lepu.blepro.event.EventMsgConst
import com.lepu.blepro.event.InterfaceEvent
import com.lepu.blepro.objs.Bluetooth
import com.lepu.blepro.objs.BluetoothController
import com.lepu.blepro.observer.BIOL
import com.lepu.blepro.observer.BleChangeObserver
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), BleChangeObserver {

    private val TAG = "MainActivity"

    private lateinit var dialog: ProgressDialog

    private val models = intArrayOf(
        Bluetooth.MODEL_PC80B,
        Bluetooth.MODEL_PC60FW,
        Bluetooth.MODEL_PC100,
        Bluetooth.MODEL_AP20,
    )

    private val permission = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN
    )

    private var list = arrayListOf<Bluetooth>()
    private var adapter = DeviceAdapter(R.layout.device_item, list)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        needPermission()
        initService()
        initView()
        initEventBus()
    }

    private fun needPermission(){
        for (p in permission) {
            if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_DENIED) {
                ActivityCompat.requestPermissions(this, permission, 0)
                break
            }
        }
        checkBt()
    }

    private fun checkBt() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            Toast.makeText(this, "Bluetooth is not supported", Toast.LENGTH_SHORT).show()
            return
        }

        if (!adapter.isEnabled) {
            if (adapter.enable()) {
                Toast.makeText(this, "Bluetooth open successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Bluetooth open failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initService() {
        if (BleServiceHelper.BleServiceHelper.checkService()) {
            // BleService already init
        } else {
            BleServiceHelper.BleServiceHelper.initService(application, BleSO.getInstance(application))
        }
    }

    private fun initView() {

        dialog = ProgressDialog(this)

        scan.setOnClickListener {
            BleServiceHelper.BleServiceHelper.startScan(models)
        }
        LinearLayoutManager(this).apply {
            this.orientation = LinearLayoutManager.VERTICAL
            rcv.layoutManager = this
        }
        rcv.adapter = adapter
        adapter.setOnItemClickListener { adapter, view, position ->
            (adapter.getItem(position) as Bluetooth).let {
                // set interface before connect
                BleServiceHelper.BleServiceHelper.setInterfaces(it.model)
                // add observer(ble state)
                lifecycle.addObserver(BIOL(this, intArrayOf(it.model)))
                // stop scan before connect
                BleServiceHelper.BleServiceHelper.stopScan()
                // connect
                BleServiceHelper.BleServiceHelper.connect(this, it.model, it.device)

                dialog.show()
            }
        }
    }

    private fun initEventBus() {
        LiveEventBus.get<Boolean>(EventMsgConst.Ble.EventServiceConnectedAndInterfaceInit)
            .observe(this, {
                // BleService init success
                Log.d(TAG, "EventServiceConnectedAndInterfaceInit")
            })
        LiveEventBus.get<Bluetooth>(EventMsgConst.Discovery.EventDeviceFound)
            .observe(this, {
                // scan result
                adapter.setList(BluetoothController.getDevices())
                adapter.notifyDataSetChanged()
                Log.d(TAG, "EventDeviceFound")
            })
        //--------------------pc80b,pc102,pc60fw--------------------
        LiveEventBus.get<Int>(EventMsgConst.Ble.EventBleDeviceReady)
            .observe(this, {
                dialog.dismiss()
                // connect success
                Log.d(TAG, "EventBleDeviceReady")
                when (it) {
                    Bluetooth.MODEL_PC100 -> {
                        startActivity(Intent(this, Pc102Activity::class.java))
                        finish()
                    }

                    Bluetooth.MODEL_PC80B -> {
                        startActivity(Intent(this, Pc80bActivity::class.java))
                        finish()
                    }
                    Bluetooth.MODEL_PC60FW -> {
                        startActivity(Intent(this, Pc60fwActivity::class.java))
                        finish()
                    }
                    else -> {
                        Toast.makeText(this, "connect success", Toast.LENGTH_SHORT).show()
                        adapter.setList(null)
                        adapter.notifyDataSetChanged()
                    }
                }
            })
        //----------------------ap10/ap20---------------------------
        LiveEventBus.get<InterfaceEvent>(InterfaceEvent.AP20.EventAp20SetTime)
            .observe(this, {
                dialog.dismiss()
                startActivity(Intent(this, Ap20Activity::class.java))
                finish()
            })
    }

    override fun onBleStateChanged(model: Int, state: Int) {
        // Ble.State
        Log.d(TAG, "model $model, state: $state")
        _bleState.value = state == Ble.State.CONNECTED
        Log.d(TAG, "bleState $bleState")
    }
}