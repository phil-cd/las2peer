package i5.las2peer.connectors.webConnector.util;

import java.io.IOException;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;

import i5.las2peer.connectors.webConnector.WebConnector;

public class CORSResponseFilter implements ContainerResponseFilter {

	private final WebConnector connector;

	public CORSResponseFilter(WebConnector connector) {
		this.connector = connector;
	}

	@Override
	public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
			throws IOException {
		if (connector.isCrossOriginResourceSharing()) {
			MultivaluedMap<String, Object> headers = responseContext.getHeaders();
			headers.add("Access-Control-Expose-Headers", String.join(", ", headers.keySet()));
			headers.add("Access-Control-Allow-Origin", connector.getCrossOriginResourceDomain());
			headers.add("Access-Control-Max-Age", String.valueOf(connector.getCrossOriginResourceMaxAge()));
			headers.add("Access-Control-Allow-Methods", "POST, GET, PUT, DELETE, OPTIONS");
			String aclRequestHeaders = requestContext.getHeaderString("Access-Control-Request-Headers");
			if (aclRequestHeaders != null && !aclRequestHeaders.isEmpty()) {
				headers.add("Access-Control-Allow-Headers", aclRequestHeaders);
			}
		}
	}

}
