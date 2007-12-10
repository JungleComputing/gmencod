package joinc;

import java.util.Map;
import java.util.Iterator;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.ListIterator;
import java.net.URISyntaxException;

import org.gridlab.gat.GAT;
import org.gridlab.gat.URI;
import org.gridlab.gat.io.File;
import org.gridlab.gat.GATContext;
import org.gridlab.gat.TimePeriod;
import org.gridlab.gat.Preferences;
import org.gridlab.gat.monitoring.Metric;
import org.gridlab.gat.monitoring.MetricDefinition;
import org.gridlab.gat.monitoring.MetricListener;
import org.gridlab.gat.monitoring.MetricValue;
import org.gridlab.gat.resources.Job;
import org.gridlab.gat.resources.JobDescription;
import org.gridlab.gat.resources.ResourceBroker;
import org.gridlab.gat.resources.ResourceDescription;
import org.gridlab.gat.resources.SoftwareDescription;
import org.gridlab.gat.resources.HardwareResourceDescription;

/**
 * This class contains all the error codes permitting to deal with job status.
 */
class			ErrorCodes{
    /**
     * Status uninitialize is 0.
     */
    private final int	uninitialize = 0;

    /**
     * Status submitted is 1.
     */
    private final int	submittionDone = 1;

    /**
     * Status completed is 2.
     */
    private final int	computationDone = 2;

    /**
     * Status submission failed is -1.
     */
    private final int	submissionFailed = -1;

    /**
     * Status computation failed is -2.
     */
    private final int	computationFailed = -2;


    /**
     * Returns the uninitialized code.
     *
     * @return uninitialized code
     */
    public int		getUninitialized(){
	return (uninitialize);
    }

    /**
     * Returns the submitted code.
     *
     * @return submitted code
     */
    public int		getSubmissionDone(){
	return (submittionDone);
    }

    /**
     * Returns the submission failed code.
     *
     * @return submission failed code
     */
    public int		getSubmissionFailed(){
	return (submissionFailed);
    }

    /**
     * Returns the completed code.
     *
     * @return completed code
     */
    public int		getComputationDone(){
	return (computationDone);
    }

    /**
     * Returns the computation failed code.
     *
     * @return computation failed code
     */
    public int		getComputationFailed(){
	return (computationFailed);
    }
}

/**
 * This class contains the basic cluster information.
 */
class			Cluster{
    /**
     * Number of submitted jobs.
     */
    private int		job = 0;
    
    /**
     * Number of running jobs.
     */
    private int		load = 0;

    /**
     * Cluster name.
     */
    private String	name = null;

    /**
     * Number of failed jobs.
     */
    private int		failure = 0;

    /**
     * Number of completed jobs.
     */
    private int		success = 0;

    /**
     * Priority (computation power).
     */
    private int		priority = 0;

    /**
     * Initialize the basics.
     */
    public		Cluster(String name, int priority){
	this.name = name;
	this.priority = priority;
    }

    /**
     * Returns the number of jobs.
     *
     * @return job
     */
    public int		getJob(){
	return (job);
    }

    /**
     * Sets the number of jobs.
     *
     * @param int the number of jobs
     */
    public void		setJob(int job){
	this.job = job;
    }

    /**
     * Returns the cluster name.
     *
     * @return name
     */
    public String	getName(){
	return (name);
    }

    /**
     * Sets the cluster name.
     *
     * @param String name
     */
    public void		setName(String name){
	this.name = name;
    }

    /**
     * Returns the number of running jobs.
     *
     * @return load
     */
    public int		getLoad(){
	return (load);
    }

    /**
     * Sets the number of running jobs.
     *
     * @param int load
     */
    public synchronized void setLoad(int load){
	this.load = load;
    }

    /**
     * Returns the number of failed jobs.
     *
     * @return failure
     */
    public int		getFailure(){
	return (failure);
    }

    /**
     * Sets the number of failed jobs.
     *
     * @param int failure
     */
    public synchronized void setFailure(int failure){
	this.failure = failure;
    }

    /**
     * Returns the number of completed jobs.
     *
     * @return success
     */
    public int		getSuccess(){
	return (success);
    }

    /**
     * Sets the number of jobs completed.
     *
     * @param int success
     */
    public synchronized void setSuccess(int success){
	this.success = success;
    }

