
import java.awt.event.*;
import java.lang.Thread;
import java.lang.Math;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.*;

import machines.*;

/**
 * The main class.
 * Bridges GUI and driver.
 * Does everything that didn't fit in other classes.
 */
public class GilosController implements ActionListener {

	// Supported commands triggered from GUI
	public static final String COMMAND_CONNECT = "connect";
	public static final String COMMAND_BLINK = "blink";
//	public static final String COMMAND_SETAXES = "set axes";
	public static final String COMMAND_START = "start";
	public static final String COMMAND_ZERO = "zero";
	public static final String COMMAND_RESET = "reset";
	public static final String COMMAND_TESTLIM = "test lim";
	public static final String COMMAND_CHUCKOPEN = "chuck open";
	public static final String COMMAND_CHUCKCLOSE = "chuck close";
	public static final String COMMAND_BACKSPACE = "backspace";
	public static final String COMMAND_CLEAR = "clear";
	public static final String COMMAND_KILL = "kill";
	public static final String COMMAND_MANUALRUN = "manual run";
	public static final String COMMAND_MANUALRUN_X = "manual run X";
	public static final String COMMAND_MANUALRUN_Y = "manual run Y";
	public static final String COMMAND_MANUALRUN_Z = "manual run Z";
	public static final String COMMAND_MANUALRUN_U = "manual run U";
	public static final String COMMAND_MANUALRUN_V = "manual run V";
	public static final String COMMAND_MANUALRUN_W = "manual run W";

	private static GilosController controller;
	private static GilosGUI gui;
	private static GilosDriver gilos;

	// handling machine configuration
	private static final ConfigLoader config = new ConfigLoader("cnc.json");
	private MachineConfig machineConf;
	private static String machineName = "CNC"; // switching between machines not yet supported
	private boolean forceMachineConf = true; // send new config to Arduino even when nothing changed

	/**
	 * Runs the aplication, initializes things
	 */
	public static void main(String[] args){
		try{
			var outStream = new PrintStream(new FileOutputStream("cnc.log"), true);
			System.setOut(outStream);
			System.setErr(outStream);
		}
		catch(FileNotFoundException e){}
		controller = new GilosController();
		gui = new GilosGUI(controller);
		gui.setSearching();
		try{
			gui.setAxes(config.load(machineName));
		}
		catch(IOException e){
			config.save(gui.getAxes(), machineName);
		}
		gilos = new GilosDriver(controller, gui.getAxes());
		if(gilos.isConnected())gui.setConnected(gilos.getName());
		else gui.setDisconnected();
		controller.invalidatePosition();
	}


	// keeping track whether the machine moves or stands still

	private boolean running = false;
	public void updateRunning(boolean running){
		updateRunning(running, gui.MODE_RUN);
	}
	public void updateRunning(boolean running, String mode){
		this.running = running;
		if(running)gui.setRunning(mode);
		else gui.setStopped();
	}
	public boolean isRunning(){
		return running;
	}

	/**
	 * Called when the last move is popped from move buffer.
	 * Triggers a status update in GUI.
	 * Public to be able to propagate events from driver.
	 */
	public void toolPathFinished(){
		gui.toolPathFinished();
	}

	/**
	 * Resets GUI status to the default value (from an error message, for example).
	 * Public to be able to propagate events from driver.
	 */
	public void defaultStatus(){
		gui.defaultStatus();
	}

	/**
	 * Takes an exception and propagates it to the GUI status bar
	 */
	public void error(Exception ex){
		if(ex instanceof BacklashOutOfBounds){
			gui.warn(gui.MSG_ERROR_BIGBACKLASH, ex.toString());
		}
		else{
			gui.warn(gui.MSG_ERROR_UNKNOWN, ex.toString());
			ex.printStackTrace();
		}
	}

	/**
	 * Updates current machine config from GUI, saves it, and sends it to Arduino if needed.
	 */
	private synchronized boolean updateAxes(){
		MachineConfig oldConf = machineConf;
		machineConf = gui.getAxes();
		config.save(machineConf, machineName);
		if(oldConf == null || forceMachineConf
		|| !oldConf.x.equals(machineConf.x)
		|| !oldConf.y.equals(machineConf.y)
		|| !oldConf.z.equals(machineConf.z)
		|| !oldConf.u.equals(machineConf.u)
		|| !oldConf.v.equals(machineConf.v)
		|| !oldConf.w.equals(machineConf.w)
		){
			try{
				gilos.setAxes(machineConf);
			}
			catch(RuntimeException ex){
				error(ex);
				return false;
			}
			forceMachineConf = false;
		}
		return true;
	}

