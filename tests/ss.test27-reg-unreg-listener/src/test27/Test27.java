/*
 * This file is part of SerialPundit project and software.
 *
 * Copyright (C) 2014-2016, Rishi Gupta. All rights reserved.
 *
 * The SerialPundit software is DUAL licensed. It is made available under the terms of the GNU Affero
 * General Public License (AGPL) v3.0 for non-commercial use and under the terms of a commercial
 * license for commercial use of this software.
 *
 * The SerialPundit software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package test27;

import com.serialpundit.core.SerialComPlatform;
import com.serialpundit.core.SerialComSystemProperty;
import com.serialpundit.serial.SerialComManager;
import com.serialpundit.serial.SerialComManager.BAUDRATE;
import com.serialpundit.serial.SerialComManager.DATABITS;
import com.serialpundit.serial.SerialComManager.FLOWCONTROL;
import com.serialpundit.serial.SerialComManager.PARITY;
import com.serialpundit.serial.SerialComManager.STOPBITS;
import com.serialpundit.serial.ISerialComDataListener;
import com.serialpundit.serial.ISerialComEventListener;
import com.serialpundit.serial.SerialComLineEvent;

class EventListener implements ISerialComEventListener {
	@Override
	public void onNewSerialEvent(SerialComLineEvent lineEvent) {
		System.out.println("eventCTS : " + lineEvent.getCTS());
		System.out.println("eventDSR : " + lineEvent.getDSR());
	}
}

class DataListener implements ISerialComDataListener {

	@Override
	public void onNewSerialDataAvailable(byte[] arg0) {
		System.out.println("Read from serial port : " + new String(arg0));
	}

	@Override
	public void onDataListenerError(int arg0) {
	}
}

/* register/unregister data/event listener with data/event many times */
public class Test27 {
	public static void main(String[] args) {
		try {
			SerialComManager scm = new SerialComManager();
			EventListener eventListener = new EventListener();
			DataListener dataListener = new DataListener();

			String PORT = null;
			String PORT1 = null;
			SerialComPlatform scp = new SerialComPlatform(new SerialComSystemProperty());

			int osType = scp.getOSType();
			if(osType == SerialComPlatform.OS_LINUX) {
				PORT = "/dev/ttyUSB0";
				PORT1 = "/dev/ttyUSB1";
			}else if(osType == SerialComPlatform.OS_WINDOWS) {
				PORT = "COM51";
				PORT1 = "COM52";
			}else if(osType == SerialComPlatform.OS_MAC_OS_X) {
				PORT = "/dev/cu.usbserial-A70362A3";
				PORT1 = "/dev/cu.usbserial-A602RDCH";
			}else if(osType == SerialComPlatform.OS_SOLARIS) {
				PORT = null;
				PORT1 = null;
			}else{
			}

			long handle = scm.openComPort(PORT, true, true, true);
			scm.configureComPortData(handle, DATABITS.DB_8, STOPBITS.SB_1, PARITY.P_NONE, BAUDRATE.B9600, 0);
			scm.configureComPortControl(handle, FLOWCONTROL.NONE, 'x', 'x', false, false);

			long handle1 = scm.openComPort(PORT1, true, true, true);
			scm.configureComPortData(handle1, DATABITS.DB_8, STOPBITS.SB_1, PARITY.P_NONE, BAUDRATE.B9600, 0);
			scm.configureComPortControl(handle1, FLOWCONTROL.NONE, 'x', 'x', false, false);
			int x = 0;

			for(x=0; x<5000; x++) {
				System.out.println("register  : " + scm.registerLineEventListener(handle, eventListener));
				scm.setDTR(handle1, true);
				scm.setRTS(handle1, true);
				scm.setRTS(handle1, false);
				scm.setDTR(handle1, false);
				Thread.sleep(10);
				System.out.println("unregister : " + scm.unregisterLineEventListener(handle, eventListener));
			}
			System.out.println("test event pass \n");

			for(x=0; x<5000; x++) {
				System.out.println("register  : " + scm.registerDataListener(handle, dataListener));
				scm.writeString(handle1, "T", 0);
				Thread.sleep(50);
				System.out.println("unregister : " + scm.unregisterDataListener(handle, dataListener));
			}
			System.out.println("test data pass \n");

			scm.closeComPort(handle);
			scm.closeComPort(handle1);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
