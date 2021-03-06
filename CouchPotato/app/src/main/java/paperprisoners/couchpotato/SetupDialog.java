package paperprisoners.couchpotato;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * The dialog that appears upon creating
 *
 * @author Ian Base Code / Chris Bluetooth
 */
public class SetupDialog extends AlertDialog implements View.OnClickListener, AdapterView.OnItemClickListener, DialogInterface.OnCancelListener, MessageListener{
    private static final String TAG = "SetupDialog";
    private int minPlayers = 3, maxPlayers = 8;
    private boolean isHost = false;
    private boolean joined = false;

    private UserData userData;
    private TextView messageText, countText;
    private Button cancelButton, startButton;
    private LinearLayout buttonArea;

    private ListView userList;
    private SetupAdapter adapter;

    private ArrayList<UserData> connectedUsers = new ArrayList<>();
    private ArrayList<UserData> finalUserList;

    private Context ownerContext;

    public SetupDialog(Context context, int minPlayers, int maxPlayers, boolean isHost, UserData userData) {
        super(context);
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.isHost = isHost;
        this.ownerContext = context;
        this.userData = userData;
        BluetoothService.listeners.add(this);
        finalUserList = new ArrayList<>();
        userData.setAddress(BluetoothService.getmAdapter().getAddress());
    }

    public SetupDialog(Context context, boolean isHost, UserData userData) {
        this(context, 3, 8, isHost, userData);
    }

    //CUSTOM METHODS

    public void addUser(UserData data) {
        //if not the host feel free to add it, if host then add if less than max players
        if (!isHost || (isHost && adapter.getCount() + 1 < maxPlayers)) {
            adapter.add(data);
            adjustContent();
            //Then adds listener if needed
            if (isHost) {
                int pos = adapter.getCount() - 1;
                if(Constants.debug) Log.v("SETUPD",""+pos);
                //Button b = (Button) userList.getChildAt(pos).findViewById(R.id.item_setup_kick);
                //b.setOnClickListener(this);
            }
        }
        if(Constants.debug) Log.i(TAG, "USER ADDED TO ADAPTER: " + data.getAddress() + "    ADAPTER SIZE: " + connectedUsers.size());
    }

    public UserData getUser(int index) {
        return adapter.getItem(index);
    }

    public void removeUser(UserData data) {
        adapter.remove(data);
        adjustContent();
    }

    //Updates what the list is displaying based on content
    private void adjustContent() {
        int count = adapter.getCount();
        ProgressBar loader = (ProgressBar) findViewById(R.id.setup_loader);
        //Toggling loader visibility
        if (count == 0 || joined) {
            loader.setVisibility(View.VISIBLE);
            userList.setVisibility(View.INVISIBLE);
        } else {
            loader.setVisibility(View.INVISIBLE);
            userList.setVisibility(View.VISIBLE);
        }
        //Adjusting host/client specific content
        if (isHost) {
            countText.setText((adapter.getCount() + 1) + "/" + maxPlayers);
            if (count + 1 >= minPlayers) {
                countText.setTextColor(ContextCompat.getColor(getContext(),R.color.main_accept));
                if (count + 1 >= maxPlayers) {
                    messageText.setText(getContext().getString(R.string.setup_host3));
                } else {
                    messageText.setText(getContext().getString(R.string.setup_host2));
                }
                startButton.setEnabled(true);
                startButton.setTextColor(ContextCompat.getColor(getContext(),R.color.main_black));
            } else {
                countText.setTextColor(ContextCompat.getColor(getContext(),R.color.main_white));
                messageText.setText(getContext().getString(R.string.setup_host1));
                startButton.setEnabled(false);
                startButton.setTextColor(ContextCompat.getColor(getContext(),R.color.main_black_superfaded));
            }
        } else {
            if (joined) {
                countText.setText(getContext().getString(R.string.setup_joining));
                messageText.setText(getContext().getString(R.string.setup_join3));
            } else {
                countText.setText(getContext().getString(R.string.setup_searching));
                if (count > 0) {
                    messageText.setText(getContext().getString(R.string.setup_join2));
                } else {
                    messageText.setText(getContext().getString(R.string.setup_join1));
                }
            }
        }
        //Then invalidates the list
        userList.invalidate();
    }

