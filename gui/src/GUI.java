
import com.jcraft.jsch.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.File;
import java.io.IOException;

/** 
 * Graphical User Interface class for GMEncoD.
 */
public class GUI{
    /**
     * Static method to copy files over a SSH connection used by GMEncoD
     * application to transfer the raw AVI file on the client's remote account.
     */
    public static boolean scpTo(String lfile, String rfile, Session session)
	throws Exception {
	/* 
	 * Execs 'scp -t rfile' remotely.
	 */
      	String command="scp -p -t "+rfile;
	Channel channel=session.openChannel("exec");
	((ChannelExec)channel).setCommand(command);
	OutputStream out=channel.getOutputStream();
	InputStream in=channel.getInputStream();
	channel.connect();
	if(checkAck(in)!=0){
	    channel.disconnect();
	    return false;
	}
	/* 
	 * Sends "C0644 filesize filename", where filename should not
	 * include '/'
	 */
	long filesize=(new File(lfile)).length();
	command="C0644 "+filesize+" ";
	if(lfile.lastIndexOf('/')>0){
	    command+=lfile.substring(lfile.lastIndexOf('/')+1);
	}
	else{
	    command+=lfile;
	}
	command+="\n";
	out.write(command.getBytes()); out.flush();

	if(checkAck(in)!=0){
	    channel.disconnect();
	    return false;
	}
	/* 
	 * Sends the content of lfile.
	 */
	FileInputStream fis=new FileInputStream(lfile);
	byte[] buf=new byte[1024];
	while(true){
	    int len=fis.read(buf, 0, buf.length);
	    if(len<=0) break;
	    out.write(buf, 0, len); out.flush();
	}
	/*
	 * Sends '\0'.
	 */
	buf[0]=0; out.write(buf, 0, 1); out.flush();

	if(checkAck(in)!=0){
	    channel.disconnect();		
	    return false;
	}
	/*
	 * Finishes copying file.
	 */
	channel.disconnect();      
	return true;
         
    }
  
    /**
     * This method is used for remote command execution it will execute the
     * user's encoding command on the remote machine.
     */ 
    public static boolean execRemoteCommand(String command, Session session) 
	throws Exception {
  	Channel channel = session.openChannel("exec");
  	((ChannelExec)channel).setCommand(command);
  	InputStream err = ((ChannelExec)channel).getErrStream();
	InputStream in = channel.getInputStream();

	channel.connect();
	byte[] tmp;

	while(true) {
	    if(channel.isEOF() && in.available()<=0) break;
	    while(in.available()>0){
		tmp = new byte[in.available()];
		int i=in.read(tmp, 0, in.available());
		if(i<0)break;
		System.out.print(new String(tmp, 0, i));
	    }
	} 
	System.out.println("job finished");
	if(err.available() != 0) { 
	    tmp=new byte[err.available()];
	    int i = err.read(tmp, 0, err.available());
	    System.out.print(new String(tmp, 0, i));
	    channel.disconnect();		
	    return false;
	}	
	channel.disconnect();
	return true;	
    }
 
