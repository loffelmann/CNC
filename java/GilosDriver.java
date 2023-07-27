
import com.fazecast.jSerialComm.SerialPort;
import java.nio.charset.StandardCharsets;
import java.lang.Thread;
import java.lang.Runnable;
import java.util.Arrays;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;

import machines.*;

/**
 * The Arduino-facing class.
 * Implements a serial line protocol.
 * Tries to follow Arduino's internal state to allow software-only reconstruction
 * of current machine position.
 */
public class GilosDriver implements Runnable {

	// compatible versions of Arduino firmware
	private static final String VERSION = "0.24";
	private static final int MIN_FIRMWARE = 6;
	private static final int MAX_FIRMWARE = 6;
	private byte firmwareVersion;

	// misc constants
	private static final byte SPEED_FULL = 100;
	private static final byte SPEED_LOW = 1;
	public static final double ACCEL_DISTANCE = 3.0;

	private static final long TIMEOUT_BLINK = 10000;
	private static final long TIMEOUT_ZERO = 3600000;

	// controller instance allowing two-way communication
	private GilosController controller;

	private Thread commandLoop;

	public GilosDriver(GilosController controller, MachineConfig mc){
		System.out.println("GilosDriver version "+VERSION);
		this.controller = controller;
		try{
			setAxes(mc);
		}
		catch(BacklashOutOfBounds ex){
			controller.error(ex);
		}
		init();
		findPort();
	}

	/**
	 * An always-active replacement of assert
	 */
	private void check(boolean condition){
		if(!condition){
			throw new RuntimeException("check failed");
		}
	}

	// Methods for debug logs //////////////////////////////////////////////////

	private boolean debug = true;
	private String getDebugTimestamp(){
		try{
			var now = LocalDateTime.now();
			var fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
			return now.format(fmt);
		}
		catch(DateTimeException e){
			return "??";
		}
	}
	private synchronized void debugPrint(String msg){
		if(debug)System.out.print(msg);
	}
	private synchronized void debugPrintln(String msg){
		if(debug){
			System.out.print(msg);
			for(int i=msg.length(); i<80; i++)System.out.print(" ");
			System.out.println("@ "+getDebugTimestamp());
		}
	}

	// connection //////////////////////////////////////////////////////////////

	private SerialPort port;

	/**
	 * Indicates whether the application has a connection to a machine
	 */
	public boolean isConnected(){
		return port != null && port.isOpen();
	}

	/**
	 * Closes connection to a machine
	 */
	public void disconnect(){
		if(isConnected())port.closePort();
		port = null;
		try{
			Thread.sleep(1000);
		}
		catch(InterruptedException e){}
	}

	/**
	 * Iterates over serial ports, connects to the first one which identifies as a supported machine
	 */
	public boolean findPort(){
		disconnect();

		SerialPort[] ports = SerialPort.getCommPorts();
		for (SerialPort port: ports) {
			this.port = port;
			System.out.println("Trying "+port.getSystemPortName());
			if(
				   !port.openPort()
				|| !port.setComPortParameters(
						115200, // baud rate
						8, // data bits
						SerialPort.ONE_STOP_BIT,
						SerialPort.NO_PARITY
				)
				|| !port.setComPortTimeouts(
						SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
						500, // read timeout
						0 // write timeout
				)
				|| !port.isOpen()
			)continue;

			try{
				Thread.sleep(3000); // Arduino may be restarting
			}
			catch(InterruptedException e){}

			// empty buffer
			byte[] readBuffer = new byte[port.bytesAvailable()];
			int numBytesRead = port.readBytes(readBuffer, readBuffer.length);
			String readChars = new String(readBuffer, 0, numBytesRead, StandardCharsets.UTF_8);
			System.out.println(String.format("   read %d bytes: %s", numBytesRead, readChars));

			// send "I", check if the response is "gilos"
//			byte[] writeBuffer = {'I'};
//			port.writeBytes(writeBuffer, 1);
			System.out.println("   sending I");
			write("I");
			try{
				Thread.sleep(300);
			}
			catch(InterruptedException e){}
			readBuffer = new byte[5];
			numBytesRead = port.readBytes(readBuffer, readBuffer.length);
			readChars = new String(readBuffer, 0, numBytesRead, StandardCharsets.UTF_8);
			System.out.println(String.format("   reading %d bytes: %s", numBytesRead, readChars));
			if(numBytesRead == readBuffer.length){
				if(readChars.equals("gilos")){

					// checking firmware version
					write("S");
					try{
						Thread.sleep(300);
					}
					catch(InterruptedException e){}
					readBuffer = new byte[8];
					numBytesRead = port.readBytes(readBuffer, readBuffer.length);
					debugPrintln("    read "+numBytesRead+" bytes, detected firmware version "+readBuffer[0]);
					if(numBytesRead == 7 && readBuffer[0] == 1){
						firmwareVersion = 1;
					}
					else if(numBytesRead == 7 && readBuffer[0] >= MIN_FIRMWARE && readBuffer[0] <= MAX_FIRMWARE){
						firmwareVersion = readBuffer[0];
					}
					else{
						System.out.println("unsupported firmware version: "+readBuffer[0]);
						continue;
					}

					// resetting firmware state
					write("R");
					read("R");

					commandLoop = new Thread(this);
					commandLoop.start();
					System.out.println("found!");
					return true;
				}
			}
			else{
				System.out.println("   not the right response");
			}

			port.closePort();
		}

		System.out.println("nothing found");

		disconnect();
		return false;
	}

	/**
	 * Returns a name to be displayed in GUI
	 */
	public String getName(){
		return isConnected()? port.getSystemPortName() : "";
	}

	// communication ///////////////////////////////////////////////////////////

	/**
	 * Reads numBytes or the closest immediately available amount of bytes from the machine
	 */
	private byte[] readRawBytes(int numBytes){
		if(this.port == null)return new byte[0];
		byte[] readBuffer = new byte[numBytes];
		int actualNumBytes = port.readBytes(readBuffer, numBytes);
		if(actualNumBytes <= 0)return new byte[0];
		return Arrays.copyOf(readBuffer, actualNumBytes);
	}

