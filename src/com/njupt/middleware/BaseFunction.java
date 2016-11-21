package com.njupt.middleware;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import org.apache.http.conn.util.InetAddressUtils;

import android.content.Context;
import android.util.Log;

import com.njupt.middleware.utils.CommProgressDialog;

public class BaseFunction {
	private static String TAG = "BaseFunction"; 
	
	
	/**
	 * 获取本机IP地址
	 */
	public static String getLocalHostIp(){
        String ipaddress = "";
        try{
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            // 遍历所用的网络接口
            while (en.hasMoreElements()){
                NetworkInterface nif = en.nextElement();// 得到每一个网络接口绑定的所有ip
                Enumeration<InetAddress> inet = nif.getInetAddresses();
                // 遍历每一个接口绑定的所有ip
                while (inet.hasMoreElements()){
                    InetAddress ip = inet.nextElement();
                    if (!ip.isLoopbackAddress() && InetAddressUtils.isIPv4Address(ip.getHostAddress())){
                        return ipaddress = ip.getHostAddress();
                    }
                }

            }
        }catch (SocketException e){
            Log.e(TAG, "获取本地ip地址失败");
            e.printStackTrace();
        }
        return ipaddress;
    }

	public static String getBroadcastAddress(){
		String ip = getLocalHostIp();
        if(ip.startsWith("172.20.10")){
            Log.e(TAG, "广播地址：172.20.10.15");
            return "172.20.10.15";
        }else if(ip.startsWith("10.66.147")){
            Log.e(TAG, "广播地址：10.66.147.255");
            return "10.66.147.255";
        }

		return ip.substring(0, ip.lastIndexOf("."))+".255";
	}
	
	public static byte[] intToByteArray(int a) {  
	    return new byte[] {  
	        (byte) ((a >> 24) & 0xFF),  
	        (byte) ((a >> 16) & 0xFF),     
	        (byte) ((a >> 8) & 0xFF),     
	        (byte) (a & 0xFF)  
	    };
	}
	
	public static byte[] IntToByteArray(int n) {  
        byte[] b = new byte[4];  
        b[0] = (byte) (n & 0xff);  
        b[1] = (byte) (n >> 8 & 0xff);  
        b[2] = (byte) (n >> 16 & 0xff);  
        b[3] = (byte) (n >> 24 & 0xff);  
        return b;  
    }

    public static CommProgressDialog showProgressDialog(Context context, String message){
        CommProgressDialog dialog = CommProgressDialog.createDialog(context, R.drawable.anim_white);
        dialog.setMessage(message);
        dialog.show();
        return dialog;
    }

}
