
import java.util.List;
import java.util.ArrayList;
import java.awt.*;
import java.awt.event.*;

/**
 * A GUI element used to display a graphical preview of tool paths
 */
class PreviewCanvas extends Canvas {

	// subsample long tool paths to at most this number of points
	private static final int MAX_POINTS = 100000;

	// colors
	private static final Color BG_INSIDE = Color.WHITE;
	private static final Color BG_OUTSIDE = Color.PINK;
	private static final Color FG = Color.BLACK;

	// tool path and operating area data (XYZ)
	private double[][] points, outline;

	// current subsampling factor
	private int skip;


	// the program allows to rescale a loaded path before running it through the machine
	private double zoom;
	public void setZoom(double zoom){
		if(Double.isFinite(zoom) && zoom != this.zoom){
			this.zoom = zoom;
		}
	}


	// extreme points of the path, to be displayed in GUI
	private double minX, maxX, minY, maxY, minZ, maxZ;
	public double[][] getPathBounds(){
		double[][] bounds = new double[3][2];
		bounds[0][0] = (minX - firstX) * zoom + start[0];
		bounds[0][1] = (maxX - firstX) * zoom + start[0];
		bounds[1][0] = (minY - firstY) * zoom + start[1];
		bounds[1][1] = (maxY - firstY) * zoom + start[1];
		bounds[2][0] = (minZ - firstZ) * zoom + start[2];
		bounds[2][1] = (maxZ - firstZ) * zoom + start[2];
		return bounds;
	}


	// making the tool path position relative to its first point
	private double firstX, firstY, firstZ; // first set of coordinates in the data
	private double[] start; // the "physical" point where the path will begin
	public void setStart(double[] xyz){
		if((start[0] != xyz[0] && (!Double.isNaN(start[0]) || !Double.isNaN(xyz[0])))
		|| (start[1] != xyz[1] && (!Double.isNaN(start[1]) || !Double.isNaN(xyz[1])))
		|| (start[2] != xyz[2] && (!Double.isNaN(start[2]) || !Double.isNaN(xyz[2])))){
			// start changed
			start = xyz;
		}
	}


	public PreviewCanvas(){
		setBackground(BG_INSIDE);
		start = new double[]{ Double.NaN, Double.NaN, Double.NaN };
		zoom = 1;
		clean();
	}


	/**
	 * Inserts new XYZ points into the path.
	 * Subsamples the path if max number of points is exceeded.
	 */
	public void addPoints(double[][] newPoints){
		int remainingCnt = points[0].length;
		int newCnt = newPoints[0].length / skip;

		double[][] allPoints;

		if(remainingCnt+newCnt > MAX_POINTS){
			// leaving out some of the previous points

			int newSkip = (remainingCnt+newCnt)/MAX_POINTS + 1;
			skip *= newSkip;
			remainingCnt = points[0].length / newSkip;
			newCnt = newPoints[0].length / skip;

			allPoints = new double[3][remainingCnt+newCnt];
			for(int i=0; i<remainingCnt; i++){
				allPoints[0][i] = points[0][i*newSkip];
				allPoints[1][i] = points[1][i*newSkip];
				allPoints[2][i] = points[2][i*newSkip];
			}
		}
		else{
			// keeping all previous points
			allPoints = new double[3][remainingCnt+newCnt];
			System.arraycopy(points[0], 0, allPoints[0], 0, remainingCnt);
			System.arraycopy(points[1], 0, allPoints[1], 0, remainingCnt);
			System.arraycopy(points[2], 0, allPoints[2], 0, remainingCnt);
		}

		// sampling new points
		for(int i=0; i<newCnt; i++){
			allPoints[0][remainingCnt+i] = newPoints[0][i*skip];
			allPoints[1][remainingCnt+i] = newPoints[1][i*skip];
			allPoints[2][remainingCnt+i] = newPoints[2][i*skip];
		}
		points = allPoints;

		if(remainingCnt == 0 && newCnt > 0){
			firstX = newPoints[0][0];
			firstY = newPoints[1][0];
			firstZ = newPoints[2][0];
		}

		// updating limits
		for(int i=newPoints[0].length-1; i>=0; i--){
			minX = Math.min(minX, newPoints[0][i]);
			maxX = Math.max(maxX, newPoints[0][i]);
			minY = Math.min(minY, newPoints[1][i]);
			maxY = Math.max(maxY, newPoints[1][i]);
			minZ = Math.min(minZ, newPoints[2][i]);
			maxZ = Math.max(maxZ, newPoints[2][i]);
		}
	}

