import java.text.*;
import java.util.*;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.io.*;
import java.nio.*;
import java.net.*;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.DAType;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;

public class RideShare {
	
	//get storage for population, each individual = matrix?
	private static String GHLOCATION = "MapData\\ireland-and-northern-ireland-latest.osm-gh";
	private static String OSMFILELOCATION = "MapData\\ireland-and-northern-ireland-latest.osm.pbf";
	private static int CONVERGENCESIZE = 10; //size of convergence
	private static double RATE = 10;//10 for every km
	private static double PERCENTAGEOFJOURNEY= .75;//rate of passenger pays
	
	private GraphHopper gh = null;
	private HashMap<GHPoint, Integer> locations = new HashMap<GHPoint, Integer>(1000);//not expecting any mor than 1000
	private HashMap<String, Person> riders = null;
	private HashMap<String, Person> drivers = null;
	//passenger pop??
	private String [][][] driverPop = null;//individuals -> list of drivers -> list of passengers
	private String [][][] tempPop = null;//temporary structure for driverPop 
	private String [][] passengerPop = null;////individuals -> list of passengers
	private String [][] tempPassengerPop;//temporary structure for passengerPop
	private String outputFile = null;
	
	private Demo demo = null;
	private PointList [][] routeList = null;
	private Stack<Person> [][] ordering = null;//different ordering for fittest individual
		
	private double [][] distances = null;
	private double [] popFit = null;//the fitness of individuals
	private double [] convergenceFit = new double[CONVERGENCESIZE];
	private double fit = 90000000;//contains highest fitness recored in current pop
	private double maxDist = 0;
	
	private int locIndex = 0;//index of location points
	private int fitIndex = 0;//index of fittest indv
	private long [][] times = null;
	
	public static void main (String [] args){
		String data  = "expr.csv";
		RideShare rs = new RideShare(data);//use args for n,f,m
	}
	
	/*
	 * Sets up parameters needed for algorithm
	 * @param file, the file contains user data
	 */
	public RideShare(String file){//take args instead?
		Person.reset();
		GHDirectory directory = new GHDirectory(GHLOCATION, DAType.RAM);
		EncodingManager em = new EncodingManager("CAR");
		GraphHopperStorage storage = new GraphHopperStorage(directory, em, false);
		gh = new GraphHopper();
		gh.setEncodingManager(em);
		gh.setGraph(storage);
		gh.setGraphHopperLocation(GHLOCATION);
		gh.setOSMFile(OSMFILELOCATION);
		//gh.init(new CmdArgs());//for creating new graphs
		gh.importOrLoad();
		Random rand = new Random();
		for(int i = 0; i < convergenceFit.length; i++){//ensure convergenceFit does not have the same values
			convergenceFit[i] = rand.nextDouble();
		}
		generate(1000, 1, 0.00, file);
	}
	
	/*
	 * Generates population
	 * @param n, size of population
	 * @param f, fraction for crossover
	 * @param m, fraction for mutation
	 * return fittest individual
	 */
	private void generate(int n, double f, double m, String file){
		int turn = 0;
		int x = 0;
		drivers = new HashMap<String, Person>(200);
		riders = new HashMap<String, Person>(200);
		readData(file);
		demo = new Demo(drivers, riders);
		ordering = new Stack[n][drivers.size()];
		demo.callJSFun(null, null, "processing new data", 0);
		
		System.out.println("Processing");
		preprocess();
		System.out.println("Evaluate");
		driverPop = new String [n][drivers.size()][];//max car size //cast int[][][]
		tempPop = new String[n][drivers.size()][];
		tempPassengerPop = new String[n][riders.size()];
		popFit = new double[n];
		passengerPop = new String[n][riders.size()];
		initialize(n);//initialize gen 0
		evaluate();
		demo.callJSFun(ordering[fitIndex], passengerPop[fitIndex], 0+"", fit);
			
		System.out.println("--------------------Gen: " + x + ", Fit: " + fit + "---------------------------------------------------------------");	
			
		//setDataSet(n + " " + f + " "+ m + " flex 40", "test2.csv"); //for outputting experiment data
		//writeData(fit);//for demo
			
		while (!checkConvergence()){
			ordering = new Stack[n][drivers.size()];
			fit = 90000000;//guarantees evaluate will output the best fitness found and not previously best
			x++;
			copy(1 - f, n);
			crossover(f, n);
			mutate(m, n);
			transferPop();
			evaluate();
			demo.callJSFun(ordering[fitIndex], passengerPop[fitIndex], x+"", fit);
			System.out.println("--------------------Gen: " + x + ", Fit: " + fit + "---------------------------------------------------------------");	
			//writeData(fit);
			addFit();
		}
		printInfo();//see what final solution is
	}
	
