package paperprisoners.couchpotato;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Created by Chris Potter on 7/16/2016.
 * Creates a Bluetooth service that allows a one-to-many Bluetooth connection up to 7 devices
 */
public class BluetoothService {
    private static final String TAG = "BluetoothService";
    private static final UUID uuid = UUID.fromString("0b8c8517-39b8-4b97-a53f-821f76661ed6");
    private static final BluetoothAdapter mAdapter = BluetoothAdapter.getDefaultAdapter();
    private static final String defaultDeviceName = mAdapter.getName();

    private static AcceptThread mAcceptThread;
    private static ConnectThread mConnectThread;
    private static ConnectedThread mConnectedThread;
    private static HashMap<Integer, ConnectedThread> mConnectedDevices = new HashMap<>();

    public static HashSet<MessageListener> listeners = new HashSet<>();
    private static int maxPlayers = 7;
    public static String DELIM = "\\|/";
    private static int tracker = 1;


    public static Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msgRead) {
            Log.i(TAG, "HANDLER handleMessage called");

            byte[] msg = (byte[]) msgRead.obj;
            String str = new String(Arrays.copyOfRange(msg, 0, msgRead.arg1));
            String[] split = TextUtils.split(str, Pattern.quote(BluetoothService.DELIM));
            Log.i(TAG, "Message: " + TextUtils.join("---", split));
            try {
                int player = Integer.parseInt(split[0]);
                Log.i(TAG, "Player: " + player);
                int type = Integer.parseInt(split[1]);
                Log.i(TAG, "Message Type: " + type);
                String[] content = Arrays.copyOfRange(split, 2, split.length);
                Log.i(TAG, "Message Content: " + Arrays.toString(split));
                Log.i(TAG, "NUMBER OF MESSAGE LISTENERS = " + listeners.size());
                for (MessageListener m : BluetoothService.listeners) {
                    if (m != null) {
                        m.onReceiveMessage(player, type, content);
                        Log.i(TAG, "Message sent to: " + m.toString());
                    }
                }
            } catch (NumberFormatException nf) {
                Log.e(TAG, "Could not parse message header");
            }
        }
    };

    //Constructor that gets the bluetooth adapter of the device
    private BluetoothService() {
    }

    public static BluetoothAdapter getmAdapter() {
        return mAdapter;
    }

    public static synchronized void start() {
        Log.d(TAG, "start");
        // Start the thread to listen on a BluetoothServerSocket
        if (mAcceptThread == null) {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }
    }

    public static synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, "connect to: " + device);
        // Start the thread to connect with the given device
        try {
            mConnectThread = new ConnectThread(device);
            mConnectThread.start();
        } catch (Exception e) {
        }
    }

    public static synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        Log.d(TAG, "connected");
        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        /*
        if (mConnectedDevices.contains(mConnectedThread)) {
            mConnectedThread.cancel();
            Log.d(TAG, "Duplicate Device tried to connect");
            mConnectedDevices.remove(mConnectedThread);
            Log.d(TAG, "Number of Devices: " + mConnectedDevices.size());
        } else {
            Log.d(TAG, "No Duplicate Found, Adding Connection To List");
            */
            mConnectedDevices.put(tracker, mConnectedThread);

        //}
        Log.d(TAG, "Number of Devices: " + mConnectedDevices.size());
        mConnectedThread.start();

        //TODO: HANDLE TRANSMISSION BETWEEN DEVICES
    }

    public static synchronized void stop() {
        Log.d(TAG, "stop");
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
    }

    public static void writeToServer(String player, int type, String[] content) {
        String output = player + DELIM + type + DELIM + TextUtils.join(DELIM, content);
        Log.i(TAG, "CLIENT WRITING TO HOST: " + output);
        ConnectedThread r = mConnectedThread;
        r.write(output.getBytes());
        Log.i(TAG, "CLIENT WRITING TO HOST: " + r.getName() + "    ---   OUTPUT:  "+ output);
    }

    public static void writeToClients(int type, String[] content) {
        String output;
        Log.i(TAG, "NUMBER OF CONNECTED DEVICES: " + mConnectedDevices.size());
        for(Map.Entry<Integer, ConnectedThread> device : mConnectedDevices.entrySet()) {
            try {
                output = "0" + DELIM + type + DELIM + TextUtils.join(DELIM, content);
                Log.i(TAG, "CREATING WRITE MESSAGE - " + output);
                device.getValue().write(output.getBytes());
            } catch(Exception e){
                device.getValue().cancel();
                mConnectedDevices.remove(device);
            }
        }
    }

    public static void write(int playerID,String player, int type, String[] content){
        String output = player + DELIM + type + DELIM + TextUtils.join(DELIM, content);
        mConnectedDevices.get(playerID-1).write(output.getBytes());
    }

    private static class AcceptThread extends Thread {
        private final BluetoothServerSocket mServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord("Couch Potato", uuid);
            } catch (IOException e) {
            }
            mServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;

            while (true) { //mState != STATE_CONNECTED
                try {
                    Log.i(TAG, "SERVER SOCKET STARTED ");
                    socket = mServerSocket.accept();
                } catch (IOException e) {
                    Log.i(TAG, "SERVER SOCKET FAILED");
                    break;
                }

                if (socket != null) {
                    //TODO: MANAGE CONNECTED SOCKETS
                    Log.i(TAG, "SOCKET CONNECTED ");
                    connected(socket, socket.getRemoteDevice());


                    if (mConnectedDevices.size() >= maxPlayers) {
                        try {
                            Log.i(TAG, "CLOSING SERVER SOCKET");
                            mServerSocket.close();
                        } catch (IOException e) {
                        }
                    }
                    socket = null;
                }
            }
            Log.i(TAG, "END mAcceptThread");
        }

        public void cancel() {
            Log.d(TAG, "cancel " + this);
            try {
                mServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of server failed", e);
            }
        }
    } //End Server

    private static class ConnectThread extends Thread {
        private final BluetoothDevice mDevice;
        private final BluetoothSocket mSocket;

        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            mDevice = device;

            try {
                tmp = device.createInsecureRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) {
                Log.e(TAG, "create() failed", e);
            }
            mSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            mAdapter.cancelDiscovery();

            try {
                Log.i(TAG, "SOCKET TRYING TO CONNECT ");
                mSocket.connect();
            } catch (IOException connectionException) {
                try {
                    Log.i(TAG, "SOCKET CONNECTION FAILED");
                    mSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                //connectionFailed();
                return;
            }

            synchronized (this) {
                mConnectThread = null;
            }

            Log.i(TAG, "SOCKET CONNECTED");
            connected(mSocket, mDevice);
        }

        public void cancel() {
            try {
                mConnectedDevices.remove(mSocket.getRemoteDevice());
                mSocket.close();
            } catch (IOException e) {
            }
        }
    } //End ConnectThread


    private static class ConnectedThread extends Thread {
        private final BluetoothSocket mSocket;
        private final InputStream mInStream;
        private final OutputStream mOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            mSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }
            mInStream = tmpIn;
            mOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    Log.i(TAG, "BEGIN try to read mConnectedThread");
                    bytes = mInStream.read(buffer);
                    Log.i(TAG, "MESSAGE RECEIVED FROM REMOTE: Attempting to process - " + bytes + " bytes");
                    if(mHandler == null) {
                        Log.e(TAG, "mHandler received a null");
                    }else {
                        Log.i(TAG, "Obtain Message Reached");
                        /*Message msg = mHandler.obtainMessage(100, bytes, -1, buffer).;
                        mHandler.sendMessage(msg);
                        */
                        mHandler.obtainMessage(Constants.MESSAGE_READ, bytes,-1,buffer).sendToTarget();
                        Log.i(TAG, "Obtain Message Finished");
                    }

                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    //connectionLost();
                    break;
                }
            }
        }

        public void write(byte[] out) {
            try {
                mOutStream.write(out);
            } catch (IOException e) {
                Log.e(TAG, "COULD NOT WRITE TO CLIENT - REMOVING CLIENT: " + this);
                cancel();
            }
        }

        public void cancel() {
            try {
                Log.e(TAG, "CANCELING SOCKET - " + this);
                //mConnectedDevices.remove(this);
                mSocket.close();
            } catch (IOException e) {
            }
        }
    } //End ConnectedThread

    //Makes the device discoverable for 300 seconds
    public static void startDiscoverable(Context context, BroadcastReceiver bCReciever) {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 180);
        context.startActivity(discoverableIntent);
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        context.registerReceiver(bCReciever, intentFilter);
        IntentFilter disconnect = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        context.registerReceiver(bCReciever,disconnect);
        Log.i(TAG, "DEVICE IS NOW DISCOVERABLE");
    }

    public static void stopDiscoverable(Context context, BroadcastReceiver bCReciever){
        mAcceptThread.cancel();
        context.unregisterReceiver(bCReciever);
        mAdapter.setName(getDefaultDeviceName());
        Log.i(TAG, "DISCOVERY ENDED");
    }

    //Starts a search for nearby Bluetooth devices that are discoverable
    public static void startSearching(Context context, BroadcastReceiver bCReciever) {
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        context.registerReceiver(bCReciever, intentFilter);
        IntentFilter disconnect = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        context.registerReceiver(bCReciever,disconnect);
        mAdapter.startDiscovery();
        Log.i(TAG, "SEARCHING FOR NEARBY DEVICES");
    }

    public static void stopSearching(Context context, BroadcastReceiver bcReceiver){
        mAdapter.cancelDiscovery();
        mAdapter.setName(getDefaultDeviceName());
        Log.i(TAG, "SEARCHING FOR DEVICES ENDED");
    }

    public static String getDefaultDeviceName() {
        return defaultDeviceName;
    }

    public static void addMessageListener(MessageListener m) {
        listeners.add(m);
    }
}
