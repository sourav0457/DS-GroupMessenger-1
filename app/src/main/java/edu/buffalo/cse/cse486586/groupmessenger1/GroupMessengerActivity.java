package edu.buffalo.cse.cse486586.groupmessenger1;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    private final Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger1.provider");
    static final String[] portList = {"11108", "11112", "11116", "11120", "11124"};
    static final int SERVER_PORT = 10000;
    Integer sequenceNumber = 0;

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        final EditText editText = (EditText) findViewById(R.id.editText1);
        final Button sendButton = (Button) findViewById(R.id.button4);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText.getText().toString();
                editText.setText("");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {                                        // Since only one parameter was passed (socket instance), sockets[0] contains the socket instance
            ServerSocket serverSocket = sockets[0];

            try {
                while(true) {
                    Socket socket = serverSocket.accept();
                    Log.d(TAG, "ServerTask - 1: Accepted Socket connection ");
                    ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                    Log.d(TAG, "ServerTask - 2: Object Input Stream created");
                    String[] objectRead = (String[]) ois.readObject();
                    Log.d(TAG, "ServerTask - 3: Object read with msg: " + objectRead[1]);
                    Log.d(TAG, "ServerTask - 4: Current seq no.: " + sequenceNumber);
                    Log.d(TAG, "ServerTask - 5: String read from Socket: " + objectRead[1]);
                    publishProgress(Integer.toString(sequenceNumber++), objectRead[1]);
                }
            } catch (IOException e) {
                Log.e(TAG, "Unable to accept server socket connections");
                e.printStackTrace();
            } catch (ClassNotFoundException e){
                Log.e(TAG, "Could not find Class");
                e.printStackTrace();
            }

            return null;
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */

            Log.d(TAG, "OnProgressUpdate - 1: Entered");
            Log.d(TAG, "OnProgressUpdate - 2: SeqNumber: " + strings[0].trim());
            Log.d(TAG, "OnProgressUpdate - 3: Message: " + strings[1].trim());
            String seqNumber = strings[0].trim();
            String strReceived = strings[1].trim();
            Log.d(TAG, "OnProgressUpdate - 4: Accessing TextView: ");
            TextView view = (TextView) findViewById(R.id.textView1);
            Log.d(TAG, "OnProgressUpdate - 5: Printing on TextView: ");
            view.append(seqNumber + ": " + strReceived + "\t\n");

            /*
             * The following code creates a file in the AVD's internal storage and stores a file.
             *
             * For more information on file I/O on Android, please take a look at
             * http://developer.android.com/training/basics/data-storage/files.html
             */
            Log.d(TAG, "OnProgressUpdate - 6: Saving into Content Provider");
            ContentResolver cr = getContentResolver();
            ContentValues cv = new ContentValues();
            cv.put(KEY_FIELD, seqNumber);
            cv.put(VALUE_FIELD, strReceived);
            try {
                cr.insert(mUri, cv);
            }
            catch (Exception e) {
                Log.e(TAG, e.toString());
            }

            return;
        }
    }

    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     *
     * @author stevko
     *
     */
    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {                                                 // msges array contains the msg at index 0 and port at index 1
            try {
                for(String port: portList) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(port));
                    Log.d(TAG, "ClientTask - 1: Message to be sent: " + msgs[0]);
                    String[] msgToSend = {Integer.toString(sequenceNumber), msgs[0]};
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.writeObject(msgToSend);

                    Log.d(TAG, "ClientTask - 2: Object sent: " + msgToSend.toString());

                    if(msgToSend[1].equals("close")){
                        Log.d(TAG, "ClientTask - 3: Closing Socket for after message: " + msgToSend[1]);
                        socket.close();
                    }
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            }

            return null;
        }
    }
}