	/*
	 * Checks if algorithm has converged
	 * @return, boolean indicating if algorithm has converged or not 
	 */
	private boolean checkConvergence(){
		double sum = DoubleStream.of(convergenceFit).sum();
		boolean result = (sum == (convergenceFit[0] * CONVERGENCESIZE));
		return result;
	}
	
	/*
	 * Adds the newest fitness to convergenceFit
	 */
	private void addFit(){
		for(int i = 0; i < convergenceFit.length - 1; i++){
			convergenceFit[i] = convergenceFit[i+1];
		}
		convergenceFit[ convergenceFit.length - 1] = fit;
	}
	
	/*
	 * Sets up generation 0
	 * @param n, number of population
	 */
	private void initialize(int n){
		int cap = 0;
		int selectedRider = 0;
		int selectedDriver = 0;
		int count = 0;//count of drivers
		
		String [] rl =  riders.keySet().toArray(new String[riders.size()]);
		String [] dl =  drivers.keySet().toArray(new String[drivers.size()]);
		
		ArrayList<String> ridersLeft = null;
		ArrayList<String> driversLeft = null;;
		
		Person driver = null;//check distances
		Person rider = null;
		Random rand = new Random();

		for(int i = 0; i < n; i++){//Sets up individuals
			
			driversLeft = new ArrayList<String>(Arrays.asList(dl));
			ridersLeft = new ArrayList<String>(Arrays.asList(rl));
			
			while(!driversLeft.isEmpty()){
				
				selectedDriver = rand.nextInt(driversLeft.size());
				driver = drivers.get(driversLeft.get(selectedDriver));
				driverPop[i][driver.getPos()] = new String [driver.getCap()];
				tempPop[i][driver.getPos()] = new String [driver.getCap()];
				
				for(int j = 0; j < driver.getCap(); j++){
					
					if(!ridersLeft.isEmpty()){ 	
						selectedRider = rand.nextInt(ridersLeft.size());
						rider = riders.get(ridersLeft.get(selectedRider));
						passengerPop[i][rider.getPos()] = driver.getId();
						driverPop[i][driver.getPos()][j] = rider.getId();
						ridersLeft.remove(selectedRider);
					} else {
						driverPop[i][driver.getPos()][j] = "R0";//indicates empty
					}
				}
				driversLeft.remove(selectedDriver);
			}

			while(!ridersLeft.isEmpty()){
				rider = riders.get(ridersLeft.get(0));
				passengerPop[i][rider.getPos()] = "D0";//indicates no driver
				ridersLeft.remove(0);
			}
			//new indv in pop
		}	
		
		//get maximum distances
		for(int i = 0; i < dl.length; i++){
			driver = drivers.get(dl[i]);
			maxDist += distances[driver.getStart()][driver.getEnd()]; 
		}
		
		for(int i = 0; i < rl.length; i++){
			rider = riders.get(rl[i]);
			maxDist += distances[rider.getStart()][rider.getEnd()]; 
		}
	}
	
	/*
	 * Copies 1 - f of the current generation to next generation
	 * @param f, fraction for crossover
	 */
	private void copy(double f, int n){
		int index = 0;
		//System.arraycopy(passengerPop[fitIndex], 0, tempPassengerPop[0], 0, passengerPop[fitIndex].length);
		//System.arraycopy(pop[fitIndex], 0, tempPop[0], 0, pop[fitIndex].length); //elitism approach, uncomment and change i = 1
		
		for(int i = 0; i < Math.ceil(f *n); i++){
			index = tournamentSelect(n);
			System.arraycopy(passengerPop[index], 0, tempPassengerPop[i], 0, passengerPop[index].length);
			System.arraycopy(driverPop[index], 0, tempPop[i], 0, driverPop[index].length); 
		}
		
	}
	