    /**
     * Static method used to retrieve a file over a SSH connection
     * used by the GMEncoD application to retrieve the encoded file 
     * from the user's remote account.
     */
     public static boolean scpFrom(String lfile, String rfile, Session session) 
	throws Exception {
  	/*
	 * Execs 'scp -f rfile' remotely.
	 */
	String prefix=null;
	if(new File(lfile).isDirectory()){
	    prefix=lfile+File.separator;
	}
	String command="scp -f "+rfile;
	Channel channel=session.openChannel("exec");
	((ChannelExec)channel).setCommand(command);
	OutputStream out=channel.getOutputStream();
	InputStream in=channel.getInputStream();
	channel.connect();
	byte[] buf=new byte[1024];
	buf[0]=0; out.write(buf, 0, 1); out.flush();
	while(true){
	    int c=checkAck(in);
	    if(c!='C'){
		break;
	    }
	    /*
	     * Reads '0644 '.
	     */
	    in.read(buf, 0, 5);

	    long filesize=0;
	    while(true){
		in.read(buf, 0, 1);
		if(buf[0]==' ')break;
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
	    /*
	     * Sends '\0'.
	     */
	    buf[0]=0; out.write(buf, 0, 1); out.flush();
	    /* 
	     * Reads a content of lfile.
	     */
	    FileOutputStream fos=new FileOutputStream(prefix==null ? 
						      lfile :
						      prefix+file);
	    int foo;
	    while(true){
		if(buf.length<filesize) foo=buf.length;
		else foo=(int)filesize;
		in.read(buf, 0, foo);
		fos.write(buf, 0, foo);
		filesize-=foo;
		if(filesize==0) break;
	    }
	    fos.close();

	    byte[] tmp=new byte[1];

	    if(checkAck(in)!=0){
		channel.disconnect();
		return false;
	    }
	    /*
	     * Sends '\0'.
	     */
	    buf[0]=0; out.write(buf, 0, 1); out.flush();
	}
	channel.disconnect();
	return true;
    }
  
    public static void main(String[] arg){

	String remoteHome = "/var/scratch/slblond/";
    
	try{
	    /*
	     * Creates a secure channel container.
	     */
	    JSch jsch=new JSch();
	    /*
	     * Graphical interface for selecting the identity file used to
	     * authenticate the user on the remote side.
	     */
	    JFileChooser chooser = new JFileChooser();
	    chooser.setDialogTitle("Choose identity file");
	    chooser.setFileHidingEnabled(false);
	    int returnVal = chooser.showOpenDialog(null);
	    if(returnVal == JFileChooser.APPROVE_OPTION) {
		System.out.println("You chose "+
				   chooser.getSelectedFile().
				   getAbsolutePath()+".");
	    }

	    String idFile = chooser.getSelectedFile().getAbsolutePath();
	    /*
	     * Graphical interface for choosing the file which is going to be
	     * encoded.
	     */
	    chooser = new JFileChooser();
	    chooser.setDialogTitle("Choose the file you want to encode");
	    chooser.setFileHidingEnabled(false);
	    returnVal = chooser.showOpenDialog(null);
	    if(returnVal == JFileChooser.APPROVE_OPTION) {
		System.out.println("You chose "+
				   chooser.getSelectedFile().
				   getAbsolutePath()+".");
	    }
	    /* 
	     * Specifies the remote account where encoding of selected file 
	     * will start.
	     */
	    String host=JOptionPane.showInputDialog("Enter username@hostname",
						    "slblond@"+
						    "fs0.das2.cs.vu.nl");
	    String user=host.substring(0, host.indexOf('@'));
	    host=host.substring(host.indexOf('@')+1);
	
	    String localFile = chooser.getSelectedFile().getAbsolutePath();
	    String remoteFile = remoteHome +
		chooser.getSelectedFile().getName();	
	 
	    jsch.addIdentity(idFile);
	    Session session=jsch.getSession(user, host, 22);
	    /*
	     * Username and passphrase will be given via UserInfo interface.
	     */
	    UserInfo ui=new MyUserInfo();
	    session.setUserInfo(ui);
	    session.connect();

	    scpTo(localFile, remoteFile, session);
      
	    System.out.println("Session started");

	    if(!execRemoteCommand("java -Djava.library.path=/home1/slblond/GMencoD/lib/" + 
				  " -Dgat.verbose=false " + 
				  " -Dgat.adaptor.path=" + 
				  "/usr/local/VU/practicum/gridcomputing/old_JavaGAT/adaptors/" +
				  " PPPMaster " + remoteFile + 
				  " /home1/slblond/GMEncoD/gmencod-worker.jar 12",
				  session)) {
		System.out.println("Encoded failed");
		System.exit(0);
	    }

	    String encodedLocalFile = "enc-" + chooser.getSelectedFile().getName();
	    scpFrom(encodedLocalFile, 
		    "/var/scratch/slblond/enc-" + chooser.getSelectedFile().getName(),
		    session);
	    session.disconnect();    
      
	    System.exit(0);
      
	}
	catch(Exception e){
	    System.out.println(e);
	}
    }

    static int checkAck(InputStream in) throws IOException{
	int b=in.read();
	/* b may be 0 for success,
	 *          1 for error,
	 *          2 for fatal error,
	 *         -1.
	 */
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
	    if(b==1){
		System.out.print(sb.toString());
	    }
	    if(b==2){
		System.out.print(sb.toString());
	    }
	}
	return b;
    }

    public static class MyUserInfo implements UserInfo{

	public String getPassword(){ return passwd; }

	public boolean promptYesNo(String str){
	    Object[] options={ "yes", "no" };
	    int foo=JOptionPane.showOptionDialog(null, 
						 str,
						 "Warning", 
						 JOptionPane.DEFAULT_OPTION, 
						 JOptionPane.WARNING_MESSAGE,
						 null, options, options[0]);
	    return foo==0;
	}
  
	String passwd;
	JTextField passwordField=(JTextField)new JPasswordField(20);

	public String getPassphrase(){ return null; }

	public boolean promptPassphrase(String message){ return true; }

	public boolean promptPassword(String message){
	    Object[] ob={passwordField}; 
	    int result=
		JOptionPane.showConfirmDialog(null, ob, message,
					      JOptionPane.OK_CANCEL_OPTION);
	    if(result==JOptionPane.OK_OPTION){
		passwd=passwordField.getText();
		return true;
	    }
	    else{ return false; }
	}

	public void showMessage(String message){
	    JOptionPane.showMessageDialog(null, message);
	}
    }
}
