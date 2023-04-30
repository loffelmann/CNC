package machines;

/**
 * Model of a machine with three orthogonal axes
 */
public class XYZMachine extends Machine {

	public double[][] xyzToAxes(double[][] xyz){
		assert xyz.length == 3;
		double[][] ax = new double[6][xyz[0].length];
		System.arraycopy(xyz[0], 0, ax[0], 0, xyz[0].length);
		System.arraycopy(xyz[1], 0, ax[1], 0, xyz[1].length);
		System.arraycopy(xyz[2], 0, ax[2], 0, xyz[2].length);
		return ax;
	}

	public double[][] axesToXyz(double[][] ax){
		assert ax.length == 6;
		double[][] xyz = new double[3][ax[0].length];
		System.arraycopy(ax[0], 0, xyz[0], 0, ax[0].length);
		System.arraycopy(ax[1], 0, xyz[1], 0, ax[1].length);
		System.arraycopy(ax[2], 0, xyz[2], 0, ax[2].length);
		return xyz;
	}

	public double[][] plotOutline(){
		double[][] outline = new double[2][];
		outline[0] = new double[]{
			config.x.lowLimitMm(),
			config.x.highLimitMm(),
			config.x.highLimitMm(),
			config.x.lowLimitMm()
		};
		outline[1] = new double[]{
			config.y.lowLimitMm(),
			config.y.lowLimitMm(),
			config.y.highLimitMm(),
			config.y.highLimitMm()
		};
		return outline;
	}

}