	/*
	 * Method of selecting f from population
	 * @param n, no of population
	 * @return, returns index of member of population
	 */
	private int tournamentSelect(int n){
		
		Random rand = new Random();
		int noCompeting = 5;
		int highIndex = 0;
		int indv = 0;
		double highFit = Double.MAX_VALUE;//ensures that the highest fitness selected is below this
		
		for(int i = 0; i < noCompeting; i++){
			indv = rand.nextInt(n);
			if(highFit >= popFit[indv]){
				highIndex = indv;
				highFit = popFit[indv];
			}
		}
		
		//popFit[highIndex] += 1;//penalise for being selected already
		return highIndex;
	}
	
	/*
	 * Creates offspring based on f amount of passengePpop
	 * @param n, number of individuals
	 * @param f, fraction of pop crossover to be perfromed on○
	 */
	private void crossover(double f, int n){
		Random rand = new Random();
		boolean found = false;
		
		ArrayList<String> holder = new ArrayList<String>();//checks for duplicate points
		String [] passParent1 = new String[riders.size()];
		String [] passParent2 = new String[riders.size()];
		String tempCol = null;
		
		int fract = (int) Math.floor(n * f);//amount to crossover
		int index = (int) Math.ceil(n * (1- f));//amount to index by
		int point = 0;
		int RPOINTS = riders.size()/2;//percentage of point to crossover, change to /4 for 25% etc.
		int indvNo1 = 0;
		int indvNo2 = 0;
		int [] riderCross = new int [RPOINTS];
		
		holder = new ArrayList<String>();
		for(int j = 0; j < RPOINTS; j++){

			while(!found){//get points to crossover
				point = rand.nextInt(riders.size());
				if(!holder.contains(point + "")){
					holder.add(point + "");
					riderCross[j] = point;
					found = true;
				}
			}
			found = false;
		}
		
		for(int i = 0; i < fract; i ++){//crossover

			indvNo1 = tournamentSelect(n);
			System.arraycopy(passengerPop[indvNo1], 0, passParent1, 0, passengerPop[indvNo1].length);//ensure no reference to parent
			indvNo2 = indvNo1;
			
			while(indvNo2 == indvNo1){
				indvNo2 = tournamentSelect(n);
				if(riders.size() < 2 ){
					break;
				}
			}
			
			System.arraycopy(passengerPop[indvNo2], 0, passParent2, 0, passengerPop[indvNo2].length);
			
			for(int j = 0; j < riderCross.length; j++){
				tempCol = passParent1[riderCross[j]];
				passParent1[riderCross[j]] = passParent2[riderCross[j]];
				passParent2[riderCross[j]] = tempCol;
			}
			
			repair(passParent1, (i + index), indvNo1, false);
			
			if((i + 1 + index) < n){//if population full don't take last created child
				i++;
				repair(passParent2, (i+index), indvNo2, false);
			}
		}
	}
	
