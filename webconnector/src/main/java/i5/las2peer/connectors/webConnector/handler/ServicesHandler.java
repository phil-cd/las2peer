package i5.las2peer.connectors.webConnector.handler;

import java.io.InputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.CookieParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import i5.las2peer.p2p.EthereumNode;
import i5.las2peer.registry.CredentialUtils;
import i5.las2peer.registry.data.ServiceDeploymentData;
import i5.las2peer.registry.data.ServiceReleaseData;
import i5.las2peer.registry.exceptions.EthereumException;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.glassfish.jersey.media.multipart.FormDataParam;

import i5.las2peer.api.persistency.EnvelopeAlreadyExistsException;
import i5.las2peer.api.persistency.EnvelopeNotFoundException;
import i5.las2peer.classLoaders.ClassManager;
import i5.las2peer.classLoaders.libraries.LibraryIdentifier;
import i5.las2peer.classLoaders.libraries.SharedStorageRepository;
import i5.las2peer.connectors.webConnector.WebConnector;
import i5.las2peer.connectors.webConnector.util.AgentSession;
import i5.las2peer.p2p.Node;
import i5.las2peer.p2p.PastryNodeImpl;
import i5.las2peer.persistency.EnvelopeVersion;
import i5.las2peer.tools.CryptoTools;
import i5.las2peer.tools.PackageUploader;
import i5.las2peer.tools.PackageUploader.ServiceVersionList;
import i5.las2peer.tools.ServicePackageException;
import i5.las2peer.tools.SimpleTools;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;

@Path(ServicesHandler.RESOURCE_PATH)
public class ServicesHandler {

	public static final String RESOURCE_PATH = DefaultHandler.ROOT_RESOURCE_PATH + "/services";

	private final WebConnector connector;
	private final Node node;
	private final PastryNodeImpl pastryNode;
	private final EthereumNode ethereumNode;

	public ServicesHandler(WebConnector connector) {
		this.connector = connector;
		this.node = connector.getL2pNode();
		pastryNode = (node instanceof PastryNodeImpl) ? (PastryNodeImpl) node :null;
		ethereumNode = (node instanceof EthereumNode) ? (EthereumNode) node : null;
	}

	@GET
	@Path("/search")
	@Produces(MediaType.APPLICATION_JSON)
	public Response handleSearchService(@HeaderParam("Host") String hostHeader,
			@QueryParam("searchname") String searchName) {
		JSONObject result = new JSONObject();
		try {
			JSONArray instances = new JSONArray();
			if (searchName == null || searchName.isEmpty()) {
				// iterate local services
				List<String> serviceNames = node.getNodeServiceCache().getLocalServiceNames();
				for (String serviceName : serviceNames) {
					// add service versions from network
					instances.addAll(getNetworkServices(node, hostHeader, serviceName));
				}
			} else {
				// search for service version in network
				instances.addAll(getNetworkServices(node, hostHeader, searchName));
			}
			result.put("instances", instances);
		} catch (EnvelopeNotFoundException | IllegalArgumentException e) {
			result.put("msg", "'" + searchName + "' not found in network");
		} catch (Exception e) {
			result.put("msg", e.toString());
		}
		return Response.ok(result.toJSONString(), MediaType.APPLICATION_JSON).build();
	}

	private JSONArray getNetworkServices(Node node, String hostHeader, String searchName) throws Exception {
		JSONArray result = new JSONArray();
		String libName = ClassManager.getPackageName(searchName);
		String libId = SharedStorageRepository.getLibraryVersionsEnvelopeIdentifier(libName);
		EnvelopeVersion networkVersions = node.fetchEnvelope(libId);
		Serializable content = networkVersions.getContent();
		if (content instanceof ServiceVersionList) {
			ServiceVersionList serviceversions = (ServiceVersionList) content;
			for (String version : serviceversions) {
				JSONObject jsonObject = new JSONObject();
				jsonObject.put("name", searchName);
				jsonObject.put("version", version);
				result.add(jsonObject);
			}
		} else {
			throw new ServicePackageException("Invalid version envelope expected " + List.class.getCanonicalName()
					+ " but envelope contains " + content.getClass().getCanonicalName());
		}
		return result;
	}

