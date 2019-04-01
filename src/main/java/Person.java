import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import com.graphhopper.util.shapes.GHPoint;

public class Person {
		private static int riderIdGen = 1;
		private static int driverIdGen = 1;
		private static int shiftIdGen = 1;
		public static int totalCap = 0;
	
		private int startPoint;
		private int endPoint;
		private int pos;
		private int cap;

		private long st;		
		private long et;
		private String role;
		private String id;
		private boolean enableShift = false;
		
		/**
		 * Sets up person object
		 * @param role
		 * @param startPoint
		 * @param endPoint
		 * @param st, start time
		 * @param et, end time
		 * @param cap, capacity of driver
		 */
		public Person(String role, int startPoint, int endPoint, long st, long et, int cap){
			this.startPoint = startPoint;
			this.endPoint = endPoint;
			role = role;
			if(role.equals("D")){//too much repetition
				id = "D" + driverIdGen;
				pos = driverIdGen - 1;
				driverIdGen++;
				enableShift = false;
				this.cap = cap;
				totalCap += cap + 1;//include driver
			} else if(role.equals("R")){
				id = "R"+ riderIdGen;
				pos = riderIdGen - 1;
				riderIdGen++;
				enableShift = false;
			} else {
				id = "S"+ shiftIdGen;
				pos = shiftIdGen - 1;
				shiftIdGen++;
				enableShift = true;
				this.cap = cap;
				totalCap += cap + 1;//include driver
				//assigned variable
			}
			this.st = st;
			this.et = et;
		}
		
		/**
		 * Resets static variable
		 */
		public static void reset(){
			riderIdGen = 1;
			driverIdGen = 1;
			shiftIdGen = 1;
			totalCap = 1;
		}
		
		public String getId(){
			return id;
		}
		
		public String getRole(){
			return role;
		}
		
		public int getPos(){
			return pos;
		}
		
		public int getCap(){
			return cap;
		}
		
		public int getStart(){
			return startPoint;
		}
		
		public int getEnd(){
			return endPoint;
		}
		
		public long getST(){
			return st;
		}
		
		public long getET(){
			return et;
		}
		
		@Override
		public String toString(){
			return "Role: " + getRole() + " Id: " + getId();
		}
}
