package com.example.BluetoothChat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;

import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.dual_test.R;

public class MainFragment extends AppCompatActivity {

    private static final String TAG = "MainFragment";
    private String device_name;
    private ArrayAdapter<String> chatAdapter;
    private StringBuffer string_buffer;
    private BluetoothAdapter adapter = null;
    private BluetoothHandler bh = null;
    private DiscoverDevice obj;

    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;
    private TextView textView;
    Intent intent;


    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.chat_activity);
        mConversationView = (ListView) findViewById(R.id.in);
        mOutEditText = (EditText) findViewById(R.id.edit_text_out);
        mSendButton = (Button) findViewById(R.id.button_send);
        textView = (TextView) findViewById(R.id.edit_text_out);
        intent= getIntent();
        adapter = BluetoothAdapter.getDefaultAdapter();

        bh = new BluetoothHandler(mHandler);
        mSendButton.setVisibility(View.INVISIBLE);
    }

    public void onStart() {

        super.onStart();
        //change later
        connectDevice(intent,true);
        if (bh.getState() == BluetoothHandler.STATE_CONNECTED)
        {
            Log.d(TAG, "onStart: connected");
            Log.d(TAG, "onStart: ----------------------------");
            Log.d(TAG, "onStart: starting chat now");
            chatAdapter.clear();
            setUpService();
            mSendButton.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Connection successful", Toast.LENGTH_LONG);
        }
        else
        {
            Log.d(TAG, "onStart: error during connection");
        }
    }

    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        Bundle extras = data.getExtras();
        if (extras == null) {
            return;
        }
        String address = data.getStringExtra("address");
        BluetoothDevice device = adapter.getRemoteDevice(address);
        device_name = device.getName();
        bh.connect(device, secure);
    }

    void setUpService() {
        Log.d(TAG, "setupChat()");
        chatAdapter = new ArrayAdapter<>(this, R.layout.message);
        mConversationView.setAdapter(chatAdapter);
        mOutEditText.setOnEditorActionListener(mWriteListener);
        mSendButton.setOnClickListener(v -> {
            String message = textView.getText().toString();
            sendMessage(message);
        });

    }

    private void sendMessage(String message) {

        if (bh.getState() != BluetoothHandler.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }
        if (message.length() > 0) {
            byte[] send = message.getBytes();
            bh.write(send);
            string_buffer.setLength(0);
            mOutEditText.setText(string_buffer);
        }
    }
    private TextView.OnEditorActionListener mWriteListener
            = (v, actionId, event) -> {
        if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
            String message = textView.getText().toString();
            sendMessage(message);
        }
        return true;
    };



    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    /*switch (msg.arg1) {
                        case BluetoothHandler.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            mConversationArrayAdapter.clear();
                            break;
                        case BluetoothHandler.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothHandler.STATE_LISTEN:
                        case BluetoothHandler.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                    }*/
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    chatAdapter.add("Me:  " + writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    chatAdapter.add(device_name + ":  " + readMessage);
                    break;


            }
        }
    };

    public void onDestroy() {

        super.onDestroy();
        Log.d(TAG, "onDestroy: Exiting..");
        adapter=null;
        bh=null;
    }
}


