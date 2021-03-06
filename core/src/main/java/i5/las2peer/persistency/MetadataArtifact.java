package i5.las2peer.persistency;

import i5.las2peer.api.security.AgentLockedException;
import i5.las2peer.security.AgentImpl;
import i5.las2peer.tools.CryptoException;
import rice.p2p.commonapi.Id;
import rice.pastry.commonapi.PastryIdFactory;

public class MetadataArtifact extends NetworkArtifact {

	private static final long serialVersionUID = 1L;

	public MetadataArtifact(PastryIdFactory idFactory, String identifier, long version, byte[] serialize,
			AgentImpl author) throws CryptoException, VerificationFailedException, AgentLockedException {
		super(buildMetadataId(idFactory, identifier, version), 0, serialize, author);
	}

	public static Id buildMetadataId(PastryIdFactory idFactory, String identifier, long version) {
		return idFactory.buildId(getMetadataIdentifier(identifier, version));
	}

	public static String getMetadataIdentifier(String identifier, long version) {
		return "metadata-" + identifier + "#" + version;
	}

}
