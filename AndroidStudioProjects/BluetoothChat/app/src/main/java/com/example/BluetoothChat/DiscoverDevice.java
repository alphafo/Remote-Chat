package com.example.BluetoothChat;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.content.Intent;
import android.content.pm.PackageManager;

import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;


import com.example.dual_test.R;

import java.util.Set;

public class DiscoverDevice extends AppCompatActivity {

    private final int BLUETOOTH_REQUEST = 1;
    private final int LOCATION_REQUEST = 2;
    private final int REQUEST_DISCOVERABLE = 3;
    private static final String TAG = "DeviceListActivity";
    public static String EXTRA_DEVICE_ADDRESS;

    private BluetoothAdapter btAdapter;

    ListView pairedListView;
    ListView newDevicesListView;

    ArrayAdapter<String> mNewDevicesArrayAdapter;
    ArrayAdapter<String> mPairedDevicesArrayAdapter;
    Set<BluetoothDevice> pairedDevices;
    Button scan;

    protected BluetoothDevice dev;


    public void locationPermissionCheck() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder alert = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.myDialog)).setTitle("Locations Permission Needed").setMessage("Allow Dualfie to location services?");
            alert.setPositiveButton("OK", (dialog, which) -> ActivityCompat.requestPermissions(DiscoverDevice.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_REQUEST));
            alert.setNegativeButton("Cancel", (dialog, which) -> System.exit(0));
            alert.create().show();
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder alert = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.myDialog)).setTitle("Background Permission Needed").setMessage("Allow Dualfie to background location? ");
            alert.setPositiveButton("Allow", (dialog, which) -> ActivityCompat.requestPermissions(DiscoverDevice.this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST));
            alert.setNegativeButton("Cancel", (dialog, which) -> System.exit(0));
            alert.create().show();
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder alert = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.myDialog)).setTitle("Background Permission Needed").setMessage("Allow Dualfie to background location? ");
            alert.setPositiveButton("OK", (dialog, which) -> ActivityCompat.requestPermissions(DiscoverDevice.this,
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    LOCATION_REQUEST));
            alert.setNegativeButton("Cancel", (dialog, which) -> System.exit(0));
            alert.create().show();
        }
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_main);
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        scan = (Button) findViewById(R.id.button_scan);
        locationPermissionCheck();
        mPairedDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.model_text);
        pairedListView = (ListView) findViewById(R.id.paired_devices);


        if (!btAdapter.isEnabled()) {
            Intent btEnableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(btEnableIntent, BLUETOOTH_REQUEST);

        } else if (btAdapter == null) {
            new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.myDialog)).setTitle("BlueTooth not Compatible").setMessage("This device does not support Bluetooth")
                    .setPositiveButton("OK", (dialog, which) -> System.exit(0));
        }


        //paired
        getPairedList();

        //new
        mNewDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.model_text);
        newDevicesListView = (ListView) findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);

        IntentFilter filter1 = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter1.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter1);

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        this.registerReceiver(mPairingRequestReceiver, filter);

        scan.setOnClickListener(v -> {
            doDiscovery();
            });

    }

    private void getPairedList()
    {
        btAdapter.enable();
        pairedDevices = btAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            findViewById(R.id.txt_paired).setVisibility(View.VISIBLE);
            for (BluetoothDevice device : pairedDevices) {
                mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            String noDevices = getResources().getText(R.string.none_paired).toString();
            mPairedDevicesArrayAdapter.add(noDevices);
        }
        pairedListView.setAdapter(mPairedDevicesArrayAdapter);
        //pairedListView.setOnItemClickListener(mDeviceClickListener);
    }



    private void doDiscovery() {
        btAdapter.enable();
        mNewDevicesArrayAdapter.clear();

        if (btAdapter.isDiscovering()) {
            btAdapter.cancelDiscovery();
            btAdapter.startDiscovery();
        }
        else {
            Log.d(TAG, "doDiscovery()");
            setTitle(R.string.scanning);
            findViewById(R.id.txt_new).setVisibility(View.VISIBLE);
            btAdapter.startDiscovery();
            /*Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 15);
            startActivity(intent);*/

        }

    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d("BroadcastActions", "Action "+action+" received");

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                mNewDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {}
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d(TAG, "onReceive: discovery done");
                setTitle(R.string.select_device);
                if (mNewDevicesArrayAdapter.getCount() == 0) {
                    String noDevices = getResources().getText(R.string.none_found).toString();
                    mNewDevicesArrayAdapter.add(noDevices);

                }
            }
        } };

    //for pairing request

    private final BroadcastReceiver mPairingRequestReceiver = new BroadcastReceiver() {
        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothDevice.ACTION_PAIRING_REQUEST)) {
                btAdapter.cancelDiscovery();
                Log.d(TAG, "request found, stopping discovery");
                try {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    int pin=intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY", 1234);

                    Log.d(TAG, "Start Auto Pairing. PIN = " + intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY",1234));
                    byte[] pinBytes;
                    pinBytes = (""+pin).getBytes("UTF-8");
                    device.setPin(pinBytes);
                    device.setPairingConfirmation(true);

                    if(device.getBondState() !=BluetoothDevice.BOND_BONDED)
                    {
                        device.createBond();
                    }
                    else if(device.getBondState() ==BluetoothDevice.BOND_BONDED)
                    {
                        Log.d(TAG, "Device: auto paired");
                        Intent intent_new = new Intent(getApplicationContext(), MainFragment.class);
                        intent_new.putExtra("address", device.getAddress());
                        startActivity(intent_new);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error occurs when trying to auto pair");
                    e.printStackTrace();
                }
            }
        }
    };


    private AdapterView.OnItemClickListener mDeviceClickListener
            = new AdapterView.OnItemClickListener() {
        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        public void onItemClick(AdapterView<?> av, View v, int i, long arg3) {

            btAdapter.cancelDiscovery();

            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);

            EXTRA_DEVICE_ADDRESS=address;
            dev = btAdapter.getRemoteDevice(EXTRA_DEVICE_ADDRESS) ;
            //api 19 min is 16
            //asynchronous
            dev.createBond();
            if(dev.getBondState() ==BluetoothDevice.BOND_BONDED)
            {
                Log.d(TAG, "Device: paired");
                Intent intent = new Intent(getApplicationContext(), MainFragment.class);
                intent.putExtra("address",dev.getAddress());
                startActivity(intent);
            }

        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (btAdapter != null) {
            btAdapter.cancelDiscovery();
        }
        this.unregisterReceiver(mReceiver);
        this.unregisterReceiver(mPairingRequestReceiver);
    }


};