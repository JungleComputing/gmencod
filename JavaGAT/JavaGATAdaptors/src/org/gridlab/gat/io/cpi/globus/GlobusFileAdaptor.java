package org.gridlab.gat.io.cpi.globus;

import java.util.Vector;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.globus.ftp.FTPClient;
import org.globus.ftp.FileInfo;
import org.globus.ftp.HostPort;
import org.globus.ftp.vanilla.FTPControlChannel;
import org.globus.gsi.gssapi.GlobusGSSManagerImpl;
import org.globus.gsi.gssapi.auth.HostAuthorization;
import org.globus.gsi.gssapi.auth.SelfAuthorization;
import org.gridlab.gat.GAT;
import org.gridlab.gat.GATContext;
import org.gridlab.gat.GATInvocationException;
import org.gridlab.gat.GATObjectCreationException;
import org.gridlab.gat.Preferences;
import org.gridlab.gat.URI;
import org.gridlab.gat.engine.GATEngine;
import org.gridlab.gat.io.File;
import org.gridlab.gat.io.cpi.FileCpi;

public abstract class GlobusFileAdaptor extends FileCpi {
    static final int DEFAULT_GRIDFTP_PORT = 2811;

    static final int DEFAULT_FTP_PORT = 21;

    static final int DEFAULT_HTTP_PORT = 80;

    static final int DEFAULT_HTTPS_PORT = 443;

    private FileInfo cachedInfo = null;

    /**
     * Constructs a LocalFileAdaptor instance which corresponds to the physical
     * file identified by the passed URI and whose access rights are determined
     * by the passed GATContext.
     * 
     * @param location
     *            A URI which represents the URI corresponding to the physical
     *            file.
     * @param gatContext
     *            A GATContext which is used to determine the access rights for
     *            this LocalFileAdaptor.
     */
    public GlobusFileAdaptor(GATContext gatContext, Preferences preferences,
            URI location) throws GATObjectCreationException {
        super(gatContext, preferences, location);

        // turn off all annoying cog prints
        if (!GATEngine.DEBUG) {
            Logger logger = Logger.getLogger(FTPControlChannel.class.getName());
            logger.setLevel(Level.OFF);

            Logger logger2 = Logger.getLogger(GlobusGSSManagerImpl.class
                    .getName());
            logger2.setLevel(Level.OFF);

            Logger logger3 = Logger
                    .getLogger(HostAuthorization.class.getName());
            logger3.setLevel(Level.OFF);

            Logger logger4 = Logger
                    .getLogger(SelfAuthorization.class.getName());
            logger4.setLevel(Level.OFF);
        }
    }

    protected abstract URI fixURI(URI in);

    protected abstract FTPClient createClient(URI hostURI)
            throws GATInvocationException;

    /*
     * (non-Javadoc)
     * 
     * @see org.gridlab.gat.io.File#copy(java.net.URI)
     */
    public void copy(URI dest) throws GATInvocationException {
        // We don't have to handle the local case, the GAT engine will select
        // the local adaptor.
        if (dest.isLocal() && (toURI().isLocal())) {
            throw new GATInvocationException("gridftp cannot copy local files");
        }

        // We don't have to handle the local case, the GAT engine will select
        // the local adaptor.
        if (dest.refersToLocalHost() && (toURI().refersToLocalHost())) {
            throw new GATInvocationException("gridftp cannot copy local files");
        }


        // create a seperate file object to determine whether the source
        // is a directory. This is needed, because the source might be a local
        // file, and gridftp might not be installed locally.
        // This goes wrong for local -> remote copies.
        try {
            File f = GAT.createFile(gatContext, preferences, toURI());
            if (f.isDirectory()) {
                copyDirectory(gatContext, preferences, toURI(), dest);
                return;
            }
        } catch (Exception e) {
            throw new GATInvocationException("gridftp", e);
        }

        if (dest.isLocal()) {
            if (GATEngine.DEBUG) {
                System.err.println("Globus file: copy remote to local");
            }
            copyToLocal(fixURI(toURI()), fixURI(dest));
            return;
        }

        if (toURI().isLocal()) {
            if (GATEngine.DEBUG) {
                System.err.println("Globus file: copy local to remote");
            }
            copyToRemote(fixURI(toURI()), fixURI(dest));
            return;
        }

        // source is remote, dest is remote.
        if (GATEngine.DEBUG) {
            System.err.println("Globus file: copy remote to remote");
        }
        copyThirdParty(fixURI(toURI()), fixURI(dest));
    }