	/**
	 * Forces sending machine config to Arduino on the next start event.
	 * Used to init Arduino after reset or reconnection.
	 */
	public void invalidateConfig(){
		forceMachineConf = true;
	}

	/**
	 * Called from GUI to trigger keyboard-controlled movement
	 */
	public synchronized void keyboardMove(int axis, int direction){
		if(gilos.manualReady()){
			System.out.println("adding keyboard move");
			if(!running){
				if(!updateAxes())return;
			}
			updateRunning(true, gui.MODE_RUN);
			gilos.manualMove(
				(axis == 0)? direction*16000 : 0,
				(axis == 1)? direction*16000 : 0,
				(axis == 2)? direction*16000 : 0,
				(axis == 3)? direction*16000 : 0,
				(axis == 4)? direction*16000 : 0,
				(axis == 5)? direction*16000 : 0,
				encodeStepRate(gui.getStepRate()),
				'K'
			);
		}
	}

	/**
	 * Called from GUI to end keyboard-controlled movement when the key is released
	 */
	public void keyboardStop(){
		gilos.stop();
		updateRunning(false, gui.MODE_RUN);
	}

	/**
	 * ActionListener API handling various events from GUI
	 */
	public void actionPerformed(ActionEvent ev){
		String cmd = ev.getActionCommand();
		System.out.println("controller command "+cmd);
		switch(cmd){

			case COMMAND_CONNECT:
				// find a serial port with compatilbe Arduino
				gui.defaultStatus();
				forceMachineConf = true;
				if(gilos.isConnected()){
					gui.setDisconnected();
					gilos.disconnect();
				}
				else{
					gui.setSearching();
					gilos.init();
					gilos.findPort();
					if(gilos.isConnected())gui.setConnected(gilos.getName());
					else gui.setDisconnected();
				}
				break;

			case COMMAND_BLINK:
				// blink LED on Arduino to check connection
				gui.defaultStatus();
				gilos.blink();
				break;

			case COMMAND_TESTLIM:
				// enter limit sensor testing mode (lights up LED when a sensor has signal)
				gui.setStatus(gui.MSG_TESTINGLIMITS);
				updateRunning(true, gui.MODE_LIMIT);
				gilos.testLimits();
				break;

			case COMMAND_CHUCKOPEN:
				// open chuck, release tool
				gilos.chuckOpen();
				break;

			case COMMAND_CHUCKCLOSE:
				// close chuck, grab tool
				gilos.chuckClose();
				break;

//			case COMMAND_SETAXES: // happens automatically now
//				updateAxes();
//				break;

			case COMMAND_START:
				// stop machine when running, start when not running (single button in GUI)
				gui.defaultStatus();
				if(running){
					gilos.stop();
					updateRunning(false, gui.MODE_RUN);
				}
				else{
					if(!updateAxes()){
						break;
					}
					updateRunning(true, gui.MODE_RUN);
					sendPoints();
					try{Thread.sleep(100);}catch(InterruptedException e){}
					gilos.start(); // driver starts on its own only when there is data
				}
				break;

			case COMMAND_RESET:
				// reset app internal states, send reset signal to Arduino
				gui.toolPathFinished();
				gui.defaultStatus();
				gilos.reset();
				if(running)invalidatePosition();
				gui.clear(true);
				updateRunning(false, gui.MODE_RUN);
				break;

			case COMMAND_ZERO:
				// enter axis homing mode
				gui.setStatus(gui.MSG_GOINGTOZERO);
				invalidatePosition();
				if(!updateAxes())break;
				updateRunning(true, gui.MODE_ZERO);
				gilos.goToZero();
				break;

			case COMMAND_MANUALRUN:
			case COMMAND_MANUALRUN_X:
			case COMMAND_MANUALRUN_Y:
			case COMMAND_MANUALRUN_Z:
			case COMMAND_MANUALRUN_U:
			case COMMAND_MANUALRUN_V:
			case COMMAND_MANUALRUN_W:
				// make a button-triggered manual move

				gui.setStatus(gui.MSG_INPUTMODE_MANUAL);
				updateRunning(true, gui.MODE_RUN);
				if(!updateAxes())break;
				double[] shift = gui.getManualPosition();
				if(gui.manualAbsolute()){
					System.out.println("absolute move from "+Arrays.toString(position)
					                  +" to "+Arrays.toString(shift));
					shift[0] -= position[0];
					shift[1] -= position[1];
					shift[2] -= position[2];
					shift[3] -= position[3];
					shift[4] -= position[4];
					shift[5] -= position[5];
				}
				else{
					System.out.println("relative move from "+Arrays.toString(position)
					                  +" by "+Arrays.toString(shift));
				}

				// handling single-axis moves
				if(!cmd.equals(COMMAND_MANUALRUN)){
					if(!cmd.equals(COMMAND_MANUALRUN_X))shift[0] = 0;
					if(!cmd.equals(COMMAND_MANUALRUN_Y))shift[1] = 0;
					if(!cmd.equals(COMMAND_MANUALRUN_Z))shift[2] = 0;
					if(!cmd.equals(COMMAND_MANUALRUN_U))shift[3] = 0;
					if(!cmd.equals(COMMAND_MANUALRUN_V))shift[4] = 0;
					if(!cmd.equals(COMMAND_MANUALRUN_W))shift[5] = 0;
				}

				// clip at limits if specified
				if(gui.manualLimits()){
					shift = limitShift(shift);
					System.out.println("shift after clipping:");
					System.out.println("  ["+shift[0]+", "+shift[1]+", "+shift[2]+", "
					                        +shift[3]+", "+shift[4]+", "+shift[5]+"]");
				}

				gilos.manualMove(
					(int)Math.round(shift[0] * machineConf.x.stepsPerMm()),
					(int)Math.round(shift[1] * machineConf.y.stepsPerMm()),
					(int)Math.round(shift[2] * machineConf.z.stepsPerMm()),
					(int)Math.round(shift[3] * machineConf.u.stepsPerMm()),
					(int)Math.round(shift[4] * machineConf.v.stepsPerMm()),
					(int)Math.round(shift[5] * machineConf.w.stepsPerMm()),
					encodeStepRate(gui.getStepRate()),
					gui.manualLimits()? 'N' : 'K'
				);
				break;

			// reflecting some GUI button actions back to the GUI
			case COMMAND_BACKSPACE:
				gui.defaultStatus();
				gui.backspace();
				break;
			case COMMAND_CLEAR:
				gui.defaultStatus();
				gui.clear(false);
				break;

			case COMMAND_KILL:
				// finalize things before exitting app
				gui.setStatus(gui.MSG_KILLED);
				forceMachineConf = true;
				gilos.disconnect();
				break;

			default:
				System.err.println("Unknown event: " + ev.getActionCommand());
				break;
		}
	}

