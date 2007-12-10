package org.gridlab.gat.io.cpi.ssh;

import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Date;
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;

import org.gridlab.gat.GAT;
import org.gridlab.gat.GATContext;
import org.gridlab.gat.GATInvocationException;
import org.gridlab.gat.GATObjectCreationException;
import org.gridlab.gat.Preferences;
import org.gridlab.gat.URI;
import org.gridlab.gat.engine.GATEngine;
import org.gridlab.gat.io.cpi.FileCpi;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

public class SshFileAdaptor extends FileCpi {

	private static final int XOS = 1;
	private static final int WOS = 2;
	private static final int UNKNOWN = 0;
	private static final int TRUE = 1;
	private static final int FALSE = 2;
	
	File f;
	private JSch jsch;
	private Session session;
	private Channel channel;
	private String userName;
	private String portNumber;
	private int osType = 0;
	private boolean sftpEnabled = false;
	private boolean isLocalFile = false;
	
	private int isDir = UNKNOWN;
	private int itExists  = UNKNOWN;
	
	/**
	 * @param gatContext
	 * @param preferences
	 * @param location
	 */
	public SshFileAdaptor(GATContext gatContext, Preferences preferences,
			URI location) throws GATObjectCreationException {
		
		super(gatContext, preferences, location);
		
		checkName("ssh");
		
		if(!location.isCompatible("ssh")) {
			throw new GATObjectCreationException("cannot handle this URI");
		}
				
		if (location.isLocal()) {
			isLocalFile = true;
			if (GATEngine.DEBUG) {
				System.err.println("SshFileAdaptor: local file LOCATION = " + location);
			}
			return;
		}
		
		if (GATEngine.DEBUG) {
			System.err.println("SshFileAdaptor: remote file LOCATION = " + location);
		}
		
		prepareSession(location);
		
		if (GATEngine.DEBUG) {
			System.err.println("SshFileAdaptor: started session with " + location.getHost() + 
			" using username: " + userName + " on port: " + portNumber + " for file: " + location.getPath());
		}
		/*check if sftp is available on the side*/
		
		/*determine remote os*/
		if(!sftpEnabled)
		    osType = determineRemoteOS();
		    
		if (GATEngine.DEBUG) {
			System.err.println("SshFileAdaptor: remote OS for " + location + " is " + osType);
		}
	}

	protected void prepareSession(URI loc) throws GATObjectCreationException {
		String host = loc.getHost();
		
		/*it will be changed for the Security Context*/			
		if(preferences == null) {
			throw new GATObjectCreationException(
			"The SshFileAdaptor needs a Preferences object to specify at least the identity file.");
		}

		//opens a ssh connection (using jsch)
		jsch = new JSch();
		java.util.Hashtable configJsch = new java.util.Hashtable(0);
    		configJsch.put("StrictHostKeyChecking","no");
		JSch.setConfig(configJsch);
		String user = loc.getUserInfo();		
		/*for the moment, assume there is only one username
		 *should contain more identity files, per (user, machine) pair*/
		if(user == null) {
		    user = (String) preferences.get("defaultUserName");
		    if(user == null) throw new GATObjectCreationException(
					"The SshFileAdaptor needs an username for the remote machine.");
		}
		userName = user;
		String identityFile = (String) preferences.get(user);
		if(identityFile == null) {
			identityFile = (String) preferences.get("defaultIdentityFile");
			if(identityFile == null)
				throw new GATObjectCreationException(
					"The SshFileAdaptor needs an identity file to connect to remote machine.");
		}
		
		try {
		jsch.addIdentity(identityFile);
		
		//no passphrase		
		/*allow port override*/
		int port = loc.getPort();
		/*it will always return -1 for user@host:path*/
		if (port == -1) {
		    String portNo = (String) preferences.get("defaultPortNumber");
		    if (portNo != null) port = Integer.parseInt(portNo);
		    else port = 22;
		}
		portNumber = port + "";
		session = jsch.getSession(user, host, port);
		setUserInfo();
		session.connect();
						
		} catch(JSchException jsche) { throw new GATObjectCreationException("internal error in SshFileAdaptor: " + jsche); }
	}

	protected void setUserInfo() {
	    session.setUserInfo(new UserInfo(){
		    public String getPassword(){ return null; }
		    public boolean promptYesNo(String str){ return true; }
		    public String getPassphrase(){ return null; }
		    public boolean promptPassphrase(String message){ return true; }
		    public boolean promptPassword(String message){ return true; }
		    public void showMessage(String message){ return; }
		    });
	}
	
	protected void cleanSession(Session s, Channel c) {
		//c.disconnect(); this would probably be needed for copy directory, as there is a bug in jsch-0.1.20
		s.disconnect();
	}
	
	protected int determineRemoteOS() throws GATObjectCreationException{
	    try {
	    channel = session.openChannel("exec");
	    ((ChannelExec)channel).setCommand("ls");
	    InputStream err = ((ChannelExec)channel).getErrStream();
    	    channel.connect();
    	    while(true) {
    		if(channel.isEOF()) break;
    	    }
    	    if(GATEngine.DEBUG){
    				System.err.println("SshFileAdaptor: determineOS");
    		} 
    	    if(err.available() != 0) {
				channel = session.openChannel("exec");
				((ChannelExec)channel).setCommand("dir");
				err = ((ChannelExec)channel).getErrStream();
    			channel.connect();
    			while(true) {
    		    	if(channel.isEOF()) break;
    			} 
    			if(GATEngine.DEBUG){
    				System.err.println("SshFileAdaptor: determineOS");
    			}
    			if(err.available() != 0) {
    				cleanSession(session, channel);
		    		throw new GATObjectCreationException("Unkown remote OS");
				}
				else {
					cleanSession(session, channel);
					return WOS;
				}
	    	}
	    	else {
	    		cleanSession(session, channel);
	    		return XOS;
	    	}
	    } catch(Exception e) {throw new Error("internal error in SshFile: " + e);}
	}
	
