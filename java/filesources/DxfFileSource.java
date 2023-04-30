package filesources;

import java.util.Iterator;
import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.kabeja.parser.*;
import org.kabeja.dxf.*;
import org.kabeja.dxf.helpers.*;

/**
 * A tool path point source reading data from an Autodesk DXF file
 */
public class DxfFileSource extends FileSource {

	private static final int circleSegmentCoef = 10;
	private static final int minCircleSegments = 10;

	private String path;

	private DXFDocument dxf;
	private boolean isXY;
	private int numPoints, numJumps;

	private boolean pointsSent;

	// Optional insertion of point {0,0,0} at the beginning of path.
	// When enabled, running the file from position {0,0,0} will result in
	// the picture drawn at coordinates used in the file.
	// Disabled upon customer request
	private final boolean startAt0 = false;

	public boolean isXYZ(){
		return true;
	}

	public boolean is2D(){
		return isXY;
	}

	private ArrayList<DXFEntity> getEntityList(){
		var entities = new ArrayList<DXFEntity>();

		Iterator layers = dxf.getDXFLayerIterator();
		int numLayers = 0;
		while(layers.hasNext()){
			DXFLayer layer = (DXFLayer)layers.next();
			System.out.println("DXF: layer "+layer.getName());
			numLayers++;

			int numEntities = 0;
			Iterator types = layer.getDXFEntityTypeIterator();
			while(types.hasNext()){
				String type = (String)types.next();
				Iterator layerEntities = layer.getDXFEntities(type).iterator();
				while(layerEntities.hasNext()){
					entities.add((DXFEntity)layerEntities.next());
					numEntities++;
				}
			}
			System.out.println("DXF: found "+numEntities+" entities in layer");
		}

		Iterator blocks = dxf.getDXFBlockIterator();
		int numBlocks = 0;
		while(blocks.hasNext()){
			DXFBlock block = (DXFBlock)blocks.next();
			System.out.println("DXF: block "+block.getName());
			numBlocks++;

			int numEntities = 0;
			Iterator blockEntities = block.getDXFEntitiesIterator();
			while(blockEntities.hasNext()){
				entities.add((DXFEntity)blockEntities.next());
				numEntities++;
			}
			System.out.println("DXF: found "+numEntities+" entities in block");
		}

		System.out.println("DXF: found "+entities.size()+" entities in "
				+numLayers+" layers and "+numBlocks+" blocks");
		return entities;
	}

	public DxfFileSource(String path, double zOff) throws IOException {
		super(Paths.get(path).getFileName().toString());
		this.path = path;
		setZ2DOffset(zOff);

		pointsSent = false;
		isXY = true;
		numPoints = 0;
		numJumps = 0;

		double[] lastPoint = new double[3];
		boolean firstEntity = true;

		dxf = null;
		Parser parser = ParserBuilder.createDefaultParser();
		try{
			parser.parse(path);
		}
		catch(ParseException ex){
			System.err.println(ex);
			throw new IOException("Cannot parse file");
		}
		dxf = parser.getDocument();
		System.out.println("DXF document loaded");

		Iterator entities = getEntityList().iterator();
		while(entities.hasNext()){
			DXFEntity entity = (DXFEntity)entities.next();

			double[][] entityPoints = entity2Points(entity);
			int numPt = entityPoints[0].length;
			if(numPt == 0)continue;

			if(Math.abs(lastPoint[0]-entityPoints[0][0]) > 1e-3
			|| Math.abs(lastPoint[1]-entityPoints[1][0]) > 1e-3
			|| Math.abs(lastPoint[2]-entityPoints[2][0]) > 1e-3){
				if(startAt0 || !firstEntity)numJumps++;
			}
			lastPoint[0] = entityPoints[0][numPt-1];
			lastPoint[1] = entityPoints[1][numPt-1];
			lastPoint[2] = entityPoints[2][numPt-1];

			numPoints += numPt;
			for(int i=0; i<numPt; i++){
				if(entityPoints[2][i] != 0)isXY = false;
			}

			firstEntity = false;
		}
	}


	public DxfFileSource duplicate() throws IOException{
		return new DxfFileSource(path, z2DOffset);
	}