	/**
	 * Finds spots where the machine needs to slow to minimum speed
	 * (sharp turns, axis direction changes)
	 */
	private double[] findZeroSpeedDistances(
		double[] x, double[] y, double[] z,
		double[] u, double[] v, double[] w,
		double startDistance, boolean zeroAtEnd
	){
		List<Double> zeroDistances = new ArrayList<Double>();
		double distance = startDistance;
		zeroDistances.add(distance);

		var previousSegments = new ArrayList<double[]>();
		previousSegments.add(new double[]{0, 0, 0, 0, 0, 0, 1});

		double lastNonzeroDx = 0;
		double lastNonzeroDy = 0;
		double lastNonzeroDz = 0;
		double lastNonzeroDu = 0;
		double lastNonzeroDv = 0;
		double lastNonzeroDw = 0;

		double dx, dy, dz, du, dv, dw, norm;

		double cos;
		double minCos = Math.cos(Math.toRadians(20));
		boolean zero;
		for(int i=1; i<x.length; i++){
			dx = x[i] - x[i-1];
			dy = y[i] - y[i-1];
			dz = z[i] - z[i-1];
			du = u[i] - u[i-1];
			dv = v[i] - v[i-1];
			dw = w[i] - w[i-1];
			norm = Math.sqrt(dx*dx + dy*dy + dz*dz + du*du + dv*dv + dw*dw);
			norm = Math.max(norm, 1e-3);

			zero = false;

			// zero at sharp turns
			double queueDist = norm;
			for(double[] prev: previousSegments){
				cos = (dx*prev[0] + dy*prev[1] + dz*prev[2]
					 + du*prev[3] + dv*prev[4] + dw*prev[5])
					/ norm / prev[6];
				if(cos < minCos){
					zero = true;
					queueDist += 2*gilos.ACCEL_DISTANCE; // empty queue
				}
			}

			// zero at axis direction change which triggers backlash correction
			if((dx*lastNonzeroDx < 0 && machineConf.x.backlash() != 0)
			|| (dy*lastNonzeroDy < 0 && machineConf.y.backlash() != 0)
			|| (dz*lastNonzeroDz < 0 && machineConf.z.backlash() != 0)
			|| (du*lastNonzeroDu < 0 && machineConf.u.backlash() != 0)
			|| (dv*lastNonzeroDv < 0 && machineConf.v.backlash() != 0)
			|| (dw*lastNonzeroDw < 0 && machineConf.w.backlash() != 0)
			)zero = true;

			if(zero){
				zeroDistances.add(distance);
			}

			previousSegments.add(new double[]{dx, dy, dz, du, dv, dw, norm});
			for(int j=previousSegments.size()-2; j>=0; j--){
				if(queueDist > gilos.ACCEL_DISTANCE){
					previousSegments.remove(j);
				}
				else{
					queueDist += previousSegments.get(j)[6];
				}
			}

			if(dx != 0)lastNonzeroDx = dx;
			if(dy != 0)lastNonzeroDy = dy;
			if(dz != 0)lastNonzeroDz = dz;
			if(du != 0)lastNonzeroDu = du;
			if(dv != 0)lastNonzeroDv = dv;
			if(dw != 0)lastNonzeroDw = dw;

			distance += norm;
		}
		if(zeroAtEnd){
			zeroDistances.add(distance);
		}
		endDistance = distance;
		Double[] zeroDistances2 = zeroDistances.toArray(new Double[zeroDistances.size()]);
		double[] zeroDistances3 = new double[zeroDistances2.length];
		for(int i=zeroDistances2.length-1; i>=0; i--){
			zeroDistances3[i] = zeroDistances2[i];
		}
		return zeroDistances3;
	}