	/*
	 * Takes a newly created child and ensures that  
	 * @param
	 */
	private void repair(String [] passPopChild, int newIndex, int oldIndex, boolean shuffle){//split repair
		
		Person passenger = null;
		Person driver = null;
		Random rand = new Random();
		int point = 0;
		
		String driverPassPop = null;//driver assigned to a passenger
		String [] rl =  riders.keySet().toArray(new String[riders.size()]);
		String [] dl =  drivers.keySet().toArray(new String[drivers.size()]);
		String [] riderIds = null;
		String [][] corrPop = new String[drivers.size()][];//corresponding driver representation
		
		ArrayList<String> driversLeft = new ArrayList<String>(Arrays.asList(dl));
		ArrayList<String> ridersLeft = new ArrayList<String>(Arrays.asList(rl));
		
		
		//create copy of array
		for(int i = 0; i < driverPop[oldIndex].length; i++){
			corrPop[i] = new String[drivers.get("D" + (i+1)).getCap()];
			System.arraycopy( driverPop[oldIndex][i], 0 ,corrPop[i], 0, driverPop[oldIndex][i].length);	
		}
		
		while(!ridersLeft.isEmpty()){
			passenger = riders.get(ridersLeft.remove(rand.nextInt(ridersLeft.size())));
			riderIds = null;
			driver = null;
			
			driverPassPop = passPopChild[passenger.getPos()];//assignment
			
			if(!driverPassPop.equals("D0")){
				
				driver = drivers.get(driverPassPop);
				riderIds = corrPop[driver.getPos()];
				
				if(!Arrays.asList(riderIds).contains(passenger.getId())){
					
					if(!Arrays.asList(riderIds).contains("R0")){
						
						point = rand.nextInt(riderIds.length);//take out random passenger
						passPopChild[riders.get(riderIds[point]).getPos()] = "D0";
						corrPop[driver.getPos()][point] = passenger.getId();
					
					} else {	
						for(int j = 0; j < riderIds.length; j++){//look for empty seat
							
							if(riderIds[j].equals("R0")){
								corrPop[driver.getPos()][j] = passenger.getId();
								break;
							}
						}
					}
				}	
			}
		}
		
		//fill in empty slots and remove duplicates
		while(!driversLeft.isEmpty()){
			point = rand.nextInt(driversLeft.size());
			driver = drivers.get(driversLeft.remove(point));//check random driver

			for(int j = 0; j < corrPop[driver.getPos()].length; j++){//go through driver's array
				
				if(corrPop[driver.getPos()][j].equals("R0")){//insert random passenger
					if(Arrays.asList(passPopChild).contains("D0")){
						point = getRandomRider(passPopChild);
						corrPop[driver.getPos()][j] = "R" + (point + 1);
						passPopChild[point] = driver.getId();
					}
				} else {//check rider is not duplicate 
					passenger = riders.get(corrPop[driver.getPos()][j]);
					if(!driver.getId().equals(passPopChild[passenger.getPos()])){//duplicate passenger
						corrPop[driver.getPos()][j] = "R0";//set seat to empty
						
						if(Arrays.asList(passPopChild).contains("D0")){//fill in empty seat
							point = getRandomRider(passPopChild);
							corrPop[driver.getPos()][j] = "R" + (point + 1);
							passPopChild[point] = driver.getId();
						}
					}			
				}
			}
		}	
		
		//add to driverPop and passPop */
		for(int i = 0; i < tempPop[newIndex].length; i++){
			tempPop[newIndex][i] = new String[drivers.get("D" + (i+1)).getCap()];
			System.arraycopy(corrPop, 0, tempPop[newIndex], 0, corrPop.length);
		}
		
		tempPassengerPop[newIndex] = new String[riders.size()];
		System.arraycopy(passPopChild, 0, tempPassengerPop[newIndex], 0, passPopChild.length);
	}
	
	/*
	 * Mutates m * n amount of the population
	 * @param m, the fraction of population to mutate
	 * @param n, the size of the population
	 */
	private void mutate(double m, int n){
		int fract = (int) Math.ceil(n*m);
		int driverNo = 0;
		int riderNo = 0;
		int indvNo = 0;
		Random rand = new Random();
		String [] temp = new String[riders.size()];
		
		for(int i = 0; i < fract; i++ ){
			indvNo = rand.nextInt(n);
			System.arraycopy(passengerPop[indvNo], 0, temp, 0, passengerPop[indvNo].length);
			driverNo = rand.nextInt(drivers.size());
			temp[riderNo] = "D" + (driverNo + 1);
			repair(temp, indvNo, indvNo, false);
		}
	}
	
	/*
	 * Evaluates the fitness of each individual in population
	 */
	private void evaluate(){
		
		String [][] indv = null;
		String [] idList = null;
		double totalFit = 0;
			
		for(int i = 0; i < driverPop.length; i++){
			
			indv = driverPop[i];
			
			for(int j = 0; j < indv.length; j++){//each driver
				
				idList = indv[j];
				if(idList.length != 0){
					totalFit += order(idList, drivers.get("D" + (j + 1)), i);
				} else{
					totalFit += 1;//driver drives alone
				}
			}

			totalFit += checkRiders(i);//compute distance for unassigned riders
			popFit[i] = totalFit;
			
			if(fit > totalFit/maxDist){//check for best fitness
				fit = totalFit/maxDist;
				fitIndex = i;
			}	
			
			totalFit = 0;
		}
	}
	