	private byte[] peekBuffer = new byte[0];
	/**
	 * Attempts to read numBytes bytes from the machine within a time limit.
	 * Stores read bytes in a buffer so they can be read again later.
	 * Returns incomplete data if the requested number of bytes doesn't appear within the timeout.
	 * Manual waiting for bytes is implemented because this functionality of the Fazecast library
	 * does not seem to work on some systems.
	 */
	public byte[] peekBytes(int numBytes){
		// waiting for full number of bytes does not seem to work on Windows => waiting manually
		debugPrintln("  gilos.peekBytes");
		for(int i=0; i<50 && peekBuffer.length<numBytes; i++){
			byte[] newData = readRawBytes(numBytes-peekBuffer.length);
			if(newData.length > 0){
				peekBuffer = Arrays.copyOf(peekBuffer, peekBuffer.length+newData.length);
				System.arraycopy(newData, 0, peekBuffer, peekBuffer.length-newData.length, newData.length);
			}
			if(peekBuffer.length >= numBytes)break;
			try{
				Thread.sleep(10);
			}
			catch(InterruptedException e){}
		}
		return Arrays.copyOf(peekBuffer, Math.min(numBytes, peekBuffer.length));
	}

	/**
	 * Attempts to read numBytes from the machine, converts them to String.
	 * Storers read data in a buffer.
	 */
	public String peek(int numBytes){
		byte[] data = peekBytes(numBytes);
		return new String(data, 0, data.length, StandardCharsets.UTF_8);
	}

	/**
	 * Attempts to read numBytes bytes from the machine.
	 * Does not store them in buffer.
	 */
	public byte[] readBytes(int numBytes){
		byte[] data = peekBytes(numBytes);
		debugPrint("  gilos.readBytes [");
		for(byte b: data)debugPrint(" "+(b & 0xFF));
		debugPrintln(" ]");
		peekBuffer = Arrays.copyOfRange(peekBuffer, data.length, peekBuffer.length);
		return data;
	}

	/**
	 * Attempts to read numBytes bytes from the machine, converts them to String.
	 * Does not store data in buffer.
	 */
	public String read(int numBytes){
		byte[] byteData = readBytes(numBytes);
		String data = new String(byteData, 0, byteData.length, StandardCharsets.UTF_8);
		debugPrintln("  gilos.read 1 \""+data+"\"");
		return data;
	}

	/**
	 * Attempts to read numBytes bytes from the machine, converts them to String,
	 * compares them to an expected value.
	 * Does not store data in buffer.
	 */
	public String read(String expectedData){
		String actualData = read(expectedData.length());
		debugPrintln("  gilos.read 2 \""+actualData+"\"");
		check(expectedData.equals(actualData));
		return actualData;
	}

	/**
	 * Returns the number of immediately available bytes from the machine
	 * (either from the Fazecast library or from the extra buffer in this class)
	 */
	public int bytesAvailable(){
		return peekBuffer.length + port.bytesAvailable();
	}

	/**
	 * Sends String data to the machine
	 */
	public void write(String data){
		debugPrintln("  gilos.write string \""+data+"\"");
		byte[] writeBuffer = new byte[data.length()];
		for(int i=data.length()-1; i>=0; i--){
			writeBuffer[i] = (byte)data.charAt(i);
		}
		port.writeBytes(writeBuffer, data.length());
	}

	/**
	 * Sends a single byte to the machine
	 */
	public void write(byte data){
		debugPrintln("  gilos.write byte "+Byte.toUnsignedInt(data));
		port.writeBytes(new byte[]{data}, 1);
	}

	/**
	 * Sends a 16-bit int to the machine
	 */
	public void writeInt(int data){
		debugPrintln("  gilos.writeInt "+data);
		write((byte)((data >> 8) & 0xFF));
		write((byte)((data >> 0) & 0xFF));
	}

	/**
	 * Sends a 32-bit int to the machine
	 */
	public void writeLong(int data){
		debugPrintln("  gilos.writeLong "+data);
		write((byte)((data >> 24) & 0xFF));
		write((byte)((data >> 16) & 0xFF));
		write((byte)((data >>  8) & 0xFF));
		write((byte)((data >>  0) & 0xFF));
	}

	// configuration ///////////////////////////////////////////////////////////

	private MachineConfig machineConf;