    /**
     * Returns the priority (computation power)
     *
     * @return priority
     */
    public int		getPriority(){
	return (priority);
    }

    /**
     * Sets the priority (computation power).
     *
     * @param int priority
     */
    public void		setPriority(int priority){
	this.priority = priority;
    }
}

/**
 * Contains the basic brokering information needed to schedule a batch over
 * multiple clusters.
 */
class			MyBroker{
    /**
     * The play ground.
     */
    private Cluster[]	grid;

    /**
     * The total number of jobs.
     */
    private int		ttlJob = 0;

    /**
     * The total number of running jobs.
     */
    private int		ttlLoad = 0;

    /**
     * The total number of completed jobs.
     */
    private int		ttlSuccess = 0;

    /**
     * The total number of failed jobs.
     */
    private int		ttlFailure = 0;

    /**
     * The total priority (computation power)
     */
    private int		ttlPriority = 0;

    /**
     * Define the ground play. 
     * (To map the computational power of your grid, just pass the number of
     * CPU as an argument to Cluster())
     */
    public		MyBroker(){
	grid = new Cluster[5];
	grid[0] = new Cluster("fs0.das2.cs.vu.nl", 100);
 	grid[1] = new Cluster("fs1.das2.liacs.nl", 0);
 	grid[2] = new Cluster("fs2.das2.nikhef.nl", 0);
 	grid[3] = new Cluster("fs3.das2.ewi.tudelft.nl", 0);
 	grid[4] = new Cluster("fs4.das2.phys.uu.nl", 0);

	for (int i = 0; i < grid.length; i++){
	    ttlLoad += grid[i].getLoad();
	    ttlFailure += grid[i].getFailure();
	    ttlPriority += grid[i].getPriority();
	}
    }

    /**
     * Returns the total number of submitted jobs.
     *
     * @return The total number of submitted jobs
     */
    public int		getTtlJob(){
	return (ttlJob);
    }

    /**
     * Sets the total number of submitted jobs.
     *
     * @param int The total number of submitted jobs
     */
    public synchronized void setTtlJob(int ttlJob){
	this.ttlJob = ttlJob;
    }

    /**
     * Returns the total number of running jobs.
     *
     * @return The total number of running jobs
     */
    public int		getTtlLoad(){
	return (ttlLoad);
    }

    /**
     * Sets the total number of running jobs.
     *
     * @param int The total number of running jobs
     */
    public synchronized void setTtlLoad(int ttlLoad){
	this.ttlLoad = ttlLoad;
    }

    /**
     * Returns the total number of failed jobs.
     *
     * @return The total number of failed jobs
     */
    public int getTtlFailure(){
	return (ttlFailure);
    }

    /**
     * Sets the total number of failed jobs.
     *
     * @param int The total number of failed jobs
     */
    public synchronized void setTtlFailure(int ttlFailure){
	this.ttlFailure = ttlFailure;
    }

    /**
     * Returns the total number of completed jobs.
     *
     * @return The total number of completed jobs
     */
    public int		getTtlSuccess(){
	return (ttlSuccess);
    }

    /**
     * Sets the total number of completed jobs.
     *
     * @param int The total number of completed jobs
     */
    public synchronized void setTtlSuccess(int ttlSuccess){
	this.ttlSuccess = ttlSuccess;
    }

    /**
     * Returns the total priority (computation power).
     *
     * @return The total priority (computation power)
     */
    public int		getTtlPriority(){
	return (ttlPriority);
    }

    /**
     * Sets the total priority (computation power).
     *
     * @param int The total number of priority (computation power)
     */
    public void		setTtlPriority(int ttlPriority){
	this.ttlPriority = ttlPriority;
    }

