package com.embeddedunveiled.serial;

import java.io.IOException;
import java.io.OutputStream;

/**
 * <p>This class represents an output stream of bytes that gets sent over to serial port for transmission.</p>
 */
public final class SerialComOutByteStream extends OutputStream {

	private SerialComManager scm = null;
	private long handle = 0;
	private boolean isOpened = false;

	public SerialComOutByteStream(SerialComManager scm, long handle) {
		this.scm = scm;
		this.handle = handle;
		isOpened = true;
	}

	/**
	 * <p>Writes the specified byte to this output stream (eight low-order bits of the argument data).
	 * The 24 high-order bits of data are ignored.</p>
	 * 
	 * @param data integer to be written to serial port
	 * @throws IOException if write fails or output stream has been closed.
	 */
	@Override
	public void write(int data) throws IOException {
		if(isOpened != true) {
			throw new IOException(SerialComErrorMapper.ERR_BYTE_STREAM_IS_CLOSED);
		}
		try {
			scm.writeSingleByte(handle, (byte)data);
		} catch (SerialComException e) {
			throw new IOException(e);
		}
	}

	/**
	 * <p>Writes data.length bytes from the specified byte array to this output stream.</p>
	 * 
	 * @param data byte type array of data to be written to serial port
	 * @throws IOException if write fails or output stream has been closed.
	 * @throws NullPointerException if data is null
	 * @throws IllegalArgumentException if data is not a byte type array
	 */
	@Override
	public void write(byte[] data) throws IOException {
		if(isOpened != true) {
			throw new IOException(SerialComErrorMapper.ERR_BYTE_STREAM_IS_CLOSED);
		}
		if(data == null) {
			throw new NullPointerException("write(), " + SerialComErrorMapper.ERR_WRITE_NULL_DATA_PASSED);
		}
		if(!(data instanceof byte[])) {
			throw new IllegalArgumentException("write()," + SerialComErrorMapper.ERR_ARG_NOT_BYTE_ARRAY);
		}
		try {
			scm.writeBytes(handle, data, 0);
		} catch (SerialComException e) {
			throw new IOException(e);
		}
	}

	/**
	 * <p>Writes len bytes from the specified byte array starting at offset off to this output stream.</p>
	 * <p>If b is null, a NullPointerException is thrown.</p>
	 * <p>If off is negative, or len is negative, or off+len is greater than the length of the array data, 
	 * then an IndexOutOfBoundsException is thrown.<p>
	 * 
	 * @param data byte type array of data to be written to serial port
	 * @param off offset from where to start sending data
	 * @param len length of data to be written
	 * @throws IOException if write fails or output stream has been closed
	 * @throws IllegalArgumentException if data is not a byte type array
	 * @throws NullPointerException if data is null
	 * @throws IndexOutOfBoundsException If off is negative, or len is negative, or off+len is greater 
	 * than the length of the array data.
	 */
	@Override
	public void write(byte[] data, int off, int len) throws IOException, IndexOutOfBoundsException {
		if(isOpened != true) {
			throw new IOException(SerialComErrorMapper.ERR_BYTE_STREAM_IS_CLOSED);
		}
		if(data == null) {
			throw new NullPointerException("write(), " + SerialComErrorMapper.ERR_WRITE_NULL_DATA_PASSED);
		}
		if((off < 0) || (len < 0) || ((off+len) > data.length)) {
			throw new IndexOutOfBoundsException("write(), " + SerialComErrorMapper.ERR_INDEX_VIOLATION);
		}
		if(!(data instanceof byte[])) {
			throw new IllegalArgumentException("write()," + SerialComErrorMapper.ERR_ARG_NOT_BYTE_ARRAY);
		}
		
		try {
			byte[] buf = new byte[len];
			System.arraycopy(data, off, buf, 0, len);
			scm.writeBytes(handle, buf, 0);
		} catch (SerialComException e) {
			throw new IOException(e);
		}
	}

	/**
	 * <p>The scm always flushes data every time writeBytes() method is called. So do nothing just return.</p>
	 * 
	 * @throws IOException if write fails or output stream has been closed.
	 */
	@Override
	public void flush() throws IOException {
		if(isOpened != true) {
			throw new IOException(SerialComErrorMapper.ERR_BYTE_STREAM_IS_CLOSED);
		}
	}
	
	/**
	 * <p>This method releases the OutputStream object internally associated with the operating handle.</p>
	 * <p>To actually close the port closeComPort() method should be used.</p>
	 * 
	 * @throws IOException if write fails or output stream has been closed.
	 */
	@Override
	public void close() throws IOException {
		if(isOpened != true) {
			throw new IOException(SerialComErrorMapper.ERR_BYTE_STREAM_IS_CLOSED);
		}
		scm.destroyOutputByteStream(this);
		isOpened = false;
	}
}