	/*
	 * Finds the best ordering of partial ordering among passengers in ids
	 * @param ids, array of passenger ids assigned to driver
	 * @param driver, Person object of driver
	 * @param indvNo, the number of the individual being checked
	 * @return distance driver travels
	 */
	private double order(String [] ids, Person driver, int indvNo){
		
		ArrayList<Person> people = new ArrayList<Person>();//people for goal node
		
		Stack<Integer> locPath = new Stack<Integer>();
		Stack<Person> currentPath = new Stack<Person>();
		Stack<Person> solutionPath = new Stack<Person>();//keeps track of best solution so found so far
		Stack<Person>  [] agenda = new Stack[(ids.length + 1) *2];//agenda 0 holds 1st tree elements
		Stack<Integer> [] loc = new Stack[(ids.length + 1) *2];//equivalent to agenda, holds locations
		
		Person currP = null;
		Person goal = driver;//cant use loc as passenger might go there too
		
		boolean solution = false;
		
		int index = 0;
		int count = 0;
		int currPLoc = 0;//current person's location
		int prevPLoc = 0;
		int tempLoc =0;
		long currentTime = 0;
		long journeyTime = 0;
		
		double bestDist = 0;//distance of best path found so far
		double currentDist = 0;//distance of current path
		double journeyDist = 0;
		double payment = 0;
		double maxCost = distances[driver.getStart()][driver.getEnd()] * RATE; //Must be less than drivers full drive
		double localMaxDist = distances[driver.getStart()][driver.getEnd()];//best distance of partial solution
		double localBestDist = localMaxDist;
		double temp = localBestDist;
		
		agenda[0] = new Stack();
		loc[0] = new Stack();
		
		for(int i = 0; i < ids.length; i++){//getsPeople
			
			if(riders.containsKey(ids[i])){
				
				currP = riders.get(ids[i]);
				
				if(driver.getST() > currP.getET() || currP.getST() > driver.getET()){//check time window
					
					driverPop[indvNo][driver.getPos()][i] = "R0";
					passengerPop[indvNo][currP.getPos()] = "D0";//drop passsneger times are off
					
				} else {
					
					localMaxDist += distances[currP.getStart()][currP.getEnd()];
					payment += distances[currP.getStart()][currP.getEnd()] * RATE * PERCENTAGEOFJOURNEY;
					agenda[0].push(currP);
					loc[0].push(currP.getStart());
					people.add(currP);
				}
			}
		}
		
		solutionPath.push(driver);
		solutionPath.push(driver);//first solution path, driver drives by themselves
		currentPath.push(driver);
		locPath.push(driver.getStart());
		currentTime = driver.getST();
		bestDist = localMaxDist;
		
		//begin search
		while(!(agenda[0].isEmpty() && index == 0)){//agenda[0] == root
		
			if( agenda[index].isEmpty()){//backtracking || currentTime > bestTime
				
				agenda[index] = null;
				index--;//go back a level
				currentPath.pop();//remove previous person
				tempLoc = locPath.pop();
				currentTime -= times[locPath.peek()][tempLoc];//minus time from current node to previous time
				currentDist -= distances[locPath.peek()][tempLoc];
				
			} else {
				
				currP = agenda[index].pop();
				currPLoc = loc[index].pop();
				prevPLoc = locPath.peek();
				currentPath.peek();
				journeyTime = times[prevPLoc][currPLoc];
				journeyDist = distances[prevPLoc][currPLoc];
				
				if( checkConstraints((journeyTime + currentTime), currP, driver, (currentDist + journeyDist), bestDist, payment)){
					currentTime += journeyTime;
					currentDist += journeyDist;
					locPath.push(currPLoc);
					currentPath.push(currP);
					
					if(goal.equals(currP)){//goal node
						
						ordering[indvNo][driver.getPos()] = copyStack(currentPath);
						solution = true;
						solutionPath = copyStack(currentPath);
						bestDist = currentDist;
						index++;//for back tracking
						agenda[index] = new Stack<Person>();
						loc[index] = new Stack<Integer>();
					
					} else {//compute successors
						if(!solution){
							temp = localBestDist;
							localBestDist = checkForSolution(currentPath, people, driver, currentTime, currentDist, localBestDist);//check for partial solution
							if(temp != localBestDist){//new partial solution
								solutionPath = copyStack(currentPath);
								solutionPath.add(driver);
							}
						}
						
						index++;//increase level
						agenda[index] = new Stack<Person>();
						loc[index] = new Stack<Integer>();
						
						if(checkPath(currentPath, people)){ //if contains path then add goal

							agenda[index].add(driver);
							loc[index].add(driver.getEnd());
							
						} else {//doesn't contain all members of set people
							
							for(int i = 0; i < people.size(); i++){//compute successors
								
								count = Collections.frequency(currentPath, people.get(i));
								
								if(count < 2){//person can only occur twice

									agenda[index].push(people.get(i)); 
									
									if(count == 0){//1st occurrence == start location
										loc[index].push(people.get(i).getStart());
									} else {//2nd occurence == end location
										loc[index].push(people.get(i).getEnd());
									} 
								}
							}
						}	
					}
				}
			}
		}
		
		if(!solution){//no solution found with all users
		
			ordering[indvNo][driver.getPos()] = copyStack(solutionPath);
			for(int i = 0; i < driverPop[indvNo][driver.getPos()].length; i++){
				
				if(!driverPop[indvNo][driver.getPos()][i].equals("R0")){
					currP = riders.get(driverPop[indvNo][driver.getPos()][i]);//rider assigned to position i
					if(!solutionPath.contains(currP)){
						driverPop[indvNo][driver.getPos()][i] = "R0";
						passengerPop[indvNo][currP.getPos()] = "D0";
					}
				}	
			}
			bestDist = localBestDist;
		}
		
		return bestDist;
	}
	