    /**
     * Submit a job on the best cluster.
     *
     * @param JobStruct The job to submit
     */
    public boolean	brokerMe(Master master, JobStruct job){
	ErrorCodes	err = new ErrorCodes();
	Cluster		cluster = getCluster();

	setTtlJob(getTtlJob()+1);
	job.setCluster(cluster);

	Hashtable hardwareAttributes = new Hashtable();
	hardwareAttributes.put("machine.node", cluster.getName());
	ResourceDescription rd =
	    new HardwareResourceDescription(hardwareAttributes);

	JobDescription jd = null;
	try {
	    jd = new JobDescription(job.getSd(), rd);
	} catch (Exception e) {
	    System.err.println("Could not create Job description: " + e);
	}

	Job job2 = null;
	try {
	    System.err.println("[" + job.getJid() + "]\tSubmitting on " +
			       cluster.getName() + "...");
	    job2 = job.getBroker().submitJob(jd);

	    setTtlLoad(getTtlLoad()+1);
	    job.setJob(job2);
	    job.setStatus(err.getSubmissionDone());
	    cluster.setJob(cluster.getJob()+1);
	    cluster.setLoad(cluster.getLoad()+1);

	    System.err.println("[" + job.getJid() + "]\tSubmission Done");
	    return (true);
	} catch (Exception e) {

	    setTtlFailure(getTtlFailure()+1);
	    job.setStatus(err.getSubmissionFailed());
	    cluster.setJob(cluster.getJob()+1);
	    cluster.setFailure(cluster.getFailure()+1);

	    System.err.println("[" + job.getJid() + "]\tSubmission failed");
	    return (false);
	}
    }

    /**
     * Compute the best cluster to use given some basic metrics
     * (Would be nice to also take the number of failure into
     * account here).
     *
     * @return The best choice of cluster
     */
    public Cluster	getCluster(){	
	Cluster		cluster = grid[0];
	float		maxDiff = -1;

	if (grid[0].getPriority() != 0){
	    maxDiff = (float)
		((ttlPriority != 0 ?
		  (float) grid[0].getPriority()/ttlPriority : 0.00) -
		 (ttlJob != 0 ? (float) grid[0].getJob()/ttlJob : 0.00));
	}

	for (int i = 1; i < grid.length; i++){
	    if (grid[i].getPriority() == 0)
		continue ;

	    if (maxDiff < (float)
		((ttlPriority != 0 ?
		  (float) grid[i].getPriority()/ttlPriority : 0.00) -
		 (ttlJob != 0 ? (float) grid[i].getJob()/ttlJob : 0.00))){

		maxDiff = (float)
		    ((ttlPriority != 0 ?
		      (float) grid[i].getPriority()/ttlPriority : 0.00) -
		     (ttlJob != 0 ? (float) grid[i].getJob()/ttlJob : 0.00));
		cluster = grid[i];
	    }
	}

	return (cluster);
    }

    /**
     * Prints some stats per cluster.
     */
    public void		printMe(){
	System.err.println("\nCluster brokering stats");
	System.err.println("-------------------------\n");
	System.err.println("[General]\t" + ttlJob + " jobs submitted");
	System.err.println("[General]\t" + ttlSuccess + " succeed");
	System.err.println("[General]\t" + ttlFailure + " failed\n");

	for (int i = 0; i < grid.length; i++){
	    System.err.println("[" + grid[i].getName() + "]\t\t" +
			       ((float) grid[i].getJob()/
				(ttlJob != 0 ? ttlJob : 1)) * 100 +
			       "% of ttl jobs submitted");
	    System.err.println("[" + grid[i].getName() + "]\t\t" +
			       ((float) grid[i].getSuccess()/
				(ttlSuccess != 0 ? ttlSuccess : 1)) * 100 +
			       "% of ttl success");
	    System.err.println("[" + grid[i].getName() + "]\t\t" +
			       ((float) grid[i].getFailure()/
			       (ttlFailure != 0 ? ttlFailure : 1)) * 100 +
			       "% of ttl failure\n");
	}
    }
}

/**
 * This Class contains all the information needed to submit a job
 * and possibly deal with its resubmission.
 */
class			JobStruct{
    /**
     * The Job ID.
     */
    private int		jid = 0;

    /**
     * The Job GAT object.
     */
    private Job		job = null;

    /**
     * The status.
     */
    private int		status = 0;

    /**
     * The Task.
     */
    private Task	task = null;

    /**
     * The Preferences GAT object.
     */
    private Preferences	prefs = null;

    /**
     * The GATContext GAT object.
     */
    private GATContext	context = null;

    /**
     * The Cluster.
     */
    private Cluster	cluster = null;

