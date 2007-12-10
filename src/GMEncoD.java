
import java.io.*;
import java.lang.*;
import java.util.*;
import java.text.*;

import joinc.Task;
import joinc.Master;

/** GMEncoD master class */
public class			GMEncoD extends Master {

    /** Raw avi file to split */
    private final String	aviFile;

    /** Splitter process */
    private Process		splitter = null;

    /** Total number of tasks (chunks to encode) */
    private int			totalTasks;

    /** Maximum number of machines we can use in parallel */
    private final int		maximumMachines;

    /** Number of completed tasks */
    private int			finishedTasks = 0;

    /** Id of the next task to be generated */
    private int			nextTaskNumber = 0;

    /** temporary directory */
    private String 		TMP_DIR;

    /** "Unique" temporary working directory */
    private final String	gMDir;

    /**
     * Jar file of the worker (has to be shipped to the remote
     * machine)
     */
    private final String	workerJar;

    /** Directory of raw avi chunks */
    private final String	aviChunksDir;

    /** Directory of encoded avi chunks */
    private final String	aviEncChunksDir;

    /** Unique merging ID */
    private int			mergeId = 0;

    /** Merging hash table */
    private Hashtable		finishedTasksHash = new Hashtable();


    /** Unique id for this computation*/
    int                     rand;

    /** GMEncoD constructor 
     *
     * Create temporary directories and start to fill them up with the raw
     * avi files (subtasks). Note that we do not wait until the splitter
     * has finished before we return - this potentially buys us a lot
     * parallelism because the first subtasks can be submitted while the
     * splitting process is busy waiting for I/O to complete.
     */
    public			GMEncoD(String chunkSize, int maximumMachines,
					String workerJar, String aviFile){
	Runtime			run;
	Random			gen;
	float			totalTasks;
	File			aviChunksDirFile;
	File			aviEncChunksDirFile;

	this.maximumMachines = maximumMachines;
	this.aviFile = aviFile;

	gen = new Random();
	rand = gen.nextInt(100);
	TMP_DIR = System.getProperty("java.io.tmpdir");
	this.gMDir = TMP_DIR + Env.WORKING_DIR + rand + "/";
	this.aviChunksDir = gMDir + Env.RAW_CHUNK_DIR;
	this.aviEncChunksDir = gMDir + Env.ENCODED_CHUNK_DIR;
	this.workerJar = workerJar;

	aviChunksDirFile = new File(this.aviChunksDir);
	aviChunksDirFile.mkdirs();
	aviEncChunksDirFile = new File(this.aviEncChunksDir);
	aviEncChunksDirFile.mkdirs();
	FilePermission fpEncChunks = new FilePermission(this.aviEncChunksDir + "/*", "write,read");
	FilePermission fpChunks = new FilePermission(this.aviChunksDir + "/*", "write,read");
	
	try {
	    
	    run = Runtime.getRuntime();
	    Process setPermissions = run.exec(new String [] {"chmod", "-R", "777", 
					      this.gMDir});
	    setPermissions.waitFor();

  	    this.splitter = run.exec(new
				     String [] {"avisplit", "-i", aviFile,
						"-o", "raw", "-s", chunkSize},
				     new String [] {}, aviChunksDirFile);

	    StreamGobbler errorGobbler =
		new StreamGobbler(splitter.getErrorStream());
            StreamGobbler outputGobbler =
		new StreamGobbler(splitter.getInputStream());
    
	    errorGobbler.start();
            outputGobbler.start();

	    while (new File(aviChunksDir + "raw-0001.avi").exists() == false){
		try {
		    if (splitter.exitValue() == 0){
			break ;
		    } else {
			System.err.println("Could not split file: " + aviFile);
			System.exit(2);			
		    }
		} catch (IllegalThreadStateException ite){
		}
		Thread.sleep(100);
	    }
	} catch (Throwable t) {
	    t.printStackTrace();
	}

	totalTasks =
	    (float) (long) (new File(aviFile).length()) /
	    (long) (new File(aviChunksDir + "raw-0000.avi").length());
	this.totalTasks = (int) totalTasks;

	if (totalTasks > this.totalTasks){
	    this.totalTasks++;
	}
    }

