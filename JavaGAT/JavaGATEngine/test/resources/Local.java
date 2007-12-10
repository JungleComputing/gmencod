package resources;

import java.util.Hashtable;

import org.gridlab.gat.*;
import org.gridlab.gat.io.*;
import org.gridlab.gat.resources.*;

public class Local { 

	public static void main(String [] args) throws Exception { 
		
		GATContext context = new GATContext();
	
		File stdout = GAT.createFile(context, new URI("file:///stdout"));
		File stderr = GAT.createFile(context, new URI("file:///stderr"));

		SoftwareDescription sd = new SoftwareDescription();
                sd.setLocation(new URI("file:////bin/date"));

                sd.setStdout(stdout);
                sd.setStderr(stderr);

		Hashtable ht = new Hashtable();
		ResourceDescription rd = new HardwareResourceDescription(ht);

		JobDescription jd = new JobDescription(sd, rd);

		ResourceBroker broker = GAT.createResourceBroker(context);

		Job job = broker.submitJob(jd);

		int state = job.getState();

		while (state != Job.STOPPED && state != Job.SUBMISSION_ERROR) {
			try { 
				System.out.println("Sleeping!");
				Thread.sleep(1000);
			} catch (Exception e) { 
				// ignore
			}
			state = job.getState();
		}

		if (state == Job.SUBMISSION_ERROR) {
			System.out.println("ERROR");			
		} else { 
			System.out.println("OK");
		}
	} 
}