	/**
	 * Encodes a motor step rate in steps per second to the format understood by Arduino
	 */
	private byte encodeStepRate(double stepRate){
		long scaledSpeed = Math.round(stepRate/32);
		byte encodedSpeed = (byte)Math.min(Math.max(scaledSpeed, 1), 255);
		return encodedSpeed;
	}

//	/**
//	 * Clips a tool path to axis limits.
//	 * Replaced by a simpler method for manual moves (customer wanted different behavior)
//	 * and Arduino code for file-based tool paths (to support manual moves in the middle).
//	 */
//	private double[][] splitAndLimit(double[][] points){
//		int numOrigPoints = points[0].length;
//		if(numOrigPoints == 0)return points;
//
//		double limX0 = machineConf.x.lowLimitMm();
//		double limX1 = machineConf.x.highLimitMm();
//		double limY0 = machineConf.y.lowLimitMm();
//		double limY1 = machineConf.y.highLimitMm();
//		double limZ0 = machineConf.z.lowLimitMm();
//		double limZ1 = machineConf.z.highLimitMm();
//		double limU0 = machineConf.u.lowLimitMm();
//		double limU1 = machineConf.u.highLimitMm();
//		double limV0 = machineConf.v.lowLimitMm();
//		double limV1 = machineConf.v.highLimitMm();
//		double limW0 = machineConf.w.lowLimitMm();
//		double limW1 = machineConf.w.highLimitMm();
//
//		// counting points after segment splitting
//		int numSplitPoints = numOrigPoints;
//		for(int i=1; i<numOrigPoints; i++){
//			if((points[0][i-1]-limX0)*(points[0][i]-limX0) < 0)numSplitPoints++;
//			if((points[0][i-1]-limX1)*(points[0][i]-limX1) < 0)numSplitPoints++;
//			if((points[1][i-1]-limY0)*(points[1][i]-limY0) < 0)numSplitPoints++;
//			if((points[1][i-1]-limY1)*(points[1][i]-limY1) < 0)numSplitPoints++;
//			if((points[2][i-1]-limZ0)*(points[2][i]-limZ0) < 0)numSplitPoints++;
//			if((points[2][i-1]-limZ1)*(points[2][i]-limZ1) < 0)numSplitPoints++;
//			if((points[3][i-1]-limU0)*(points[3][i]-limU0) < 0)numSplitPoints++;
//			if((points[3][i-1]-limU1)*(points[3][i]-limU1) < 0)numSplitPoints++;
//			if((points[4][i-1]-limV0)*(points[4][i]-limV0) < 0)numSplitPoints++;
//			if((points[4][i-1]-limV1)*(points[4][i]-limV1) < 0)numSplitPoints++;
//			if((points[5][i-1]-limW0)*(points[5][i]-limW0) < 0)numSplitPoints++;
//			if((points[5][i-1]-limW1)*(points[5][i]-limW1) < 0)numSplitPoints++;
//		}
//
//		// splitting segments at crossection with limits
//		double[][] splitPoints = new double[6][numSplitPoints];
//		for(int i=0; i<6; i++)splitPoints[i][0] = points[i][0];
//		int splitI = 1;
//		for(int i=1; i<numOrigPoints; i++){
//			double[] mus = new double[12];
//			if((points[0][i-1]-limX0)*(points[0][i]-limX0) < 0){
//				mus[0] = (points[0][i-1]-limX0) / (points[0][i-1]-points[0][i]);
//			}
//			if((points[0][i-1]-limX1)*(points[0][i]-limX1) < 0){
//				mus[1] = (points[0][i-1]-limX1) / (points[0][i-1]-points[0][i]);
//			}
//			if((points[1][i-1]-limY0)*(points[1][i]-limY0) < 0){
//				mus[2] = (points[1][i-1]-limY0) / (points[1][i-1]-points[1][i]);
//			}
//			if((points[1][i-1]-limY1)*(points[1][i]-limY1) < 0){
//				mus[3] = (points[1][i-1]-limY1) / (points[1][i-1]-points[1][i]);
//			}
//			if((points[2][i-1]-limZ0)*(points[2][i]-limZ0) < 0){
//				mus[4] = (points[2][i-1]-limZ0) / (points[2][i-1]-points[2][i]);
//			}
//			if((points[2][i-1]-limZ1)*(points[2][i]-limZ1) < 0){
//				mus[5] = (points[2][i-1]-limZ1) / (points[2][i-1]-points[2][i]);
//			}
//			if((points[3][i-1]-limU0)*(points[3][i]-limU0) < 0){
//				mus[6] = (points[3][i-1]-limU0) / (points[3][i-1]-points[3][i]);
//			}
//			if((points[3][i-1]-limU1)*(points[3][i]-limU1) < 0){
//				mus[7] = (points[3][i-1]-limU1) / (points[3][i-1]-points[3][i]);
//			}
//			if((points[4][i-1]-limV0)*(points[4][i]-limV0) < 0){
//				mus[8] = (points[4][i-1]-limV0) / (points[4][i-1]-points[4][i]);
//			}
//			if((points[4][i-1]-limV1)*(points[4][i]-limV1) < 0){
//				mus[9] = (points[4][i-1]-limV1) / (points[4][i-1]-points[4][i]);
//			}
//			if((points[5][i-1]-limW0)*(points[5][i]-limW0) < 0){
//				mus[10] = (points[5][i-1]-limW0) / (points[5][i-1]-points[5][i]);
//			}
//			if((points[5][i-1]-limW1)*(points[5][i]-limW1) < 0){
//				mus[11] = (points[5][i-1]-limW1) / (points[5][i-1]-points[5][i]);
//			}
//			Arrays.sort(mus);
//			for(double mu: mus){
//				if(mu > 0){
//					assert mu < 1;
//					splitPoints[0][splitI] = points[0][i]*mu + points[0][i-1]*(1-mu);
//					splitPoints[1][splitI] = points[1][i]*mu + points[1][i-1]*(1-mu);
//					splitPoints[2][splitI] = points[2][i]*mu + points[2][i-1]*(1-mu);
//					splitPoints[3][splitI] = points[3][i]*mu + points[3][i-1]*(1-mu);
//					splitPoints[4][splitI] = points[4][i]*mu + points[4][i-1]*(1-mu);
//					splitPoints[5][splitI] = points[5][i]*mu + points[5][i-1]*(1-mu);
//					splitI++;
//				}
//			}
//			splitPoints[0][splitI] = points[0][i];
//			splitPoints[1][splitI] = points[1][i];
//			splitPoints[2][splitI] = points[2][i];
//			splitPoints[3][splitI] = points[3][i];
//			splitPoints[4][splitI] = points[4][i];
//			splitPoints[5][splitI] = points[5][i];
//			splitI++;
//		}
//		assert splitI == numSplitPoints;
//
//		// clipping to limits
//		for(int i=0; i<numSplitPoints; i++){
//			splitPoints[0][i] = Math.min(Math.max(splitPoints[0][i], limX0), limX1);
//			splitPoints[1][i] = Math.min(Math.max(splitPoints[1][i], limY0), limY1);
//			splitPoints[2][i] = Math.min(Math.max(splitPoints[2][i], limZ0), limZ1);
//			splitPoints[3][i] = Math.min(Math.max(splitPoints[3][i], limU0), limU1);
//			splitPoints[4][i] = Math.min(Math.max(splitPoints[4][i], limV0), limV1);
//			splitPoints[5][i] = Math.min(Math.max(splitPoints[5][i], limW0), limW1);
//		}
//
//		return splitPoints;
//	}