	/**
	 * Send configuration of axes to the machine
	 */
	public synchronized void setAxes(MachineConfig mc){
		if(!isConnected())return;
		machineConf = mc.clone();

		boolean xZeroUp = mc.x.zeroUp() != mc.x.inverted();
		boolean yZeroUp = mc.y.zeroUp() != mc.y.inverted();
		boolean zZeroUp = mc.z.zeroUp() != mc.z.inverted();
		boolean uZeroUp = mc.u.zeroUp() != mc.u.inverted();
		boolean vZeroUp = mc.v.zeroUp() != mc.v.inverted();
		boolean wZeroUp = mc.w.zeroUp() != mc.w.inverted();

		// triggering "too large backlash" errors before any data is sent
		mc.x.backlashSteps();
		mc.y.backlashSteps();
		mc.z.backlashSteps();
		mc.u.backlashSteps();
		mc.v.backlashSteps();
		mc.w.backlashSteps();

		write("C"); // enter config mode

		write("X" + (mc.x.exists()? (xZeroUp? "u" : "d") : "0"));
		if(mc.x.inverted()){
			writeLong(-mc.x.highLimitSteps());
			writeLong(-mc.x.lowLimitSteps());
		}
		else{
			writeLong(mc.x.lowLimitSteps());
			writeLong(mc.x.highLimitSteps());
		}
		writeInt(mc.x.backlashSteps());
		read("c");

		write("Y" + (mc.y.exists()? (yZeroUp? "u" : "d") : "0"));
		if(mc.y.inverted()){
			writeLong(-mc.y.highLimitSteps());
			writeLong(-mc.y.lowLimitSteps());
		}
		else{
			writeLong(mc.y.lowLimitSteps());
			writeLong(mc.y.highLimitSteps());
		}
		writeInt(mc.y.backlashSteps());
		read("c");

		write("Z" + (mc.z.exists()? (zZeroUp? "u" : "d") : "0"));
		if(mc.z.inverted()){
			writeLong(-mc.z.highLimitSteps());
			writeLong(-mc.z.lowLimitSteps());
		}
		else{
			writeLong(mc.z.lowLimitSteps());
			writeLong(mc.z.highLimitSteps());
		}
		writeInt(mc.z.backlashSteps());
		read("c");

		write("U" + (mc.u.exists()? (uZeroUp? "u" : "d") : "0"));
		if(mc.u.inverted()){
			writeLong(-mc.u.highLimitSteps());
			writeLong(-mc.u.lowLimitSteps());
		}
		else{
			writeLong(mc.u.lowLimitSteps());
			writeLong(mc.u.highLimitSteps());
		}
		writeInt(mc.u.backlashSteps());
		read("c");

		write("V" + (mc.v.exists()? (vZeroUp? "u" : "d") : "0"));
		if(mc.v.inverted()){
			writeLong(-mc.v.highLimitSteps());
			writeLong(-mc.v.lowLimitSteps());
		}
		else{
			writeLong(mc.v.lowLimitSteps());
			writeLong(mc.v.highLimitSteps());
		}
		writeInt(mc.v.backlashSteps());
		read("c");

		write("W" + (mc.w.exists()? (wZeroUp? "u" : "d") : "0"));
		if(mc.w.inverted()){
			writeLong(-mc.w.highLimitSteps());
			writeLong(-mc.w.lowLimitSteps());
		}
		else{
			writeLong(mc.w.lowLimitSteps());
			writeLong(mc.w.highLimitSteps());
		}
		writeInt(mc.w.backlashSteps());
		read("c");

		write("."); // exit config mode
	}

	// communication state machine /////////////////////////////////////////////

	// tracking operation modes of the machine
	private static enum State {
		READY,
		BLINKING_LED,
		GOING_TO_ZERO,
		TESTING_LIMITS,
//		CHECKING_BUFFER,
	};
	private State state;

	// start of miscellaneous timeouts
	private long timeoutStart;

	// is the machine moving, or waiting for a start command?
	// (detected via serial line)
	private boolean moving;

	// does the machine have free space for upcoming moves?
	// (detected via serial line, not via the mirror move buffer in this class)
	private boolean moveBufferFree;

	// was the last move sent to the machine a manual move?
	private boolean lastMoveManual;

	private boolean manualReady;
	/**
	 * Tracking if the machine has a free buffer for a manual move
	 */
	public boolean manualReady(){
		return manualReady;
	}

	private static final Move zeroMove = new Move(0, 0, 0, 0, 0, 0, (byte)0, ' ');

	/**
	 * Sets all internal state to initial values
	 */
	public void init(){
		check(!isConnected());
		state = State.READY;
		timeoutStart = 0;
		moving = false;
		commandBuffer = new ArrayList<Command>();
		moveBuffer = new ArrayList<Move>();
		moveBufferFree = true;
		manualReady = true;
		lastMoveManual = false;
		numManualMoves = 0;
		machineMoveBuffer = new ArrayList<Move>();
		remainingSteps = 0;
		currentMove = zeroMove;
		resetSequence();
	}

	/**
	 * Discards position data waiting to be transformed to moves
	 */
	private synchronized void resetSequence(){
		x = new double[0];
		y = new double[0];
		z = new double[0];
		u = new double[0];
		v = new double[0];
		w = new double[0];
		bufDx = bufDy = bufDz = bufDu = bufDv = bufDw = 0;
		zeroSpeedDistances = new double[0];
		segmentIndex = 0;
		sequenceDistance = 0;
		nextZeroIndex = 0;
		prevZeroDistance = nextZeroDistance = 0;
		controller.sequenceReset();
	}

	// buffer for commands to be sent to the machine
	private static enum Command {
		NO_COMMAND,
		BLINK_LED,
		START,
		STOP,
		RESET,
		SEND_MOVE,
		CHECK_BUFFER,
		GO_TO_ZERO,
		TEST_LIMITS,
	};
	private ArrayList<Command> commandBuffer;
	private ArrayList<Move> moveBuffer;
	private int numManualMoves;

	/**
	 * Returns the next command to be sent to the machine.
	 * Keeps the command in the command buffer.
	 */
	private synchronized Command getCommand(){
		if(moveBuffer.size() > 0 && numManualMoves > 0 && manualReady){
			return Command.SEND_MOVE;
		}
		else if(moveBuffer.size() > 0 && numManualMoves == 0 && moveBufferFree){
			return Command.SEND_MOVE;
		}
		else if(commandBuffer.size() > 0){
			return commandBuffer.get(0);
		}
		else{
			return Command.NO_COMMAND;
		}
	}

	/**
	 * Removes a processed command from the command buffer
	 */
	private synchronized void popCommand(Command c){
		if(c == Command.SEND_MOVE){
			moveBuffer.remove(0);
			if(numManualMoves > 0)numManualMoves--;
		}
		else if(c != Command.NO_COMMAND){
			check(c == commandBuffer.remove(0));
		}
	}

