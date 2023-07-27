package machines;

/**
 * Stores various things that can be configured for a single machine axis
 */
public class AxisConfig{

	private boolean exists;
	private double stepsPerMm;
	private boolean inverted;
	private boolean zeroUp;
	private Double lowLimit;
	private Double highLimit;
	private double backlash;

	public AxisConfig(
		boolean exists,
		double  stepsPerMm,
		boolean inverted,
		boolean zeroUp,
		Double  lowLimit,
		Double  highLimit,
		double backlash
	){
		this.exists = exists;
		this.stepsPerMm = stepsPerMm;
		this.inverted = inverted;
		this.zeroUp = zeroUp;
		this.lowLimit = lowLimit;
		this.highLimit = highLimit;
		this.backlash = backlash;
	}

	public AxisConfig(
		Boolean exists,
		Double  stepsPerMm,
		Boolean inverted,
		Boolean zeroUp,
		Double  lowLimit,
		Double  highLimit,
		Double  backlash
	){
		this.exists = (boolean)exists;
		this.stepsPerMm = (double)stepsPerMm;
		this.inverted = (boolean)inverted;
		this.zeroUp = (boolean)zeroUp;
		this.lowLimit = lowLimit;
		this.highLimit = highLimit;
		this.backlash = (double)backlash;
	}

	public AxisConfig(){
		this.exists = false;
		this.stepsPerMm = 200;
		this.inverted = false;
		this.zeroUp = false;
		this.lowLimit = null;
		this.highLimit = null;
		this.backlash = 0;
	}

	private boolean limitEquals(Double a, Double b){
		if(a == null && b == null)return true;
		if((a == null) != (b == null)) return false;
		return a.equals(b);
	}

	public boolean equals(AxisConfig other){
		return this.exists == other.exists
		    && this.stepsPerMm == other.stepsPerMm
		    && this.inverted == other.inverted
		    && this.zeroUp == other.zeroUp
		    && limitEquals(this.lowLimit, other.lowLimit)
		    && limitEquals(this.highLimit, other.highLimit)
		    && this.backlash == other.backlash;
	}

	public AxisConfig clone(){
		return new AxisConfig(
			exists,
			stepsPerMm,
			inverted,
			zeroUp,
			lowLimit,
			highLimit,
			backlash
		);
	}

	public boolean exists(){
		return exists;
	}

	public double stepsPerMm(){
		return stepsPerMm;
	}

	public boolean inverted(){
		return inverted;
	}

	public boolean zeroUp(){
		return zeroUp;
	}

	public Double lowLimit(){
		return lowLimit;
	}
	public double lowLimitMm(){
		if(lowLimit == null || lowLimit.isNaN())return Double.NEGATIVE_INFINITY;
		else return lowLimit;
	}
	public int lowLimitSteps(){
		if(lowLimit == null || lowLimit.isNaN())return Integer.MIN_VALUE;
		else return (int)Math.round(lowLimit * stepsPerMm);
	}

	public Double highLimit(){
		return highLimit;
	}
	public double highLimitMm(){
		if(highLimit == null || highLimit.isNaN())return Double.POSITIVE_INFINITY;
		else return highLimit;
	}
	public int highLimitSteps(){
		if(highLimit == null || highLimit.isNaN())return Integer.MIN_VALUE;
		else return (int)Math.round(highLimit * stepsPerMm);
	}

	public double backlash(){
		return backlash;
	}
	public int backlashSteps(){
		int bs = (int)Math.round(backlash * stepsPerMm);
		if(bs < -32767 || bs > 32767){
			throw new BacklashOutOfBounds();
		}
		return bs;
	}

}

