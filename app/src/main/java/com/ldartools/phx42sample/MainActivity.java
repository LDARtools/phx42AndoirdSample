package com.ldartools.phx42sample;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    //message constants
    private final String hostToUnit = "ZUzu";
    private final String unitToHost = "YTyt";
    private final String endOfMessage = "\r\n";

    //ui elements
    private TextView statusView = null;
    private TextView heartbeatView = null;
    private TextView ppmView = null;
    private TextView versionView = null;
    private EditText serialText = null;
    private Button connectButton = null;
    private Button igniteButton = null;

    //bluetooth stuff
    private BluetoothAdapter bluetoothAdapter = null;
    private BluetoothDevice phxDevice = null;
    private BluetoothSocket socket = null;
    private OutputStream outputStream = null;

    private boolean heartbeat = false;

    private Handler uiHandler = null;

    //this is the receiver that gets bluetooth updates for found devices and discovery finished
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        private boolean found = false;
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // If it's already paired, skip it, because it's been listed already
                if (device != null) {
                    String name = device.getName();
                    if (name != null && name.equalsIgnoreCase("phx42-"+serialText.getText())){
                        found = true;
                        setStatus("phx42-"+serialText.getText() + " found, connecting");
                        bluetoothAdapter.cancelDiscovery();

                        phxDevice = device;

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                connect(phxDevice);
                            }
                        }).start();
                    }
                }
                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (!found) {
                    setStatus("phx42-" + serialText.getText() + " not found.  Discovery finished");
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusView = findViewById(R.id.statusTextView);
        serialText = findViewById(R.id.serialEditText);
        heartbeatView = findViewById(R.id.heartbeatTextView);
        ppmView = findViewById(R.id.ppmTextView);
        versionView = findViewById(R.id.versionTextView);

        heartbeatView.setVisibility(View.INVISIBLE);

        //this is the handler to invoke UI updates on the UI thread
        uiHandler = new Handler();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        //register for discovered devices
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(broadcastReceiver, filter);

        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(broadcastReceiver, filter);

        connectButton = findViewById(R.id.connectButton);
        connectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (connectButton.getText().toString().equalsIgnoreCase("connect")) {
                    connectButton.setText("Disconnect");
                    onConnect();
                }
                else{
                    connectButton.setText("Connect");
                    onDisconnect();
                }
            }
        });

        igniteButton = findViewById(R.id.igniteButton);
        igniteButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                HashMap<String, String> params = new HashMap<String, String>();
                params.put("GO", "1");
                sendCommand(Phx42Message.createMessageToSendToPhx42("AIGS", params, null));
            }
        });
        igniteButton.setVisibility(View.INVISIBLE);
    }

    private void onDisconnect(){
        if (socket != null){
            try {
                socket.close();
                outputStream.close();
            } catch (Exception ex){

            } finally {
                socket = null;
                outputStream = null;
            }
        }

        setStatus("Disconnected");
    }

    private void setPPM(final String ppm){
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    ppmView.setText("PPM: " + ppm);
                }catch(Exception ex){

                }
            }
        });
    }

    protected void onConnect(){
        try {
            setStatus("Connecting to phx42-" + serialText.getText() + "...");

            new Thread(new Runnable() {
                @Override
                public void run() {
                    bluetoothAdapter.startDiscovery();
                }
            }).start();
        } catch(Exception ex){
            setStatus("Error starting discovery: " + ex.getMessage());
        }
    }

    private void setStatus(final String status){
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    statusView.setText(status);
                }catch(Exception ex){

                }
            }
        });
    }

    private void heartbeat(){
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (heartbeat) {
                        heartbeatView.setVisibility(View.INVISIBLE);
                        heartbeat = false;
                    } else {
                        heartbeatView.setVisibility(View.VISIBLE);
                        heartbeat = true;
                    }
                }catch(Exception ex){

                }
            }
        });
    }


    ////////////////////////////////////////////////////////////////////////////////////
    //above is UI code, below is phx code
    ////////////////////////////////////////////////////////////////////////////////////

    // after a connection is made this thread becomes the listen thread, all it does is listen for messages and handle them
    private void connect(BluetoothDevice device){
        try {
            socket = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"));

            socket.connect();

            InputStream inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();

            //Start the heartbeat loop in a different thread
            new Thread(new Runnable() {
                @Override
                public void run() {
                    sendHeartbeatLoop();
                }
            }).start();

            //this thread becomes the listen thread, all it does is listen for messages and handle them
            java.util.Scanner scanner = new java.util.Scanner(inputStream).useDelimiter("\r\n");

            setStatus("Connected");
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    igniteButton.setVisibility(View.VISIBLE);
                }
            });

            while (socket != null && socket.isConnected()){
                if (scanner.hasNext()){
                    String message = scanner.next();
                    handleMessage(message);
                }
                else{
                    Thread.sleep(100);
                }
            }

            if (socket != null){
                onDisconnect();
            }
        }catch (Exception ex){
            setStatus("Problem connecting to phx42-"+serialText.getText() + ": " + ex.getMessage());
        }
    }

    private void sendHeartbeatLoop(){
        try{
            //always set the time first ("yyyy/MM/dd_HH:mm:ss")
            sendSetTimeCommand();

            Thread.sleep(200);

            //set the periodic rate to 1 second
            sendCommand(Phx42Message.createMessageToSendToPhx42("VERS",  null, null));

            Thread.sleep(200);

            //set the periodic rate to 1 second
            HashMap<String, String> params = new HashMap<String, String>();
            params.put("MS", "1000");
            sendCommand(Phx42Message.createMessageToSendToPhx42("TRPT", params, null));

            Thread.sleep(200);

            //enable periodic FID Readings (this is the message that has the PPM value)
            params = new HashMap<String, String>();
            params.put("TYPE", "FIDR");
            params.put("EN", "1");
            sendCommand(Phx42Message.createMessageToSendToPhx42("PRPT", params, null));

            Thread.sleep(200);

            while(socket != null){
                //send the heartbeat
                sendCommand(Phx42Message.createMessageToSendToPhx42("CHEK", null,  null));

                //any space of less than a second between heartbeats should be fine
                Thread.sleep(900);
            }

        } catch (Exception ex){

        }
    }

    private void handleMessage(String message){
        try{
            Phx42Message m = Phx42Message.parseMessageFromPhx42(message);

            if (m.getType().equalsIgnoreCase("CHEK")){
                heartbeat();
            } else if (m.getType().equalsIgnoreCase("FIDR")){ //handle FID readings message
                if (m.hasParameter("CALPPM")){
                    setPPM(m.getParameterValue("CALPPM"));
                }
            } else if (m.getType().equalsIgnoreCase("SERR") || m.getType().equalsIgnoreCase("EROR")){ //handle spontaneous errors
                setStatus("phx reported an error: CODE=" + m.getParameterValue("CODE"));
            } else if (m.getType().equalsIgnoreCase("SHUT")){ //handle errors
                setStatus("phx reported flameout error: " + m.getExtra());
            } else if (m.getType().equalsIgnoreCase("VERS")){ //handle firmware version
                final String version = m.getParameterValue("MAJOR") + "." + m.getParameterValue("MINOR");

                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        versionView.setText("Firmware version: " + version);
                    }
                });
            }
        }catch (Exception ex){

        }
    }

    private void sendSetTimeCommand(){
        Date date = Calendar.getInstance().getTime();
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd_HH:mm:ss");

        HashMap<String, String> params = new HashMap<String, String>();
        params.put("MS", dateFormat.format(date));

        Phx42Message message = Phx42Message.createMessageToSendToPhx42("TIME", params,  null);

        sendCommand(message);
    }

    //it is important to synchronize when you access outputStream.write()
    private synchronized void sendCommand(Phx42Message message){
        if (outputStream == null) return;

        try{
            outputStream.write(message.toString().getBytes(Charset.forName("UTF-8")));
            outputStream.flush();   // always flush so the message gets written immediately
        } catch (Exception ex){

        }
    }
}