	/**
	 * Reads a status byte from the machine, updates state variables
	 */
	private synchronized char checkBuffer(){
		String response = read(1);
		debugPrintln("checkBuffer: read "+response);
		if(response.equals("o")){
			moving = false;
			moveBufferFree = false;
		}
		else if(response.equals("O")){
			moving = true;
			moveBufferFree = false;
		}
		else if(response.equals("g")){
			moving = false;
			moveBufferFree = false;
		}
		else if(response.equals("G")){
			moving = true;
			moveBufferFree = false;
		}
		else if(response.equals("f")){
			moving = false;
			moveBufferFree = true;
			if(moveBuffer.size() == 0){
				debugPrintln("machine stopped with free move buffer");
				controller.updateRunning(false);
				if(machineMoveBuffer.size() == 0 && !lastMoveManual){
					debugPrintln("machine stopped after last move");
					controller.toolPathFinished();
				}
			}
		}
		else if(response.equals("F")){
			moving = true;
			moveBufferFree = true;
		}
		else if(response.equals("p")){
			moving = false;
			moveBufferFree = true;
			popMachineMove(true);
		}
		else if(response.equals("P")){
			moving = true;
			moveBufferFree = true;
			popMachineMove(true);
		}
		else if(response.equals("q")){
			popMachineMove(false); // ended by "." signal, move not finished
		}
		else if(response.equals("Q")){
			if(machineMoveBuffer.size() <= 1 || machineMoveBuffer.get(0).type() != 'M'){
				moving = false;
				controller.updateRunning(false);
			}
			popMachineMove(true);
		}
		else if(response.equals("W")){
			manualReady = true;
		}
		else if(response.equals(".")){
			moving = false;
			manualReady = true;
			popAllManualMoves();
			controller.updateRunning(false);
		}
		else if(response.equals("R")){
			controller.invalidateConfig();
		}
		else{
			System.err.println("Invalid buffer status received: \""+response+"\"");
		}
		return (response.length() == 0)? 0 : response.charAt(0);
	}

	/**
	 * Requests a position status from the machine after a stop command; updates current position.
	 */
	private void updateRemainingSteps(){
		write("$");
		while(checkBuffer() != 'b'){}
		byte[] response = readBytes(2);
		if(response.length != 2){
			System.err.println("Invalid segment part read");
			return;
		}
		int stepsDone = (((int)response[0] & 0xFF) << 8) + ((int)response[1] & 0xFF);
		debugPrintln("driver.updateRemainingSteps "+stepsDone);
		remainingSteps = Math.max(remainingSteps-stepsDone, 0);

		int moveSteps = stepsPerMove(currentMove);
		debugPrintln("stepsDone: "+stepsDone+", moveSteps: "+moveSteps);
		shiftPositionByMove(currentMove, ((double)stepsDone)/Math.max(moveSteps, 1));
	}

	/**
	 * Event loop running in a thread
	 */
	public void run(){
		debugPrintln("driver.commandLoop starting");
		while(isConnected()){
			if(!processCommand())try{
				Thread.sleep(1);
			}
			catch(InterruptedException e){}
		}
		debugPrintln("driver.commandLoop exiting");
	}

