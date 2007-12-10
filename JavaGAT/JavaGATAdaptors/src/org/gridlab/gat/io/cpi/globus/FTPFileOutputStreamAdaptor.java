package org.gridlab.gat.io.cpi.globus;

import java.io.OutputStream;

import org.globus.io.streams.FTPOutputStream;
import org.gridlab.gat.GATContext;
import org.gridlab.gat.GATInvocationException;
import org.gridlab.gat.GATObjectCreationException;
import org.gridlab.gat.Preferences;
import org.gridlab.gat.URI;

public class FTPFileOutputStreamAdaptor extends GlobusFileOutputStreamAdaptor {

	public FTPFileOutputStreamAdaptor(GATContext gatContext,
			Preferences preferences, URI location, Boolean append)
			throws GATObjectCreationException {
		super(gatContext, preferences, location, append);

		checkName("ftp");

		if(!location.isCompatible("ftp")) {
			throw new GATObjectCreationException("cannot handle this URI");
		}

		// now try to create a stream.
		try {
			out = createStream();
		} catch (GATInvocationException e) {
			throw new GATObjectCreationException("ftp outputstream", e);
		}
	}

	protected OutputStream createStream() throws GATInvocationException {
		String host = location.getHost();
		String path = location.getPath();

		int port = GlobusFileAdaptor.DEFAULT_FTP_PORT;

		// allow port override
		if (location.getPort() != -1) {
			port = location.getPort();
		}

		try {
			String user = (String) preferences.get("user");
			if (user == null) {
				throw new GATInvocationException(
						"no user provided in preferences");
			}

			String password = (String) preferences.get("password");
			if (password == null) {
				throw new GATInvocationException(
						"no password provided in preferences");
			}

			FTPOutputStream output = new FTPOutputStream(host, port, user,
					password, path, append);

			return output;
		} catch (Exception e) {
			throw new GATInvocationException("ftp", e);
		}
	}
}