    // first try efficient 3rd party transfer.
    // If that fails, try copying using temp file.
    protected void copyThirdParty(URI src, URI dest)
            throws GATInvocationException {
        try {
            FTPClient srcClient = createClient(src);
            FTPClient destClient = createClient(dest);
            HostPort hp = destClient.setPassive();
            srcClient.setActive(hp);

            boolean append = true;
            String remoteSrcFile = this.getPath();
            String remoteDestFile = dest.getPath();

            srcClient.transfer(remoteSrcFile, destClient, remoteDestFile,
                    append, null);

            destClient.close();
            srcClient.close();

        } catch (Exception e) {
            try {
                // use a local tmp file.
            	java.io.File tmp = null;
                tmp = java.io.File.createTempFile("GATgridFTP", ".tmp");

                copyToLocal(toURI(), new URI(tmp.toURI()));
                copyToRemote(new URI(tmp.toURI()), dest);
            } catch (Exception e2) {
                GATInvocationException oops = new GATInvocationException();
                oops.add("Globus file", e);
                oops.add("Globus file", e2);

                throw oops;
            }
        }
    }

    protected void copyToRemote(URI src, URI dest)
            throws GATInvocationException {
        // copy from the local machine to a remote machine.

        try {
            String remotePath = dest.getPath();
            String localPath = src.getPath();
            java.io.File localFile = new java.io.File(localPath);

            if (GATEngine.DEBUG) {
                System.err.println("copying from " + localPath + " to "
                        + remotePath);
            }

            FTPClient client = createClient(dest);
            client.put(localFile, remotePath, false); // overwrite
            client.close();

        } catch (Exception e) {
            throw new GATInvocationException("gridftp", e);
        }
    } /*
       * (non-Javadoc)
       * 
       * @see org.gridlab.gat.io.File#copy(java.net.URI)
       */

    protected void copyToLocal(URI src, URI dest) throws GATInvocationException {
        // copy from a remote machine to the local machine
        try {
            String remotePath = src.getPath();
            String localPath = dest.getPath();
            java.io.File localFile = new java.io.File(localPath);

            if (GATEngine.DEBUG) {
                System.err.println("copying from " + remotePath + " to "
                        + localPath);
            }

            FTPClient client = createClient(src);
            client.get(remotePath, localFile);
            client.close();
        } catch (Exception e) {
            throw new GATInvocationException("gridftp", e);
        }
    }

    public boolean delete() throws GATInvocationException {
        try {
            String remotePath = getPath();
            FTPClient client = createClient(toURI());

            if (isDirectory()) {
                client.deleteDir(remotePath);
            } else {
                client.deleteFile(remotePath);
            }
            client.close();
        } catch (Exception e) {
            throw new GATInvocationException("gridftp", e);
        }
        return false;
    }

    //  aarg, the COG returns a flakey name for links.
    protected String getName(FileInfo info) {
        if (info.isSoftLink()) {
            int pos = info.getName().indexOf(" ->");
            if (pos != -1) {
                return info.getName().substring(0, pos);
            }
        }

        return info.getName();
    }

    public String[] list() throws GATInvocationException {
        try {
            if (isDirectory()) {
                String remotePath = getPath();

                FTPClient client = createClient(toURI());
                client.changeDir(remotePath);
                Vector v = client.list();
                client.close();

                String[] res = new String[v.size()];
                for (int i = 0; i < v.size(); i++) {
                    FileInfo info = ((FileInfo) v.get(i));
                    res[i] = getName(info);
                }

                return res;
            }

            // OK, not a dir, just return the list...
            String[] res = new String[1];
            res[0] = getName(getInfo());
            return res;
        } catch (Exception e) {
            throw new GATInvocationException("gridftp", e);
        }
    }

