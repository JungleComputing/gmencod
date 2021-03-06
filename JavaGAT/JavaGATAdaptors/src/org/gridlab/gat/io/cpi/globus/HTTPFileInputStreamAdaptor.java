package org.gridlab.gat.io.cpi.globus;

import java.io.InputStream;

import org.globus.io.streams.HTTPInputStream;
import org.gridlab.gat.GATContext;
import org.gridlab.gat.GATInvocationException;
import org.gridlab.gat.GATObjectCreationException;
import org.gridlab.gat.Preferences;
import org.gridlab.gat.URI;

/**
 * A DefaultFileStreamAdaptor represents a connection to open file, the file may
 * be either remote or local.
 * <p>
 * A DefaultFileStreamAdaptor represents a seekable connection to a file and has
 * semantics similar to a standard Unix filedescriptor. It provides methods to
 * query the current position in the file and to seek to new positions.
 * <p>
 * To Write data to a DefaultFileStreamAdaptor it is necessary to construct a
 * Buffer and pack it with data. Similarly, to read data a buffer must be
 * created to store the read data. Writes and reads may either be blocking, or
 * asynchronous. Asynchronous writes or reads must be completed by appropriate
 * call.
 */
public class HTTPFileInputStreamAdaptor extends GlobusFileInputStreamAdaptor {

	public HTTPFileInputStreamAdaptor(GATContext gatContext,
			Preferences preferences, URI location)
			throws GATObjectCreationException {
		super(gatContext, preferences, location);

		checkName("http");

		if(!location.isCompatible("http")) {
			throw new GATObjectCreationException("cannot handle this URI");
		}

		// now try to create a stream.
		try {
			in = createStream();
		} catch (GATInvocationException e) {
			throw new GATObjectCreationException("http inputstream", e);
		}
	}

	protected InputStream createStream() throws GATInvocationException {
		String host = location.getHost();
		String path = location.getPath();
		GATInvocationException gridFTPException = null;

		try {
			int port = GlobusFileAdaptor.DEFAULT_HTTP_PORT;

			// allow port override
			if (location.getPort() != -1) {
				port = location.getPort();
			}

			HTTPInputStream input = new HTTPInputStream(host, port, path);

			return input;
		} catch (Exception e) {
			throw new GATInvocationException("http", e);
		}
	}
}