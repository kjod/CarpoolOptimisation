import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.swing.JOptionPane;

import com.graphhopper.http.GHServer;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.PointList;


public class Demo {
	
	private PointList [][] pointList = null;
	private HashMap<String, Person> drivers = null;
	private HashMap<String, Person> riders = null;
	private String cord_loc = "cord.txt";
    private String gen_loc = "gen.txt";
    private String fitness_loc = "fitness.txt";
    
	public Demo(HashMap<String, Person> drivers, HashMap<String, Person> riders){
		this.drivers = drivers;
		this.riders = riders;
		
		//this.pointList = //new PointList[drivers.size()+ riders.size()][drivers.size()+ riders.size()];
	}
	
	public void writeToFile(String cord, String gen, double fit){
		try{
			PrintWriter writer = new PrintWriter(cord_loc, "UTF-8");
			writer.print("");
			writer.println(cord);
			writer.close();
			writer = new PrintWriter(gen_loc, "UTF-8");
			writer.print("");
			writer.println(gen + "");
			writer.close();
			writer = new PrintWriter(fitness_loc, "UTF-8");
			writer.print("");
			writer.println(fit + "");
			writer.close();
		} catch (Exception e){
			
		}
	}
	
	public void setPointList(PointList [][] list){
		pointList = list;
	}
	
	public void callJSFun(Stack<Person> [] order, String [] passengerPop, String gen, double fit){
		ScriptEngineManager manager = new ScriptEngineManager();//fix
		ScriptEngine engine = manager.getEngineByName("JavaScript");
		Invocable inv = (Invocable) engine;
		Stack<Person> ordering = null;
		ArrayList<Person> checked = new ArrayList<Person>();
		String cord = "";
		//String [][] cord = new String[order.length][];
		int prevP;
		Person currP;
		int p1 = 0;
		int p2 = 0;
		if(!(passengerPop == (null) || order == (null))){
		for(int i = 0; i < order.length; i++){
			ordering = order[i];
			prevP = ordering.get(0).getStart();
			for(int j = 1; j < ordering.size(); j++){//for each driver
				currP = ordering.get(j);
				if(checked.contains(currP)){//getEnd
					cord += getPoints(prevP, currP.getEnd());
					prevP = currP.getEnd();
				} else {//getStart
					cord += getPoints(prevP, currP.getStart());
					prevP = currP.getStart();
					checked.add(currP);
				}
			}
			cord += "?";
			//System.out.println(cord);
		}
		
		for(int i = 0; i < passengerPop.length; i++ ){
			if(passengerPop.equals("D0")){
				currP = riders.get("R" + (i+1));
				cord += getPoints(currP.getStart(),currP.getEnd());
				cord += "?";
			}		
		}
		} else {
			cord ="";
		}
		writeToFile(cord, gen, fit);
	}
	
	private String getPoints(int s, int e){
		//getLonLat
		String points = new String();
		PointList list = pointList[s][e];
		if(list != null){
			
		for(int i = 0; i < list.size(); i++){
			points += list.getLon(i) + "," +list.getLat(i) + "!";//for each point
			//System.out.println(list.getLon(i) + "|" +list.getLat(i));
		}
		}
		return points;
	}
}