	private int noCommandCount = 0;
	/**
	 * Single pass of the event loop.
	 * Attempts to pop and execute a command from the command buffer, then wait for its results (if any).
	 */
	private synchronized boolean processCommand(){
		byte[] writeBuffer;
		Command command = getCommand();
		if(command != Command.NO_COMMAND || noCommandCount % 1000 == 0){
			debugPrintln("processCommand "+command+", state = "+state+", noCommandCount = "+noCommandCount);
		}
		switch(state){

			case READY:
				if(moveBufferFree)popSequence();
				for(int i=0; i<100 && bytesAvailable()>0; i++){
//					debugPrintln("driver: Reading in READY: \""+read(bytesAvailable())+"\"");
//					debugPrint("      moveBufferFree = "+moveBufferFree);
					debugPrintln("driver.processCommand READY: data to read");
					checkBuffer();
				}
				switch(command){

					case BLINK_LED:
						debugPrintln("driver.processCommand READY: BLINK_LED");
						write("l");
						state = State.BLINKING_LED;
						timeoutStart = System.currentTimeMillis();
						popCommand(command);
						debugPrintln("processCommand return 1");
						return true;

					case START:
						debugPrintln("driver.processCommand READY: START");
						write("B");
						checkBuffer();
//						state = State.CHECKING_BUFFER;
//						timeoutStart = System.currentTimeMillis();
						popCommand(command);
						debugPrintln("processCommand return 2");
						return true;

					case STOP:
						debugPrintln("driver.processCommand READY: STOP");
						write(".");
						checkBuffer();
//						state = State.CHECKING_BUFFER;
//						timeoutStart = System.currentTimeMillis();
						updateRemainingSteps();
						popCommand(command);
						if(currentMove.type() != 'M'){
							updateCurrentMove(); // a manual move might have been popped unfinished by checkBuffer
						}
						debugPrintln("processCommand return 3");
						return true;

					case RESET:
						debugPrintln("driver.processCommand READY: RESET");
						write("R");
						popCommand(command);
						resetSequence();
						commandBuffer = new ArrayList<Command>();
						moveBuffer = new ArrayList<Move>();
						machineMoveBuffer = new ArrayList<Move>();
						remainingSteps = 0;
						currentMove = zeroMove;
						moving = false;
						moveBufferFree = true;
						manualReady = true;
						lastMoveManual = false;
						numManualMoves = 0;
						read("R");
						debugPrintln("processCommand return 4");
						return true;

					case SEND_MOVE:
						debugPrintln("driver.processCommand READY: SEND_MOVE");
						if(numManualMoves == 0 && !moveBufferFree){
							debugPrintln("processCommand return 5");
							return false;
						}
						if(numManualMoves > 0 && !manualReady){
							debugPrintln("processCommand return 6");
							return false;
						}
//						if(numManualMoves > 0 && moving){ // no good, causes stopping between send_moves
//							debugPrintln("driver.processCommand READY: STOP before SEND_MOVE");
//							write(".");
//							checkBuffer();
//							updateRemainingSteps();
//						}
						Move m = moveBuffer.get(0);
						debugPrintln("send move "+m.dx()+" "+m.dy()+" "+m.dz()+", "+m.du()+" "+m.dv()+" "+m.dw()+", speed "+((int)m.endSpeed() & 0xFF)+", type "+m.type());
						lastMoveManual = m.type() != 'M';
						sendMove(m);
						if(numManualMoves > 0)manualReady = false;
						char chb = '?';
						for(int i=0; i<100; i++){ // that many chances to read the move's return value among other noise
							chb = checkBuffer();
							if("fFgGoO".contains(""+chb)){
								break;
							}
						}
						if(chb != 'o' && chb != 'O'){ // no overflow
							popCommand(command);
							if(!moving && controller.isRunning() && (!moveBufferFree || moveBuffer.size() == 0)){
								// starting machine again when it stops from lack of data
								debugPrintln("start from processCommand");
								start();
							}
							debugPrintln("processCommand return 7");
							return true;
						}
						else{
							debugPrintln("processCommand return 8");
							return false;
						}
//						state = State.CHECKING_BUFFER;
//						timeoutStart = System.currentTimeMillis();
//						return true;

					case CHECK_BUFFER:
						debugPrintln("driver.processCommand READY: CHECK_BUFFER");
						write("s");
						checkBuffer();
//						state = State.CHECKING_BUFFER;
//						timeoutStart = System.currentTimeMillis();
						popCommand(command);
						debugPrintln("processCommand return 9");
						return true;

					case GO_TO_ZERO:
						debugPrintln("driver.processCommand READY: GO_TO_ZERO");
						write("0");
						state = State.GOING_TO_ZERO;
						timeoutStart = System.currentTimeMillis();
						popCommand(command);
						debugPrintln("processCommand return 10");
						return true;

					case TEST_LIMITS:
						debugPrintln("driver.processCommand READY: TEST_LIMITS");
						write("L");
						state = State.TESTING_LIMITS;
						popCommand(command);
						debugPrintln("processCommand return 11");
						return true;

					case NO_COMMAND:
//						debugPrintln("driver.processCommand READY: NO_COMMAND");
						noCommandCount++;
						return false;

					default:
						System.err.println("Unknown command in READY mode");
						popCommand(command);
						debugPrintln("processCommand return 12");
						return true;
				}

			case BLINKING_LED:
				if(bytesAvailable() >= 1 && peek(1).equals("l")){
					debugPrintln("driver.processCommand BLINKING_LED: read \"l\"");
					read(1);
					state = State.READY;
				}
				else if(System.currentTimeMillis()-timeoutStart >= TIMEOUT_BLINK){
					System.err.println("Blinking LED timeout elapsed");
					state = State.READY;
				}
//				debugPrintln("processCommand return 13");
				return false;

			case GOING_TO_ZERO:
				switch(command){
					case STOP:
						debugPrintln("driver.processCommand GOING_TO_ZERO: STOP");
						write(".");
						state = State.READY;
						popCommand(command);
						debugPrintln("processCommand return 14");
						return true;
					case NO_COMMAND:
						int ba = bytesAvailable();
						if(ba >= 1 && peek(1).equals("0")){
							debugPrintln("driver.processCommand GOING_TO_ZERO: read 0");
							read(1);
							state = State.READY;
							controller.updateRunning(false);
							controller.zeroPosition();
						}
						else if(ba >= 1 && (peek(1).equals("d") || peek(1).equals("D"))){
							debugPrintln("driver.processCommand GOING_TO_ZERO: read debug");
							read(3);
						}
						else if(ba >= 1){
							debugPrintln("driver.processCommand GOING_TO_ZERO: read something else");
							read(1);
						}
						else if(System.currentTimeMillis()-timeoutStart >= TIMEOUT_ZERO){
							System.err.println("Going to zero timeout elapsed");
							state = State.READY;
							controller.updateRunning(false);
						}
//						debugPrintln("processCommand return 15");
						return false;
					default:
//						debugPrintln("driver.processCommand GOING_TO_ZERO: other command");
						debugPrintln("processCommand return 16");
						return false;
				}

			case TESTING_LIMITS:
				switch(command){
					case STOP:
						debugPrintln("driver.processCommand READY: STOP");
						write(".");
						state = State.READY;
						popCommand(command);
						debugPrintln("processCommand return 17");
						return true;
					default:
//						debugPrintln("driver.processCommand TEST_LIMITS: other command");
						debugPrintln("processCommand return 18");
						return false;
				}

			default:
				System.err.println("Should not happen 1");
				return false;

		}
	}

	// simple board functions //////////////////////////////////////////////////

	public synchronized void blink(){
		if(!isConnected())return;
		commandBuffer.add(Command.BLINK_LED);
	}

	public synchronized void start(){
		if(!isConnected())return;
		if(moving)return;
		commandBuffer.add(Command.START);
	}

	public synchronized void stop(){
		if(!isConnected())return;
		commandBuffer.add(Command.STOP);
	}

	public synchronized void reset(){
		if(!isConnected())return;
		commandBuffer.add(Command.RESET);
	}

	public synchronized void goToZero(){
		if(!isConnected())return;
		commandBuffer.add(Command.GO_TO_ZERO);
	}

	public synchronized void testLimits(){
		if(!isConnected())return;
		commandBuffer.add(Command.TEST_LIMITS);
	}

	/**
	 * Adds a move to the move buffer, to be sent to machine by processCommand.
	 * Manual moves go before normal ones.
	 */
	public synchronized void addMove(
		int dx, int dy, int dz,
		int du, int dv, int dw,
		byte endSpeed,
		char type
	){
		if(!isConnected())return;
		System.out.println("addMove--");
		check((dx > -32768) && (dx < 32768));
		check((dy > -32768) && (dy < 32768));
		check((dz > -32768) && (dz < 32768));
		check((du > -32768) && (du < 32768));
		check((dv > -32768) && (dv < 32768));
		check((dw > -32768) && (dw < 32768));
		if(type == 'M'){
			moveBuffer.add(new Move(dx, dy, dz, du, dv, dw, endSpeed, type));
		}
		else{
			if(moving && !lastMoveManual){
				System.out.println("stopping before manual move");
				stop();
			}
			moveBuffer.add(numManualMoves, new Move(dx, dy, dz, du, dv, dw, endSpeed, type));
			numManualMoves++;
		}
	}

	// handling move sequences /////////////////////////////////////////////////

