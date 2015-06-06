/**
 * Author : Rishi Gupta
 * 
 * This file is part of 'serial communication manager' library.
 *
 * The 'serial communication manager' is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by the Free Software 
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * The 'serial communication manager' is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with serial communication manager. If not, see <http://www.gnu.org/licenses/>.
 */

package com.embeddedunveiled.serial;

import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import com.embeddedunveiled.serial.SerialComManager.FTPMODE;
import com.embeddedunveiled.serial.SerialComManager.FTPPROTO;
import com.embeddedunveiled.serial.SerialComManager.FTPVAR;

/**
 * <p>This class realizes state machine for XMODEM-1k file transfer protocol in Java.</p>
 * <p>Increase in block size increases overall throughput.</p>
 */
public final class SerialComXModem1K {

	private final byte SOH = 0x01;  // Start of header character
	private final byte STX = 0x02;  // Start of text character
	private final byte EOT = 0x04;  // End-of-transmission character
	private final byte ACK = 0x06;  // Acknowledge byte character
	private final byte NAK = 0x15;  // Negative-acknowledge character
	private final byte SUB = 0x1A;  // Substitute/CTRL+Z
	private final byte C   = 0x43;  // ASCII capital C character

	private SerialComManager scm = null;
	private long handle = 0;
	private File fileToProcess = null;
	private int mode = 0;

	private int blockNumber = -1;
	private byte[] block = new byte[1029];            // 1029 bytes xmodem-1k block/packet
	private BufferedInputStream inStream = null;     // sent file from local to remote system
	private BufferedOutputStream outStream = null;   // received file from remote to local system
	private boolean noMoreData = false;


	/**
	 * <p>Allocates object of this class and associate this object with the supplied scm object.</p>
	 * 
	 * @param scm SerialComManager instance associated with this handle
	 * @param handle of the port on which file is to be communicated
	 * @param fileToProcess File instance representing file to be communicated
	 */
	public SerialComXModem1K(SerialComManager scm, long handle, File fileToProcess, int mode) {
		this.scm = scm;
		this.handle = handle;
		this.fileToProcess = fileToProcess;
		this.mode = mode;
	}