    /** GMEncoD maximumMachines method definition
     *
     * Returns the number of machines Joinc is allowed to use in
     * parallel.
     */
    public int			maximumMachines() {
	return maximumMachines;
    }

    /** GMEncoD totalTasks method definition
     *
     * Returns the number of tasks Joinc has to submit.
     */
    public int			totalTasks() {
	return totalTasks;
    }

    /** GMEncoD getTask method definition
     *
     * Initializes a task (pre/poststaged, stdfiles etc) so that Joinc
     * can submit it. Possibly waits until the subtask is ready; the
     * corresponding chunk gets split.
     */
    public Task			getTask() {
	DecimalFormat		form =
	    (DecimalFormat) NumberFormat.getInstance();
	form.applyPattern("0000");


	String inputFile = aviChunksDir + Env.RAW_CHUNK +
	    form.format(nextTaskNumber) + Env.AVI_EXTENTION;

	String workerInputFile = Env.RAW_CHUNK +
            form.format(nextTaskNumber) + Env.AVI_EXTENTION;

	String inputNext = aviChunksDir + Env.RAW_CHUNK +
	    form.format(nextTaskNumber+1) + Env.AVI_EXTENTION;
	
	File nextChunkFile = new File(inputNext);

	long start_wait_input = System.currentTimeMillis();
	while (nextChunkFile.exists() == false){

	    try {
		if (splitter.exitValue() == 0){
		    break ;
		} else {
		    System.err.println("Could not split file: " + aviFile);
		    System.exit(2);			
		}
	    } catch (IllegalThreadStateException ite){
		System.err.println(ite);
	    }

	    try {
		Thread.sleep(100);
	    } catch (InterruptedException ie) {
		System.err.println(ie);
	    }
	}

	long end_wait_input = System.currentTimeMillis();
	
	String outputFile = aviEncChunksDir + Env.ENCODED_CHUNK +
	    nextTaskNumber + Env.AVI_EXTENTION;

	String stdin = Env.STDIN + rand + "-" + nextTaskNumber + Env.GM_EXTENTION;
	String stdout = Env.STDOUT + rand + "-" + nextTaskNumber + Env.GM_EXTENTION;
	String stderr = Env.STDERR + rand + "-" + nextTaskNumber + Env.GM_EXTENTION;

	try {
	    File fstdout = new File(stdout);
	    fstdout.createNewFile();
	}catch(IOException ioe) {
	    System.err.println("Error creating stdout for task " +
			       "number: " + nextTaskNumber);
	}

	try {
	    File fstdin = new File(stdin);
	    fstdin.createNewFile();
	}catch(IOException ioe) {
	    System.err.println("Error creating stdin for task " +
			       "number: " + nextTaskNumber);
	}
	   
	try {
	    File fstderr = new File(stderr);
	    fstderr.createNewFile();
	}catch(IOException ioe){
	    System.err.println("Error creating stderr for task " +
			       "number: " + nextTaskNumber);
	}
	
	nextTaskNumber++;
	String [] parameters = new String[2];
	parameters[0] = inputFile;
	parameters[1] = outputFile;

	String [] vmParameters = new String[1];
	vmParameters[0] = "-Dgmencod.lib=" + System.getProperty("gmencod.lib");

	//MEAS
	long task_start = System.currentTimeMillis();
	try {
	    new PrintWriter(new FileWriter(stdin, true), true).
		println("waited for input for: " + (end_wait_input-start_wait_input) + 
			"\ntask_start: " + task_start); 
	    
	} catch(IOException ioe) {
		System.err.println("Error writing to stderr for task " + 
				   "number: " + nextTaskNumber);
	}

	return new Task("Worker", stdout, stderr, stdin,
			parameters, vmParameters,
			new String [] {workerJar},
			new String [] {inputFile},
			new String [] {outputFile});
    }