	private byte fullSpeed = SPEED_FULL;
	/**
	 * Sets the default speed, used when no sharp turn in sight
	 * @param speed Speed in the Arduino format
	 */
	public void setSpeed(byte speed){
		fullSpeed = speed;
	}

	// arrays if axis coordinates to be converted to Moves
	private double[] x, y, z, u, v, w;

	// index of the currently processed segment in x, y, z...
	private int segmentIndex;

	// step length of the processed part of tool path (ending at segmentIndex)
	private double sequenceDistance;

	// points on the tool path where speed must be lowered to minimum (parametrized by path length in steps)
	private double[] zeroSpeedDistances;

	// index of the next item of zeroSpeedDistances to be processed
	private int nextZeroIndex;

	// distances to previous and next zero-speed points (from the point at segmentIndex)
	private double prevZeroDistance, nextZeroDistance;

	/**
	 * Updates two nearest zero-speed points after a segment is processed
	 */
	private void updateZeroDistances(){
		while(nextZeroIndex < zeroSpeedDistances.length-1
		   && zeroSpeedDistances[nextZeroIndex] < sequenceDistance){
			nextZeroIndex++;
		}
		prevZeroDistance = (nextZeroIndex==0)? 0 : zeroSpeedDistances[nextZeroIndex-1];
		nextZeroDistance = Math.max(zeroSpeedDistances[nextZeroIndex], sequenceDistance);
	}

	/**
	 * Removes safely processed segments from point arrays
	 */
	// ?? trim zeroSpeedDistances
	public synchronized void trimSequence(){
		int trimCount = segmentIndex-2;
		if(trimCount <= 0)return;
		double[] newX = new double[x.length-trimCount];
		System.arraycopy(x, trimCount, newX, 0, x.length-trimCount);
		x = newX;
		double[] newY = new double[y.length-trimCount];
		System.arraycopy(y, trimCount, newY, 0, y.length-trimCount);
		y = newY;
		double[] newZ = new double[z.length-trimCount];
		System.arraycopy(z, trimCount, newZ, 0, z.length-trimCount);
		z = newZ;
		double[] newU = new double[u.length-trimCount];
		System.arraycopy(u, trimCount, newU, 0, u.length-trimCount);
		u = newU;
		double[] newV = new double[v.length-trimCount];
		System.arraycopy(v, trimCount, newV, 0, v.length-trimCount);
		v = newV;
		double[] newW = new double[w.length-trimCount];
		System.arraycopy(w, trimCount, newW, 0, w.length-trimCount);
		w = newW;
		segmentIndex -= trimCount;
	}

	/**
	 * Adds new points to x, y, z arrays
	 */
	public synchronized void extendSequence(
		double[] x, double[] y, double[] z,
		double[] u, double[] v, double[] w,
		double[] zeroSpeedDistances
	){
		double[] newX = new double[this.x.length+x.length];
		System.arraycopy(this.x, 0, newX, 0, this.x.length);
		System.arraycopy(x, 0, newX, this.x.length, x.length);
		double[] newY = new double[this.y.length+y.length];
		System.arraycopy(this.y, 0, newY, 0, this.y.length);
		System.arraycopy(y, 0, newY, this.y.length, y.length);
		double[] newZ = new double[this.z.length+z.length];
		System.arraycopy(this.z, 0, newZ, 0, this.z.length);
		System.arraycopy(z, 0, newZ, this.z.length, z.length);
		double[] newU = new double[this.u.length+u.length];
		System.arraycopy(this.u, 0, newU, 0, this.u.length);
		System.arraycopy(u, 0, newU, this.u.length, u.length);
		double[] newV = new double[this.v.length+v.length];
		System.arraycopy(this.v, 0, newV, 0, this.v.length);
		System.arraycopy(v, 0, newV, this.v.length, v.length);
		double[] newW = new double[this.w.length+w.length];
		System.arraycopy(this.w, 0, newW, 0, this.w.length);
		System.arraycopy(w, 0, newW, this.w.length, w.length);
		double[] new0 = new double[this.zeroSpeedDistances.length+zeroSpeedDistances.length];
		if(this.zeroSpeedDistances.length > 0){
			System.arraycopy(this.zeroSpeedDistances, 0, new0, 0, this.zeroSpeedDistances.length);
		}
		for(int i=0; i<zeroSpeedDistances.length; i++){
			new0[this.zeroSpeedDistances.length+i] = zeroSpeedDistances[i];
		}
		this.x = newX;
		this.y = newY;
		this.z = newZ;
		this.u = newU;
		this.v = newV;
		this.w = newW;
		this.zeroSpeedDistances = new0;
		updateZeroDistances();
	}

	/**
	 * Calculates single-axis difference between ends of current segment in physical units
	 */
	private int getSegmentDx(double[] x, AxisConfig ax, double part){
		if(!ax.exists())return 0;
		int dx = (int)Math.round((x[segmentIndex+1]-x[segmentIndex])*part*ax.stepsPerMm());
		if(ax.inverted())dx = -dx;
		return dx;
	}

	/**
	 * Calculates step length of the current segment.
	 * Used to parametrize zero-speed positions.
	 */
	private double getSegmentLength(){
		return Math.sqrt(
			  (x[segmentIndex+1]-x[segmentIndex])*(x[segmentIndex+1]-x[segmentIndex])
			+ (y[segmentIndex+1]-y[segmentIndex])*(y[segmentIndex+1]-y[segmentIndex])
			+ (z[segmentIndex+1]-z[segmentIndex])*(z[segmentIndex+1]-z[segmentIndex])
			+ (u[segmentIndex+1]-u[segmentIndex])*(u[segmentIndex+1]-u[segmentIndex])
			+ (v[segmentIndex+1]-v[segmentIndex])*(v[segmentIndex+1]-v[segmentIndex])
			+ (w[segmentIndex+1]-w[segmentIndex])*(w[segmentIndex+1]-w[segmentIndex])
		);
	}

