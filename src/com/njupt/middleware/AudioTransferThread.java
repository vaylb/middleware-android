
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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AudioTransferThread implements Runnable {
    private static final String TAG = "DataTransferThread";
    private static final int DEFAULT_PORT_TCP = 40003;
    private final Lock lock = new ReentrantLock();

    private DeviceManager mDeviceManager;

    public volatile boolean TcpFlag = true;
    private volatile boolean hasConnect = false;

    private static ServerSocket serverSocket = null;
    private ConcurrentHashMap<String, Socket> socketsMap = null;

    private FileInputStream stream_in = null;
    private int mTargetNum = -1;
    private int mFileSize = -1;

    public AudioTransferThread(DeviceManager dm, File file,int targetNum) {
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
    	int size = 4096,read_tmp = 0;
    	byte data[] = new byte[size];
        int count = 0;
        int flash = 0;
    	
        try {
            if(serverSocket ==  null){
                serverSocket = new ServerSocket(DEFAULT_PORT_TCP);
                serverSocket.setReuseAddress(true);
                serverSocket.setSoTimeout(500000);
            }
            
            while (TcpFlag) {
				if (!hasConnect) {
					do{
						Log.e(TAG, "TCP listen, current num "+socketsMap.size()+" total "+mTargetNum);
						Socket socket = serverSocket.accept();
						socket.setSoTimeout(500000);
						socketsMap.put(socket.getInetAddress().getHostAddress(),socket);
					}while (socketsMap.size() < mTargetNum);
					Log.d(TAG, "vaylb->Tcp listen, total audio device: "+ mTargetNum);
					hasConnect = true;
                    DataOutputStream outputStream;
                    for(ConcurrentMap.Entry<String,Socket> e: socketsMap.entrySet() ){
                        outputStream = new DataOutputStream(e.getValue().getOutputStream());
                        outputStream.writeInt(mFileSize);
                        outputStream.flush();
                    }
				} else { 
					DataOutputStream outputStream;
					if((read_tmp = stream_in.read(data)) > 0){
						for(ConcurrentMap.Entry<String,Socket> e: socketsMap.entrySet() ){
	                    	outputStream = new DataOutputStream(e.getValue().getOutputStream());
	                    	outputStream.write(data);
	                    	outputStream.flush();
	            		}
                        count+=read_tmp;
//                        Log.d(TAG, "vaylb->send data:"+ read_tmp);
					} else TcpFlag = false;
				}
				Thread.sleep(20);
            }
            count+=read_tmp;
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
            Log.d(TAG, "vaylb->Tcp thread end, send "+count+" byte");
            try {
            	for(ConcurrentMap.Entry<String,Socket> e: socketsMap.entrySet())
            		e.getValue().close();
            	if (serverSocket != null) {
                    serverSocket.close();
                    serverSocket = null;
                }
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
            
        }

    }

}