	/*
	 * Checks if partial solution can be found
	 * @param currentPath, the current path being search
	 * @param people, people assigned to driver's carpool
	 * @param driver, the driver that passengers will share with
	 * @param time, the time the path has taken
	 * @param dist, the distance of the path
	 * @param bestDist, the distance
	 * @return the best distance found
	 */
	private double checkForSolution(Stack<Person> currentPath, ArrayList<Person> people , Person driver, long time, double dist, double bestDist){
		
		Stack<Person> temp = new Stack<Person>();
		temp.push(driver);
		
		long newTime = time;
		double newDist = dist;
		double payment = 0;
		
		for(int i = 0; i < currentPath.size(); i++){
			people.remove(currentPath.get(i));//left with people no in carpool
			if(!currentPath.get(i).equals(driver)){
				if(Collections.frequency(currentPath, currentPath.get(i)) != 2){//occurs twice if start and end added
					return bestDist;
				}
				else {
					payment += distances[currentPath.get(i).getStart()][currentPath.get(i).getEnd()] * RATE;//payment for passengers travelling
					temp.add(currentPath.get(i));
				}
			}
		}
		
		payment /= 2;//each person represented twice therefore half payment
		newTime += times[temp.get(temp.size() - 1).getEnd()][driver.getEnd()];
		newDist += distances[temp.get(temp.size() - 1).getEnd()][driver.getEnd()]; 
		
		if(checkConstraints(newTime, temp.get(temp.size() - 1), driver, newDist, bestDist, payment)){
			for(int i = 0; i < people.size(); i++){
				newDist += distances[people.get(i).getStart()][people.get(i).getEnd()];//add distance of people not in partial solution
			}
			if(newDist < bestDist){
				bestDist = newDist;
			}
		}
		
		return bestDist;
	}
	
	/*
	 * Copies a stack orgStack and returns a new stack containg contents of orgStack
	 * @param orgStack, the stack that needs to be copied
	 * @return the copy of orgStack
	 */
	private Stack<Person> copyStack(Stack<Person> orgStack){
		
		Stack<Person> newStack = new Stack<Person>();
		
		for(int i = (orgStack.size()) - 1; i >= 0; i--){
			newStack.push(orgStack.get(i)); 
		}
		
		return newStack;
	}
	