    /**
     * The ResourceBroker GAT object.
     */
    private ResourceBroker broker = null;

    /**
     * The SoftwareDescription GAT object.
     */
    private SoftwareDescription	sd = null;

    /**
     * The GAT location.
     */
    static final String JVM =
	"/usr/local/sun-java/j2sdk1.4.2/j2sdk1.4.2/bin/java";

    /**
     * The GAT protocol.
     */
    static final String PROTO = "any:///";

    /**
     * The basic properties of the job.
     *
     * @param int The Job ID
     * @param Task The Task
     * @param Preferences The Preferences GAT object
     * @param ResourceBroker The ResourceBroker GAT object
     * @param GATContext The GatContext GAT object
     */
    public		JobStruct(int jid, Task task, Preferences prefs,
				  ResourceBroker broker, GATContext context)
    {
	this.jid = jid;
	this.task = task;
	this.prefs = prefs;
	this.broker = broker;
	this.context = context;
	sd = new SoftwareDescription();

	this.setLocation();
	this.setStdFiles();
	//this.setStagedFiles();
	this.setParameters();
    }

    /**
     * Returns the Job ID.
     *
     * @return The Job ID
     */
    public int		getJid(){
	return (jid);
    }

    /**
     * Sets the Job ID.
     *
     * @param int The Job ID
     */
    public void		setJid(int jid){
	this.jid = jid;
    }

    /**
     * Returns the job GAT object.
     *
     * @return The Job GAT object
     */
    public Job		getJob(){
	return (job);
    }

   /**
     * Sets the job GAT object.
     *
     * @param int The Job GAT object
     */
    public void		setJob(Job job){
	this.job = job;
    }

    /**
     * Returns the Task.
     *
     * @return The Task
     */
    public Task		getTask(){
	return (task);
    }

    /**
     * Sets the Task.
     *
     * @param Task The Task
     */
    public void		setTask(Task task){
	this.task = task;
    }

    /**
     * Returns the status.
     *
     * @return The status
     */
    public int		getStatus(){
	return (status);
    }

   /**
     * Sets the status.
     *
     * @param int The Status
     */
    public synchronized void setStatus(int status){
	this.status = status;
    }

    /**
     * Returns the Cluster.
     *
     * @return The Cluster
     */
    public Cluster	getCluster(){
	return (cluster);
    }

    /**
     * Sets the Cluster.
     *
     * @param Cluster The Cluster
     */
    public void		setCluster(Cluster cluster){
	this.cluster = cluster;
    }

    /**
     * Returns the ResourceBroker GAT object.
     *
     * @return The ResourceBroker GAT object
     */
    public ResourceBroker getBroker(){
	return (broker);
    }

    /**
     * Returns the SoftwareDescription GAT object.
     *
     * @return The SoftwareDescription GAT object
     */
    public SoftwareDescription getSd(){
	return (sd);
    }

    /**
     * This method creates a GAT URI for the remote java virtual
     * machine and configure the GAT location using it.
     */
    public void		setLocation(){
	URI location = null;

	try {
	    location = new URI(JVM);
	} catch (URISyntaxException e) {
	    System.err.println("Syntax error in URI: " + e);
	    System.exit(2);
	}
	sd.setLocation(location);
    }

    /**
     * This method is responsible to configure the 3 Standart files
     * namely the input, output and error.
     */
    public void		setStdFiles(){
	File		stdin = null;
	File		stdout = null;
	File		stderr = null;

	try {
	    if (task.stdinFile != null){
		stdin = GAT.createFile(context,
				       new URI(PROTO + task.stdinFile));
		sd.setStdin(stdin);
	    }
	    if (task.stdoutFile != null){
		stdout = GAT.createFile(context,
					new URI(PROTO + task.stdoutFile));
		sd.setStdout(stdout);
	    }
	    if (task.stderrFile != null){
		stderr = GAT.createFile(context,
					new URI(PROTO + task.stderrFile));
		sd.setStderr(stderr);
	    }
	} catch (Exception e) {
	    System.err.println("Could not create file: " + e);
	    System.exit(2);
	}
    }