	// Make life a bit easier for the programmer:
	protected URI correctURI(URI in) {
		if (in.getScheme() == null) {
			try {				return new URI("sftp:" + in.toString());
			} catch (URISyntaxException e) {
				throw new Error("internal error in SshFile: " + e);
			}
		}
		
		return in;
		/*
		 * URI tmpLocation = in;
		 * 
		 * if (in.getScheme() == null) { java.io.File tmp = new
		 * java.io.File(in.toString()); tmpLocation = tmp.toURI(); }
		 * 
		 * return tmpLocation;
		 */
	}

	/**
	 * This method copies the physical file represented by this File instance to
	 * a physical file identified by the passed URI.
	 * 
	 * @param loc
	 *            The new location
	 * @throws java.io.IOException
	 *             Upon non-remote IO problem
	 */
	public void copy(URI loc) throws GATInvocationException {
		/*if same machine
	     *	if same user
	     *		if same file
	     *			do nothing
	     *		else
	     *			copy oldfile newfile
	     *	else
	     *		if auth succeeded 
	     *			copy oldfile absolutePath(newfile)
	     *		else
	     *			fail
	     *else
	     *	if loc is local
	     *		scp remoteSource to local (ScpFrom)
	     *	else
	     *		if auth succeeded
	     *			scp remoteSource to remoteDestination		
	     *		else
	     *			fail
	     **/
	    if(loc == null) return;	    
	    if(isLocalFile) {
	    	if(loc.isLocal()) 
	    		throw new GATInvocationException(
	    			"SshFileAdaptor:the source file is local ("+ getPath() +
	    		    "), then the destination file must be remote, path = " + loc.getPath());
	    	f = new File(getPath());
	    	if(!f.exists())
	    		throw new GATInvocationException("SshFileAdaptor:the local source file does not exist, path = " + getPath());
	    	scpFromLocalToRemote(loc);	    	
	    	return;	
	    }
	    if(itExists == UNKNOWN) exists();
	    if(itExists == FALSE) {
				throw new GATInvocationException("the remote source file does not exist, path = " + toURI());
		}    
	    if(location.getHost().equals(loc.getHost())) {
	    	/*should consider loc.getUserInfo to test for null, and take it as being 
	    	 *either the same as this one or from the preferences, as being the default
	    	 *for the loc.getHost*/
		    if(loc.getUserInfo().equals(userName)) {
		    	if (loc.equals(toURI())) {
					if (GATEngine.DEBUG) {
						System.err.println("remote copy, source is the same file as dest.");
					}
					return;			
		    	}
		    	else {
		    		if (GATEngine.DEBUG) {
						System.err.println("remote copy, source is on the same host and of same user as dest.");
					}
		    		copyOnSameHost(loc.getPath());
		    		return;
		    	}
		  	}
		  	else {
		  		/* don't think it's such a good idea, unless the user has write-access for the 
		  		 *other account as well
		  		 */
		  		try {
		  		org.gridlab.gat.io.File dest = GAT.createFile(gatContext, preferences, loc);
		  		copyOnSameHost(dest.getAbsolutePath());
		  		} catch(Exception e) {
		  			/*try to copy using third party*/		  					  		
		  			if (GATEngine.DEBUG) {
						System.err.println("remote copy, source: user " + userName + " host " 
								+ location.getHost() + " dest: user " + loc.getUserInfo() + 
								" host " + loc.getHost());
					}
		  			throw new Error("Not yet implemented");	
		  		}
		    	return;		    	
		  	}		  	
		}		
		else {
			if(loc.isLocal()) {
			/*the URI is local, on this machine*/
			    if (GATEngine.VERBOSE) {
				System.err.println("local copy of remote file " + toURI() + " to "
						+ loc.getPath());
			    }
			  /* this should be tested somewherelse*/
			  /*	
			    if (isDir == UNKNOWN) {
			    	isDirectory();	
			    }
			    if (isDir == TRUE){
					if (GATEngine.DEBUG) {
						System.err.println("local copy, remote is a dir");
					}
					copyDirectory(gatContext, preferences, toURI(), loc);
					return;
			    }
	
			    if (GATEngine.DEBUG) {
				System.err.println("local copy, remote is a file");
			    }
			  */
			    scpFromRemoteToLocal(loc);
			  }
		  	else {
		  		/*third party transfer:*/		  		
		  		if (GATEngine.DEBUG) {
					System.err.println("remote copy, source: user " + userName + " host " 
										+ location.getHost() + " dest: user " + loc.getUserInfo() + 
										" host " + loc.getHost());
				}
		  		throw new Error("Not yet implemented");
		  	}
		}
		
	}
 
