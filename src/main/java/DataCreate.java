import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.util.shapes.GHPoint;

public class DataCreate {
	
	private double lonMin = 0;
	private double lonMax = 100;
	private double latMin = 0;
	private double latMax = 100;
	private Random rand = new Random();
	
	private GraphHopper gh;
	
	/**
	 * Sets longitude and latitude limits
	 * @param minLon, minimum longitude
	 * @param maxLon, maximum longitude
	 * @param minLat, minimum latitude
	 * @param maxLat, maximum latitude
	 * @param gh, GraphHopper instance
	 */
	public DataCreate(double minLon, double maxLon, double minLat, double maxLat, GraphHopper gh){
		setGH(gh);
		setLon(minLon, maxLon); //51.885355,-8.6057851 ballincollig
		setLat(minLat, maxLat);
	}
	
	public static void main (String [] args){
		System.out.println("Type 0 to create new data, 1 to fix data");
		Scanner scan = new Scanner(System.in);
		String input =  scan.next();
		//dc = new DataCreate(-8.65864, -8.35128, 51.8242665, 51.9177092, gh);
		//dc.createData(300, 0, 0, "Rates.csv");
				//dc.fixData("Flex0mins.csv", "Flex40mins.csv");
		if(input.equals("0")){
			
		} else if(input.equals("1")) {
			
		} else {
			System.out.println("Not valid command");
		}
	}
	
	/**
	 * Creates random data
	 * @param n, number of users to create
	 * @param fd, fraction of drivers among users
	 * @param fs, fraction of shifters among users
	 * @param file, file file to output data to
	 */
	public void createData(int n, double fd, double fs, String file){
		if(fd < fs){
			System.out.println("error fd < fs");//raise exception
		} else {
			
			boolean shift = false;
			int total = (int) (n * (1 - fd)), i;
			GHPoint start;
			GHPoint finish;
			Person p;
			PrintWriter writer = null;
			try{
				 writer = new PrintWriter(file);
				 writer.println("Role,Start Time,End Time,Start Loc,End Loc,Cap,");
			} catch(Exception e){
			
			}
			
			for(i = 0; i < total; i++){//riders
				start = createRandPoint();
				finish = createRandPoint();
				
				try{
					writer.println("R,"+createRandTime() +",?," + start.getLat() + "|" + start.getLon() + "," + finish.getLat() + "|" + finish.getLon() + ",0");
				} catch(Exception e){
					
				}
				
				//riders.put(p.getId(), p);
			}
			
			i = total;
			total =(int) (n * fd);
			
			for(i = 0; i < total; i++){//drivers and shifters
				start = createRandPoint();
				finish = createRandPoint();
				writer.println("D," + createRandTime() + ",?," + start.getLat() + "|" + start.getLon() + "," + finish.getLat() + "|" + finish.getLon() + ",0");
			}
			writer.close();
		}	
		
	}
	
	public void setLon(double minLon, double maxLon){
		lonMin = minLon;
		lonMax = maxLon;
	}
	
	public void setLat(double minLat, double maxLat){
		latMin = minLat;
		latMax = maxLat;
		
	}
	
	public void setGH(GraphHopper graphhopper){
		gh = graphhopper;
	}
	
	/**
	 * Creates random point
	 * @return, GHPoint
	 */
	public GHPoint createRandPoint(){//throws error if lat lon not set
		double lat;
		double lon;
		GHPoint p = null;
		GHRequest request;
		GHResponse response;
		boolean result = false;
		
		while(!result){
			lon = lonMin + (lonMax - lonMin) * rand.nextDouble();
			lat = latMin + (latMax - latMin) * rand.nextDouble();
			p = new GHPoint(lat, lon);
			request = new GHRequest(p,p);
			response = gh.route(request);
			result = response.isFound();
		}
		return p;
	}
	
	public String createRandTime(){
		String result = "";
		String hour = null;
		String minute = null;
		try {
			int x = rand.nextInt(25);
			hour = (x < 10)? "0" + x : x + "";
			x = rand.nextInt(60);		
			minute = (x < 10)? "0" + x : x + "";
			DateFormat formatter = new SimpleDateFormat("HH:mm");
			Date dtime = (Date)formatter.parse(hour + ":" + minute);
			result = formatter.format(dtime );
			//result = dtime.getTime();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return result;//(result);
	}
	
	public long createFixedTime(String time){
		long result = 0;
		try {
			DateFormat formatter = new SimpleDateFormat("HH:mm");
			Date dtime = (Date)formatter.parse(time);
			result = dtime.getTime();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return (result);
	}
	
	/**
	 * Fixes data, creates new end times and capacities
	 * @param input, input file
	 * @param output, output file
	 */
	public void fixData(String input, String output){

		String cvsSplitBy = ",";
		String [] data = null;
		String line = null;
		Person p = null;
		DateFormat formatter = new SimpleDateFormat("HH:mm");
		Random rand = new Random();
		GHRequest request = null;
		GHResponse response = null;
		PrintWriter writer = null;
		BufferedReader br = null;
		int driverTime = 60;//one hour 40
		int riderTime = 40;//30 mins 20
		double journeyTime = 0;
		Calendar cl = null;
		
		try {
			//driverTime = (Date)formatter.parse("01:00");//30 mins
			//riderTime = (Date)formatter.parse("00:30");//15 mins
			writer = new PrintWriter(output);
			br = new BufferedReader(new FileReader(input));
			while ((line = br.readLine()) != null) {
			        // use comma as separator
				data = line.split(cvsSplitBy);
				
				if(data[2].equals("?")){//if no end time given giveon based on role and detour
					request = new GHRequest(GHPoint.parse(data[3].replace("|", ",")),GHPoint.parse(data[4].replace("|", ",")));
					response = gh.route(request);
					if(data[0].equals("D")){
						System.out.println("before " + data[2]);
						cl = Calendar.getInstance();
					    cl.setTime(formatter.parse(data[1]));
					    cl.add(Calendar.MINUTE, (int) (driverTime + TimeUnit.MILLISECONDS.toMinutes(response.getMillis())));
					    data[2] = formatter.format(cl.getTime());            
						System.out.println("after " + data[2]);
						if(Integer.parseInt(data[5]) ==  0){//give capacity if driver
							System.out.println("tre");
							data[5] = String.valueOf(rand.nextInt(4) + 1);
						}
					} else {
						cl = Calendar.getInstance();
					    cl.setTime(formatter.parse(data[1]));
					    cl.add(Calendar.MINUTE, (int) (riderTime + TimeUnit.MILLISECONDS.toMinutes(response.getMillis())));
					    data[2] = formatter.format(cl.getTime());
					}
				}	
				
				writer.println(data[0]+ ","+ data[1] + "," + data[2] + "," + data[3] + "," + data[4] + "," + data[5]);
					
					//riders.put(p.getId(), p);
				
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}
	
	@Override
	public String toString(){
		return ("latMin: " + latMin + "\nlatMax: " + latMax + "\nlonMin: " + lonMin + "\nlonMax: " + lonMax); 
	}
	
}
