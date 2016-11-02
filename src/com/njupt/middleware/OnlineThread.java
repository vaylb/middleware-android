
package com.njupt.middleware;

import android.os.Message;
import android.util.Log;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class OnlineThread implements Runnable {
    private static final String TAG = "OnlineThread";
    public static final int DEFAULT_AUDIO_PORT = 40003;
    public static final int DEFAULT_VIDEO_PORT = 40005;
    private final Lock lock = new ReentrantLock();
    private DeviceManager mDeviceManager;
    private String mDataUrl;
    private HttpURLConnection mConn;
    private InputStream mDataInputStream;
    public volatile boolean TcpFlag = true;
    private volatile boolean hasConnect = false;
    private int mCurrentPort = -1;

    private ServerSocket serverSocket = null;
    private ConcurrentHashMap<String, Socket> socketsMap = null;


    public OnlineThread(DeviceManager dm, String url, int port) {
        this.mDeviceManager = dm;
        this.mDataUrl = url;
        this.mCurrentPort = port;
        this.socketsMap = new ConcurrentHashMap<String, Socket>();
    }

    @Override
    public void run() {
    	int size = 4096;
    	byte data[] = new byte[size];
        try {
            serverSocket = new ServerSocket(mCurrentPort);
            serverSocket.setReuseAddress(true);
            serverSocket.setSoTimeout(500000);
            DataOutputStream outputStream;
            while (TcpFlag) {
				if (!hasConnect) {
					do{
						Log.e(TAG, "TCP listen");
						Socket socket = serverSocket.accept();
						socket.setSoTimeout(500000);
						socketsMap.put(socket.getInetAddress().getHostAddress(),socket);
					}while (socketsMap.size() < mDeviceManager.getAudioDeviceNum());
					Log.d(TAG, "vaylb->Tcp listen, total audio device: "+ mDeviceManager.getAudioDeviceNum());
					hasConnect = true;
                    URL url=new URL(mDataUrl);
                    mConn = (HttpURLConnection)url.openConnection();
                    mDataInputStream = mConn.getInputStream();
				} else {
//					if(!audio_params_send_flag){ //传递音频相关参数
//						int channels= 2, frequency = 44100, perframebits = 16;
//	                    for(ConcurrentMap.Entry<String,Socket> e: socketsMap.entrySet() ){
//	                    	outputStream = new DataOutputStream(e.getValue().getOutputStream());
//	                    	outputStream.write(BaseFunction.IntToByteArray(channels));
//	                    	outputStream.write(BaseFunction.IntToByteArray(frequency));
//	                    	outputStream.write(BaseFunction.IntToByteArray(perframebits));
//	                    	outputStream.flush();
//	            		}
//	                    audio_params_send_flag = true;
//					}

                    {
                        int i = size;
                        int ret = 0;
                        while(i > 0 && (ret = mDataInputStream.read(data,size - i,i)) > 0)
                        {
                            i -= ret;
                        }
                    }
                    for(ConcurrentMap.Entry<String,Socket> e: socketsMap.entrySet() ){
                        outputStream = new DataOutputStream(e.getValue().getOutputStream());
                        outputStream.write(data,0,size);
                        outputStream.flush();
                    }
				}
            }
        }catch (SocketTimeoutException e) {
            e.printStackTrace();
            Log.e(TAG, "vaylb-> SocketTimeout");
            Message message=new Message();
            message.what=7;
            mDeviceManager.mHandler.sendMessage(message);
        }
        catch (Exception e) {
            Log.e(TAG, "vaylb->tcp Exception");
            e.printStackTrace();
        } finally {
            Log.e(TAG, "vaylb->Tcp thread end");
            try {
            	for(ConcurrentMap.Entry<String,Socket> e: socketsMap.entrySet())
            		e.getValue().close();
            	if (serverSocket != null) serverSocket.close();
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
            
        }

    }

}