	/**
	 * Finds the relative part of a single-axis movement which fits into limits
	 */
	private double findAxisMu(double pos, double shift, double low, double high){
		if((pos <= low && shift < 0) || (pos >= high && shift > 0)){
			return 0; // already beyond limit => prevent moving even further
		}
		else if(pos >= low && pos+shift < low){
			return (pos-low) / Math.abs(shift);
		}
		else if(pos <= high && pos+shift > high){
			return (high-pos) / Math.abs(shift);
		}
		else{
			return 1;
		}
	}

	/**
	 * Clips a multi-axis movement (starting at current position) to fit into axis limits
	 */
	private double[] limitShift(double[] shift){
		double mu = 1;
		mu = Math.min(mu, findAxisMu(
			position[0], shift[0],
			machineConf.x.lowLimitMm(), machineConf.x.highLimitMm()
		));
		mu = Math.min(mu, findAxisMu(
			position[1], shift[1],
			machineConf.y.lowLimitMm(), machineConf.y.highLimitMm()
		));
		mu = Math.min(mu, findAxisMu(
			position[2], shift[2],
			machineConf.z.lowLimitMm(), machineConf.z.highLimitMm()
		));
		mu = Math.min(mu, findAxisMu(
			position[3], shift[3],
			machineConf.u.lowLimitMm(), machineConf.u.highLimitMm()
		));
		mu = Math.min(mu, findAxisMu(
			position[4], shift[4],
			machineConf.v.lowLimitMm(), machineConf.v.highLimitMm()
		));
		mu = Math.min(mu, findAxisMu(
			position[5], shift[5],
			machineConf.w.lowLimitMm(), machineConf.w.highLimitMm()
		));
		mu = Math.max(mu, 0);
		return new double[]{ shift[0]*mu, shift[1]*mu, shift[2]*mu,
		                     shift[3]*mu, shift[4]*mu, shift[5]*mu };
	}