    private File[]	toFileList(String[] stringList, GATContext context) {
	File[]		fileList = new File[stringList.length];
	int		n = 0;

	for(int i = 0; i < stringList.length; i++) {
	    try {
		if (stringList[i] != null){
		    fileList[n++] =
			GAT.createFile(context,
				       new URI(PROTO + stringList[i]));
		}
	    } catch (Exception e){
		System.err.println("Could not create file:" + e);
		System.exit(2);
	    }
	}
	return (fileList);
    }

    /**
     * This method deals with pre/poststaged files. They are respectively
     * files which must be uploaded at the remote machine side prior job
     * execution and resulting files which have to be downloaded at the
     * end of the computation.
     */
    public void		setStagedFiles(){
	File[]		fileList;
	String[]	preStaged =
	    new String[task.inputFiles.length + task.jars.length];

	for (int i = 0; i < task.inputFiles.length; i++){
	    preStaged[i] = task.inputFiles[i];
	}
	System.err.println("input file: " + task.inputFiles[0]);
	for (int i = 0; i < task.jars.length; i++){
	    preStaged[i + task.inputFiles.length] = task.jars[i];
	}
	
	sd.setPreStaged(toFileList(preStaged, context));
	sd.setPostStaged(toFileList(task.outputFiles, context));
    }

    /**
     * This method is responsible of the concatenation of several parameters
     * in the aim of a proper execution at the remote side. First an additional
     * field defines the parameters which are gonna be passed to the remote
     * java virtual machine. In a second hand, a high priority classpath is
     * forced to the jar file of the worker. Finally, the class name of the
     * worker plus the actual parameters are added to the loop.
     */
    public void		setParameters(){
	String[]	parameters = new String[2 + task.vmParameters.length +
						task.parameters.length +
						task.jars.length];
	int		i;
	int		n = 0;

	for(i = 0; i < task.vmParameters.length; i++){
	    if (task.vmParameters[i] != null){
		parameters[n++] = task.vmParameters[i];
	    }
	}

	parameters[n++] = new String("-cp");
	if(task.jars[0] != null) 
		parameters[n] = task.jars[0];
	for(i = 1; i < task.jars.length; i++){
	    if (task.jars[i] != null){
		parameters[n] += ":" + task.jars[i];
	    }
	}
	n++;
	parameters[n++] = task.className;

	for(i = 0; i < task.parameters.length; i++)
	    if (task.parameters[i] != null){
		parameters[n++] = task.parameters[i];
	    }

	sd.setArguments(parameters);
    }
}

/**
 * This is the main class of the Joinc library.
 * 
 * It mainly consists of abstract methods, which will be implemented by 
 * application. Since these methods are the interface to the outside world, 
 * they may NOT be changed! For an example of how they are used, see the 
 * applications.      
 *  
 * @author Jason Maassen
 * @version 1.0 Mar 24, 2005
 * @since 1.0
 * 
 */
public abstract class Master implements MetricListener{
        
    /**
     * Returns a task that can be run on the grid.
     * (This method will be implemented by the application).
     * 
     * @return a task to run on the grid.
     */
    public abstract Task getTask(); 
    
    /**
     * This method will be implemented by the application. 
     * It must be called by your code after a task is done 
     * to notify the application of this fact.
     *
     * @param task The task that has finished.
     */
    public abstract void taskDone(Task task); 
        
    /**
     * Returns the total number of tasks that need to be run.
     * (This method will be implemented by the application).
     *  
     * @return number of tasks that will be produced.
     */
    public abstract int totalTasks();
    
    /**
     * Returns the maximum number of machines that may be used.
     * (This method will be implemented by the application). 
     *      
     * @return the maximum number of machines you may use.
     */
    public abstract int maximumMachines(); 
        
    /**
     * If there is nothing else to do, you may call this method 
     * to allow the application to do some work.
     * (This method will be implemented by the application).
     */
    public abstract void idle();  

    private Hashtable		hashJob = null;
    private Hashtable		hashFailedJob = null;
    private MyBroker		myBroker = null;