	/**
	 * <p>Represents actions to execute in state machine to implement xmodem-1k protocol for sending files.</p>
	 */
	public boolean sendFileX() throws SecurityException, IOException, SerialComException {

		// Finite state machine
		final int CONNECT = 0;
		final int BEGINSEND = 1;
		final int WAITACK = 2;
		final int RESEND = 3;
		final int SENDNEXT = 4;
		final int ENDTX = 5;
		final int ABORT = 6;

		boolean cReceived = false;
		boolean eotAckReceptionTimerInitialized = false;
		String errMsg = null;
		int retryCount = 0;
		int state = -1;
		byte[] data = null;
		long responseWaitTimeOut = 0;
		long eotAckWaitTimeOutValue = 0;
		SerialComCRC crcCalculator = new SerialComCRC();
		inStream = new BufferedInputStream(new FileInputStream(fileToProcess));

		state = CONNECT;
		while(true) {
			switch(state) {
				case CONNECT:
					responseWaitTimeOut = System.currentTimeMillis() + 60000;
					while(cReceived != true) {
						try {
							data = scm.readBytes(handle, 1024);
						} catch (SerialComException exp) {
							inStream.close();
							throw exp;
						}
						if((data != null) && (data.length > 0)) {
							/* Instead of purging receive buffer and then waiting for C, receive all data because
							 * this approach might be faster. The other side might have opened first time and may 
							 * have flushed garbage data. So receive buffer may contain garbage + C character. */
							for(int x=0; x < data.length; x++) {
								if(data[x] == C) {
									cReceived = true;
									state = BEGINSEND;
									break;
								}
							}
						}else {
							try {
								Thread.sleep(100);  // delay before next attempt to check C character reception
							} catch (InterruptedException e) {
							}
							// abort if timed-out while waiting for C character
							if((cReceived != true) && (System.currentTimeMillis() >= responseWaitTimeOut)) {
								errMsg = SerialComErrorMapper.ERR_TIMEOUT_RECEIVER_CONNECT;
								state = ABORT;
								break;
							}
						}
					}
					break;
				case BEGINSEND:
					blockNumber = 1; // Block numbering starts from 1 for the first block sent, not 0.
					assembleBlock(crcCalculator);
					try {
						scm.writeBytes(handle, block, 0);
					} catch (SerialComException exp) {
						inStream.close();
						throw exp;
					}
					state = WAITACK;
					break;
				case RESEND:
					if(retryCount > 10) {
						errMsg = SerialComErrorMapper.ERR_MAX_TX_RETRY_REACHED;
						state = ABORT;
						break;
					}
					try {
						scm.writeBytes(handle, block, 0);
					} catch (SerialComException exp) {
						inStream.close();
						throw exp;
					}
					state = WAITACK;
					break;
				case WAITACK:
					responseWaitTimeOut = System.currentTimeMillis() + 60000; // 1 minute
					while(true) {
						// delay before next attempt to read from serial port
						try {
							if(noMoreData != true) {
								Thread.sleep(150);
							}else {
								Thread.sleep(1500);
							}
						} catch (InterruptedException e) {
						}
	
						// try to read data from serial port
						try {
							data = scm.readBytes(handle);
						} catch (SerialComException exp) {
							inStream.close();
							throw exp;
						}
	
						/* if data received process it. if long timeout occurred abort otherwise retry reading from serial port.
						 * if nothing received at all abort. */
						if((data != null) && (data.length > 0)) {
							break;
						}else {
							if(noMoreData == true) {
								state = ENDTX;
								break;
							}
							if(System.currentTimeMillis() >= responseWaitTimeOut) {
								if(noMoreData == true) {
									errMsg = SerialComErrorMapper.ERR_TIMEOUT_ACKNOWLEDGE_EOT;
								}else {
									errMsg = SerialComErrorMapper.ERR_TIMEOUT_ACKNOWLEDGE_BLOCK;
								}
								state = ABORT;
								break;
							}
						}
					}
	
					if((state != ABORT) && (state != ENDTX)) {
						if(noMoreData != true) {
							if(data[0] == ACK) {
								state = SENDNEXT;
							}else if(data[0] == NAK) {
								retryCount++;
								state = RESEND;
							}else{
								errMsg = SerialComErrorMapper.ERR_KNOWN_ERROR_OCCURED;
								state = ABORT;
							}
						}else {
							if(data[0] == ACK) {
								inStream.close();
								return true; // successfully sent file, let's go back home happily
							}else{
								if(System.currentTimeMillis() >= eotAckWaitTimeOutValue) {
									errMsg = SerialComErrorMapper.ERR_TIMEOUT_ACKNOWLEDGE_EOT;
									state = ABORT;
								}else {
									state = ENDTX;
								}
							}
						}
					}
					break;
				case SENDNEXT:
					retryCount = 0; // reset retry count
					blockNumber++;
					assembleBlock(crcCalculator);
					if(noMoreData == true) {
						state = ENDTX;
						break;
					}
					try {
						scm.writeBytes(handle, block, 0);
					} catch (SerialComException exp) {
						inStream.close();
						throw exp;
					}
					state = WAITACK;
					break;
				case ENDTX:
					if(eotAckReceptionTimerInitialized != true) {
						eotAckWaitTimeOutValue = System.currentTimeMillis() + 60000; // 1 minute
						eotAckReceptionTimerInitialized = true;
					}
					try {
						scm.writeSingleByte(handle, EOT);
					} catch (SerialComException exp) {
						inStream.close();
						throw exp;
					}
					state = WAITACK;
					break;
				case ABORT:
					/* if IOexception occurs, control will not reach here instead exception would have been
					 * thrown already. */
					inStream.close();
					throw new SerialComTimeOutException("sendFileX()", errMsg);
				default:
					break;
			}
		}
	}

