package com.example.BluetoothChat;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.os.Bundle;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;



public class BluetoothHandler extends Activity {


    public static boolean CONNECTED_AS_SERVER = false;
    int len =0;

    private static final String TAG = "BluetoothHandler";
    private static final String NAME_SECURE = "BluetoothCameraSecure";
    private static final String NAME_INSECURE = "BluetoothCameraInsecure";


    private static final UUID MY_UUID_SECURE =
            UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private static final UUID MY_UUID_INSECURE =
            UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");



    BluetoothAdapter adapter;
    Handler handler;
    private AcceptThread secure_accept_thread;
    private AcceptThread insecure_accept_thread;
    private ConnectThread connect_thread;
    private ConnectedThread connected_thread;
    private int var_state;


    public static final int STATE_NONE = 0;
    public static final int STATE_LISTEN = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 3;


    public BluetoothHandler( Handler handler) {
        adapter = BluetoothAdapter.getDefaultAdapter();
        var_state = STATE_NONE;
        this.handler = handler;
    }


    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + state + " -> " + state);
        var_state = state;
        handler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }


    public synchronized int getState() {
        return var_state;
    }

    public void mod()
    {
        if (connect_thread != null) {
            connect_thread.cancel();
            connect_thread = null;
        }

        if (connected_thread != null) {
            connected_thread.cancel();
            connected_thread = null;
        }
    }

    public synchronized void start() {
        Log.d(TAG, "start");

        mod();
        setState(STATE_LISTEN);

        //listens  to BluetoothServerSocket
        if (secure_accept_thread == null) {
            secure_accept_thread = new AcceptThread(true);
            secure_accept_thread.start();
        }
        if (insecure_accept_thread == null) {
            insecure_accept_thread = new AcceptThread(false);
            insecure_accept_thread.start();
        }
    }

    //Start the ConnectThread to initiate a connection to a remote device.
    public synchronized void connect(BluetoothDevice device, boolean secure) {
        Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (var_state == STATE_CONNECTING) {
            if (connect_thread != null) { connect_thread.cancel(); connect_thread = null;
            }
        }
        // Cancel any thread currently running a connection
        if (connected_thread != null) { connected_thread.cancel();  connected_thread = null;
        }

        connect_thread = new ConnectThread(device, secure);
        connect_thread.start();
        setState(STATE_CONNECTING);
    }

    //starting connectedThread
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        Log.d(TAG, "connected, Socket Type:" + socketType);
       mod();
        //cancel accept thread to connect to only ONE DEVICE
        if (secure_accept_thread != null) {
            secure_accept_thread.cancel();
            secure_accept_thread = null;
        }
        if (insecure_accept_thread != null) {
            insecure_accept_thread.cancel();
            insecure_accept_thread = null;
        }
        //starting connected thread
        connected_thread = new ConnectedThread(socket, socketType);
        connected_thread.start();

        //name of connected device
        Message msg = handler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        handler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }


    public synchronized void stop() {
        Log.d(TAG, "stop");
        mod();

        if (secure_accept_thread != null) {
            secure_accept_thread.cancel();
            secure_accept_thread = null;
        }

        if (insecure_accept_thread != null) {
            insecure_accept_thread.cancel();
            insecure_accept_thread = null;
        }
        setState(STATE_NONE);
    }


    public void write(byte[] out) {

        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (var_state != STATE_CONNECTED) return;
            r = connected_thread;        }
        //write unsync
        r.write(out);
    }


    private void connectionFailed() {

        Message msg = handler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Unable to connect device");
        msg.setData(bundle);
        handler.sendMessage(msg);
        //restart listening mode
        BluetoothHandler.this.start();
    }


    private void connectionLost() {

        Message msg = handler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Device connection was lost");
        msg.setData(bundle);
        handler.sendMessage(msg);

        BluetoothHandler.this.start();
    }

    //listening to incoming connections. Server-side client

    private class AcceptThread extends Thread {

        private final BluetoothServerSocket mmServerSocket;
        private String mSocketType;

        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            try {
                if (secure) {
                    tmp = adapter.listenUsingRfcommWithServiceRecord(NAME_SECURE,
                            MY_UUID_SECURE);
                } else {
                    tmp = adapter.listenUsingInsecureRfcommWithServiceRecord(
                            NAME_INSECURE, MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            Log.d(TAG, "Accept thread: " + mSocketType +
                    "BEGIN mAcceptThread" + this);
            setName("AcceptThread" + mSocketType);

            BluetoothSocket socket = null;


            while (var_state != STATE_CONNECTED) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e);
                    break;
                }

                if (socket != null) {
                    synchronized (BluetoothHandler.this) {
                        switch (var_state) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                //starting connected thread
                                CONNECTED_AS_SERVER = true;
                                connected(socket, socket.getRemoteDevice(), mSocketType);
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
            Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);

        }

        public void cancel() {
            Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e);
            }
        }
    }

    //making outgoing connection

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            try {
                if (secure) {
                    tmp = device.createRfcommSocketToServiceRecord(
                            MY_UUID_SECURE);
                } else {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(
                            MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
            setName("ConnectThread" + mSocketType);
            adapter.cancelDiscovery();
            try {
                mmSocket.connect();
            } catch (IOException e) {
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() " + mSocketType +
                            " socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            //reset connectthread
            synchronized (BluetoothHandler.this) {
                connect_thread = null;
            }
            connected(mmSocket, mmDevice, mSocketType);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }
    }

//handles incoming and outgoing transmission
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        private Timer timer;
        TimerTask task;
        byte[] sample;


        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            timer= new Timer();   sample=new byte[2];
            task= new TimerTask() {
                @Override
                public void run() {
                    write(sample);
                }
            };


            //bt I/O streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
         public void write(byte[] buffer) {
         try {
            len = buffer.length;
            mmOutStream.write(buffer);

            } catch (IOException e) {
            Log.e(TAG, "Exception during write", e);
            }
        }
        //reads data from SLAVE. MASTER needs to send keepalive for android >4.3
        public void run() {
            Log.d(TAG, "BEGIN mConnectedThread");

            byte[] buffer = new byte[2048];
            //buffer = new byte[35344];
            int bytes;

            handler.obtainMessage(Constants.START_CAMERA_SERVICE,0, -1,0) .sendToTarget();
            //performing read
            while (true) {
                try {
                    // stream of data need to be displayed on surface view.
                    bytes = mmInStream.read(buffer);
                    handler.obtainMessage(Constants.CAMERA_PREVIEW, bytes, -1, buffer).sendToTarget();
                    Log.d(TAG, "Reading");

                    timer.scheduleAtFixedRate( task, 0, 8000);

                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    BluetoothHandler.this.start();
                    break;
                }
            }
        }



        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }


}



