
package com.njupt.middleware;

import android.os.Message;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SetUpThread implements Runnable {
    private static final String TAG = "SetUpThread";
    private static final int DEFAULT_PORT_TCP = 39999;
    private DeviceManager mDeviceManager;

    private volatile boolean hasConnect = false;
    public volatile boolean TcpFlag = true;

    private static ServerSocket serverSocket = null;
    private ConcurrentHashMap<String, Socket> socketsMap = null;
    private String SSID,PSW;

    public SetUpThread(DeviceManager dm,String ssid,String psw) {
        this.mDeviceManager = dm;
        this.SSID = ssid;
        this.PSW = psw;
        this.socketsMap = new ConcurrentHashMap<String, Socket>();
    }

    @Override
    public void run() {
        try {
            if(serverSocket ==  null){
                serverSocket = new ServerSocket(DEFAULT_PORT_TCP);
                serverSocket.setReuseAddress(true);
                serverSocket.setSoTimeout(50000);
            }
            while (TcpFlag) {
                if (!hasConnect) {
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(50000);
                    socketsMap.put(socket.getInetAddress().getHostAddress(), socket);
                    hasConnect = true;
                    Log.e(TAG, "TCP listen, device ip: " +socket.getInetAddress().getHostAddress());
                } else {
                    DataOutputStream outputStream;
                    String msg = SSID + ":" + PSW;
                    int size = 4 + msg.length();
                    Log.e(TAG, "set up msg size = " + size);
                    for (ConcurrentMap.Entry<String, Socket> e : socketsMap.entrySet()) {
                        outputStream = new DataOutputStream(e.getValue().getOutputStream());
                        outputStream.writeInt(size);
                        outputStream.writeBytes(msg);
                        outputStream.flush();
                    }
                    TcpFlag = false;
                    Message message = new Message();
                    message.what = 1;
                    mDeviceManager.mHandler.sendMessage(message);
                }
            }
        }catch (SocketTimeoutException e) {
            e.printStackTrace();
            Message message=new Message();
            message.what=2;
            mDeviceManager.mHandler.sendMessage(message);
        }
        catch (Exception e) {
            Log.d(TAG, "vaylb->tcp Exception");
            e.printStackTrace();
        } finally {
            Log.d(TAG, "vaylb->Tcp thread end");
            try {
            	for(ConcurrentMap.Entry<String,Socket> e: socketsMap.entrySet())
            		e.getValue().close();
            	if (serverSocket != null) serverSocket.close();
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
        }

    }

}