	/*
	 * Return the distance of all the journeys of all the riders listed as D0
	 * @param index of the individual to check
	 * @returns total distance calculated
	 */
	private double checkRiders(int index){
		
		double totalFit = 0;
		Person rider = null;
		
		for(int i = 0; i < passengerPop[index].length; i++){
			
			if(passengerPop[index][i].equals("D0")){
				
				rider = riders.get("R"+(i + 1));
				totalFit += distances[rider.getStart()][rider.getEnd()]; 
			}
		}
		
		return totalFit;
	}
	
	/*
	 * Checks to see if constraints are satisfied
	 * @param time, current time in search
	 * @param currP, the current person being checked
	 * @paramm d, the driver
	 * @param currDist, the current distance in search
	 * @param bestDist, the best distance in search
	 * @param payment, the payment offered by passengers
	 * @return, returns boolean indicating if constraints are satisfied or not.
	 */
	private boolean checkConstraints(long time, Person currP, Person d, double currDist, double bestDist, double payment){
		return (currP.getST() <= time && currP.getET() >= time && time <= d.getET() && currDist <= bestDist //);
				&& currDist * RATE - payment < (distances[d.getStart()][d.getEnd()] * RATE) );
	}
	
	/*
	 * @param currentPath, the current path that has been searched
	 * @param people, the people involved in the carpool
	 * @return, boolean indicating if goal is ready to be added
	 */
	private boolean checkPath(Stack<Person> currentPath, ArrayList<Person> people){
		for(int i = 0; i< people.size(); i++){
			if(Collections.frequency(currentPath, people.get(i)) != 2){
				return false;
			}
		}
		
		return true;
	}
	
	/*
	 * Transfers new generation into the correct data structure
	 */
	private void transferPop(){
		
		/*for(int k = 0; k < tempPop.length; k++ ){
			for(int i = 0; i < tempPop[k].length; i++){
				for(int j = 0; j < tempPop[k][i].length; j++){
					if(!tempPop[k][i][j].equals("R0")){
						if(!("D" + (i + 1)).equals(tempPassengerPop[k][riders.get(tempPop[k][i][j]).getPos()])){
							System.out.println("Count " + k);
							System.out.println("D" + (i + 1)+  " " + tempPassengerPop[k][riders.get(tempPop[k][i][j]).getPos()]);
							System.out.println("Exited before transfer");
							System.exit(0);
						}
					} 
				}
			}
		}*/
		
		for( int i=0; i < driverPop.length; i++){
			passengerPop[i] = new String[riders.size()];
			System.arraycopy(tempPassengerPop[i], 0, passengerPop[i], 0, tempPassengerPop[i].length);
			for( int j =0; j < driverPop[i].length; j++){
				driverPop[i][j] = new String[drivers.get("D" + (j + 1)).getCap()];//need to null to store new elements
				System.arraycopy(tempPop[i][j], 0, driverPop[i][j], 0, tempPop[i][j].length);
			}
		}
	}
	
	/*
	 * Gets a random passenger that is not assigned to a driver.
	 * @param passIndv, the index of the individual
	 * @return index of the unassigned passenger  
	 */
	private int getRandomRider( String [] passIndv){
		
		Random rand = new Random();
		boolean found = false;
		ArrayList<Integer> passengers = new ArrayList<Integer>(); 
		int index = 0; 
		
		if(Collections.frequency(Arrays.asList(passIndv), "R0") == 1){
			return Arrays.asList(passIndv).indexOf("D0");
		}
		
		for(int i = 0; i < passIndv.length; i++){
			if(passIndv[i].equals("D0")){
				passengers.add(i);
			}
		}
		
		index = rand.nextInt(passengers.size());
		
		return passengers.get(index);
	}
	
	/*
	 * Store the location s and return its position
	 * @param s, the location
	 * @return the index that location is stored at
	 */
	private int store(GHPoint s){
		int index = 0;
		if(!locations.containsKey(s)){
			locations.put(s, locIndex);
			index = locIndex;
			locIndex++;
		} else{
			index = locations.get(s);
		}
		return index;
	}
	
