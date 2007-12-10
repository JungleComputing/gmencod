/*
 * Created on Jan 7, 2005
 */
package org.gridlab.gat;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

import org.gridlab.gat.engine.IPUtils;

/**
 * @author rob
 */

/** This class implements URIs. It is API compatible with java.net.URI.
 * However,
 * the standard Java class has a bug. The Java URI class does not work correctly
 * if you omit the hostname in a URI. For example: <BR>
 * file:// <hostname>/ <path><BR>
 * and <BR>
 * <hostname>== not set (empty string) <BR>
 * and <BR>
 * <path>== /bin/date <BR>
 * then the correct URI is <BR>
 * file:////bin/date <BR>
 * So four slashes in total after the "file:" <BR>
 * if the path would be a relative path such as foo/bar, the URI would be: <BR>
 * file:///foo/bar (three slashes because of the empty hostname). <BR>
 * However, the Java URI class getPath() method will return "/foo/bar" as the
 * path instead of "foo/bar"... <BR>
 */
public class URI implements Serializable, Comparable {
	java.net.URI u;

	public URI(String s) throws URISyntaxException {
	    u = new java.net.URI(s);
	}
	
	public URI(java.net.URI u) {
	    this.u = u;
	}
	
	public static URI create(String s) throws URISyntaxException {
		return new URI(s);
	}

	/** Check whether URI refers to the local machine */
	public boolean isLocal() {
		if (u.getHost() == null) {
			return true;
		}
		if (u.getHost().equals("localhost")) {
			return true;
		}
/*
		if (IPUtils.getLocalHostName().equals(u.getHost())) {
			return true;
		}
*/
		return false;
	}

	/** Check whether URI refers to the local machine. The difference between this call and isLocal is that
	    this call also checks if the host in the URI is equal to the hostname of the local mahince */
	public boolean refersToLocalHost() {
		if (u.getHost() == null) {
			return true;
		}
		if (u.getHost().equals("localhost")) {
			return true;
		}

		if (IPUtils.getLocalHostName().equals(u.getHost())) {
			return true;
		}

		return false;
	}

	/* this is where the magic happens to fix SUNs bug.. */
	public String getPath() {
		String path = u.getPath();

		//		System.err.println("IPgetPath: uri = " + u);
		if (u.getScheme() == null && u.getHost() == null) {
			//		System.err.println("IPgetPath: path = " + path);
			return path;
		}
		/*
		 * if (u.getHost() == null) { if (path.startsWith("/")) { path =
		 * path.substring(1); } }
		 * 
		 * if (path.startsWith("//")) { path = path.substring(1); }
		 */
		path = path.substring(1);
		//		System.err.println("IPgetPath: path = " + path);
		return path;
	}

	public int compareTo(Object arg0) {
		return u.compareTo(arg0);
	}

	public boolean equals(Object arg0) {
		if(! (arg0 instanceof URI)) {
			return false;
		}
		
		URI other = (URI) arg0;
		if(other.getScheme().equals("any") || getScheme().equals("any")) {
	        String tmpURIString = getScheme() + "://";
	        tmpURIString += (other.getUserInfo() == null ? "" : other.getUserInfo());
	        tmpURIString += (other.getHost() == null ? "" : other.getHost());
	        tmpURIString += (other.getPort() == -1 ? "" : ":" + other.getPort());
	        tmpURIString += "/" + other.getPath();
	        
//	        System.err.println("URI equals: created tmp URI: " + tmpURIString + ", orig was: " + other + ", compare with: " + u + ".");
	        
	        boolean res = u.toString().equals(tmpURIString);
//	        System.err.println("result of URI equals = " + res);
	        return res;
		}

		return u.equals(arg0);
	}

	public String getAuthority() {
		return u.getAuthority();
	}

	public String getFragment() {
		return u.getFragment();
	}

	public String getHost() {
		return u.getHost();
	}

	public int getPort() {
		return u.getPort();
	}

	public String getQuery() {
		return u.getQuery();
	}

	public String getRawAuthority() {
		return u.getRawAuthority();
	}

	public String getRawFragment() {
		return u.getRawFragment();
	}

	public String getRawPath() {
		return u.getRawPath();
	}

	public String getRawQuery() {
		return u.getRawQuery();
	}

	public String getRawSchemeSpecificPart() {
		return u.getRawSchemeSpecificPart();
	}

	public String getRawUserInfo() {
		return u.getRawUserInfo();
	}

	public String getScheme() {
		String scheme = u.getScheme();
		if(scheme == null) {
		    if(getHost() == null) {
		        return "file";
		    } else {
		        return "any";
		    }
		}
		
		return scheme;
	}

	public String getSchemeSpecificPart() {
		return u.getSchemeSpecificPart();
	}

	public String getUserInfo() {
		return u.getUserInfo();
	}

	public int hashCode() {
		return u.hashCode();
	}

	public boolean isAbsolute() {
		return u.isAbsolute();
	}

	public boolean isOpaque() {
		return u.isOpaque();
	}

	public URI normalize() {
		return new URI(u.normalize());
	}

	public URI parseServerAuthority() throws URISyntaxException {
		return new URI(u.parseServerAuthority());
	}

	public URI relativize(java.net.URI arg0) {
		return new URI(u.relativize(arg0));
	}

	public URI resolve(String arg0) {
		return new URI(u.resolve(arg0));
	}

	public URI resolve(java.net.URI arg0) {
		return new URI(u.resolve(arg0));
	}

	public String toASCIIString() {
		return u.toASCIIString();
	}

	public String toString() {
		return u.toString();
	}

	public URL toURL() throws MalformedURLException {
		return u.toURL();
	}
	
	public java.net.URI toJavaURI() {
	    return u;
	}
	
	/** Checks whether this URI is "compatible" with the given scheme
	 * If this URI has the same scheme, it is compatible.
	 * When this URI has "any" as scheme, or no scheme at all, it is also
	 * compatible.
	 * 
	 * @param scheme the scheme to compare to
	 * @return true: the URIs are compatible
	 */
	public boolean isCompatible(String scheme) {
		if(getScheme() == null || getScheme().equals("any")) {
			return true;
		}
		
		if(getScheme().equals(scheme)) {
			return true;
		}
		
		return false;
	}
	
	public void debugPrint() {
	    System.err.println("URI: scheme = " + getScheme() + ", host = " + getHost() + ", port = " + getPort() + ", path = " + getPath());
	}
}
