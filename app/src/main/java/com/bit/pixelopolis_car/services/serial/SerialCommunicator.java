/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bit.pixelopolis_car.services.serial;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Toast;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.HashMap;
import java.util.Map;

public class SerialCommunicator implements ServiceConnection, SerialListener {
    private enum Connected { False, Pending, True }
    //public static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    public static final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    public static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private Context context;
    private UsbDevice device;
    private UsbSerialDriver driver;
    private Integer portNum;
    private final int baudRate = 9600;
    private final String newline = "\r\n";

    private SerialSocket socket;
    private SerialService service;
    private boolean initialStart = true;
    private boolean isResumed = false;
    private Connected connected = Connected.False;
    private BroadcastReceiver broadcastReceiver;

    private final Handler handler = new Handler();
    private int errorCount = 0;
    private String debugText = "";
    private String receivedData = "";
    private Map<Integer,String> baseNumber24;

    private SerialCommunicatorListener listener;

    public interface SerialCommunicatorListener{
        void onReceive(String text);
        void onDisconnect();
        void onDebugTextUpdate(String text);
    }

    public SerialCommunicator(Context context,SerialCommunicatorListener listener){
        this.context = context;
        this.listener = listener;
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction()!=null) {
                    switch (intent.getAction()) {
                        case ACTION_USB_PERMISSION:
                            Toast.makeText(context,"USB get permission",Toast.LENGTH_SHORT).show();
                            Boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                            connect(granted);
                            break;
                        case ACTION_USB_ATTACHED:
                            Toast.makeText(context,"Attach",Toast.LENGTH_SHORT).show();
                            findDevice();
                            connect();
                            break;
                        case ACTION_USB_DETACHED:
                            Toast.makeText(context,"Detach",Toast.LENGTH_SHORT).show();
                            disconnect();
                            break;
                        default:
                    }
                }
            }
        };
        findDevice();
        setupMap();
        context.bindService(new Intent(context, SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    public void start(){
        if(service != null)
            service.attach(this);
        else
            context.startService(new Intent(context, SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change

    }

    public void resume(){
        setFilter();
        //context.registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB));
        if(initialStart && service !=null) {
            initialStart = false;
            handler.post(this::connect);
        }
        isResumed = true;
    }

    public void pause(){
        context.unregisterReceiver(broadcastReceiver);
        isResumed = false;
    }

    public void stop(){
        if(service != null )
            service.detach();
    }

    public void destroy(){
        if (connected != Connected.False)
            disconnect();
        context.stopService(new Intent(context, SerialService.class));

        try { context.unbindService(this); } catch(Exception ignored) {}
    }

    private void findDevice(){
        driver = null;
        device = null;
        portNum = null;
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        UsbSerialProber usbDefaultProber = UsbSerialProber.getDefaultProber();
        UsbSerialProber usbCustomProber = CustomProber.getCustomProber();
        for(UsbDevice deviceItem : usbManager.getDeviceList().values()) {
            UsbSerialDriver driverItem = usbDefaultProber.probeDevice(deviceItem);
            if(driverItem == null) {
                driverItem = usbCustomProber.probeDevice(deviceItem);
            }
            if(driverItem != null) {
                for(int portItem = 0; portItem < driverItem.getPorts().size(); portItem++) {
                    driver = driverItem;
                    device = deviceItem;
                    portNum = portItem;
                    break;
                }
            }
            if(driver !=null && device !=null && portNum !=null)
                break;
        }
    }

    private void connect() {
        connect(null);
    }

    private void connect(Boolean permissionGranted) {
        if (device == null ||driver == null || portNum == null)
            findDevice();

        if(device == null) {
            appendStatus("connection failed: device not found");
            return;
        }
        if(driver == null) {
            appendStatus("connection failed: no driver for device");
            return;
        }
        if(portNum == null || driver.getPorts().size() < portNum) {
            appendStatus("connection failed: not enough ports at device");
            return;
        }
        UsbManager usbManager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
        UsbSerialPort usbSerialPort = driver.getPorts().get(portNum);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if(usbConnection == null && permissionGranted == null && !usbManager.hasPermission(driver.getDevice())) {
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            Toast.makeText(context,"connection null",Toast.LENGTH_SHORT).show();
            return;

        }
        String text = "portNum Num"+Integer.toString(portNum);
        Toast.makeText(context,text,Toast.LENGTH_SHORT).show();
        if(usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                appendStatus("connection failed: permission denied");
            else
                appendStatus("connection failed: open failed");
            return;
        }

        connected = Connected.Pending;
        try {
            socket = new SerialSocket();
            service.connect(this, "Connected");
            socket.connect(context, service, usbConnection, usbSerialPort, baudRate);
            // usb connect is not asynchronous. connect-success and connect-error are returned immediately from socket.connect
            // for consistency to bluetooth/bluetooth-LE app use same SerialListener and SerialService classes
            onSerialConnect();
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }
    private void disconnect() {
        connected = Connected.False;
        if(service != null)
            service.disconnect();
        if(socket != null)
            socket.disconnect();
        socket = null;

        listener.onDisconnect();
    }

    private void setFilter(){
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_ATTACHED);
        filter.addAction(ACTION_USB_DETACHED);
        filter.addAction(ACTION_USB_PERMISSION);
        //filter.addAction(INTENT_ACTION_GRANT_USB);
        context.registerReceiver(broadcastReceiver,filter);
    }

    public void send(int leftSpeed,int rightSpeed){
        if(connected != Connected.True) {
            Toast.makeText(context, "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String leftString = get24BaseNumber(leftSpeed);
            String rightString = get24BaseNumber(rightSpeed);
            String sendString = leftString+rightString+"\n";
            byte[] data = sendString.getBytes();
            //Log.i("Debug",data.toString());


            String receiveTextStr = "";
            receiveTextStr += leftSpeed +" "+ rightSpeed +"size = "+data.length+"\n";


            socket.write(data);
            Thread.sleep(35);

            receiveTextStr+=String.format("error count: %d\n" , errorCount);

            status(receiveTextStr);

        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(byte[] data) {
        // TODO: callback to caller
        String s = new String(data);

        if(s.equals("\n")) {
            listener.onReceive(receivedData);
            receivedData = "";
        }
        else{
            receivedData += s;
        }
    }

    public void refresh(){
        findDevice();
        if(device!=null){
            connect();
        }
    }

    private void setupMap(){
        baseNumber24 = new HashMap<>();
        baseNumber24.put(0,"0");
        baseNumber24.put(1,"1");
        baseNumber24.put(2,"2");
        baseNumber24.put(3,"3");
        baseNumber24.put(4,"4");
        baseNumber24.put(5,"5");
        baseNumber24.put(6,"6");
        baseNumber24.put(7,"7");
        baseNumber24.put(8,"8");
        baseNumber24.put(9,"9");
        baseNumber24.put(10,"a");
        baseNumber24.put(11,"b");
        baseNumber24.put(12,"c");
        baseNumber24.put(13,"d");
        baseNumber24.put(14,"e");
        baseNumber24.put(15,"f");
        baseNumber24.put(16,"g");
        baseNumber24.put(17,"h");
        baseNumber24.put(18,"i");
        baseNumber24.put(19,"j");
        baseNumber24.put(20,"k");
        baseNumber24.put(21,"l");
        baseNumber24.put(22,"m");
        baseNumber24.put(23,"n");
        baseNumber24.put(24,"o");
    }

    private String get24BaseNumber(int input){
        String out="";
        int upperInput = input+288;
        Integer firstIndex =  (int)Math.floor(upperInput/24.0f);
        out +=baseNumber24.get(firstIndex);
        Integer secondIndex = upperInput-firstIndex*24;
        out +=baseNumber24.get(secondIndex);
        return out;
    }

    private void status(String str) {
        debugText = str;
        listener.onDebugTextUpdate(str);
    }

    private void appendStatus(String str) {
        debugText += str+"\n";
        if(debugText.length() > 100)
            debugText = debugText.substring(50);
        listener.onDebugTextUpdate(debugText);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        service = ((SerialService.SerialBinder) iBinder).getService();
        if(initialStart && isResumed) {
            initialStart = false;
            handler.post(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        service = null;
    }

    @Override
    public void onSerialConnect() {
        appendStatus("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        appendStatus("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        appendStatus("connection lost: " + e.getMessage());
        errorCount++;
    }
}