	/*
	 * Populate times and distance between points
	 */
	private void preprocess(){
		GHPoint p1;
		GHPoint p2;
		GHRequest request;
		GHResponse response;
		GHPoint [] locs = (GHPoint[]) locations.keySet().toArray(new GHPoint[locations.size()]);
		PointList [][] list = new PointList[locations.size()][locations.size()];
		
		times = new long[locations.size()][locations.size()];
		distances = new double[locations.size()][locations.size()];
		
		for(int i = 0; i < locations.size(); i++){
			
			p1 = locs[i];
			for(int j = 0; j < locations.size(); j++){
				p2 = locs[j];
				request = new GHRequest(p1,p2);
				response = gh.route(request);
				list[locations.get(p1)][locations.get(p2)] = response.getPoints();//store route info
				times[locations.get(p1)][locations.get(p2)] = response.getMillis();
				distances[locations.get(p1)][locations.get(p2)] = response.getDistance();
			}
		}
		
		demo.setPointList(list);
	}
	
	/*
	 * Print info of fittest solution
	 */
	private void printInfo(){
		String [][] fitSol = driverPop[fitIndex];
		
		for(int i= 0; i < fitSol.length; i++ ){
			System.out.println("D" + (i + 1) + " ");
			for(int j=0; j < fitSol[i].length; j++){
				System.out.print(" - " + fitSol[i][j]);
			}
			System.out.println();
		}
		System.out.println("");
		
		System.out.println();
		for(int i= 0; i < passengerPop[fitIndex].length; i++ ){
			System.out.println("R" + (i + 1) + " " + passengerPop[fitIndex][i]);
		}
		
		System.out.println("Match ---------");
		for(int i = 0; i < driverPop[fitIndex].length; i++){
			System.out.println("D" +(i + 1)+ " ");
			for(int j = 0; j < driverPop[fitIndex][i].length; j++){
				
				System.out.print(" -" + driverPop[fitIndex][i][j]);
				if(!driverPop[fitIndex][i][j].equals("R0")){
					System.out.print(" - " + (("D" + (i + 1)).equals(passengerPop[fitIndex][riders.get(driverPop[fitIndex][i][j]).getPos()]))+ " | ");
				}	
			}
			System.out.println();
		}
		System.out.println(" - End ");
		
		System.out.println("Riders");
		for(int j = 0; j < passengerPop[fitIndex].length; j++){
			
			System.out.print(" -" + passengerPop[fitIndex][j]);
			if(!passengerPop[fitIndex][j].equals("D0")){
				System.out.print(" - " + (Arrays.asList(driverPop[fitIndex][drivers.get(passengerPop[fitIndex][j]).getPos()]).contains("R" + (j + 1)) + " | "));
			}	
		}
	}
	
	/*
	 * Reads in data file
	 * @param file, file to be read
	 */
	private void readData(String file){
		BufferedReader br = null;
		String line = "";
		String cvsSplitBy = ",";
		String [] data= null;
		
		Person p = null;
		
		DateFormat formatter = new SimpleDateFormat("HH:mm");
		Date stime = null;
		Date etime = null;
		
		try {
			
			br = new BufferedReader(new FileReader(file));
			line = br.readLine();//consume header line
			while ((line = br.readLine()) != null) {

				data = line.split(cvsSplitBy);
				stime = (Date)formatter.parse(data[1]);
				etime = (Date)formatter.parse(data[2]);
				p = new Person(data[0], store(GHPoint.parse(data[3].replace("|", ","))), store(GHPoint.parse(data[4].replace("|", ","))),
						stime.getTime(), etime.getTime(), Integer.parseInt(data[5]));
				
				if(data[0].equals("D")){
					drivers.put(p.getId(), p);
				} else {
					riders.put(p.getId(), p);
				}
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
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/*
	 * For experiments
	 * @param title, title of experiment
	 * @param file to output to
	 */
	private void setDataSet(String title, String file){
			outputFile = file;
			PrintWriter out;
			System.out.println(title);
			try {
				out = new PrintWriter(new FileWriter(outputFile, true));
				out.println(title + ":");
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		
	}
	
	/*
	 * Write fitness to file
	 * @param fit, fittest solution
	 */
	private void writeData(double fit){
		try {
			PrintWriter out = new PrintWriter(new FileWriter(outputFile, true));
			out.println(fit + ",");
			out.close();
		} catch(IOException e){
			
		}
	}
}
