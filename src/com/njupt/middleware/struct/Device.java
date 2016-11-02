package com.njupt.middleware.struct;

import java.net.InetAddress;


public class Device {
	public String name;
	public int type;
	public InetAddress address;
	
	public final static int TYPE_AUDIO = 0;
	public final static int TYPE_VIDEO = 1;
	public final static int TYPE_PRINTER = 2;
	
	public Device(String name,int type, InetAddress addr){
		this.name = name;
		this.type = type;
		this.address = addr;
	}
}