	/**
	 * Calculates maximum of absolute values
	 */
	private int absmax(int[] values){
		int maximum = values[0];
		for(int v: values)maximum = Math.max(maximum, Math.abs(v));
		return maximum;
	}

	private int bufDx, bufDy, bufDz, bufDu, bufDv, bufDw;
	/**
	 * Converts current segment to moves in the move buffer.
	 * Splits too long segments into multiple moves.
	 */
	private synchronized void addSegment(double startPart, double endPart, byte endSpeed, boolean force){
		int dx = getSegmentDx(x, machineConf.x, endPart-startPart) + bufDx;
		int dy = getSegmentDx(y, machineConf.y, endPart-startPart) + bufDy;
		int dz = getSegmentDx(z, machineConf.z, endPart-startPart) + bufDz;
		int du = getSegmentDx(u, machineConf.u, endPart-startPart) + bufDu;
		int dv = getSegmentDx(v, machineConf.v, endPart-startPart) + bufDv;
		int dw = getSegmentDx(w, machineConf.w, endPart-startPart) + bufDw;
		int steps = absmax(new int[]{dx, dy, dz, du, dv, dw});
		if(steps >= 32768){ // splitting long segments
			int numParts = steps / 30000 + 1;
			for(int i=0; i<numParts; i++){
				addMove(
					getSegmentDx(x, machineConf.x, (endPart-startPart)/numParts),
					getSegmentDx(y, machineConf.y, (endPart-startPart)/numParts),
					getSegmentDx(z, machineConf.z, (endPart-startPart)/numParts),
					getSegmentDx(u, machineConf.u, (endPart-startPart)/numParts),
					getSegmentDx(v, machineConf.v, (endPart-startPart)/numParts),
					getSegmentDx(w, machineConf.w, (endPart-startPart)/numParts),
					endSpeed, 'M'
				);
			}
		}
		else if(steps < 10 && !force){ // joining short segments
			bufDx = dx;
			bufDy = dy;
			bufDz = dz;
			bufDu = du;
			bufDv = dv;
			bufDw = dw;
		}
		else{
			addMove(dx, dy, dz, du, dv, dw, endSpeed, 'M');
			bufDx = bufDy = bufDz = bufDu = bufDv = bufDw = 0;
		}
	}

	/**
	 * Pushes manual moves
	 */
	public synchronized void manualMove(int dx, int dy, int dz, int du, int dv, int dw, byte endSpeed, char type){
		int steps = absmax(new int[]{dx, dy, dz, du, dv, dw});
		if(machineConf.x.inverted())dx = -dx;
		if(machineConf.y.inverted())dy = -dy;
		if(machineConf.z.inverted())dz = -dz;
		if(machineConf.u.inverted())du = -du;
		if(machineConf.v.inverted())dv = -dv;
		if(machineConf.w.inverted())dw = -dw;
		if(steps >= 32768){ // splitting long segments
			int numParts = steps / 30000 + 1;
			for(int i=0; i<numParts; i++){
				addMove(
					(int)Math.round(dx / numParts),
					(int)Math.round(dy / numParts),
					(int)Math.round(dz / numParts),
					(int)Math.round(du / numParts),
					(int)Math.round(dv / numParts),
					(int)Math.round(dw / numParts),
					endSpeed, type
				);
			}
		}
		else{
			addMove(dx, dy, dz, du, dv, dw, endSpeed, type);
		}
	}

	/**
	 * Calculates speed in vicinity of a zero-speed point.
	 * @param distToFullSpeed Distance from the zero-speed point, in steps
	 * ?? mixing steps and millimeters?
	 */
	private byte interpolateSpeed(double distToFullSpeed){
		int fullSpeed = Byte.toUnsignedInt(this.fullSpeed);
		double deltaT = 2 * ACCEL_DISTANCE / (fullSpeed+SPEED_LOW); // time to accelerate from LOW to FULL or back
		double v = Math.sqrt(2*(ACCEL_DISTANCE-distToFullSpeed)*(fullSpeed-SPEED_LOW)/deltaT + SPEED_LOW*SPEED_LOW);
		return (byte)Math.floor(v);
	}

	/**
	 * Converts current segment of x, y, z to moves in the move buffers; Shifts segemnt index.
	 * Splits the segment when change in acceleration is needed (Arduino can only handle linear
	 * speed change with distance)
	 */
	private synchronized void popSequence(){
		if(segmentIndex >= x.length-1)return;
		boolean lastSegment = (segmentIndex == x.length-2);
		double segmentLength = getSegmentLength();
		debugPrintln("popSequence index = "+segmentIndex+" length = "+segmentLength);
		double startToFullSpeed = Math.max(ACCEL_DISTANCE - Math.min(
			Math.abs(sequenceDistance - prevZeroDistance),
			Math.abs(nextZeroDistance - sequenceDistance)
		), 0);
		sequenceDistance += segmentLength;
		updateZeroDistances();
		double endToFullSpeed = Math.max(ACCEL_DISTANCE - Math.min(
			Math.abs(sequenceDistance - prevZeroDistance),
			Math.abs(nextZeroDistance - sequenceDistance)
		), 0);

		double startPart, endPart, clip;
		if(startToFullSpeed >= segmentLength || endToFullSpeed >= segmentLength){
			addSegment(0, 1, interpolateSpeed(endToFullSpeed), lastSegment);
		}
		else if(startToFullSpeed+endToFullSpeed >= segmentLength){
			clip = (startToFullSpeed+endToFullSpeed - segmentLength) / 2;
			startPart = (startToFullSpeed-clip) / segmentLength;
			endPart = 1 - (endToFullSpeed-clip) / segmentLength;
			addSegment(0, startPart, interpolateSpeed(clip), false);
			addSegment(endPart, 1, interpolateSpeed(endToFullSpeed), lastSegment);
		}
		else if(startToFullSpeed > 0 && endToFullSpeed > 0){
			startPart = startToFullSpeed / segmentLength;
			endPart = 1 - endToFullSpeed / segmentLength;
			addSegment(0, startPart, fullSpeed, false);
			addSegment(startPart, endPart, fullSpeed, false);
			addSegment(endPart, 1, interpolateSpeed(endToFullSpeed), lastSegment);
		}
		else if(startToFullSpeed > 0){
			startPart = startToFullSpeed / segmentLength;
			addSegment(0, startPart, fullSpeed, false);
			addSegment(startPart, 1, fullSpeed, lastSegment);
		}
		else if(endToFullSpeed > 0){
			endPart = 1 - endToFullSpeed / segmentLength;
			addSegment(0, endPart, fullSpeed, false);
			addSegment(endPart, 1, interpolateSpeed(endToFullSpeed), lastSegment);
		}
		else{
			addSegment(0, 1, fullSpeed, lastSegment);
		}

		segmentIndex++;
		if(x.length-segmentIndex <= 200 && segmentIndex%50 == 0){
			controller.sendPoints();
		}
	}

