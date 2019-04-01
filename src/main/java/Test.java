import java.awt.Graphics2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Stack;
import java.util.stream.Collectors;

import com.graphhopper.*;
import com.graphhopper.http.GHServer;
import com.graphhopper.http.GraphHopperWeb;
import com.graphhopper.reader.OSMElement;
import com.graphhopper.reader.OSMInputFile;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.storage.DAType;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.ui.MiniGraphUI;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;


public class Test {

	public static void main(String[] args) {
		String ghLocation = "MapData\\CorkCity-gh";
		String osmFileLocation = "MapData\\CorkCity.osm"; 
		try {
			GHResponse response = null;
			GHRequest request = null;
			GHDirectory directory = new GHDirectory(ghLocation, DAType.RAM);
			EncodingManager em = new EncodingManager("CAR");
			GraphHopperStorage storage = new GraphHopperStorage(directory, em, false);
			GraphHopper gh = new GraphHopper();
			gh.setEncodingManager(em);
			gh.setGraph(storage);
			gh.setGraphHopperLocation(ghLocation);
			gh.setOSMFile(osmFileLocation);
			System.out.println(gh.getGraphHopperLocation());
			System.out.println(gh.getGraph());
			System.out.println(gh.getOSMFile());
			System.out.println(gh.getEncodingManager());
			System.out.println(gh.importOrLoad());
			request = new GHRequest(new GHPoint(51.8915293,-8.5048495),new GHPoint(51.8828855,-8.5105741));
			//set request algorithm
			//set requ est vechicle
			response = gh.route(request);
			System.out.println(response);
			//draw roads consists of ways?
			//draw river also consists of ways
			//name features
			
			//boolean debug = new CmdArgs().getBool("minigraphui.debug", false);
	       // MiniGraphUI graph = new MiniGraphUI(gh, true);
	        //graph.visualize();
	        request = new GHRequest(new GHPoint(51.8915293,-8.5048495),new GHPoint(51.8828855,-8.5105741));
	        response = gh.route(request);
	        System.out.println(response.getPoints().size());
	        PointList p = response.getPoints();
	        System.out.println("Dist: " + p.getLat(0) + " " + p.getLon(0));
	        //p = response.getPoints()..remove(0);
	        System.out.println("Dist: "  + p.getLat(1) + " " + p.getLon(1));
	        //graph.plotPath(response.getPoints(), new Graphics2D(), 100);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
