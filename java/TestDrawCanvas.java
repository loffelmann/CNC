
import java.util.List;
import java.util.ArrayList;
import java.awt.*;
import java.awt.event.*;

/**
 * A GUI element allowing 2D tool path input by clicking.
 * Used for initial testing, probably to be removed later.
 */
class TestDrawCanvas extends Canvas {

	// tool path points
	private List<Integer> pointsX;
	private List<Integer> pointsY;

	// number of points already processed (cannot be deleted from the path except by reset)
	private int fixedSegments = 0;

	// listening to clicks or not
	public boolean enabled = true;

	private GilosGUI gui;

	public TestDrawCanvas(GilosGUI gui){
		this.gui = gui;
		enableEvents(AWTEvent.MOUSE_EVENT_MASK);
		clear(true);
		setBackground(Color.WHITE);
		setSize(340, 300);
	}

	/**
	 * Removes all non-fixed points from the path, so they will not be sent to the device.
	 * With `hard`, fixed points are also removed.
	 */
	public void clear(boolean hard){
		System.out.println("canvas clear("+hard+"), enabled = "+enabled+", size = "+((pointsX == null)? "null" : pointsX.size())+", fs = "+fixedSegments);
		if(!enabled)return;
		if(fixedSegments == 0 || hard){
			pointsX = new ArrayList<Integer>();
			pointsY = new ArrayList<Integer>();
			fixedSegments = 0;
		}
		else{
			for(int i=pointsX.size()-1; i>fixedSegments; i--){
				pointsX.remove(i);
				pointsY.remove(i);
			}
		}
		repaint();
	}

	/**
	 * Removes single unfixed point from the path, so it will not
	 * be sent to the device on next start
	 */
	public void backspace(){
		System.out.println("canvas backspace, enabled = "+enabled+", size = "+pointsX.size()+", fs = "+fixedSegments);
		if(!enabled)return;
		if(fixedSegments == 0 || pointsX.size() > fixedSegments+1){
			pointsX.remove(pointsY.size()-1);
			pointsY.remove(pointsY.size()-1);
			repaint();
		}
	}

	public void processMouseEvent(MouseEvent ev){
		if(!enabled)return;
		if(ev.getID() == MouseEvent.MOUSE_CLICKED){
			System.out.println("canvas click["+ev.getX()+", "+ev.getY()+"], size = "+pointsX.size()+", fs = "+fixedSegments);
			pointsX.add(ev.getX());
			pointsY.add(ev.getY());
			repaint();
			gui.canvasUpdated();
		}
	}

//	public void fixNextSegment(){
//		System.out.println("canvas fixNextSegment, size = "+pointsX.size()+", fs = "+fixedSegments);
//		if(fixedSegments < pointsX.size()-1){
//			fixedSegments++;
//			repaint();
//		}
//	}

	/**
	 * Marks all present path points as fixed.
	 * New unfixed points can be added by clicking after this.
	 */
	private void fixAllSegments(){
		System.out.println("canvas fixAllSegments, size = "+pointsX.size()+", fs = "+fixedSegments);
		if(fixedSegments < pointsX.size()-1){
			fixedSegments = pointsX.size()-1;
			repaint();
		}
	}

	public void paint(Graphics g){
		g.setColor(Color.RED);
		if(pointsX.size() == 1){
			g.fillOval(pointsX.get(0), pointsY.get(0), 5, 5);
		}
		else if(pointsX.size() > 1){
			for(int i=pointsX.size()-1; i!=0; i--){
				if(i == fixedSegments){
					g.setColor(Color.BLACK);
				}
				g.drawLine(
					pointsX.get(i), pointsY.get(i),
					pointsX.get(i-1), pointsY.get(i-1)
				);
			}
		}
	}

	/**
	 * Number of points yet to be sent to the machine ("unfixed")
	 */
	public int remaining(){
		return pointsX.size() - fixedSegments;
	}

	/**
	 * Generates a batch of tool path points to be sent to the machine
	 */
	public double[][] getPoints(double scale, int xIndex, int yIndex){
		System.out.println("canvas getPoints("+scale+", "+xIndex+", "+yIndex+"), size = "+pointsX.size()+", fs = "+fixedSegments);
		Integer[] x = pointsX.toArray(new Integer[pointsX.size()]);
		Integer[] y = pointsY.toArray(new Integer[pointsY.size()]);
		double[][] points = new double[6][x.length-fixedSegments];
		for(int i=fixedSegments; i<x.length; i++){
			points[xIndex][i-fixedSegments] = x[i]*scale;
			points[yIndex][i-fixedSegments] = y[i]*scale;
		}
		fixAllSegments();
		return points;
	}

}