	// tracking move buffer in Arduino

	private ArrayList<Move> machineMoveBuffer;
	private Move currentMove;
	private int remainingSteps;

	/**
	 * Sends a move to Arduino.
	 * Called from processCommand on a move from the move buffer.
	 * Stores the move in another buffer mirroring the one in Arduino.
	 */
	public synchronized void sendMove(Move m){
		if(machineMoveBuffer.size() == 0){
			currentMove = m;
			System.out.println("sendMove 1 -> currentMove {"+currentMove.dx()+", "+currentMove.dy()+", "+currentMove.dz()+", speed "+currentMove.endSpeed()+", type "+currentMove.type()+"}");
			remainingSteps = stepsPerMove(m);
		}
		if(m.type() != 'M'){
			currentMove = m;
			System.out.println("sendMove 2 -> currentMove {"+currentMove.dx()+", "+currentMove.dy()+", "+currentMove.dz()+", speed "+currentMove.endSpeed()+", type "+currentMove.type()+"}");
			machineMoveBuffer.add(0, m);
		}
		else{
			machineMoveBuffer.add(m);
		}
		write(""+m.type());
		if(machineConf.x.exists())writeInt(m.dx());
		if(machineConf.y.exists())writeInt(m.dy());
		if(machineConf.z.exists())writeInt(m.dz());
		if(machineConf.u.exists())writeInt(m.du());
		if(machineConf.v.exists())writeInt(m.dv());
		if(machineConf.w.exists())writeInt(m.dw());
		write(m.endSpeed());
	}

	/**
	 * Removes a move from mirror Arduino buffer.
	 * Called when Arduino reports a move is finished.
	 */
	private synchronized void popMachineMove(boolean shiftRest){
		Move m = machineMoveBuffer.remove(0);
		System.out.println(""+machineMoveBuffer.size()+" moves remaining");
		if(shiftRest){
			if(m.type() == 'M'){
				debugPrintln("remainingSteps "+remainingSteps+", move steps "+stepsPerMove(m));
				shiftPositionByMove(m, ((double)remainingSteps)/Math.max(stepsPerMove(m), 1));
			}
			else{
				debugPrintln("type "+m.type()+", move steps "+stepsPerMove(m));
				shiftPositionByMove(m, 1); // a manual move has no remainingSteps,
				                           // it can only start at the beginning
			}
			updateCurrentMove();
		}
		controller.defaultStatus();
	}

	/**
	 * Removes manual moves from mirror Arduino buffer.
	 * Used to reflect that "stop" operation cancels an ongoing machine move, discarding its remaining part.
	 */
	private synchronized void popAllManualMoves(){
		while(machineMoveBuffer.size() > 0 && machineMoveBuffer.get(0).type() != 'M'){
			machineMoveBuffer.remove(0);
		}
	}

	/**
	 * Finds the ongoing move in mirror Arduino buffer, stores it in dedicated variables
	 */
	private synchronized void updateCurrentMove(){
		if(machineMoveBuffer.size() != 0){
			remainingSteps = stepsPerMove(machineMoveBuffer.get(0));
			currentMove = machineMoveBuffer.get(0);
		}
		else{
			remainingSteps = 0;
			currentMove = zeroMove;
		}
		System.out.println("updateCurrentMove -> currentMove {"+currentMove.dx()+", "+currentMove.dy()+", "+currentMove.dz()+", speed "+currentMove.endSpeed()+", type "+currentMove.type()+"}");
	}

	/**
	 * Calculates the number of steps needed to run a full move
	 */
	private synchronized int stepsPerMove(Move m){
		return absmax(new int[]{m.dx(), m.dy(), m.dz(), m.du(), m.dv(), m.dw()});
	}

	/**
	 * Sends a position update to the controller.
	 * Called when a move is finished or interrupted by a "stop" command.
	 */
	private synchronized void shiftPositionByMove(Move m, double part){
		debugPrintln("shift part "+part);
		double[] shift = new double[]{
			machineConf.x.exists()? ((machineConf.x.inverted()? -1 : 1) * m.dx() * part / machineConf.x.stepsPerMm()) : 0,
			machineConf.y.exists()? ((machineConf.y.inverted()? -1 : 1) * m.dy() * part / machineConf.y.stepsPerMm()) : 0,
			machineConf.z.exists()? ((machineConf.z.inverted()? -1 : 1) * m.dz() * part / machineConf.z.stepsPerMm()) : 0,
			machineConf.u.exists()? ((machineConf.u.inverted()? -1 : 1) * m.du() * part / machineConf.u.stepsPerMm()) : 0,
			machineConf.v.exists()? ((machineConf.v.inverted()? -1 : 1) * m.dv() * part / machineConf.v.stepsPerMm()) : 0,
			machineConf.w.exists()? ((machineConf.w.inverted()? -1 : 1) * m.dw() * part / machineConf.w.stepsPerMm()) : 0
		};
		System.out.println("shifting by "+shift[0]+" "+shift[1]+" "+shift[2]
		                   +"   "+shift[3]+" "+shift[4]+" "+shift[5]);
		controller.shiftPosition(shift, m.type() != 'K');
	}

}

