package com.njupt.middleware;

import android.os.Message;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by vaylb on 16-11-22.
 */
public class FileTransferThread implements Runnable{

    private static final String TAG = "FileTransferThread";
    private static final int DEFAULT_PORT_TCP = 40006;

    private DeviceManager mDeviceManager;

    public volatile boolean TcpFlag = true;
    private volatile boolean hasConnect = false;

    private static ServerSocket serverSocket = null;
    private ConcurrentHashMap<String, Socket> socketsMap = null;
    private FileInputStream stream_in = null;
    private int mTargetNum = -1;
    private int mFileSize = -1;

    public FileTransferThread(DeviceManager dm, File file, int targetNum) {
        this.mDeviceManager = dm;
        this.socketsMap = new ConcurrentHashMap<String, Socket>();
        mTargetNum = targetNum;
        mFileSize = (int)file.length();
        try {
            this.stream_in = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        int size = 4096;
        byte data[] = new byte[size];
        try {
            if(serverSocket ==  null) {
                serverSocket = new ServerSocket(DEFAULT_PORT_TCP);
                serverSocket.setReuseAddress(true);
                serverSocket.setSoTimeout(50000);
            }

            while (TcpFlag) {
                if (!hasConnect) {
                    do{
                        Log.e(TAG, "TCP listen");
                        Socket socket = serverSocket.accept();
                        socket.setSoTimeout(50000);
                        socketsMap.put(socket.getInetAddress().getHostAddress(),socket);
                    }while (socketsMap.size() < mTargetNum);
                    Log.d(TAG, "vaylb->Tcp listen, total video device: "+ mTargetNum+", file size:"+mFileSize);
                    hasConnect = true;
                    DataOutputStream outputStream;
                    for(ConcurrentMap.Entry<String,Socket> e: socketsMap.entrySet() ){
                        outputStream = new DataOutputStream(e.getValue().getOutputStream());
                        outputStream.writeInt(mFileSize);
                        outputStream.flush();
                    }
                } else {
                    DataOutputStream outputStream;
                    if(stream_in.read(data) > 0){
                        for(ConcurrentMap.Entry<String,Socket> e: socketsMap.entrySet() ){
                            outputStream = new DataOutputStream(e.getValue().getOutputStream());
                            outputStream.write(data);
                            outputStream.flush();
                        }
                    }else TcpFlag = false;
//                    else{
//                        for(ConcurrentMap.Entry<String,Socket> e: socketsMap.entrySet() ){
//                            outputStream = new DataOutputStream(e.getValue().getOutputStream());
//                            outputStream.writeInt(flash);
//                            outputStream.flush();
//                        }
//                    }
                }
                Thread.sleep(2);
            }
        }catch (SocketTimeoutException e) {
            e.printStackTrace();
            Message message=new Message();
            message.what=7;
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