	private double[][] entity2Points(DXFEntity entity){

		if(entity instanceof DXFLine){
			DXFLine line = (DXFLine)entity;
			double[][] points = new double[3][2];

			Point pt = line.getStartPoint();
			points[0][0] = pt.getX();
			points[1][0] = pt.getY();
			points[2][0] = pt.getZ();

			pt = line.getEndPoint();
			points[0][1] = pt.getX();
			points[1][1] = pt.getY();
			points[2][1] = pt.getZ();

			return points;
		}

		else if(entity instanceof DXFMLine){
			DXFMLine mline = (DXFMLine)entity;
			int numSegments = mline.getDXFMLineSegmentCount();
			if(numSegments <= 0)return new double[3][0];

			double[][] points = new double[3][2*numSegments];
			DXFMLineSegment segment;
			Point pt;
			Vector dir;
			for(int i=0; i<numSegments; i++){
				segment = mline.getDXFMLineSegment(i);

				pt = segment.getStartPoint();
				points[0][i << 1] = pt.getX();
				points[1][i << 1] = pt.getY();
				points[2][i << 1] = pt.getZ();

				dir = segment.getDirection();
				points[0][(i << 1) + 1] = points[0][i << 1] + dir.getX();
				points[1][(i << 1) + 1] = points[1][i << 1] + dir.getY();
				points[2][(i << 1) + 1] = points[2][i << 1] + dir.getZ();
			}

			return points;
		}

		else if(entity instanceof DXFCircle){
			DXFCircle circ = (DXFCircle)entity;

			// keep area between line segment and ideal circle segment below some limit
			double approxSegments = Math.pow(circ.getRadius(), 2.0/3) * circleSegmentCoef;
			int numSegments = Math.max((int)approxSegments, minCircleSegments);

//			// numSegments formula debugging
//			double theta = 2*3.141592653589793238/numSegments;
//			double area = R*R/2.0*(theta-Math.sin(theta));
//			System.out.println("R = "+circ.getRadius()+", "+numSegments+" segments, error area = "+area);

			double[][] points = new double[3][numSegments+1];

			Point pt = circ.getPointAt(0);
			points[0][0] = points[0][numSegments] = pt.getX();
			points[1][0] = points[1][numSegments] = pt.getY();
			points[2][0] = points[2][numSegments] = pt.getZ();

			for(int pointInd=1; pointInd<numSegments; pointInd++){
				pt = circ.getPointAt(360.0*pointInd/numSegments);
				points[0][pointInd] = pt.getX();
				points[1][pointInd] = pt.getY();
				points[2][pointInd] = pt.getZ();
			}

			return points;
		}

		else if(entity instanceof DXFArc){
			DXFArc arc = (DXFArc)entity;

			double ang0 = arc.getStartAngle();
			double ang1 = arc.getEndAngle();
			if(arc.isCounterClockwise()){
				if(ang0 <= ang1)ang0 += 360;
			}
			else{
				if(ang1 <= ang0)ang1 += 360;
			}

			// keep area between line segment and ideal circle segment below some limit
			double approxSegments = Math.abs(arc.getTotalAngle()) / 360.0 * Math.pow(arc.getRadius(), 2.0/3) * circleSegmentCoef;
			int numSegments = Math.max((int)approxSegments, minCircleSegments);

			double[][] points = new double[3][numSegments+1];

			Point pt = arc.getStartPoint();
			points[0][0] = pt.getX();
			points[1][0] = pt.getY();
			points[2][0] = pt.getZ();

			pt = arc.getEndPoint();
			points[0][numSegments] = pt.getX();
			points[1][numSegments] = pt.getY();
			points[2][numSegments] = pt.getZ();

			for(int pointInd=1; pointInd<numSegments; pointInd++){
				double ang = ang0 + (ang1-ang0)*pointInd/numSegments;
				pt = arc.getPointAt(ang % 360);
				points[0][pointInd] = pt.getX();
				points[1][pointInd] = pt.getY();
				points[2][pointInd] = pt.getZ();
			}

			return points;
		}

		else if(entity instanceof DXFEllipse){
			DXFEllipse ell = (DXFEllipse)entity;

			double param0 = ell.getStartParameter();
			double param1 = ell.getEndParameter();
			if(ell.isCounterClockwise()){
				if(param0 <= param1)param0 += 2*Math.PI;
			}
			else{
				if(param1 <= param0)param1 += 2*Math.PI;
			}

			// adapted numSegments formula from DXFArc -- good enough for narrow ellipses?
			double approxR = ell.getLength() / Math.abs(param1-param0) / (2*Math.PI);
			double approxSegments = Math.abs(param1-param0) / (2*Math.PI) * Math.pow(approxR, 2.0/3) * circleSegmentCoef;
			int numSegments = Math.max((int)approxSegments, minCircleSegments);

			double[][] points = new double[3][numSegments+1];

			Point pt = ell.getPointAt(ell.getStartParameter());
			points[0][0] = pt.getX();
			points[1][0] = pt.getY();
			points[2][0] = pt.getZ();

			pt = ell.getPointAt(ell.getEndParameter());
			points[0][numSegments] = pt.getX();
			points[1][numSegments] = pt.getY();
			points[2][numSegments] = pt.getZ();

			for(int pointInd=1; pointInd<numSegments; pointInd++){
				double param = param0 + (param1-param0)*pointInd/numSegments;
				pt = ell.getPointAt(param % (2*Math.PI));
				points[0][pointInd] = pt.getX();
				points[1][pointInd] = pt.getY();
				points[2][pointInd] = pt.getZ();
			}

			return points;
		}

		else{
			System.err.println("DXF:   Cannot process DXF entity of type "+entity.getType());
			return new double[3][0];
		}
//		else throw new RuntimeException("Cannot process DXF entity of type "+entity.getType());
	}

