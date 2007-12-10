
import java.io.*;
import java.util.*;

class                       StreamGobbler extends Thread{
    InputStream             input;

    StreamGobbler(InputStream input){
	this.input = input;
    }

    public void             run(){
	try{
	    String line = null;
	    InputStreamReader streamReader = new InputStreamReader(input);
	    BufferedReader bufferReader = new BufferedReader(streamReader);

	    while ((line = bufferReader.readLine()) != null){
		System.err.println("SG: " + line);
	    }
	} catch (IOException ioe){
	    ioe.printStackTrace();
	}
    }
}
