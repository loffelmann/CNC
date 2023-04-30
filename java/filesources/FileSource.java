package filesources;

import java.io.IOException;

/**
 * Base class for data sources reading a tool path from a file
 */
public abstract class FileSource {

	public FileSource(String name){
		this.name = name;
	}

	private String name;
	public String getName(){
		return this.name;
	}

	/**
	 * Creates a copy of the tool path source, starting from the first point again
	 */
	public abstract FileSource duplicate() throws IOException;

	/**
	 * Returns a batch of tool path points.
	 * @param preview Boolean flag allowing the source to adapt its output to
	 *                GUI preview rather than a physical tool path
	 */
	public abstract double[][] getPoints(boolean preview) throws IOException;
	public double[][] getPoints() throws IOException {
		return getPoints(false);
	}

	/**
	 * Reports whether the source returns Cartesian coordinates needed to be
	 * recalculated to axis movements (true), or returns axis movements directly (false)
	 */
	public abstract boolean isXYZ();

	/**
	 * Reports whether the source contains 2D data (in which case,
	 * raising the tool between disconnected path segments can be configured)
	 */
	public abstract boolean is2D();

	/**
	 * Reports if any tool path points have been sent from this source yet
	 */
	public abstract boolean started();

	/**
	 * Reports if there are more tool path points to be sent from this source
	 */
	public abstract boolean finished();

	/**
	 * Finalizes whathever is needed
	 */
	public abstract void close();

}

