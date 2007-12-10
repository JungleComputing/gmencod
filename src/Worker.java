
import java.io.*;
import java.lang.*;
import java.util.*;

class			Worker{

    public static void	main(String args[]){
	Runtime		run;
	Process		proc;
	String		chunkName;
	String		encodedChunkName;

	if (args.length != 2){

	    System.err.println("Expecting 2 parameters: chunkName " +
			       "encodedChunkName; received " +
			       args.length);

	    for (int i=0;i<args.length;i++) { 
		System.err.println(" " + i + " -> " + args[i]);     
	    }
	    System.exit(2);
	}
		
	chunkName = args[0];
	encodedChunkName = args[1];

	/* Start computation */
	try {
	    run = Runtime.getRuntime();

	    //MEAS
	    long start = System.currentTimeMillis(); 
	    proc = run.exec(new String [] {"mencoder", chunkName, "-o",
         		   encodedChunkName, "-ovc", "lavc", "-oac", "lavc"});

	    //System.err.println(System.getProperty("gmencod.lib"));
	    
	    StreamGobbler errorGobbler =
		new StreamGobbler(proc.getErrorStream());
            StreamGobbler outputGobbler =
                new StreamGobbler(proc.getInputStream());

            errorGobbler.start();
            outputGobbler.start();

	    proc.waitFor();
	    if (proc.exitValue() != 0){
		System.err.println("Could not encode file " + chunkName);
		System.exit(2);
	    }

	    //MEAS
	    long end = System.currentTimeMillis();
	    System.out.println("worker computation time: " + (end-start));

	/* Computation done */
	} catch (Throwable t) {
	    t.printStackTrace();
	}
    }
}