  /*copies local file to remote loc file*/
  protected void scpFromLocalToRemote(URI loc) throws GATInvocationException {
	  
	  try{
	  	
	  /*prepare Session for remote location of destination file*/
	  prepareSession(loc);
  	   
	  session = jsch.getSession(userName, loc.getHost(), Integer.parseInt(portNumber));
	  setUserInfo();
	  session.connect();
  	  
  	  // exec 'scp -t rfile' remotely
	  String command="scp -p -t " + loc.getPath();
	  channel=session.openChannel("exec");
	  ((ChannelExec)channel).setCommand(command);
	
	  // get I/O streams for remote scp
	  OutputStream out=channel.getOutputStream();
	  InputStream in=channel.getInputStream();
	
	  channel.connect();
	
	  byte[] tmp=new byte[1];
	
	  if(checkAck(in)!=0){
	  	channel.disconnect();	    	
	  	cleanSession(session, channel);
		throw new GATInvocationException("SshFileAdaptor: failed checkAck in scpFromLocalToRemote " 
					+ getPath() + " to " + loc);
	  }
	
	  // send "C0644 filesize filename", where filename should not include '/'
	  int filesize=(int)f.length();
	  command="C0644 "+filesize+" ";
	  command+=f.getName();
	  command+="\n";
	  out.write(command.getBytes()); out.flush();
	
	  if(checkAck(in)!=0){
	  	channel.disconnect();	    	
	  	cleanSession(session, channel);
		throw new GATInvocationException("SshFileAdaptor: failed checkAck in scpFromLocalToRemote " 
					+ getPath() + " to " + loc);
	  }
	
	  // send a content of lfile
	  FileInputStream fis=new FileInputStream(f.getPath());
	  byte[] buf=new byte[1024];
	  while(true){
	    int len=fis.read(buf, 0, buf.length);
		if(len<=0) break;
	    	out.write(buf, 0, len); out.flush();
	  }
	
	  // send '\0'
	  buf[0]=0; out.write(buf, 0, 1); out.flush();
	
	  if(checkAck(in)!=0){
	  	channel.disconnect();	    	
	  	cleanSession(session, channel);
		throw new GATInvocationException("SshFileAdaptor: failed checkAck in scpFromLocalToRemote " 
					+ getPath() + " to " + loc);
	  }	
	  channel.disconnect();	    	
	  cleanSession(session, channel);
	  return;
	}
	catch(Exception e) {
		if (GATEngine.DEBUG) {
				System.err.println("SshFileAdaptor: for location " + location + " throws error " + e);
				e.printStackTrace();
		}
		channel.disconnect();	    	
		cleanSession(session, channel);
	   	throw new GATInvocationException("SshFileAdaptor: internal error: " + e + " in scpFromLocalToRemote " 
					+ f.getPath() + " to " + loc);
	}
  }

  /*copies from this remote file to loc file*/
  protected void scpFromRemoteToLocal(URI loc) throws GATInvocationException {  	
  	
  	File tmpFile = new File(loc.getPath());
	// if the destination URI is a dir, append the file name.
	if (tmpFile.isDirectory()) {
		String u = loc.toString() + "/" + getName();
		try {
			loc = new URI(u);
		} catch (URISyntaxException e) {			
			throw new GATInvocationException("default file", e);
		}
	}
	
	// Create destination file
	File destinationFile = new File(loc.getPath());
	
	if (GATEngine.DEBUG) {
		System.err.println("creating local file " + destinationFile);
	}
	
	try {
	    destinationFile.createNewFile();
	} catch (IOException e) {
	    throw new GATInvocationException("ssh file", e);
	}
	
	// Copy source to destination
	
	try{
		
	session = jsch.getSession(userName, location.getHost(), Integer.parseInt(portNumber));
	session.connect();

	// exec 'scp -f rfile' remotely
	String command="scp -f "+ getPath();
	Channel channel=session.openChannel("exec");
	((ChannelExec)channel).setCommand(command);
	
	// get I/O streams for remote scp
	OutputStream out=channel.getOutputStream();
	InputStream in=channel.getInputStream();
	
	channel.connect();
	
	byte[] buf=new byte[1024];
	
	// send '\0'
	buf[0]=0; out.write(buf, 0, 1); out.flush();
	
	while(true){
		int c=checkAck(in);
		if(c!='C') break;

		// read '0644 '
		in.read(buf, 0, 5);

		int filesize=0;
		while(true){
	    	in.read(buf, 0, 1);
	    	if(buf[0]==' ') break;
	    	filesize=filesize*10+(buf[0]-'0');
		}

		String file=null;
		for(int i=0;;i++){
	    	in.read(buf, i, 1);
	    	if(buf[i]==(byte)0x0a){
				file=new String(buf, 0, i);
				break;
	    	}
		}

		// send '\0'
		buf[0]=0; out.write(buf, 0, 1); out.flush();

		// read a content of lfile
		FileOutputStream fos=new FileOutputStream(destinationFile);
		int foo;
		while(true){
	    	if(buf.length<filesize) foo=buf.length;
	    		else foo=filesize;
	    	in.read(buf, 0, foo);
	    	fos.write(buf, 0, foo);
	    	filesize-=foo;
	    	if(filesize==0) break;
		}
		fos.close();

		byte[] tmp=new byte[1];

		if(checkAck(in)!=0){
			channel.disconnect();
			cleanSession(session, channel);
	    	throw new GATInvocationException("SshFileAdaptor: failed checkAck in scpFromRemoteToLocal " 
					+ getPath() + " to " + loc);
		}
			// send '\0'
		buf[0]=0; out.write(buf, 0, 1); out.flush();
	}
		channel.disconnect();
		cleanSession(session, channel);
		return;
	} catch(Exception e) {
		if (GATEngine.DEBUG) {
				System.err.println("SshFileAdaptor: for location " + location + " throws error " + e);
				e.printStackTrace();
		}
		channel.disconnect();
		cleanSession(session, channel);
	   	throw new GATInvocationException("SshFileAdaptor: internal error: " + e + " in scpFromRemoteToLocal " 
					+ getPath() + " to " + loc);
	} 
  }

  protected void copyOnSameHost(String path) throws GATInvocationException {
  		try {
  		
  		session = jsch.getSession(userName, location.getHost(), Integer.parseInt(portNumber));
	  	session.connect();
  	  
	    channel = session.openChannel("exec");
	    InputStream err;
	    switch(osType) {
		case XOS :  ((ChannelExec)channel).setCommand("cp " + getPath() + " " + path);
			    	err = ((ChannelExec)channel).getErrStream();    			    
    			    channel.connect();
    			    while(true) {
    					if(channel.isEOF()) break;
    			    } 
    			    if(err.available() != 0) {
    			    	cleanSession(session, channel);
    			    	throw new GATInvocationException("cannot copy remote file to another file on the same machine");
    			   	}
    			   	cleanSession(session, channel);
    			    return;
		case WOS :  ((ChannelExec)channel).setCommand("copy " + toMW(getPath()) + " " + toMW(path));
			    	err = ((ChannelExec)channel).getErrStream();    			    
    			    channel.connect();
    			    while(true) {
    					if(channel.isEOF()) break;
    			    } 
    			    if(err.available() != 0) { 
    			    	cleanSession(session, channel);
    			    	throw new GATInvocationException("cannot copy remote file to another file on the same machine");
    			   	}
    			   	cleanSession(session, channel);
    			    return;
	    }	    
	    cleanSession(session, channel);
	    throw new Error("Unknown remote OS type");	    
	    } catch(Exception e) {
			if (GATEngine.DEBUG) {
				System.err.println("SshFileAdaptor: for location " + location + " throws error " + e);
				e.printStackTrace();
			}	    	
			cleanSession(session, channel);
	   		throw new GATInvocationException("SshFileAdaptor: internal error: " + e + " in copyOnSameHost " 
					+ getPath() + " to " + path);
		}  	
  }

