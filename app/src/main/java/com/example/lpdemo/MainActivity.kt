package com.example.lpdemo

import android.Manifest
import android.app.AlertDialog
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.provider.Settings
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lpdemo.utils.*
import com.jeremyliao.liveeventbus.LiveEventBus
import com.lepu.blepro.ext.BleServiceHelper
import com.lepu.blepro.constants.Ble
import com.lepu.blepro.event.EventMsgConst
import com.lepu.blepro.event.InterfaceEvent
import com.lepu.blepro.objs.Bluetooth
import com.lepu.blepro.objs.BluetoothController
import com.lepu.blepro.observer.BIOL
import com.lepu.blepro.observer.BleChangeObserver
import com.permissionx.guolindev.PermissionX
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), BleChangeObserver {

    private val TAG = "MainActivity"

    private lateinit var dialog: ProgressDialog

    private val models = intArrayOf(
        Bluetooth.MODEL_PC60FW, Bluetooth.MODEL_PC_60NW, Bluetooth.MODEL_PC_60NW_1,
        Bluetooth.MODEL_PC66B, Bluetooth.MODEL_PF_10, Bluetooth.MODEL_PF_20,
        Bluetooth.MODEL_OXYSMART, Bluetooth.MODEL_POD2B,
        Bluetooth.MODEL_POD_1W, Bluetooth.MODEL_S5W,
        Bluetooth.MODEL_PF_10AW, Bluetooth.MODEL_PF_10AW1,
        Bluetooth.MODEL_PF_10BW, Bluetooth.MODEL_PF_10BW1,
        Bluetooth.MODEL_PF_20AW, Bluetooth.MODEL_PF_20B,
        Bluetooth.MODEL_S7W, Bluetooth.MODEL_S7BW,
        Bluetooth.MODEL_S6W, Bluetooth.MODEL_S6W1,  // Pc60fwActivity
        Bluetooth.MODEL_O2RING, Bluetooth.MODEL_O2M,
        Bluetooth.MODEL_BABYO2, Bluetooth.MODEL_BABYO2N,
        Bluetooth.MODEL_CHECKO2, Bluetooth.MODEL_SLEEPO2,
        Bluetooth.MODEL_SNOREO2, Bluetooth.MODEL_WEARO2,
        Bluetooth.MODEL_SLEEPU, Bluetooth.MODEL_OXYLINK,
        Bluetooth.MODEL_KIDSO2, Bluetooth.MODEL_OXYFIT,
        Bluetooth.MODEL_OXYRING, Bluetooth.MODEL_BBSM_S1,
        Bluetooth.MODEL_BBSM_S2, Bluetooth.MODEL_OXYU,
        Bluetooth.MODEL_AI_S100,  // OxyActivity
        Bluetooth.MODEL_PC80B, Bluetooth.MODEL_PC80B_BLE,  // Pc80bActivity
        Bluetooth.MODEL_PC100,  // Pc102Activity
        Bluetooth.MODEL_AP20,  // Ap20Activity
        Bluetooth.MODEL_PC_68B,  // Pc68bActivity
        Bluetooth.MODEL_PULSEBITEX, Bluetooth.MODEL_HHM4, Bluetooth.MODEL_CHECKME,  // PulsebitExActivity
        Bluetooth.MODEL_CHECKME_LE,  // CheckmeLeActivity
        Bluetooth.MODEL_PC300, Bluetooth.MODEL_PC300_BLE,  // Pc303Activity
        Bluetooth.MODEL_CHECK_POD,  // CheckmePodActivity
        Bluetooth.MODEL_AOJ20A,  // Aoj20aActivity
        Bluetooth.MODEL_SP20, Bluetooth.MODEL_SP20_BLE,  // Sp20Activity
        Bluetooth.MODEL_VETCORDER, Bluetooth.MODEL_CHECK_ADV,  // CheckmeMonitorActivity
        Bluetooth.MODEL_TV221U,  // Vtm20fActivity
        Bluetooth.MODEL_BPM,  // BpmActivity
        Bluetooth.MODEL_BIOLAND_BGM,  // BiolandBgmActivity
        Bluetooth.MODEL_POCTOR_M3102,  // PoctorM3102Activity
        Bluetooth.MODEL_LPM311,  // Lpm311Activity
        Bluetooth.MODEL_LEM,  // LemActivity
        Bluetooth.MODEL_ER1,
        Bluetooth.MODEL_ER1_N,
        Bluetooth.MODEL_HHM1,  // Er1Activity
        Bluetooth.MODEL_ER2,
        Bluetooth.MODEL_LP_ER2,
        Bluetooth.MODEL_DUOEK,
        Bluetooth.MODEL_HHM2,
        Bluetooth.MODEL_HHM3,  // Er2Activity
    )

    private var list = arrayListOf<Bluetooth>()
    private var adapter = DeviceAdapter(R.layout.device_item, list)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        needPermission()
        needService()
        initService()
        initView()
        initEventBus()
    }

    private fun needService() {
        var gpsEnabled = false
        var networkEnabled = false
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        if (!gpsEnabled && !networkEnabled) {
            val dialog: AlertDialog.Builder = AlertDialog.Builder(this)
            dialog.setMessage("open location service")
            dialog.setPositiveButton("ok") { _, _ ->
                val myIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(myIntent)
            }
            dialog.setNegativeButton("cancel") { _, _ ->
                finish()
            }
            dialog.setCancelable(false)
            dialog.show()
        }
    }

    private fun needPermission() {
        PermissionX.init(this)
            .permissions(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
            .onExplainRequestReason { scope, deniedList ->
                scope.showRequestReasonDialog(
                    deniedList, "location permission", "ok", "ignore"
                )
            }
            .onForwardToSettings { scope, deniedList ->
                scope.showForwardToSettingsDialog(
                    deniedList, "location setting", "ok", "ignore"
                )
            }
            .request { allGranted, grantedList, deniedList ->
                Log.d(TAG, "permission : $allGranted, $grantedList, $deniedList")

                //permission OK, check Bluetooth status
                if (allGranted)
                    checkBt()
            }
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
            BleServiceHelper.BleServiceHelper.initService(application)
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
                BleServiceHelper.BleServiceHelper.connect(applicationContext, it.model, it.device)

                deviceName = it.name
                deviceAddress = it.macAddr

                dialog.show()
            }
        }
    }

    private fun initEventBus() {
        LiveEventBus.get<Boolean>(EventMsgConst.Ble.EventServiceConnectedAndInterfaceInit)
            .observe(this) {
                // BleService init success
                Log.d(TAG, "EventServiceConnectedAndInterfaceInit")
            }
        LiveEventBus.get<Bluetooth>(EventMsgConst.Discovery.EventDeviceFound)
            .observe(this) {
                // scan result
                splitDevices(ble_split.text.toString())
                Log.d(TAG, "EventDeviceFound")
            }
        //--------------------pc80b,pc102,pc60fw,pc68b,pod1w,pc300--------------------
        LiveEventBus.get<Int>(EventMsgConst.Ble.EventBleDeviceReady)
            .observe(this) {
                dialog.dismiss()
                // connect success
                Log.d(TAG, "EventBleDeviceReady")
                when (it) {
                    Bluetooth.MODEL_PC100 -> {
                        startActivity(Intent(this, Pc102Activity::class.java))
                    }
                    Bluetooth.MODEL_PC80B, Bluetooth.MODEL_PC80B_BLE -> {
                        val intent = Intent(this, Pc80bActivity::class.java)
                        intent.putExtra("model", it)
                        startActivity(intent)
                    }
                    Bluetooth.MODEL_PC60FW, Bluetooth.MODEL_PC_60NW, Bluetooth.MODEL_PC_60NW_1,
                    Bluetooth.MODEL_PC66B, Bluetooth.MODEL_PF_10, Bluetooth.MODEL_PF_20,
                    Bluetooth.MODEL_OXYSMART, Bluetooth.MODEL_POD2B,
                    Bluetooth.MODEL_POD_1W, Bluetooth.MODEL_S5W,
                    Bluetooth.MODEL_PF_10AW, Bluetooth.MODEL_PF_10AW1,
                    Bluetooth.MODEL_PF_10BW, Bluetooth.MODEL_PF_10BW1,
                    Bluetooth.MODEL_PF_20AW, Bluetooth.MODEL_PF_20B,
                    Bluetooth.MODEL_S7W, Bluetooth.MODEL_S7BW,
                    Bluetooth.MODEL_S6W, Bluetooth.MODEL_S6W1 -> {
                        val intent = Intent(this, Pc60fwActivity::class.java)
                        intent.putExtra("model", it)
                        startActivity(intent)
                    }
                    Bluetooth.MODEL_PC_68B -> {
                        startActivity(Intent(this, Pc68bActivity::class.java))
                    }
                    Bluetooth.MODEL_PC300, Bluetooth.MODEL_PC300_BLE -> {
                        val intent = Intent(this, Pc303Activity::class.java)
                        intent.putExtra("model", it)
                        startActivity(intent)
                    }
                    Bluetooth.MODEL_VETCORDER, Bluetooth.MODEL_CHECK_ADV -> {
                        val intent = Intent(this, CheckmeMonitorActivity::class.java)
                        intent.putExtra("model", it)
                        startActivity(intent)
                    }
                    Bluetooth.MODEL_TV221U -> {
                        startActivity(Intent(this, Vtm20fActivity::class.java))
                    }
                    Bluetooth.MODEL_BIOLAND_BGM -> {
                        startActivity(Intent(this, BiolandBgmActivity::class.java))
                    }
                    Bluetooth.MODEL_POCTOR_M3102 -> {
                        startActivity(Intent(this, PoctorM3102Activity::class.java))
                    }
                    Bluetooth.MODEL_LPM311 -> {
                        startActivity(Intent(this, Lpm311Activity::class.java))
                    }
                    Bluetooth.MODEL_LEM -> {
                        startActivity(Intent(this, LemActivity::class.java))
                    }
                    else -> {
                        Toast.makeText(this, "connect success", Toast.LENGTH_SHORT).show()
                        adapter.setList(null)
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        //----------------------ap10/ap20---------------------------
        LiveEventBus.get<InterfaceEvent>(InterfaceEvent.AP20.EventAp20SetTime)
            .observe(this) {
                dialog.dismiss()
                startActivity(Intent(this, Ap20Activity::class.java))
            }
        //----------------------pulsebit ex/hhm4/checkme---------------------------
        LiveEventBus.get<InterfaceEvent>(InterfaceEvent.Pulsebit.EventPulsebitSetTime)
            .observe(this) {
                dialog.dismiss()
                val intent = Intent(this, PulsebitExActivity::class.java)
                intent.putExtra("model", it.model)
                startActivity(intent)
            }
        //----------------------checkme le---------------------------
        LiveEventBus.get<InterfaceEvent>(InterfaceEvent.CheckmeLE.EventCheckmeLeSetTime)
            .observe(this) {
                dialog.dismiss()
                startActivity(Intent(this, CheckmeLeActivity::class.java))
            }
        //----------------------checkme pod---------------------------
        LiveEventBus.get<InterfaceEvent>(InterfaceEvent.CheckmePod.EventCheckmePodSetTime)
            .observe(this) {
                dialog.dismiss()
                startActivity(Intent(this, CheckmePodActivity::class.java))
            }
        //----------------------aoj20a---------------------------
        LiveEventBus.get<InterfaceEvent>(InterfaceEvent.AOJ20a.EventAOJ20aSetTime)
            .observe(this) {
                dialog.dismiss()
                startActivity(Intent(this, Aoj20aActivity::class.java))
            }
        //----------------------sp20---------------------------
        LiveEventBus.get<InterfaceEvent>(InterfaceEvent.SP20.EventSp20SetTime)
            .observe(this) {
                dialog.dismiss()
                val intent = Intent(this, Sp20Activity::class.java)
                intent.putExtra("model", it.model)
                startActivity(intent)
            }
        //----------------------oxy---------------------------
        LiveEventBus.get<InterfaceEvent>(InterfaceEvent.Oxy.EventOxySyncDeviceInfo)
            .observe(this) {
                val types = it.data as Array<String>
                if (types.isEmpty()) return@observe
                if (types[0] == "SetTIME") {
                    dialog.dismiss()
                    val intent = Intent(this, OxyActivity::class.java)
                    intent.putExtra("model", it.model)
                    startActivity(intent)
                }
            }
        //----------------------bpm---------------------------
        LiveEventBus.get<InterfaceEvent>(InterfaceEvent.BPM.EventBpmSyncTime)
            .observe(this) {
                dialog.dismiss()
                startActivity(Intent(this, BpmActivity::class.java))
            }
        //----------------------er1/vbeat/hhm1-------------------
        LiveEventBus.get<InterfaceEvent>(InterfaceEvent.ER1.EventEr1SetTime)
            .observe(this) {
                dialog.dismiss()
                val intent = Intent(this, Er1Activity::class.java)
                intent.putExtra("model", it.model)
                startActivity(intent)
            }
        //------------------er2/lp er2/duoek/hhm2/hhm3----------------
        LiveEventBus.get<InterfaceEvent>(InterfaceEvent.ER2.EventEr2SetTime)
            .observe(this) {
                dialog.dismiss()
                val intent = Intent(this, Er2Activity::class.java)
                intent.putExtra("model", it.model)
                startActivity(intent)
            }
    }

    private fun splitDevices(name: String) {
        list.clear()
        for (b in BluetoothController.getDevices()) {
            if (b.name.contains(name, true)) {
                list.add(b)
            }
        }

        adapter.setNewInstance(list)
        adapter.notifyDataSetChanged()

    }

    override fun onBleStateChanged(model: Int, state: Int) {
        // Ble.State
        Log.d(TAG, "model $model, state: $state")
        _bleState.value = state == Ble.State.CONNECTED
        Log.d(TAG, "bleState $bleState")
    }
}