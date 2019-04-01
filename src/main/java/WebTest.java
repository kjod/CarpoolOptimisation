import com.graphhopper.GHRequest;
import com.graphhopper.http.GHServer;
import com.graphhopper.http.GraphHopperWeb;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.shapes.GHPoint;

public class WebTest {

	public static void main( String [] args){
		try {
			args = new String[6];
			args[0] = "map_data\CorkCity.osm";
			args[1] = "CAR";
			CmdArgs cmd = CmdArgs.readFromConfig("config.properties", "graphhopper.config");
			new GHServer(cmd).start();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		/*GraphHopperWeb gh = new GraphHopperWeb();
		gh.load("http://localhost:8989/route");
		GHRequest request = new GHRequest(new GHPoint(51.8915293,-8.5048495),new GHPoint(51.8828855,-8.5105741));
		System.out.println(gh.route(request));*/
	}
}