	/**
	 * Sets the operating area to be highlighted
	 */
	public void setOutline(double[][] outline){
		this.outline = outline;
	}

	/**
	 * Discards both the tool path and the operating area
	 */
	public void clean(){
		points = new double[3][0];
		skip = 1;
		minX = Double.POSITIVE_INFINITY;
		maxX = Double.NEGATIVE_INFINITY;
		minY = Double.POSITIVE_INFINITY;
		maxY = Double.NEGATIVE_INFINITY;
		minZ = Double.POSITIVE_INFINITY;
		maxZ = Double.NEGATIVE_INFINITY;
		firstX = firstY = firstZ = 0;
		outline = new double[2][0];
	}

	public void paint(Graphics gg){
		Graphics2D g = (Graphics2D)gg;

		// find ranges including outline

		double minX = (this.minX - firstX) * zoom;
		double maxX = (this.maxX - firstX) * zoom;
		double minY = (this.minY - firstY) * zoom;
		double maxY = (this.maxY - firstY) * zoom;

		if(!Double.isNaN(start[0]) && !Double.isNaN(start[1])){

			// shift by current tool position
			minX += start[0];
			maxX += start[0];
			minY += start[1];
			maxY += start[1];

			// only include outline into image boundaries if position is known
			for(int i=outline[0].length-1; i>=0; i--){
				if(!Double.isInfinite(outline[0][i])){
					minX = Math.min(minX, outline[0][i]);
					maxX = Math.max(maxX, outline[0][i]);
				}
				if(!Double.isInfinite(outline[1][i])){
					minY = Math.min(minY, outline[1][i]);
					maxY = Math.max(maxY, outline[1][i]);
				}
			}
		}

		if(minX > maxX && minY > maxY)return; // nothing to plot

		// precalculate things for drawing

		int offset = 10;
		int w = getWidth();
		int h = getHeight();

		double scale = Double.POSITIVE_INFINITY;
		if(maxX >= minX)scale = Math.min(scale, (w-2*offset)/(maxX-minX+0.001));
		if(maxY >= minY)scale = Math.min(scale, (h-2*offset)/(maxY-minY+0.001));

		double centerX = (maxX >= minX)? getWidth()*0.5 - (minX+maxX)*scale*0.5 : 0;
		double centerY = (maxY >= minY)? getHeight()*0.5 - (minY+maxY)*scale*0.5 : 0;

		// draw outline
		// (only if position is known)

		if(!Double.isNaN(start[0]) && !Double.isNaN(start[1])){
			setBackground(BG_OUTSIDE);
			int length = outline[0].length;
			int[] xPoints = new int[length];
			int[] yPoints = new int[length];
			for(int i=0; i<length; i++){
				double x = outline[0][i]*scale + centerX + 0.5;
				double y = outline[1][i]*scale + centerY + 0.5;
				xPoints[i] = (int)Math.min(Math.max(x, -10000), 10000);
				yPoints[i] = h - (int)Math.min(Math.max(y, -10000), 10000);
			}

			g.setColor(BG_INSIDE);
			g.fillPolygon(xPoints, yPoints, length);
		}
		else{
			setBackground(BG_INSIDE);
		}

		// draw path

		if(!Double.isNaN(start[0]) && !Double.isNaN(start[1])){
			centerX += start[0] * scale;
			centerY += start[1] * scale;
		}

		scale *= zoom;
		centerX -= firstX * scale;
		centerY -= firstY * scale;

		int length = points[0].length;
		double zScale = (maxZ > minZ)? 1.0/(maxZ-minZ) : 0;
		for(int i=1; i<length; i++){
			int x1 = (int)(points[0][i-1]*scale + centerX + 0.5);
			int y1 = (int)(points[1][i-1]*scale + centerY + 0.5);
			int x2 = (int)(points[0][i]*scale + centerX + 0.5);
			int y2 = (int)(points[1][i]*scale + centerY + 0.5);
			if(points[2][i-1] == points[2][i]){
				g.setPaint(getFgColor((points[2][i]-minZ)*zScale));
			}
			else{
				Color c1 = getFgColor((points[2][i-1]-minZ)*zScale);
				Color c2 = getFgColor((points[2][i]-minZ)*zScale);
				g.setPaint(new GradientPaint(x1, y1, c1, x2, y2, c2));
			}
			g.drawLine(x1, h-y1, x2, h-y2);
		}

	}

	/**
	 * Interpolates colors to indicate a third dimension
	 */
	private Color getFgColor(double mu){
		mu = Math.min(Math.max(mu, 0), 1);
		return new Color(
			(int)(mu*255),
			0,
			(int)((1-mu)*255)
		);
	}

}