	// remembering posistion at the end of a tool path slice
	// to allow processing tool paths loaded piece by piece from a file
	private double lastDistance, endDistance;
	private double[] lastPoint;

	/**
	 * Forgets that there was a partially processed tool path
	 */
	public void sequenceReset(){
		lastDistance = 0;
	}

	/**
	 * Obtains next portion of tool path from GUI, sends it to driver
	 */
	public void sendPoints(){
		double[][] points = gui.getPoints();
//		points = splitAndLimit(points);
		if(points[0].length == 0)return;
		if(lastDistance > 0){
			lastDistance += Math.sqrt(
				  (lastPoint[0]-points[0][0])*(lastPoint[0]-points[0][0])
				+ (lastPoint[1]-points[1][0])*(lastPoint[1]-points[1][0])
				+ (lastPoint[2]-points[2][0])*(lastPoint[2]-points[2][0])
				+ (lastPoint[3]-points[3][0])*(lastPoint[3]-points[3][0])
				+ (lastPoint[4]-points[4][0])*(lastPoint[4]-points[4][0])
				+ (lastPoint[5]-points[5][0])*(lastPoint[5]-points[5][0])
			);
		}
		double[] zeroSpeedDistances = findZeroSpeedDistances(
			points[0], points[1], points[2],
			points[3], points[4], points[5],
			lastDistance, !gui.readingPathFromFile()
		);
		lastDistance = endDistance;
		lastPoint = new double[]{
			points[0][points[0].length-1],
			points[1][points[1].length-1],
			points[2][points[2].length-1],
			points[3][points[3].length-1],
			points[4][points[4].length-1],
			points[5][points[5].length-1]
		};
		gilos.setSpeed(encodeStepRate(gui.getStepRate()));
		gilos.trimSequence();
		gilos.extendSequence(
			points[0], points[1], points[2],
			points[3], points[4], points[5],
			zeroSpeedDistances
		);
	}