    /** 
     * GMEncoD getTask method definition
     *
     * Increments the finishedTasks counter unpon a task completion and delete
     * the corresponding raw chunk file. More importantly, checks whether this
     * completion can be merged with others (either encoded or merged) and, if
     * so, spawns a new process to do it for us. This method also cleans up 
     * chunks that won't be of any use anymore.
     */
    public void			taskDone(Task job) {
	Runtime			run;
	Process			proc;
	File			aviEncChunksDirFile;

	//MEAS
	long task_end = System.currentTimeMillis();

	finishedTasks++;
	(new File(job.inputFiles[0])).delete();
	System.out.println("Finished task: " + job.taskNumber);

	finishedTasksHash.put("" + job.taskNumber, Env.ENCODED_CHUNK +
			      job.taskNumber + Env.AVI_EXTENTION);

	//MEAS
	long merge_start = System.currentTimeMillis();

	if (job.taskNumber != 0 &&
	    finishedTasksHash.containsKey("" + (job.taskNumber-1))){

	    this.mergeId++;
	    aviEncChunksDirFile = new File(this.aviEncChunksDir);

	    try {
		run = Runtime.getRuntime();
		proc = run.exec(new String []
		    {"avimerge", "-o", Env.MERGED_CHUNK + this.mergeId +
		     Env.AVI_EXTENTION, "-i", (String) finishedTasksHash.
		     get("" + (job.taskNumber-1)), (String) finishedTasksHash.
		     get("" + job.taskNumber)},
				new String [] {}, aviEncChunksDirFile);

		StreamGobbler errorGobbler =
		    new StreamGobbler(proc.getErrorStream());
		StreamGobbler outputGobbler =
		    new StreamGobbler(proc.getInputStream());
		errorGobbler.start();
		outputGobbler.start();

		proc.waitFor();
		if (proc.exitValue() != 0){
		    System.err.println("Could not merge files: " +
				       (String) finishedTasksHash.
				       get("" + (job.taskNumber-1)) + " " +
				       (String) finishedTasksHash.
				       get("" + job.taskNumber));
		    System.exit(2);
		}

		(new File(this.aviEncChunksDir + (String) finishedTasksHash.
			  get("" + (job.taskNumber-1)))).delete();
		(new File(this.aviEncChunksDir + (String) finishedTasksHash.
			  get("" + job.taskNumber))).delete();

		finishedTasksHash.put("" + job.taskNumber, Env.MERGED_CHUNK +
				      this.mergeId + Env.AVI_EXTENTION);
		String previousKey =
		    (String) finishedTasksHash.get("" + (job.taskNumber-1));

		for (int nr = job.taskNumber-1;
		     previousKey.equals((String) finishedTasksHash.get(""+nr));
		     nr--){
		    finishedTasksHash.put("" + nr, (String) finishedTasksHash.
					  get("" + job.taskNumber));
		}

	    }  catch (Throwable t) {
		t.printStackTrace();
	    }
	}
	
	if (job.taskNumber != (this.totalTasks-1) &&
	    finishedTasksHash.containsKey("" + (job.taskNumber+1))){

            this.mergeId++;
            aviEncChunksDirFile = new File(this.aviEncChunksDir);

            try {
                run = Runtime.getRuntime();
                proc = run.exec(new String []
                    {"avimerge", "-o", Env.MERGED_CHUNK + this.mergeId +
                     Env.AVI_EXTENTION, "-i", (String) finishedTasksHash.
                     get("" + job.taskNumber), (String) finishedTasksHash.
                     get("" + (job.taskNumber+1))},
                                new String [] {}, aviEncChunksDirFile);

                StreamGobbler errorGobbler =
                    new StreamGobbler(proc.getErrorStream());
                StreamGobbler outputGobbler =
                    new StreamGobbler(proc.getInputStream());
                errorGobbler.start();
                outputGobbler.start();

                proc.waitFor();
		if (proc.exitValue() != 0){
		    System.err.println("Could not merge file: " +
				       (String) finishedTasksHash.
				       get("" + job.taskNumber) + " " +
				       (String) finishedTasksHash.
				       get("" + (job.taskNumber+1)));
		    System.exit(2);
		}

                (new File(this.aviEncChunksDir + (String) finishedTasksHash.
                          get("" + job.taskNumber))).delete();
                (new File(this.aviEncChunksDir + (String) finishedTasksHash.
                          get("" + (job.taskNumber+1)))).delete();

		finishedTasksHash.put("" + job.taskNumber, Env.MERGED_CHUNK +
				      this.mergeId + Env.AVI_EXTENTION);
		String previousKey =
		    (String) finishedTasksHash.get("" + (job.taskNumber+1));

		for (int nr = job.taskNumber+1;
		     previousKey.equals((String) finishedTasksHash.get(""+nr));
		     nr++){
		    finishedTasksHash.put("" + nr, (String) finishedTasksHash.
					  get("" + job.taskNumber));
		}

		previousKey =
		    (String) finishedTasksHash.get("" + (job.taskNumber-1));

		for (int nr = job.taskNumber-1;
		     previousKey.equals((String) finishedTasksHash.get(""+nr));
		     nr--){
		    finishedTasksHash.put("" + nr, (String) finishedTasksHash.
					  get("" + job.taskNumber));
		}

            }  catch (Throwable t) {
		t.printStackTrace();
            }
	}

	//MEAS
        long merge_end = System.currentTimeMillis();
        try {
            new PrintWriter(new FileWriter(job.stdoutFile, true), true).
                println("task_end: " + task_end + 
			"\ntime spent in merge: " + (merge_end-merge_start));

        } catch(IOException ioe) {
                System.err.println("Error writing to stderr for task " +
                                   "number: " + job.taskNumber);
        }
		
    }

