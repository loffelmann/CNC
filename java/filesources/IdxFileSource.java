package filesources;

import java.io.*;
import java.nio.file.Paths;

/**
 * A tool path point source reading data from an "idx" file.
 * An idx file is meant to contain an array of axis coordinates.
 */
public class IdxFileSource extends FileSource {

	private static final int MAGIC_IDX = 0x00000C02; // idx file of int32, two dimensions

	private static final int MAX_READ_PTS = 10000;
	private static final int MAX_PREVIEW_PTS = 100000;

	private String path;

	private int expNumAxes;
	private int[] axisIndices;

	private FileInputStream fis;
	private BufferedInputStream bis;
	private DataInputStream dis;

	private int remaining, read;

	public boolean isXYZ(){
		return false;
	}

	public boolean is2D(){
		return false;
	}

	public IdxFileSource(String path, int expNumAxes, int[] axisIndices) throws IOException{
		super(Paths.get(path).getFileName().toString());
		this.expNumAxes = expNumAxes;
		this.axisIndices = axisIndices;
		this.path = path;

		fis = new FileInputStream(path);
		bis = new BufferedInputStream(fis);
		dis = new DataInputStream(bis);

		if(dis.readInt() != MAGIC_IDX){
			throw new IOException("Unrecognized idx file magic");
		}

		read = 0;
		remaining = dis.readInt();
		System.out.println("Loading idx file with "+remaining+" points");
		if(remaining <= 0){
			throw new IOException("Zero points to be read");
		}

		int numAxes = dis.readInt();
		if(numAxes != expNumAxes){
			throw new IOException("Mismatching number of axes ("+numAxes+", expected "+expNumAxes+")");
		}
	}

	public IdxFileSource duplicate() throws IOException{
		return new IdxFileSource(path, expNumAxes, axisIndices);
	}

	public void close(){
		try{
			if(dis != null)dis.close();
			if(bis != null)bis.close();
			if(fis != null)fis.close();
		}
		catch(IOException ex){}
		fis = null;
		bis = null;
		dis = null;
	}

	public double[][] getPoints(boolean preview) throws IOException{
		int readPoints = Math.min(remaining, MAX_READ_PTS);
		double[][] points = new double[6][readPoints];
		for(int i=0; i<readPoints; i++){
			for(int axInd: axisIndices){
				points[axInd][i] = dis.readInt() * 0.001; // microns => mm
			}
		}
		remaining -= readPoints;
		read += readPoints;
		return points;
	}

	public boolean started(){
		return read > 0;
	}
	public boolean finished(){
		return remaining <= 0;
	}

}