    protected FileInfo getInfo() throws GATInvocationException {
        if (cachedInfo != null) {
            //			System.err.println("CACHED INFO: " + cachedInfo);
            return cachedInfo;
        }

        try {
            String remotePath = getPath();
            if (GATEngine.DEBUG) {
                System.err.println("getINFO: remotePath = " + remotePath
                        + ", creating client to: " + toURI());
            }
            FTPClient client = createClient(toURI());

            if (GATEngine.DEBUG) {
                System.err.println("getINFO: client created");
            }
            // these two commands are used by Michael.
            //			client.setLocalPassive();
            //			client.setActive();

            Vector v = client.list(remotePath);
            client.close();

            if (v.size() == 0) {
                throw new GATInvocationException(
                        "File not found");
            }

            if (v.size() != 1) {
                throw new GATInvocationException(
                        "Internal error: size of list is not 1, remotePath = " + remotePath + ", list is: " + v);
            }

            cachedInfo = (FileInfo) v.get(0);

            //			System.err.println("INFO: " + cachedInfo);
            return cachedInfo;
        } catch (Exception e) {
            throw new GATInvocationException("gridftp", e);
        }
    }

    public boolean isDirectory() throws GATInvocationException {
        try {
            FileInfo info = getInfo();
            return info.isDirectory();
        } catch (Exception e) {
            throw new GATInvocationException("gridftp", e);
        }
    }

    public boolean isFile() throws GATInvocationException {
        try {
            FileInfo info = getInfo();
            return info.isFile();
        } catch (Exception e) {
            throw new GATInvocationException("gridftp", e);
        }
    }

    public boolean canRead() throws GATInvocationException {
        try {
            FileInfo info = getInfo();
            return info.userCanRead();
        } catch (Exception e) {
            throw new GATInvocationException("gridftp", e);
        }
    }

    public boolean mkdir() throws GATInvocationException {
        try {
            String remotePath = getPath();
            FTPClient client = createClient(toURI());

            client.makeDir(remotePath);
            client.close();
        } catch (Exception e) {
            throw new GATInvocationException("gridftp", e);
        }
        return false;
    }

    public boolean canWrite() throws GATInvocationException {
        try {
            FileInfo info = getInfo();
            return info.userCanWrite();
        } catch (Exception e) {
            throw new GATInvocationException("gridftp", e);
        }
    }

    public boolean exists() throws GATInvocationException {
        if (cachedInfo != null) {
        	return true;
        }

        try {
            String remotePath = getPath();
            if (GATEngine.DEBUG) {
                System.err.println("getINFO: remotePath = " + remotePath
                        + ", creating client to: " + toURI());
            }
            FTPClient client = createClient(toURI());

            if (GATEngine.DEBUG) {
                System.err.println("getINFO: client created");
            }
            // these two commands are used by Michael.
            //			client.setLocalPassive();
            //			client.setActive();

            Vector v = client.list(remotePath);
            client.close();

            if (v.size() == 0) {
            	return false;
            }

            if (v.size() != 1) {
                throw new GATInvocationException(
                        "Internal error: size of list is not 1, remotePath = " + remotePath + ", list is: " + v);
            }

            cachedInfo = (FileInfo) v.get(0);
            
            return true;
        } catch (Exception e) {
            throw new GATInvocationException("gridftp", e);
        }
	}
        
	public String getAbsolutePath() throws GATInvocationException {
		if(getPath().startsWith("/")) {
			return getPath();
		}
		
        try {
            FTPClient client = createClient(toURI());

            String dir = client.getCurrentDir();
            client.close();
            return dir + "/" + getPath(); 
            
        } catch (Exception e) {
            throw new GATInvocationException("gridftp", e);
        }
	}
}