	/* Prepares xmodem/crc block [STX][blk #][255-blk #][1019 data bytes][2 byte CRC]
	 * using CRC-16-CCITT. */
	private void assembleBlock(SerialComCRC scCRC) throws IOException {
		int x = 0;
		int numBytesRead = 0;
		int blockCRCval = 0;

		// starts at 01 increments by 1, and wraps 0FFH to 00H (not to 01)
		if(blockNumber > 0xFF) {
			blockNumber = 0x00;
		}

		block[0] = STX;
		block[1] = (byte) blockNumber;
		block[2] = (byte) ~blockNumber;
		
		// read data from file to be sent
		numBytesRead = inStream.read(block, 3, 1024);
		if((numBytesRead > 0) && (numBytesRead < 1024)) {
			// assembling last block with padding
			x = numBytesRead;
			for(x = x + 0; x < 1027; x++) {
				block[x] = SUB;
			}
		}else if(numBytesRead < 0){
			// EOF encountered
			noMoreData = true;
			return;
		}else {
		}

		// 2 byte CRC
		blockCRCval = scCRC.getCRCval(block, 3, 1026);
		block[1027] = (byte) (blockCRCval >>> 8); // CRC high byte
		block[1028] = (byte) blockCRCval;         // CRC low byte
	}

	/**
	 * <p>Represents actions to execute in state machine to implement xmodem protocol for receiving files.</p>
	 * @throws IOException 
	 */
	public boolean receiveFileX() throws IOException, SerialComException {
		
		// Finite state machine
		final int CONNECT = 0;
		final int RECEIVEDATA = 1;
		final int VERIFY = 2;
		final int REPLY = 3;
		final int ABORT = 4;

		int z = 0;
		int delayVal = 300;
		int retryCount = 0;
		int duplicateBlockRetryCount = 0;
		int state = -1;
		int blockNumber = 1;
		int bufferIndex = 0;
		long connectTimeOut = 0;
		long nextDataRecvTimeOut = 0;
		boolean rxDone = false;
		boolean firstBlock = false;
		boolean isCorrupted = false;
		boolean isDuplicateBlock = false;
		boolean handlingLargeBlock = false;
		byte[] block = new byte[1029];
		byte[] data = null;
		String errMsg = null;
		int blockCRCval = 0;
		SerialComCRC crcCalculator = new SerialComCRC();

		/* The data bytes get flushed automatically to file system physically whenever BufferedOutputStream's internal
		   buffer gets full and request to write more bytes have arrived. */
		outStream = new BufferedOutputStream(new FileOutputStream(fileToProcess));

		// Clear receive buffer before start
		try {
			scm.clearPortIOBuffers(handle, true, false);
		} catch (SerialComException exp) {
			outStream.close();
			throw exp;
		}

		state = CONNECT;
		while(true) {
			switch(state) {
				case CONNECT:
					if(retryCount < 3) {
						try {
							scm.writeSingleByte(handle, C);
							firstBlock = true;
							connectTimeOut = System.currentTimeMillis() + 3000; // update timeout, 3 seconds
							state = RECEIVEDATA;
						} catch (SerialComException exp) {
							outStream.close();
							throw exp;
						}
					}else {
						// fall back to checksum mode
						if(mode == 1) {
							return scm.receiveFile(handle, fileToProcess, FTPPROTO.XMODEM, FTPVAR.CHKSUM, FTPMODE.TEXT);
						}else if(mode == 2) {
							return scm.receiveFile(handle, fileToProcess, FTPPROTO.XMODEM, FTPVAR.CHKSUM, FTPMODE.BINARY);
						}else {
						}
					}
					break;
				case RECEIVEDATA:
					while(true) {
						try {
							Thread.sleep(delayVal);
						} catch (InterruptedException e) {
						}
						try {
							data = scm.readBytes(handle, 1500);
						} catch (SerialComException exp) {
							outStream.close();
							throw exp;
						}
						if((data != null) && (data.length > 0)) {
							firstBlock = false;
							if(data[0] == EOT) {
								isCorrupted = false;
								rxDone = true;
								state = REPLY;
								break;
							}else if((data[0] == STX) || (handlingLargeBlock == true)) {
								/* At the beginning of block data[0] will be either STX or SOH but when receiving partial data
								 * data[0] will not be STX/SOH.handlingLargeBlock check tells whether we are receiving 133 or 1029
								 * length block. */
								handlingLargeBlock = true;
								if(data.length == 1029) {
									// complete block read in one go
									for(int i=0; i < 1029; i++) {
										block[i] = data[i];
									}
									state = VERIFY;
									break;
								}else {
									// partial block read
									for(z=0; z < data.length; z++) {
										block[bufferIndex] = data[z];
										bufferIndex++;
									}
									if(bufferIndex == 1029) {
										delayVal = 300;  // reset delay
										bufferIndex = 0; // reset index
										state = VERIFY;
										break;
									}else {
										delayVal = 120; // next remaining data bytes should arrive early
										continue;
									}	
								}
							}else if((data[0] == SOH) || (handlingLargeBlock == false)) {
								handlingLargeBlock = false;
								if(data.length == 133) {
									// complete block read in one go
									for(int i=0; i < 133; i++) {
										block[i] = data[i];
									}
									state = VERIFY;
									break;
								}else {
									// partial block read
									for(z=0; z < data.length; z++) {
										block[bufferIndex] = data[z];
										bufferIndex++;
									}
									if(bufferIndex == 133) {
										delayVal = 300;  // reset delay
										bufferIndex = 0; // reset index
										state = VERIFY;
										break;
									}else {
										delayVal = 100; // next remaining data bytes should arrive early
										continue;
									}	
								}
							}else {
							}
						}else {
							if(firstBlock == false) {
								if(System.currentTimeMillis() > nextDataRecvTimeOut) {
									errMsg = SerialComErrorMapper.ERR_TIMEOUT_RECV_FROM_SENDER;
									state = ABORT;
									break;
								}
							}else {
								if(System.currentTimeMillis() > connectTimeOut) {
									retryCount++;
									state = CONNECT;
									break;
								}
							}
						}
					}
					break;
				case VERIFY:
					isCorrupted = false;      // reset
					isDuplicateBlock = false; // reset
					state = REPLY;
					// check duplicate block
					if(block[1] == (blockNumber - 1)){
						isDuplicateBlock = true;
						duplicateBlockRetryCount++;
						if(duplicateBlockRetryCount > 10) {
							errMsg = SerialComErrorMapper.ERR_MAX_RX_RETRY_REACHED;
							state = ABORT;
						}
						break;
					}
					// verify block number sequence
					if(block[1] != blockNumber){
						isCorrupted = true;
						break;
					}
					// verify block number
					if(block[1] != ~block[2]){
						isCorrupted = true;
						break;
					}
					// verify CRC
					if(handlingLargeBlock == true) {
						blockCRCval = crcCalculator.getCRCval(block, 3, 1026);
						if((block[1027] != (byte)(blockCRCval >>> 8)) || (block[1028] != (byte)blockCRCval)){
							isCorrupted = true;
						}
					}else {
						blockCRCval = crcCalculator.getCRCval(block, 3, 130);
						if((block[131] != (byte)(blockCRCval >>> 8)) || (block[132] != (byte)blockCRCval)){
							isCorrupted = true;
						}
					}
					break;
				case REPLY:
					try {
						if(rxDone == false) {
							if(isCorrupted == false) {
								scm.writeSingleByte(handle, ACK);
								if(handlingLargeBlock == true) {
									outStream.write(block, 3, 1024);
								}else {
									outStream.write(block, 3, 128);
								}
								if(isDuplicateBlock != true) {
									blockNumber++;
									if(blockNumber > 0xFF) {
										blockNumber = 0x00;
									}
								}
							}else {
								scm.writeSingleByte(handle, NAK);
							}
							handlingLargeBlock = false; // reset
							state = RECEIVEDATA;
						}else {
							scm.writeSingleByte(handle, ACK);
							outStream.flush();
							outStream.close();
							return true;        // file reception successfully finished, let us go back home
						}
					} catch (SerialComException exp) {
						outStream.close();
						throw exp;
					} catch (IOException exp) {
						outStream.close();
						throw exp;
					}
					nextDataRecvTimeOut = System.currentTimeMillis() + 1000; // update timeout for next byte 1 second
					break;
				case ABORT:
					/* if an IOexception occurs, control will not reach here instead exception would have been
					 * thrown already. */
					outStream.close();
					throw new SerialComTimeOutException("receiveFileX()", errMsg);
				default:
					break;
			}
		}
	}
}
