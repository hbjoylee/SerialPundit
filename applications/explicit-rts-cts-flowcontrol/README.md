This example demonstrates how to use RTS and DTR lines to control flow of data under
9600 8N1 no flow control configuration. It does not uses data or event listeners.

#####Running this application
   
Open and configure minicom/teraterm for 9600 8N1 settings and start it. Launch this 
application. Minicom/tertarem will show "start" as data received from this java program.
Now type some characters in minicom/teraterm and they will appear in this Java
program's console. 
   
See the output.jpg to see output of this program.
   
#####What this application does and how it does

At start up, it creates a worker thread from which it opens and configures serial port for
9600 8N1 no flow control configuration. It asserts RTS and DTR lines to inform serial device
that host computer application is now ready for communication. Then it calls a read method 
which will block until serial device sends some data. Upon reception of data from serial device,
it prints this data on console, terminates worker thread and notifies application to exit.
     
#####Going further
- There are many different versions of read methods provided by this library and an application 
can use the method that is best fit for application requirement. Other variant of read are :
     ```java
     Non-blocking
     readBytes(long handle)
     readBytes(long handle, int byteCount)
     readBytesDirect(long handle, java.nio.ByteBuffer buffer, int offset, int length)
     readSingleByte(long handle)
     readString(long handle)
     readString(long handle, int byteCount)
     
     Blocking
     readBytesBlocking(long handle, int byteCount, long context)
     
     Non-blocking/ Blocking
     readBytes(long handle, byte[] buffer, int offset, int length, long context)
     ```
- The purpose of this program is to give a simple example of getting started and do some basic 
communication with serial device controlling the data flow. See other examples for developing 
full fledged applications.
     