    private UserData getUserDataFromButton(Button btn) {
        if (!isHost)
            return null;
        for (int i = 0; i < adapter.getCount(); i++) {
            View v = userList.getChildAt(i);
            if (isHost && v.findViewById(R.id.item_setup_kick) == btn)
                return adapter.getItem(i);
        }
        return null;
    }

    private void setupHost() {
        //BLUETOOTH
        BluetoothService.getmAdapter().setName(Constants.app_name + " - " + userData.getUsername());
        BluetoothService.startDiscoverable(ownerContext, bCReciever);
        BluetoothService.start();
        //END BLUETOOTH
        userData.setPlayerID(0);
        isHost = true;
        setTitle(getContext().getString(R.string.select_host));
        adjustContent();

    }

    private void setupClient() {
        //BLUETOOTH
        BluetoothService.getmAdapter().setName(userData.getUsername());
        BluetoothService.stopSearching(ownerContext, bCReciever);
        BluetoothService.startSearching(ownerContext, bCReciever);
        //END BLUETOOTH
        isHost = false;
        setTitle(getContext().getString(R.string.select_join));
        userList.setOnItemClickListener(this);
        buttonArea.removeView(startButton);
        adapter.clear();
        adjustContent();
    }


    //INHERITED METHODS

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_setup);
        //Gets elements
        messageText = (TextView) findViewById(R.id.setup_message);
        countText = (TextView) findViewById(R.id.setup_count);
        cancelButton = (Button) findViewById(R.id.setup_cancel);
        startButton = (Button) findViewById(R.id.setup_start);
        buttonArea = (LinearLayout) findViewById(R.id.setup_buttons);
        userList = (ListView) findViewById(R.id.setup_list);
        //Setting fonts
        Typeface light = TypefaceManager.get("Oswald-Light");
        Typeface regular = TypefaceManager.get("Oswald-Regular");
        Typeface bold = TypefaceManager.get("Oswald-Bold");
        messageText.setTypeface(regular);
        countText.setTypeface(light);
        cancelButton.setTypeface(bold);
        startButton.setTypeface(bold);
        //List setup stuff
        adapter = new SetupAdapter(this,isHost,connectedUsers);
        userList.setAdapter(adapter);
        //Adding listeners
        cancelButton.setOnClickListener(this);
        startButton.setOnClickListener(this);
        if (isHost) {
            setupHost();
        } else {
            setupClient();
        }
    }

    @Override
    public void onClick(View v) {
        if (v == startButton) {
            createFinalPlayerList();
            //Send Player ID To Clients
            if(Constants.debug) Log.i(TAG, "FINAL USER LIST SIZE BEFORE WRITE: " + finalUserList.size());
            //TODO: Send Final Player List to all devices in the START message
            if(Constants.debug) Log.i(TAG, "CREATING PLAYERS TEMP ARRAYLIST");
            //Create ArrayList that gets sent over to the next activity
            ArrayList<String> players = new ArrayList<String>();
            for(UserData user : finalUserList){
                players.add(UserData.toString(user));
            }
            if(Constants.debug) Log.i(TAG, "PLAYERS TEMP ARRAYLIST CREATED WITH SIZE: " + players.size() + "   --- CREATING VALUES STRING ARRAY");
            //Create String Array of all players that gets sent to all of the clients
            String[] values = new String[players.size()];
            for(int i = 0; i < players.size(); i++){
                values[i] = players.get(i);
            }
            if(Constants.debug) Log.i(TAG, "VALUES STRING ARRAY CREATED WITH SIZE: " + values.length);
            //TODO: HOST SEND FINAL USER LIST TO CLIENTS
            //BluetoothService.writeToClients(Constants.START,values);
            for(int i = 1; i < finalUserList.size(); i++){
                BluetoothService.write(finalUserList.get(i).getAddress(), finalUserList.get(i).getPlayerID() + "",Constants.START, values);
            }
            if(Constants.debug) Log.i(TAG, "HOST WROTE FINAL USER LIST TO CLIENTS");
            //Game Intent Stuff
            Intent toGame = new Intent(ownerContext, GameActivity.class);
            toGame.putStringArrayListExtra("PlayerArray", players);
            toGame.putExtra("me", UserData.toString(userData));
            toGame.putExtra("host", isHost);
            ownerContext.startActivity(toGame);
            cancel();
        } else if (v == cancelButton) {
            if(isHost){
                BluetoothService.stopDiscoverable(ownerContext, bCReciever);
            }
            else{
                BluetoothService.stopSearching(ownerContext, bCReciever);
            }
            BluetoothService.listeners.remove(this);
            adapter.clear();
            userList.invalidate();
            cancel();
        } else {
            try {
                //This should only run for the host
                UserData user = getUserDataFromButton((Button) v);
                // TODO: Host sends kick to selected client as described in user object
                BluetoothService.writeToClients(Constants.USER_KICKED, new String[] {user.getAddress()});
                removeUser(user);
            } catch (ClassCastException e) {
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        //Should only fire off for client.
        // TODO: Connect client to selected host as described in user object

        UserData selected = adapter.getItem(position);
        if(Constants.debug) Log.i("Log", "Item clicked trying to connect");
        try {
            BluetoothService.connect(selected.getDevice());
        } catch (Exception e) {
            if(Constants.debug) Log.e(TAG, "Cannot connect to server, server not available?");
            adapter.clear();
        }
        if(Constants.debug) Log.i(TAG, "Successfully connected to the host");
        BluetoothService.getmAdapter().cancelDiscovery();
        BluetoothService.writeToClients(Constants.USER_CONNECTED,userData.toArray());
        if (true)
            joined = true;
        adjustContent();
    }

    //BLUETOOTH METHODS
    public  final BroadcastReceiver bCReciever = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(Constants.debug) Log.i(TAG, action);
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(Constants.debug) Log.i(TAG,"Device Name: " + device.getName());
                UserData foundDevice = new UserData(device, device.getAddress(), device.getName());
                if (!connectedUsers.contains(foundDevice) && device.getName() != null) {
                    if(Constants.debug) Log.i(TAG, "USER IS NOT ON LIST");
                    //TODO: MAKE IT SO THAT IT LOOKS FOR THE GAME NAME, NOT APP NAME.
                    if (foundDevice.getUsername().contains(Constants.app_name)) {
                        if(Constants.debug) Log.i(TAG, "USERNAME CONTAINS APP NAME - ADDED TO AVAILABLE DEVICES");
                        addUser(foundDevice);
                    }
                }
            }
            if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
                if(Constants.debug) Log.i(TAG, "USER CONNECTED TO SERVER - adding user to list");
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                UserData connectedDevice = new UserData(device,device.getAddress(), device.getName());
                addUser(connectedDevice);
                if(Constants.debug) Log.i(TAG, "USER ADDED TO LIST WITH ADDRESS: " + device.getAddress());
                if(isHost){

                }
            }
            if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){
                if(Constants.debug) Log.i(TAG, "USER DISCONNECTED");
                if(isHost){
                    //TODO: REMOVE DISCONNECTED USER FROM USER LIST
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if(Constants.debug) Log.i(TAG, "USER DISCONNECTED WITH ADDRESS: " + device.getAddress());
                    if(Constants.debug) Log.i(TAG, "ADAPTER SIZE: " + connectedUsers.size());
                    for(UserData user : connectedUsers){
                        if(Constants.debug) Log.i(TAG, "USER ADDRESS: " + user.getAddress() + "   DEVICE ADDRESS: " + device.getAddress());
                        if(device.getAddress().equals(user.getAddress())){
                            removeUser(user);
                            if(Constants.debug) Log.i(TAG, "REMOVING " + user.getUsername());
                        }
                    }
                    userList.invalidate();
                }else{
                    BluetoothService.stop();
                    adapter.clear();
                    userList.invalidate();
                    new MessageToast(ownerContext,"You were disconnected from the host.").show();
                    cancel();
                }
            }
        }
    };

    @Override
    public void onCancel(DialogInterface dialog) {
        if(Constants.debug) Log.i(TAG, "ON CANCEL CALLED");
    }

    @Override
    protected void onStop() {
        super.onStop();
        //ownerContext.unregisterReceiver(bCReciever);
    }

    @Override
    public void onReceiveMessage(int player, int messageType, Object[] content) {
        switch(messageType){
            case Constants.START:
                if(Constants.debug) Log.i(TAG, "START MESSAGE RECEIVED FROM HOST");
                //ownerContext.unregisterReceiver(bCReciever);
                BluetoothService.getmAdapter().cancelDiscovery();
                //TODO: ADD BUNDLED DATA TO SEND TO GAME ACTIVITY
                userData.setPlayerID(player);
                if(Constants.debug) Log.i(TAG, "USER ID FROM HOST:" + userData.playerID);
                Intent toGame = new Intent(ownerContext, GameActivity.class);
                if(Constants.debug) Log.i(TAG, "RECREATING PLAYER LIST SENT FROM HOST WITH SIZE: " + content.length);
                ArrayList<String> players = new ArrayList<String>();
                for(int i = 0; i < content.length; i++){
                    players.add((String)content[i]);
                }
                if(Constants.debug) Log.i(TAG, "RECREATED PLAYER LIST SENT FROM HOST WITH SIZE: " + players.size());
                toGame.putStringArrayListExtra("PlayerArray", players);

                toGame.putExtra("me", UserData.toString(userData));
                toGame.putExtra("host", isHost);
                ownerContext.startActivity(toGame);
                break;
            case Constants.USER_CONNECTED:
                if(Constants.debug) Log.i(TAG, "USER_CONNECTED: PROCESSING USER DATA");
                UserData user = new UserData((String)content[0], Integer.getInteger((String)content[1]));
                if(connectedUsers.contains(user)){
                    if(Constants.debug) Log.i(TAG, "Duplicate user found, removing and adding again");
                    connectedUsers.remove(user);
                }
                adapter.add(user);
                break;
            case Constants.USER_ID:
                if(Constants.debug) Log.i(TAG, "USER ID RECEIVED: " + player);
                userData.setPlayerID(player);
                break;
            case Constants.USER_KICKED:
                if(Constants.debug) Log.i(TAG, "USER KICK RECEIVED: User Address - " + content[0]);
                if(Constants.debug) Log.i(TAG, "User Address: " + BluetoothService.getmAdapter().getAddress());
                if(content[0].equals(userData.address)) {
                    BluetoothService.stop();
                    adapter.clear();
                    userList.invalidate();
                    cancel();
                    new MessageToast(ownerContext, "You were kicked by the host.").show();
                }
                break;
        }
    }

    public void createFinalPlayerList(){
        finalUserList.add(userData);
        finalUserList.addAll(connectedUsers);
        if(Constants.debug) Log.i(TAG, "SETTING IDS, count " +  finalUserList.size());
        int i = 0;
        for(UserData user : finalUserList){
            if(Constants.debug) Log.i(TAG, "PRE SET: " + user.username + " | " + user.playerID + "");
            user.setPlayerID(i);
            if(Constants.debug) Log.i(TAG, "AFTER SET: " + user.playerID + "");
            i++;
        }
        userData.setPlayerID(0);
    }
}