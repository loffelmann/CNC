package machines;

/**
 * Base class for models of specific machines.
 * A machine model allows recalculation of axis coordinates into Cartesian and back,
 * as well as displaying an operating area in GUI.
 */
public abstract class Machine {

	protected MachineConfig config;
	public void configure(MachineConfig mc){
		config = mc;
	}

	public abstract double[][] xyzToAxes(double[][] xyz);
	public double[] xyzToAxes(double[] xyz){
		assert xyz.length == 3;
		double[][] xyzSeq = new double[3][1];
		xyzSeq[0][0] = xyz[0];
		xyzSeq[1][0] = xyz[1];
		xyzSeq[2][0] = xyz[2];
		double[][] axSeq = xyzToAxes(xyzSeq);
		return new double[]{
			axSeq[0][0], axSeq[1][0], axSeq[2][0],
			axSeq[3][0], axSeq[4][0], axSeq[5][0]
		};
	}

	public abstract double[][] axesToXyz(double[][] ax);
	public double[] axesToXyz(double[] ax){
		assert ax.length == 6;
		double[][] axSeq = new double[6][1];
		axSeq[0][0] = ax[0];
		axSeq[1][0] = ax[1];
		axSeq[2][0] = ax[2];
		axSeq[3][0] = ax[3];
		axSeq[4][0] = ax[4];
		axSeq[5][0] = ax[5];
		double[][] xyzSeq = axesToXyz(axSeq);
		return new double[]{
			xyzSeq[0][0], xyzSeq[1][0], xyzSeq[2][0]
		};
	}

	public abstract double[][] plotOutline();

}