	// tracking current position

	// where it would be without limits
	private double[] position = new double[]{Double.NaN, Double.NaN, Double.NaN,
	                                         Double.NaN, Double.NaN, Double.NaN};

	// where it actually is
	private double[] limPosition = new double[]{Double.NaN, Double.NaN, Double.NaN,
	                                            Double.NaN, Double.NaN, Double.NaN};

	/**
	 * Sets selected components of reconstructed position to NaN.
	 * Used after operations which do not allow position tracking
	 * (like reset or interrupted homing).
	 */
	public void invalidatePosition(){
		invalidatePosition(new boolean[]{true, true, true, true, true, true});
	}
	public void invalidatePosition(boolean[] invalidFlags){
		for(int i=0; i<position.length; i++){
			if(invalidFlags[i]){
				position[i] = Double.NaN;
				limPosition[i] = Double.NaN;
			}
		}
		sendPosition();
		System.out.println("Position invalidated "
			+invalidFlags[0]+" "+invalidFlags[0]+" "+invalidFlags[1]
			+" "+invalidFlags[2]+" "+invalidFlags[3]+" "+invalidFlags[4]);
	}

	/**
	 * Sets reconstructed position to zero after successful homing
	 */
	public void zeroPosition(){
		if(machineConf.x.exists())position[0] = limPosition[0] = 0;
		if(machineConf.y.exists())position[1] = limPosition[1] = 0;
		if(machineConf.z.exists())position[2] = limPosition[2] = 0;
		if(machineConf.u.exists())position[3] = limPosition[3] = 0;
		if(machineConf.v.exists())position[4] = limPosition[4] = 0;
		if(machineConf.w.exists())position[5] = limPosition[5] = 0;
		sendPosition();
		gui.defaultStatus();
		System.out.println("Position zeroed");
	}

	/**
	 * Provides reconstructed position to GUI
	 */
	public void sendPosition(){
		if(gui != null)gui.setPosition(limPosition);
	}

	/**
	 * Updates reconstructed position by a shift
	 */
	public void shiftPosition(double[] shift, boolean applyLimits){
		double[] lowLimit = new double[]{
			machineConf.x.lowLimitMm(), machineConf.y.lowLimitMm(), machineConf.z.lowLimitMm(), 
			machineConf.u.lowLimitMm(), machineConf.v.lowLimitMm(), machineConf.w.lowLimitMm(),
		};
		double[] highLimit = new double[]{
			machineConf.x.highLimitMm(), machineConf.y.highLimitMm(), machineConf.z.highLimitMm(), 
			machineConf.u.highLimitMm(), machineConf.v.highLimitMm(), machineConf.w.highLimitMm(),
		};
		double[] newPos = new double[6];
		for(int i=0; i<position.length; i++){
			newPos[i] = position[i] + shift[i];
			if(applyLimits){
				if(position[i] > highLimit[i]){
					assert limPosition[i] <= position[i];
					limPosition[i] = Math.min(limPosition[i], Math.max(newPos[i], lowLimit[i]));
				}
				else if(position[i] < lowLimit[i]){
					assert limPosition[i] >= position[i];
					limPosition[i] = Math.max(limPosition[i], Math.min(newPos[i], highLimit[i]));
				}
				else{
					assert limPosition[i] == position[i];
					limPosition[i] = Math.min(Math.max(newPos[i], lowLimit[i]), highLimit[i]);
				}
			}
			else{
				limPosition[i] += shift[i];
			}
		}
		System.out.println("old position "
		                         +position[0]+" "+position[1]+" "+position[2]
		                   +"   "+position[3]+" "+position[4]+" "+position[5]);
		position = newPos;
		System.out.println("new position "
		                         +position[0]+" "+position[1]+" "+position[2]
		                   +"   "+position[3]+" "+position[4]+" "+position[5]);
		System.out.println("new limited position "
		                         +limPosition[0]+" "+limPosition[1]+" "+limPosition[2]
		                   +"   "+limPosition[3]+" "+limPosition[4]+" "+limPosition[5]);
		sendPosition();
	}

}