    /** 
     * GMEncoD idle method definition
     *
     * No idling
     */
    public void			idle() {
    }

    /**
     * GMEncoD done method definition
     *
     * Rename the last merged file into the final filename and clean up
     * the temporary directories.
     */
    public void			done() {

	System.out.println("Tasks done        : " + finishedTasks);

	(new File(this.aviEncChunksDir + Env.MERGED_CHUNK + this.mergeId +
		  Env.AVI_EXTENTION)).renameTo
	    (new File(TMP_DIR + "enc-" +
		      aviFile.substring(aviFile.lastIndexOf('/')+1)));

	(new File(this.aviEncChunksDir + Env.MERGED_CHUNK + this.mergeId +
		  Env.AVI_EXTENTION)).delete();
	(new File(aviEncChunksDir)).delete();
	(new File(aviChunksDir)).delete();
	(new File(gMDir)).delete();
    }

    /** GMEncoD main function
     *
     * Checks parameters sanity, calls the Joinc start method and
     * invokes done when it returns.
     */
    public static void main(String args[]){
	try {
	    if (args.length < 3) {
		System.err.println(Env.USAGE);
		System.exit(2);				
	    }

	    GMEncoD master;
	    if (args.length == 3) {
		master = new GMEncoD(Env.CHUNK_SIZE,
				     Integer.parseInt(args[2]),
				     args[1], args[0]);
	    } else {
		master = new GMEncoD(args[3],
				     Integer.parseInt(args[2]),
				     args[1], args[0]);
	    }
	    long start = System.currentTimeMillis();
	    master.start();
	    long end = System.currentTimeMillis();
	    master.done();
	    System.out.println("elapsed computation time: " + (end-start));
	} catch (Exception e) {
	    System.err.println("Oops " + e);
	    e.printStackTrace(System.err);
	}
    }
}