  protected static int checkAck(InputStream in) throws IOException{
    int b=in.read();
    // b may be 0 for success,
    //          1 for error,
    //          2 for fatal error,
    //          -1
    if(b==0) return b;
    if(b==-1) return b;

    if(b==1 || b==2){
      StringBuffer sb=new StringBuffer();
      int c;
      do {
	c=in.read();
	sb.append((char)c);
      }
      while(c!='\n');
      if(b==1){ // error
	System.out.print(sb.toString());
      }
      if(b==2){ // fatal error
	System.out.print(sb.toString());
      }
    }
    return b;
  }

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#canRead()
	 */
	public boolean canRead() {
		if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
		try {
	    
	    session = jsch.getSession(userName, location.getHost(), Integer.parseInt(portNumber));
	  	session.connect();
  	 
	    channel = session.openChannel("exec");
	    InputStream err, res;
	    switch(osType) {
		case XOS :  ((ChannelExec)channel).setCommand("test -r " + getPath() + 
													  " && echo 0");
			    	err = ((ChannelExec)channel).getErrStream();
    			    res = ((ChannelExec)channel).getInputStream();
    			    channel.connect();
    			    while(true) {
    				if(channel.isEOF()) break;
    			    } 
    			    if(err.available() != 0) { 
    			    	cleanSession(session, channel);
    			    	return false;
    			   	}
    			    if(res.available() != 0) { 
    			    	cleanSession(session, channel);
    			    	return true;
    			   	}
    			   	cleanSession(session, channel);
			    	return false;
		case WOS : cleanSession(session, channel); throw new Error("not implemented yet");	    
	    }	    
	    cleanSession(session, channel);
	    throw new Error("Unknown remote OS type");	    
	    } catch(Exception e) {
	    	cleanSession(session, channel);
	    	throw new Error("internal error in SshFile: " + e);
	    }	
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#canWrite()
	 */
	public boolean canWrite() {
		if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
		try {
	    
	    session = jsch.getSession(userName, location.getHost(), Integer.parseInt(portNumber));
	  	session.connect();
  	 
	    channel = session.openChannel("exec");
	    InputStream err, res;
	    switch(osType) {
		case XOS :  ((ChannelExec)channel).setCommand("test -w " + getPath() + 
													  " && echo 0");
			    	err = ((ChannelExec)channel).getErrStream();
    			    res = ((ChannelExec)channel).getInputStream();
    			    channel.connect();
    			    while(true) {
    					if(channel.isEOF()) break;
    			    } 
    			    if(err.available() != 0) { 
    			    	cleanSession(session, channel);
    			    	return false;
    			   	}
    			    if(res.available() != 0) { 
    			    	cleanSession(session, channel);
    			    	return true;
    			   	}
    			   	cleanSession(session, channel);
			    	return false;
		case WOS : cleanSession(session, channel); throw new Error("not implemented yet");	    
	    }	    
	    cleanSession(session, channel);
	    throw new Error("Unknown remote OS type");	    
	    } catch(Exception e) {
	    	cleanSession(session, channel);
	    	throw new Error("internal error in SshFile: " + e);
	    }
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#compareTo(gat.io.File)
	 */
	public int compareTo(org.gridlab.gat.io.File other) {
		return super.compareTo(other);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#compareTo(java.lang.Object)
	 */
	public int compareTo(Object other) {
		return super.compareTo(other);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#createNewFile()
	 */
	public boolean createNewFile() throws GATInvocationException {
		if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
		try {
	 	
	 	session = jsch.getSession(userName, location.getHost(), Integer.parseInt(portNumber));
	  	session.connect();
  	 
	    channel = session.openChannel("exec");
	    InputStream err;
	    switch(osType) {
		case XOS :  ((ChannelExec)channel).setCommand("test ! -d " + getPath() + 
													  " && test ! -f " + getPath() + 
													  " && touch " + getPath());
			    	err = ((ChannelExec)channel).getErrStream();    			    
    			    channel.connect();
    			    while(true) {
    				if(channel.isEOF()) break;
    			    } 
    			    if(err.available() != 0) { 
    			    	cleanSession(session, channel);
    			    	return false;
    			   	}
    			   	cleanSession(session, channel);
    			    return true;
		case WOS :  String mwPath = toMW(getPath());
					((ChannelExec)channel).setCommand("dir " + mwPath + 
													  " || cd . > " + mwPath);
			    	err = ((ChannelExec)channel).getErrStream();    			    
    			    channel.connect();
    			    while(true) {
    				if(channel.isEOF()) break;
    			    } 
    			    if(err.available() != 0) { 
    			    	cleanSession(session, channel);
    			    	return false;
    			   	}
    			   	cleanSession(session, channel);
    			    return true;
	    }	    
	    cleanSession(session, channel);
	    throw new Error("Unknown remote OS type");	    
	    } catch(Exception e) {
	    	cleanSession(session, channel);
		    throw new GATInvocationException("ssh file", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#delete()
	 */
	public boolean delete() {
		if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
		try {
	    
	    if(isDir == UNKNOWN) { 
	    	isDirectory();
	    }
	    
	    session = jsch.getSession(userName, location.getHost(), Integer.parseInt(portNumber));
	  	session.connect();
  	 
	    channel = session.openChannel("exec");
	    
	    InputStream err;
	    switch(osType) {
		case XOS :  if(isDir == TRUE)
						((ChannelExec)channel).setCommand("rmdir " + getPath());
					else 
						((ChannelExec)channel).setCommand("rm -f " + getPath());
			    	err = ((ChannelExec)channel).getErrStream();
    			    channel.connect();
    			    while(true) {
    				if(channel.isEOF()) break;
    			    } 
    			    if(err.available() != 0) { 
    			    	itExists = TRUE;
    			    	cleanSession(session, channel);
    			    	return false;
    			    }
    			    itExists = FALSE;
    			    cleanSession(session, channel);
    			    return true;
		case WOS :  if(isDir == TRUE)
						((ChannelExec)channel).setCommand("rmdir " + toMW(getPath()));
					else 
						((ChannelExec)channel).setCommand("del /q" + toMW(getPath()));
			    	err = ((ChannelExec)channel).getErrStream();
    			    channel.connect();
    			    while(true) {
    				if(channel.isEOF()) break;
    			    } 
    			    if(err.available() != 0) { 
    			    	itExists = TRUE;
    			    	cleanSession(session, channel);
    			    	return false;
    			    }
    			    itExists = FALSE;
    			    cleanSession(session, channel);
    			    return true;
	    }	    
	    cleanSession(session, channel);
	    throw new Error("Unknown remote OS type");	    
	    } catch(Exception e) {
	    	cleanSession(session, channel);
	    	throw new Error("internal error in SshFile: " + e);
	    }
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#exists()
	 */
	public boolean exists() throws GATInvocationException {
		if(isLocalFile) throw new GATInvocationException("SshFileAdaptor for local files: only copy to remote machine");
	    try {
	    
	    session = jsch.getSession(userName, location.getHost(), Integer.parseInt(portNumber));
	    session.connect();
	    
	    channel = session.openChannel("exec");
	    InputStream err;
	    switch(osType) {
		case XOS :  ((ChannelExec)channel).setCommand("ls -log " + getPath());
			    	err = ((ChannelExec)channel).getErrStream();
    			    channel.connect();
    			    while(true) {
    					if(channel.isEOF()) break;
    			    } 
    			    if(err.available() != 0) {    			    	
    			    	itExists = FALSE;
    			    	if (GATEngine.DEBUG) {
							System.err.println("SshFileAdaptor: for location " + location + " file does not exist");
						}
						cleanSession(session, channel);
    			    	return false;
    			    }
			    	itExists = TRUE;
			    	if (GATEngine.DEBUG) {
							System.err.println("SshFileAdaptor: for location " + location + " file does exist");
					}
					cleanSession(session, channel);
			    	return true;
		case WOS :	((ChannelExec)channel).setCommand("dir /B" + toMW(getPath()));
			    	err = ((ChannelExec)channel).getErrStream();
    			    channel.connect();
    			    while(true) {
    				if(channel.isEOF()) break;
    			    } 
    			    if(err.available() != 0) {    			    	
    			    	itExists = FALSE;
    			    	if (GATEngine.DEBUG) {
							System.err.println("SshFileAdaptor: for location " + location + " file does not exist");
						}
						cleanSession(session, channel);
    			    	return false;
    			    }
			    	itExists = TRUE;
			    	if (GATEngine.DEBUG) {
							System.err.println("SshFileAdaptor: for location " + location + " file does exist");
					}
					cleanSession(session, channel);
			    	return true;
	    }	    
	    cleanSession(session, channel);
	    throw new GATInvocationException("Unknown remote OS type");	    
	    } catch(Exception e) {
	    	if (GATEngine.DEBUG) {
				System.err.println("SshFileAdaptor: for location " + location + " throws error " + e);
				e.printStackTrace();
			}	    	
			cleanSession(session, channel);
	    	throw new GATInvocationException("internal error in SshFile: " + e);	    	
	    }
	}
	
	protected String toMW(String path) {
	    return path.replace('/', '\\');
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#getAbsoluteFile()
	 */
	public org.gridlab.gat.io.File getAbsoluteFile() throws GATInvocationException {
		if(isLocalFile) throw new GATInvocationException("SshFileAdaptor for local files: only copy to remote machine");
		String uriString = location.toString();		
		String absUri = "//" + userName + "@" + location.getHost() + ":" + portNumber + 
						"/" + getAbsolutePath();
		try{
			return GAT.createFile(gatContext, preferences, new URI(absUri));
		} catch(Exception e) {throw new GATInvocationException("SshFileAdaptor: getAbsoluteFile: " + e);}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#getAbsolutePath()
	 */
	public String getAbsolutePath() throws GATInvocationException {
		if(isLocalFile) throw new GATInvocationException("SshFileAdaptor for local files: only copy to remote machine");
	    try {
	    
	    session = jsch.getSession(userName, location.getHost(), Integer.parseInt(portNumber));
	  	session.connect();
  	 
	    channel = session.openChannel("exec");
	    InputStream err, res;
	    StringBuffer sb;
	    int resLength;
	    switch(osType) {
		case XOS :  if(getPath().startsWith("/")) return getPath();
					((ChannelExec)channel).setCommand("echo ~" + userName);
			    	err = ((ChannelExec)channel).getErrStream();
			    	res = ((ChannelExec)channel).getInputStream();
    			    channel.connect();
    			    while(true) {
    				if(channel.isEOF()) break;
    			    } 
    			    if(err.available() != 0) {
    			    	cleanSession(session, channel);    			    	
    			    	throw new GATInvocationException("internal error in SshFileAdaptor: getAbsolutePath ");
    			    }
			    	sb = new StringBuffer();
			    	resLength = res.available() - 1; /*we do not care for the final \n*/
			    	for(int i=0; i<resLength ; i++)
			    		sb.append((char)res.read());
			    	if (GATEngine.DEBUG) {
							System.err.println("SshFileAdaptor: getAbsolutePath for location " + location);
					}
					cleanSession(session, channel);
			    	return sb.toString() + getPath();
		case WOS :	cleanSession(session, channel); throw new Error("Not implemented");
	    }	    
	    throw new GATInvocationException("Unknown remote OS type");	    
	    } catch(Exception e) { 
	    	cleanSession(session, channel);
	    	throw new Error("internal error in SshFile: " + e);
	    }
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#getCanonicalFile()
	 */
	public org.gridlab.gat.io.File getCanonicalFile() throws GATInvocationException {
		if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
		throw new Error("Not implemented");	
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#getCanonicalPath()
	 */
	public String getCanonicalPath() throws GATInvocationException {
		if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
		throw new Error("Not implemented");	
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#getParent()
	 */
	public String getParent() {
		if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
		String path = getPath();
		String parentPath;
	    if(path.endsWith("/")) {
	    	parentPath = path.substring(0,path.lastIndexOf('/',path.length()-2));	    	
	 	}
	    else {
	    	parentPath = path.substring(0,path.lastIndexOf('/'));
	 	}
	 	if(parentPath.length() == 0) return null;
	 	return parentPath;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#getParentFile()
	 */
	public org.gridlab.gat.io.File getParentFile() throws GATInvocationException {
		if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
	    String uriString = location.toString();
		String parentUri = uriString.split(":")[0] + ":" + getParent();
		try{
			return GAT.createFile(gatContext, preferences, new URI(parentUri));
		} catch(Exception e) {throw new GATInvocationException("SshFileAdaptor: getAbsoluteFile: " + e);}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#isAbsolute()
	 */
	public boolean isAbsolute() {		
		switch(osType) {
		case XOS : if(getPath().startsWith("/")) return true;
				   else return false;
		case WOS : throw new Error("Not implemented");	
		default  : throw new Error("Unknown remote OS type");
	    }
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#isDirectory()
	 */
	public boolean isDirectory() {
		if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
	    try {
	        
	    String path = null;	    
	    switch(osType) {
		case XOS : path = getPath(); break;
		case WOS : path = toMW(getPath()); break;
		default  : throw new Error("Unknown remote OS type");
	    }
	    
	    session = jsch.getSession(userName, location.getHost(), Integer.parseInt(portNumber));
	  	session.connect();
  	 
	    channel = session.openChannel("exec");
	    ((ChannelExec)channel).setCommand("cd " + path);
	    InputStream err = ((ChannelExec)channel).getErrStream();
    	    channel.connect();
    	    while(true) {
    		if(channel.isEOF()) break;
    	    } 
    	    if(err.available() != 0) { 
    	    	isDir = FALSE;
    	    	cleanSession(session, channel);
    	    	return false;
    	    }
	    else {
	    	isDir = TRUE;
	    	cleanSession(session, channel);
	    	return true;
	 	}
	    } catch(Exception e) {
	    	cleanSession(session, channel);
	    	throw new Error("internal error in SshFile: " + e);
	    }
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#isFile()
	 */
	public boolean isFile() {
	    /*at least for now*/
	    if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
		return (!isDirectory());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#isHidden()
	 */
	public boolean isHidden() {
		if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
		
		switch(osType) {
	    	/*assume name of the file is not . or ..*/
		case XOS :  if(getName().startsWith(".")) return true;
			    	else return false;
		case WOS :	throw new Error("Not implemented");
	    }	    
	    throw new Error("Unknown remote OS type");	    	    
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#lastModified()
	 */
	public long lastModified() throws GATInvocationException {
		throw new Error("Not implemented");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#length()
	 */
	public long length() throws GATInvocationException {
		if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
		
		try {
		InputStream err, res;
	    int resLength;
	    StringBuffer sb;

		if(itExists == UNKNOWN) { exists(); }
		if(itExists == FALSE) { return 0L; }
	    
	    session = jsch.getSession(userName, location.getHost(), Integer.parseInt(portNumber));
	  	session.connect();
  	 
	    channel = session.openChannel("exec");
	    
	    switch(osType) {
		case XOS :  ((ChannelExec)channel).setCommand("wc -c < " + getPath());
			    	err = ((ChannelExec)channel).getErrStream();
			    	res = ((ChannelExec)channel).getInputStream();
    			    channel.connect();
    			    while(true) {
    					if(channel.isEOF()) break;
    			    } 
    			    if(err.available() != 0) { 
    			    	cleanSession(session, channel);
    			    	return -1;
    			   	}
			    	/*get the value from res*/
			    	sb = new StringBuffer();
			    	resLength = res.available() - 1; /*we do not care for the final \n*/
			    	for(int i=0; i<resLength ; i++)
			    		sb.append((char)res.read());
			    	cleanSession(session, channel);
			    	return Long.parseLong(sb.toString().replaceAll("[ \t\n\f\r]",""));
			    		
		case WOS :	((ChannelExec)channel).setCommand("dir /-C " + toMW(getPath()));
			    	err = ((ChannelExec)channel).getErrStream();
			    	res = ((ChannelExec)channel).getInputStream();
    			    channel.connect();
    			    while(true) {
    				if(channel.isEOF()) break;
    			    } 
    			    if(err.available() != 0) {
    			    	cleanSession(session, channel);
    			    	return -1;
    			   	}
			    	/*get the value from res*/
			    	sb = new StringBuffer();
			    	resLength = res.available();
			    	for(int i=0; i<resLength ; i++)
			    		sb.append((char)res.read());
			    	cleanSession(session, channel);
			    	/*process the returned string and extract the long value from it*/
			    	return Long.parseLong(sb.toString().replaceAll("[ \t\n\f\r]",""));
	    }	    
	    cleanSession(session, channel);
	    throw new Error("Unknown remote OS type");	    
	    } catch(Exception e) { 
	    	cleanSession(session, channel);
	    	throw new Error("internal error in SshFile: " + e);
	    }
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#list()
	 */
	public String[] list() throws GATInvocationException {
		if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
		try {
		
		InputStream err, res;
	    int resLength;
	    StringBuffer sb;
	    List resultList;
	    String output;
	    StringTokenizer st;
	    
		if(isDir == UNKNOWN) isDirectory();
		if(isDir == FALSE) return null;
		
		session = jsch.getSession(userName, location.getHost(), Integer.parseInt(portNumber));
	  	session.connect();
  	 
	    channel = session.openChannel("exec");
	    
	    switch(osType) {
		case XOS :  ((ChannelExec)channel).setCommand("ls -1 " + getPath());
			    	err = ((ChannelExec)channel).getErrStream();
			    	res = ((ChannelExec)channel).getInputStream();
    			    channel.connect();
    			    while(true) {
    					if(channel.isEOF()) break;
    			    } 
    			    if(err.available() != 0) { 
    			    	cleanSession(session, channel);
    			    	return null;
    			   	}
			    	break;
			    	
		case WOS :	((ChannelExec)channel).setCommand("dir /-C " + toMW(getPath()));
			    	err = ((ChannelExec)channel).getErrStream();
			    	res = ((ChannelExec)channel).getInputStream();
    			    channel.connect();
    			    while(true) {
    				if(channel.isEOF()) break;
    			    } 
    			    if(err.available() != 0) { 
    			    	cleanSession(session, channel);
    			    	return null;
    			   	}
			    	break;
			    	
		default :	cleanSession(session, channel); throw new Error("Unknown remote OS type");
	    }	    
	    sb = new StringBuffer();
		resLength = res.available(); 
		for(int i=0; i<resLength ; i++)
			sb.append((char)res.read());
			    	
		resultList = new ArrayList();
		output = sb.toString();
		st = new StringTokenizer(output, "\n\r\f");
		while(st.hasMoreTokens()) {
			resultList.add(st.nextToken());
		}
		cleanSession(session, channel);
		return (String[])resultList.toArray();
	    } catch(Exception e) { 
	    	cleanSession(session, channel);
	    	throw new Error("internal error in SshFile: " + e);
	    }
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#list(java.io.FilenameFilter)
	 */
	public String[] list(FilenameFilter arg0) throws GATInvocationException {
		if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
		if(arg0 == null) return list();
		String[] fileList = list();
		java.io.File dir = new java.io.File(getPath());		
		List resultList = new ArrayList();
		for(int i=0; i<fileList.length; i++) {
			if(arg0.accept(dir,fileList[i])) resultList.add(fileList[i]);	
		}
		return (String[])resultList.toArray();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#listFiles()
	 */
	public org.gridlab.gat.io.File[] listFiles() throws GATInvocationException {
		if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
		String[] r = list();
		org.gridlab.gat.io.File[] res = new org.gridlab.gat.io.File[r.length];
		String uri = location.toString();
		if(!uri.endsWith("/")) uri += "/";

		for (int i = 0; i < r.length; i++) {
			try {
				res[i] = GAT.createFile(gatContext, preferences, new URI(uri + r[i]));
			} catch (Exception e) {
				throw new GATInvocationException("default file", e);
			}
		}
		return res;	    
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#listFiles(java.io.FileFilter)
	 */
	public org.gridlab.gat.io.File[] listFiles(FileFilter arg0)
			throws GATInvocationException {
	   if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
		String[] r = list();
		org.gridlab.gat.io.File[] res = new org.gridlab.gat.io.File[r.length];
		String dir = getPath();
		String uri = location.toString();
		if(!dir.endsWith("/")) { 
			dir += "/";
			uri += "/";
		}

		for (int i = 0; i < r.length; i++) {
			try {
				if(arg0.accept(new java.io.File(dir + r[i]))) 
					res[i] = GAT.createFile(gatContext, preferences, new URI(uri + r[i]));
			} catch (Exception e) {
				throw new GATInvocationException("default file", e);
			}
		}
		return res;	   
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#listFiles(java.io.FilenameFilter)
	 */
	public org.gridlab.gat.io.File[] listFiles(FilenameFilter arg0)
			throws GATInvocationException {
	    if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
		String[] r = list(arg0);
		org.gridlab.gat.io.File[] res = new org.gridlab.gat.io.File[r.length];
		String uri = location.toString();
		if(!uri.endsWith("/")) uri += "/";
				
		for (int i = 0; i < r.length; i++) {
			try {
				res[i] = GAT.createFile(gatContext, preferences, new URI(uri + r[i]));
			} catch (Exception e) {
				throw new GATInvocationException("default file", e);
			}
		}
		return res;	
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#mkdir()
	 */
	public boolean mkdir() throws GATInvocationException {
		if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
	    try {
	    	
	    session = jsch.getSession(userName, location.getHost(), Integer.parseInt(portNumber));
	  	session.connect();
  	 	
	    channel = session.openChannel("exec");
	    InputStream err;
	    switch(osType) {
		case XOS :  ((ChannelExec)channel).setCommand("mkdir " + getPath());
			    	err = ((ChannelExec)channel).getErrStream();
    			    channel.connect();
    			    while(true) {
    				if(channel.isEOF()) break;
    			    } 
    			    if(err.available() != 0) { 
    			    	cleanSession(session, channel);
    			    	return false;
    			   	}
			    	else { 
			    		cleanSession(session, channel);
			    		return true;
			    	}
		case WOS :	cleanSession(session, channel); throw new Error("Not yet implemented");	    
	    }	    
	    cleanSession(session, channel);
	    throw new Error("Unknown remote OS type");	    
	    } catch(Exception e) { 
	    	cleanSession(session, channel);
	    	throw new Error("internal error in SshFile: " + e);
	    }
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#mkdirs()
	 */
	public boolean mkdirs() {
		if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
	    try {
	    	
	    session = jsch.getSession(userName, location.getHost(), Integer.parseInt(portNumber));
	  	session.connect();
  	 
	    channel = session.openChannel("exec");
	    InputStream err;
	    switch(osType) {
		case XOS :  ((ChannelExec)channel).setCommand("mkdir -p " + getPath());
			    	err = ((ChannelExec)channel).getErrStream();
    			    channel.connect();
    			    while(true) {
    				if(channel.isEOF()) break;
    			    } 
    			    if(err.available() != 0) {
    			    	cleanSession(session, channel);
    			    	return false;
    			   	}    			   	
			    	else { 
			    		cleanSession(session, channel);
			    		return true;
			 		}
		case WOS :	((ChannelExec)channel).setCommand("mkdir " + toMW(getPath()));
			    	err = ((ChannelExec)channel).getErrStream();
    			    channel.connect();
    			    while(true) {
    				if(channel.isEOF()) break;
    			    } 
    			    if(err.available() != 0) {
    			    	cleanSession(session, channel);
    			    	return false;
    			   	}    			   	
			    	else { 
			    		cleanSession(session, channel);
			    		return true;
			 		}
	    }
	   	cleanSession(session, channel);
	    throw new Error("Unknown remote OS type");	    
	    } catch(Exception e) { 
	    	cleanSession(session, channel);
	    	throw new Error("internal error in SshFile: " + e);
	    }
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#move(java.net.URI)
	 */
	public void move(URI destination) throws GATInvocationException {
		if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
	     /*may be improved in some cases, but this is a more general approach*/
	     copy(destination);
	     if(!delete()) 
	     	throw new GATInvocationException
	     	("internal error in SshFileAdaptor: could not rename file " + toURI() + " to " + destination);
	     return;		     
	}

	public void renameTo(URI destination) throws GATInvocationException {	     
	if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
	     copy(destination);
	     if(!delete()) 
	     	throw new GATInvocationException
	     	("internal error in SshFileAdaptor: could not rename file " + toURI() + " to " + destination);
	     return;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#renameTo(java.io.File)
	 */
	public boolean renameTo(java.io.File arg0) throws GATInvocationException {
		if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
    	copy(new URI(arg0.toURI()));
		if(!delete()) return false;	     	
	    return true;				     
	}
	
	public boolean renameTo(org.gridlab.gat.io.File arg0) throws GATInvocationException {
		if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
		if(GATEngine.DEBUG){
			System.err.println("SshFileAdaptor: trying to rename " + location + " to " + arg0.toURI());
		}
    	copy(arg0.toURI());
		if(!delete()) return false;	     	
	    return true;				     
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#setLastModified(long)
	 */
	public boolean setLastModified(long arg0) {
		if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
		try {
		
		InputStream err;
			
		session = jsch.getSession(userName, location.getHost(), Integer.parseInt(portNumber));
	  	session.connect();
  	 	
	    channel = session.openChannel("exec");
	    
	    switch(osType) {
		case XOS :  ((ChannelExec)channel).setCommand("touch -t " + toTouchDateFormat(arg0) + " " + getPath());
			    	err = ((ChannelExec)channel).getErrStream();
    			    channel.connect();
    			    while(true) {
    				if(channel.isEOF()) break;
    			    } 
    			    if(err.available() != 0) {
    			    	cleanSession(session, channel);
    			    	return false;
    			   	}
			    	else {
			    		cleanSession(session, channel);
			    		return true;
			    	}
		case WOS :	cleanSession(session, channel); throw new Error("Not implemented");	
	    }	    
	    cleanSession(session, channel);
	    throw new Error("Unknown remote OS type");
	    } catch(Exception e) {
	    	cleanSession(session, channel);
	    	throw new Error("internal error in SshFile: " + e);
	    }
	}

	protected String toTouchDateFormat(long date) {
		Date d = new Date(date);
		String dString = d.toString();
		StringTokenizer st = new StringTokenizer(dString, " :");
		/*skip day of week*/
		st.nextToken();
		String month = st.nextToken();
		String m = new String();
		if(month.compareTo("Jan") == 0) m = "01";
		else if(month.compareTo("Feb") == 0) m = "02";
		else if(month.compareTo("Mar") == 0) m = "03";
		else if(month.compareTo("Apr") == 0) m = "04";
		else if(month.compareTo("May") == 0) m = "05";
		else if(month.compareTo("Jun") == 0) m = "06";
		else if(month.compareTo("Jul") == 0) m = "07";
		else if(month.compareTo("Aug") == 0) m = "08";
		else if(month.compareTo("Sep") == 0) m = "09";
		else if(month.compareTo("Oct") == 0) m = "10";
		else if(month.compareTo("Nov") == 0) m = "11";
		else if(month.compareTo("Dec") == 0) m = "12";
		String day = st.nextToken();
		String hour = st.nextToken();
		String min = st.nextToken();
		String sec = st.nextToken();
		String year = st.nextToken();
		return year+m+day+hour+min+"."+sec;
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gridlab.gat.io.File#setReadOnly()
	 */
	public boolean setReadOnly() {
		if(isLocalFile) throw new Error("SshFileAdaptor for local files: only copy to remote machine");
		try {
		
		InputStream err;
			
		session = jsch.getSession(userName, location.getHost(), Integer.parseInt(portNumber));
	  	session.connect();
  	 
	    channel = session.openChannel("exec");
	    
	    switch(osType) {
		case XOS :  ((ChannelExec)channel).setCommand("chmod a-w " + getPath());
			    	err = ((ChannelExec)channel).getErrStream();
    			    channel.connect();
    			    while(true) {
    				if(channel.isEOF()) break;
    			    } 
    			    if(err.available() != 0) {
    			    	cleanSession(session, channel);
    			    	return false;
    			   	}
			    	else {
			    		cleanSession(session, channel);
			    		return true;
			    	}
		case WOS :	cleanSession(session, channel); throw new Error("Not implemented");	
	    }	
	    cleanSession(session, channel);    
	    throw new Error("Unknown remote OS type");
	    } catch(Exception e) {
	    	cleanSession(session, channel);
	    	throw new Error("internal error in SshFile: " + e);
	    }
	}
}