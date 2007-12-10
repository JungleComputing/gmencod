package org.gridlab.gat.io.cpi.globus;

import java.io.OutputStream;

import org.globus.gsi.GlobusCredential;
import org.globus.gsi.gssapi.GlobusGSSCredentialImpl;
import org.globus.io.streams.GridFTPOutputStream;
import org.gridlab.gat.GATContext;
import org.gridlab.gat.GATInvocationException;
import org.gridlab.gat.GATObjectCreationException;
import org.gridlab.gat.Preferences;
import org.gridlab.gat.URI;
import org.ietf.jgss.GSSCredential;

public class GridFTPFileOutputStreamAdaptor extends
		GlobusFileOutputStreamAdaptor {

	public GridFTPFileOutputStreamAdaptor(GATContext gatContext,
			Preferences preferences, URI location, Boolean append)
			throws GATObjectCreationException {
		super(gatContext, preferences, location, append);

		checkName("gridftp");

		if(!location.isCompatible("gsiftp")) {
			throw new GATObjectCreationException("cannot handle this URI");
		}

		// now try to create a stream.
		try {
			out = createStream();
		} catch (GATInvocationException e) {
			throw new GATObjectCreationException("grid ftp outputstream", e);
		}
	}

	protected OutputStream createStream() throws GATInvocationException {
		String host = location.getHost();
		String path = location.getPath();
		GATInvocationException gridFTPException = null;

		try {
			int port = GlobusFileAdaptor.DEFAULT_GRIDFTP_PORT;

			// allow port override
			if (location.getPort() != -1) {
				port = location.getPort();
			}

			GlobusCredential gcred = GlobusCredential.getDefaultCredential();
			GSSCredential credential = new GlobusGSSCredentialImpl(gcred,
					GSSCredential.DEFAULT_LIFETIME);

			GridFTPOutputStream output = new GridFTPOutputStream(credential,
					host, port, path, append);

			return output;
		} catch (Exception e) {
			throw new GATInvocationException("gridftp", e);
		}
	}
}