	@POST
	@Path("/upload")
	@Produces(MediaType.APPLICATION_JSON)
	public Response handleServicePackageUpload(@CookieParam(WebConnector.COOKIE_SESSIONID_KEY) String sessionId,
			@FormDataParam("jarfile") InputStream jarfile) throws Exception {
		AgentSession session = connector.getSessionById(sessionId);
		if (session == null) {
			throw new BadRequestException("You have to be logged in to upload");
		} else if (jarfile == null) {
			throw new BadRequestException("No jar file provided");
		} else if (pastryNode == null) {
			throw new ServerErrorException(
					"Service upload only available for " + PastryNodeImpl.class.getCanonicalName() + " Nodes",
					Status.INTERNAL_SERVER_ERROR);
		}
		// create jar from inputstream
		JarInputStream jarStream = new JarInputStream(jarfile);
		// read general service information from jar manifest
		Manifest manifest = jarStream.getManifest();
		if (manifest == null) {
			jarStream.close();
			throw new BadRequestException("Service jar package contains no manifest file");
		}
		String serviceName = manifest.getMainAttributes().getValue(LibraryIdentifier.MANIFEST_LIBRARY_NAME_ATTRIBUTE);
		String serviceVersion = manifest.getMainAttributes()
				.getValue(LibraryIdentifier.MANIFEST_LIBRARY_VERSION_ATTRIBUTE);
		// read files from jar and generate hashes
		HashMap<String, byte[]> depHashes = new HashMap<>();
		HashMap<String, byte[]> jarFiles = new HashMap<>();
		JarEntry entry = null;
		while ((entry = jarStream.getNextJarEntry()) != null) {
			if (!entry.isDirectory()) {
				byte[] bytes = SimpleTools.toByteArray(jarStream);
				jarStream.closeEntry();
				byte[] hash = CryptoTools.getSecureHash(bytes);
				String filename = entry.getName();
				depHashes.put(filename, hash);
				jarFiles.put(filename, bytes);
			}
		}
		jarStream.close();
		try {
			PackageUploader.uploadServicePackage(pastryNode, serviceName, serviceVersion, depHashes, jarFiles,
					session.getAgent());
			JSONObject json = new JSONObject();
			json.put("code", Status.OK.getStatusCode());
			json.put("text", Status.OK.getStatusCode() + " - Service package upload successful");
			json.put("msg", "Service package upload successful");
			return Response.ok(json.toJSONString(), MediaType.APPLICATION_JSON).build();
		} catch (EnvelopeAlreadyExistsException e) {
			throw new BadRequestException("Version is already known in the network. To update increase version number",
					e);
		} catch (ServicePackageException e) {
			e.printStackTrace();
			throw new BadRequestException("Service package upload failed", e);
		}
	}

	@GET
	@Path("/names")
	@Produces(MediaType.APPLICATION_JSON)
	public String getRegisteredServices() {
		JSONArray serviceNameList = new JSONArray();
		serviceNameList.addAll(ethereumNode.getRegistryClient().getServiceNames());
		return serviceNameList.toJSONString();
	}

	@GET
	@Path("/authors")
	@Produces(MediaType.APPLICATION_JSON)
	public String getServiceAuthors() {
		JSONObject jsonObject = new JSONObject();
		for (ConcurrentMap.Entry<String, String> serviceWithAuthor: ethereumNode.getRegistryClient().getServiceAuthors().entrySet()) {
			jsonObject.put(serviceWithAuthor.getKey(), serviceWithAuthor.getValue());
		}
		return jsonObject.toJSONString();
	}

