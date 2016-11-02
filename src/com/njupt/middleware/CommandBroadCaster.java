package com.njupt.middleware;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.util.Log;

import com.njupt.middleware.struct.Device;

public class CommandBroadCaster implements Runnable {
	private static final String TAG = "CommandBroadCaster";
	private static final int SINGLE_CAST = 0x01;
	private static final int BROADCAST_CAST= 0x02;
	private static final int DEFAULT_PORT_TCP = 40001;
	private static String destAddressStr = null;
	private static int repeat;
	private InetAddress destAddress = null;
	private static DatagramSocket socket = null;
	private String sendMsg;
	private int mode; //single or broadcast
	private Map<Integer,Device> singleCastTargetDevice;

	public CommandBroadCaster(String msg) {
		this.sendMsg = msg;
		this.mode = BROADCAST_CAST;
		this.repeat = -1;
		if(destAddressStr == null)destAddressStr = BaseFunction.getBroadcastAddress();
	}

	public CommandBroadCaster(String msg, Map<Integer,Device> devices){
		this.sendMsg = msg;
		this.mode = SINGLE_CAST;
		this.repeat = -1;
		singleCastTargetDevice = devices;
	}

	public CommandBroadCaster(String msg, Integer repeat){
		this.sendMsg = msg;
		this.mode = BROADCAST_CAST;
		this.repeat = repeat;
		if(destAddressStr == null)destAddressStr = BaseFunction.getBroadcastAddress();
	}


	@Override
	public void run() {
		try {
			if(socket == null)socket = new DatagramSocket(DEFAULT_PORT_TCP);
			if(repeat == -1){
				if(mode == SINGLE_CAST){
					Log.d(TAG, "vaylb-->singlecast message ["+sendMsg+"] to "+singleCastTargetDevice.size()+" devices");
					socket.setBroadcast(false);
					for (HashMap.Entry<Integer,Device> e:singleCastTargetDevice.entrySet()) {
						destAddress = e.getValue().address;
						DatagramPacket dp = new DatagramPacket(sendMsg.getBytes(), sendMsg.length(), destAddress, DEFAULT_PORT_TCP);
						socket.send(dp);
					}
				}else{
					Log.d(TAG, "vaylb-->broadcast message ["+sendMsg+"]");
					destAddress = InetAddress.getByName(destAddressStr); // 初始化多播地址
					socket.setBroadcast(true);
					DatagramPacket dp = new DatagramPacket(sendMsg.getBytes(), sendMsg.length(), destAddress, DEFAULT_PORT_TCP);
					socket.send(dp);
				}
			}else{
				Log.d(TAG, "vaylb-->repeat broadcast message ["+sendMsg+"]");
				destAddress = InetAddress.getByName(destAddressStr); // 初始化多播地址
				socket.setBroadcast(true);
				DatagramPacket dp = new DatagramPacket(sendMsg.getBytes(), sendMsg.length(), destAddress, DEFAULT_PORT_TCP);
				for (int i = 0; i < repeat; i++) {
					socket.send(dp);
					Thread.sleep(5000);
				}
			}

		} catch (SocketException e) {
			e.printStackTrace();

		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			if (socket != null) {
				socket.close();
				socket = null;

			}
			if(destAddressStr != null){
				destAddressStr = null;
			}
		}
	}

}