	public boolean started(){
		return pointsSent;
	}
	public boolean finished(){
		return pointsSent;
	}

	private double z2DOffset = 10.0;
	public void setZ2DOffset(double value){
		System.out.println("setting z offset to "+value);
		z2DOffset = value;
	}

	public double[][] getPoints(boolean preview) throws IOException{
		if(dxf == null){
			throw new RuntimeException("No DXF data available");
		}

		double z2DOffset = preview? 1 : this.z2DOffset;

		int recalcPoints;
		if(isXY){ // 2D image => raise Z axis between unconnected entities
			recalcPoints = startAt0? numPoints+2*numJumps+2 : numPoints+2*numJumps+1;
		}
		else{
			recalcPoints = startAt0? numPoints+1 : numPoints;
		}
		double[][] points = new double[3][recalcPoints];

		// finding points

		int ptInd = startAt0? 1 : 0; // startAt0 => point 0 = coordinate origin
		double[] lastPoint = new double[3];
		boolean firstEntity = true;

		Iterator entities = getEntityList().iterator();
		while(entities.hasNext()){
			DXFEntity entity = (DXFEntity)entities.next();

			double[][] entityPoints = entity2Points(entity);
			int numPt = entityPoints[0].length;
			if(numPt == 0)continue;

			// making a jump
			if(isXY && (startAt0 || !firstEntity) && (
			   Math.abs(lastPoint[0]-entityPoints[0][0]) > 1e-3
			|| Math.abs(lastPoint[1]-entityPoints[1][0]) > 1e-3
			|| Math.abs(lastPoint[2]-entityPoints[2][0]) > 1e-3
			)){
				points[0][ptInd] = lastPoint[0];
				points[1][ptInd] = lastPoint[1];
				points[2][ptInd] = lastPoint[2] + z2DOffset;
				ptInd++;
				points[0][ptInd] = entityPoints[0][0];
				points[1][ptInd] = entityPoints[1][0];
				points[2][ptInd] = entityPoints[2][0] + z2DOffset;
				ptInd++;
			}

			lastPoint[0] = entityPoints[0][numPt-1];
			lastPoint[1] = entityPoints[1][numPt-1];
			lastPoint[2] = entityPoints[2][numPt-1];

			for(int i=0; i<numPt; i++){
				points[0][ptInd] = entityPoints[0][i];
				points[1][ptInd] = entityPoints[1][i];
				points[2][ptInd] = entityPoints[2][i];
				ptInd++;
			}

			firstEntity = false;
		}

		if(isXY && ptInd > 0){
			points[0][ptInd] = points[0][ptInd-1];
			points[1][ptInd] = points[1][ptInd-1];
			points[2][ptInd] = points[2][ptInd-1] + z2DOffset;
			ptInd++;
		}

		pointsSent = true;
		return points;
	}

	public void close(){
		// ?? does something need to be closed?
	}

}