	@GET
	@Path("/releases")
	@Produces(MediaType.APPLICATION_JSON)
	public String getServiceReleases() {
		JSONObject jsonObject = new JSONObject();
		for (ConcurrentMap.Entry<String, List<ServiceReleaseData>> service: ethereumNode.getRegistryClient().getServiceReleases().entrySet()) {
			JSONArray releaseList = new JSONArray();
			for (ServiceReleaseData release : service.getValue()) {
				JSONObject entry = new JSONObject();
				entry.put("name", release.getServiceName());
				entry.put("version", release.getVersion());
				releaseList.add(entry);
			}
			jsonObject.put(service.getKey(), releaseList);
		}
		return jsonObject.toJSONString();
	}

	@GET
	@Path("/deployments")
	@Produces(MediaType.APPLICATION_JSON)
	public String getServiceDeployments() {
		JSONObject jsonObject = new JSONObject();
		for (ConcurrentMap.Entry<String, List<ServiceDeploymentData>> service: ethereumNode.getRegistryClient().getServiceDeployments().entrySet()) {
			JSONArray deploymentList = new JSONArray();
			for (ServiceDeploymentData deployment : service.getValue()) {
				JSONObject entry = new JSONObject();
				entry.put("packageName", deployment.getServicePackageName());
				entry.put("className", deployment.getServiceClassName());
				entry.put("version", deployment.getVersion());
				entry.put("time", deployment.getTime());
				entry.put("nodeId", deployment.getNodeId());
				deploymentList.add(entry);
			}
			jsonObject.put(service.getKey(), deploymentList);
		}
		return jsonObject.toJSONString();
	}

	@GET
	@Path("/registry/tags")
	@Produces(MediaType.APPLICATION_JSON)
	public String getTags() {
		JSONObject jsonObject = new JSONObject();
		for (ConcurrentMap.Entry<String, String> tag: ethereumNode.getRegistryClient().getTags().entrySet()) {
			jsonObject.put(tag.getKey(), tag.getValue());
		}
		return jsonObject.toJSONString();
	}

	@POST
	@Path("/registry/faucet")
	@Produces(MediaType.APPLICATION_JSON)
	public Response sendEtherFromNodeOwnerToAddress(String requestBody) {
		JSONObject payload = parseBodyAsJson(requestBody);
		String address = payload.getAsString("address");
		if (!WalletUtils.isValidAddress(address)) {
			throw new BadRequestException("Address is not valid.");
		}

		Number valueAsNumber = payload.getAsNumber("valueInWei");
		if (valueAsNumber == null) {
			throw new BadRequestException("Value is invalid.");
		}

		BigDecimal valueInWei = BigDecimal.valueOf(valueAsNumber.longValue());
		try {
			ethereumNode.getRegistryClient().sendEther(address, valueInWei);
		} catch (EthereumException e) {
			return Response.serverError().entity(e.toString()).build();
		}
		return Response.ok().build();
	}

	@GET
	@Path("/registry/mnemonic")
	@Produces(MediaType.TEXT_PLAIN)
	public Response generateMnemonic() {
		String mnemonic = CredentialUtils.createMnemonic();
		return Response.ok(mnemonic).build();
	}

	@POST
	@Path("/registry/mnemonic")
	@Produces(MediaType.APPLICATION_JSON)
	public Response showKeysForMnemonic(String requestBody) {
		JSONObject payload = parseBodyAsJson(requestBody);
		String mnemonic = payload.getAsString("mnemonic");
		String password = payload.getAsString("password");

		Credentials credentials = CredentialUtils.fromMnemonic(mnemonic, password);

		JSONObject json = new JSONObject()
				.appendField("mnemonic", mnemonic)
				.appendField("password", password)
				.appendField("publicKey", credentials.getEcKeyPair().getPublicKey())
				.appendField("privateKey", credentials.getEcKeyPair().getPrivateKey())
				.appendField("address", credentials.getAddress());
		return Response.ok(json.toJSONString(), MediaType.APPLICATION_JSON).build();
	}

	private JSONObject parseBodyAsJson(String requestBody) {
		if (requestBody.trim().isEmpty()) {
			throw new BadRequestException("No request body");
		}
		try {
			return (JSONObject) new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE).parse(requestBody);
		} catch (ParseException e) {
			throw new BadRequestException("Could not parse json request body");
		}
	}
}
