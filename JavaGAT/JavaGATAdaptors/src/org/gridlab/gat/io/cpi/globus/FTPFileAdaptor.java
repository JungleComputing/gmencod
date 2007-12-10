package org.gridlab.gat.io.cpi.globus;

import org.globus.ftp.FTPClient;
import org.gridlab.gat.GATContext;
import org.gridlab.gat.GATInvocationException;
import org.gridlab.gat.GATObjectCreationException;
import org.gridlab.gat.Preferences;
import org.gridlab.gat.URI;

public class FTPFileAdaptor extends GlobusFileAdaptor {

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
	public FTPFileAdaptor(GATContext gatContext, Preferences preferences,
			URI location) throws GATObjectCreationException {
		super(gatContext, preferences, location);

		checkName("ftp");

		if(!location.isCompatible("ftp")) {
			throw new GATObjectCreationException("cannot handle this URI");
		}

		String user = (String) preferences.get("user");
		if (user == null) {
			throw new GATObjectCreationException(
					"no user provided in preferences");
		}

		String password = (String) preferences.get("password");
		if (password == null) {
			throw new GATObjectCreationException(
					"no password provided in preferences");
		}
	}

	protected URI fixURI(URI in) {
		return fixURI(in, "ftp");
	}

	protected static void setChannelOptions(FTPClient client) throws Exception {
		// no options needed for normal ftp case
	}

	protected FTPClient createClient(URI hostURI) throws GATInvocationException {
		String host = hostURI.getHost();
		GATInvocationException gridFTPException = null;

		int port = DEFAULT_FTP_PORT;

		// allow port override
		if (hostURI.getPort() != -1) {
			port = hostURI.getPort();
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

			FTPClient client = new FTPClient(host, port);
			client.authorize(user, password);

			setChannelOptions(client);
			return client;
		} catch (Exception e) {
			// ouch, both failed.
			throw new GATInvocationException("ftp", e);
		}
	}
}