    /**
     * This method is the metric handler. By definition a handler
     * should do as few work as possible however, for reason of
     * conveniance, ProcessMetricEvent deals with the status checking
     * itself. Not doing so would have implied exporting further
     * variables and/or computation. Notice that this method has to be
     * protected against concurrent accesses.
     */
    public synchronized void	ProcessMetricEvent(MetricValue val){
	Map			info = null;
        Cluster			cluster = null;
        ErrorCodes		err = new ErrorCodes();
	Job			job2 = (Job) val.getSource();
        JobStruct		job = (JobStruct) hashJob.get((Object) job2);

        try {
            info = job2.getInfo();
        } catch (Exception e) {
            System.err.println("getInfo failed: " + e);
        }

        if ((info.containsKey("state") &&
	     info.get("state").equals("SUBMISSION_ERROR")) ||
	    (info.containsKey("postStageError"))){

	    hashFailedJob.put((Object) job2, job);
	    hashJob.remove((Object) job2);

	    job.setStatus(err.getComputationFailed());
	    cluster = job.getCluster();
	    cluster.setLoad(cluster.getLoad()-1);
	    cluster.setFailure(cluster.getFailure()+1);
	    myBroker.setTtlLoad(myBroker.getTtlLoad()-1);
	    myBroker.setTtlFailure(myBroker.getTtlFailure()+1);

            System.err.println("[" +
                               job.getJid() + "]\tFailed");
	    notifyAll();
        } else if (info.get("state").equals("STOPPED")){

	    hashJob.remove((Object) job2);

	    job.setStatus(err.getComputationDone());
	    cluster = job.getCluster();
	    cluster.setLoad(cluster.getLoad()-1);
	    cluster.setSuccess(cluster.getSuccess()+1);
	    myBroker.setTtlLoad(myBroker.getTtlLoad()-1);
	    myBroker.setTtlSuccess(myBroker.getTtlSuccess()+1);

            taskDone(job.getTask());
            System.err.println("[" +
                               job.getJid() + "]\tDone");
	    notifyAll();
        }
    }

    /**
     * The start() method is the heart of the joinc library. Here we
     * call the general GAT methods dealing with the context,
     * preferences, resource broker etc.
     *
     * Once done, JobStruct objects are created, each one containing the
     * job dependant information needed to run a job using GAT. The jobs
     * are then submitted in a loop until the maximum number of machines
     * is reached or we submitted all the tasks. Notice that as long as it
     * is possible, we keep submitting jobs.
     *
     * Metrics are used to wait for the jobs to complete instead of busy
     * waiting to offload the CPU. The variables being possibly accessed
     * by multiple thread of execution are serialized correspondingly. See
     * the ProcessMetricEvent method for more information.
     *
     * To conclude, statistics about the execution properties are
     * printed on the standart error.
     */
    public void		start() {
	int		jid = 0;
	JobStruct	job = null;
	ResourceBroker	broker = null;

	hashJob = new Hashtable();
	hashFailedJob = new Hashtable();
	myBroker = new MyBroker();
	ErrorCodes	err = new ErrorCodes();
	GATContext	context = new GATContext();
	Preferences	prefs = new Preferences();

	prefs.put("ResourceBroker.adaptor.name", "globus");
	prefs.put("ResourceBroker.jobmanager", "sge");

	try {
	    broker = GAT.createResourceBroker(context, prefs);
	} catch (Exception e) {
	    System.err.println("Could not create broker: " + e);
	    System.exit(2);
	}

	while (myBroker.getTtlSuccess() != totalTasks()){

	    if (myBroker.getTtlLoad() < maximumMachines() &&
		myBroker.getTtlSuccess()+myBroker.getTtlLoad() < totalTasks()){

		if (jid < totalTasks()){
		    job = new JobStruct(jid++,
					getTask(), prefs, broker, context);
		} else {
		    Object obj = hashFailedJob.keys().nextElement();
		    job = (JobStruct) hashFailedJob.get(obj);
		    hashFailedJob.remove(obj);
		}

		while (myBroker.brokerMe(this, job) == false) ;
		hashJob.put(job.getJob(), job);

		try {
		    MetricDefinition md =
			job.getJob().getMetricDefinitionByName("job.status");
		    Metric m = md.createMetric(null);
		    job.getJob().addMetricListener(this, m);
		} catch (Exception e) {
		    System.err.println("Can't create metric");
		}
	    } else {
		synchronized (this){
		    try {
			idle();
			wait();
		    } catch (Exception e) {
		    }
		}
	    }
	}
	myBroker.printMe();
    }
}
