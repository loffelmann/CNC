package machines;

/**
 * Stores parameters of a specific machine.
 * Can be jsonified.
 */
public class MachineConfig {

	public AxisConfig x, y, z, u, v, w;
	public double stepsPerSecond;

	public MachineConfig(
		AxisConfig x,
		AxisConfig y,
		AxisConfig z,
		AxisConfig u,
		AxisConfig v,
		AxisConfig w,
		double sps
	){
		this.x = x;
		this.y = y;
		this.z = z;
		this.u = u;
		this.v = v;
		this.w = w;
		this.stepsPerSecond = sps;
	}

	public MachineConfig(){
		x = new AxisConfig();
		y = new AxisConfig();
		z = new AxisConfig();
		u = new AxisConfig();
		v = new AxisConfig();
		w = new AxisConfig();
		stepsPerSecond = 3200;
	}

	public MachineConfig clone(){
		return new MachineConfig(
			x.clone(),
			y.clone(),
			z.clone(),
			u.clone(),
			v.clone(),
			w.clone(),
			stepsPerSecond
		);
	}

}

