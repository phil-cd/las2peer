package i5.las2peer.security;

import i5.las2peer.execution.L2pThread;
import i5.las2peer.logging.NodeObserver.Event;
import i5.las2peer.p2p.AgentNotKnownException;
import i5.las2peer.p2p.ArtifactNotFoundException;
import i5.las2peer.p2p.Node;
import i5.las2peer.p2p.StorageException;
import i5.las2peer.persistency.DecodingFailedException;
import i5.las2peer.persistency.Envelope;
import i5.las2peer.tools.CryptoException;
import i5.las2peer.tools.SerializationException;

import java.util.Date;
import java.util.Hashtable;


/**
 * Each {@link i5.las2peer.execution.L2pThread} is bound to a context,
 * which is mainly determined by the executing agent.
 * 
 * @author Holger Jan&szlig;en
 *
 */
public class Context implements AgentStorage {

	private Agent agent;
	private Object remoteNodeReference = null;
	
	private Hashtable<Long, GroupAgent> groupAgents = new Hashtable <Long, GroupAgent> ();
	
	private Node localNode;
	
	private long lastUsageTime = -1;
	
	
	/**
	 * Creates a new (local) context.
	 * 
	 * @param mainAgent
	 * @param localNode
	 * 
	 * @throws L2pSecurityException
	 */
	public Context ( Node localNode, Agent mainAgent) throws L2pSecurityException {
		this.agent = mainAgent;
		
		//if ( agent.isLocked() )
		//	throw new L2pSecurityException ("Agent needs to be unlocked!");
		
		this.localNode = localNode;
		
		touch();
	}
	
	
	/**
	 * Creates a new (remote) context.
	 * 
	 * @param localNode
	 * @param mainAgent
	 * @param remoteNodeReference
	 * 
	 * @throws L2pSecurityException
	 */
	public Context ( Node localNode, Agent mainAgent, Object remoteNodeReference ) throws L2pSecurityException {
		this ( localNode, mainAgent );
		this.remoteNodeReference = remoteNodeReference;
	}
	
	
	/**
	 * Gets the main agent of this context.
	 * 
	 * @return the agent of this context
	 */
	public Agent getMainAgent () {
		return agent;
	}
	
	
	/**
	 * Gets all group agents, which have been unlocked in this context.
	 * 
	 * @return all (unlocked) group agents of this context
	 */
	public GroupAgent[] getGroupAgents () {
		return groupAgents.entrySet().toArray(new GroupAgent[0]);
	}
	
	
	/**
	 * Tries to open the given id for this context.
	 * 
	 * @param groupId
	 * 
	 * @return the unlocked GroupAgent of the given id
	 * 
	 * @throws AgentNotKnownException 
	 * @throws L2pSecurityException 
	 */
	public GroupAgent requestGroupAgent ( long groupId ) throws AgentNotKnownException, L2pSecurityException {
		if ( groupAgents.containsKey(groupId))
			return groupAgents.get( groupId );
		
		// TODO: hierarchical groups
		// insert here?
		
		
		Agent agent = localNode.getAgent(groupId);
		
		if ( ! (agent instanceof GroupAgent))
			throw new AgentNotKnownException("Agent " + groupId + " is not a group agent!");
	
		GroupAgent group = (GroupAgent) agent; 
		
		try {
			group.unlockPrivateKey(this.getMainAgent());
		} catch (SerializationException e) {
			throw new L2pSecurityException ( "Unable to open group! - serialization problems", e);
		} catch (CryptoException e) {
			throw new L2pSecurityException ( "Unable to open group! - cryptographic problems", e);
		}
		
		groupAgents.put( groupId, group );
		
		return group;
	}
	
	
	/**
	 * Gets a stored envelope from the p2p network.
	 * 
	 * @param id
	 * 
	 * @return envelope containing the requested data
	 * 
	 * @throws ArtifactNotFoundException
	 * @throws StorageException
	 */
	public Envelope getStoredObject ( long id ) throws ArtifactNotFoundException, StorageException{
		return localNode.fetchArtifact( id );
	}
	
	
	/**
	 * Gets a stored envelope from the p2p network.
	 * The envelope will be identified by the stored class and an arbitrary identifier selected by
	 * the using service(s).
	 * 
	 * @param cls
	 * @param identifier
	 * 
	 * @return envelope containing the requested data
	 * 
	 * @throws ArtifactNotFoundException
	 * @throws StorageException
	 */
	public Envelope getStoredObject ( Class<?> cls, String identifier ) throws ArtifactNotFoundException, StorageException {
		long id = Envelope.getClassEnvelopeId(cls, identifier);
		return getStoredObject ( id );
	}
	
	
	/**
	 * Gets a stored envelope from the p2p network.
	 * The envelope will be identified by the stored class and an arbitrary identifier selected by
	 * the using service(s).
	 * 
	 * @param className
	 * @param identifier
	 * 
	 * @return envelope containing the requested data
	 * 
	 * @throws ArtifactNotFoundException
	 * @throws StorageException
	 */
	public Envelope getStoredObject ( String className, String identifier) throws ArtifactNotFoundException, StorageException {
		return getStoredObject ( Envelope.getClassEnvelopeId(className, identifier));
	}
	
	
	/**
	 * Returns the reference object to the executing node.
	 * 
	 * @return the (possibly remote) node trying to execute something at this (local) node
	 */
	public Object getNodeReference () {
		return remoteNodeReference;
	}
	
	
	/**
	 * Refers this context to a local executing agent or a remote one? 
	 * 
	 * @return true, if the request is started locally and not via the P2P network
	 */
	public boolean isLocal () {
		return remoteNodeReference == null;
	}
	
	
	/**
	 * Gives access to the local node.
	 * 
	 * @return	the local P2P node
	 */
	public Node getLocalNode() {
		return localNode;
	}
	
	
	/**
	 * Mark the current time as the last usage.
	 */
	public void touch() {
		lastUsageTime = new Date().getTime();
	}
	
	
	/**
	 * Returns the time of the last usage of this context.
	 * 
	 * @return the timestamp of the last usage
	 */
	public long getLastUsageTimestamp () {
		return lastUsageTime;
	}
	
	
	/**
	 * Uses this context as {@link AgentStorage}.
	 * Returns agents that are unlocked in this context first.
	 * E.g. necessary for opening a received {@link i5.las2peer.communication.Message}. 
	 *  
	 * @param id
	 * 
	 * @return get the agent of the given id 
	 * 
	 * @throws AgentNotKnownException
	 */
	public Agent getAgent(long id) throws AgentNotKnownException {
		if( id == agent.getId())
			return agent;
		
		Agent result;
		if ( (result = groupAgents.get(id)) != null)
			return result;
		
		return localNode.getAgent(id);
	}
	
	
	@Override
	public boolean hasAgent(long id) {
		return id == agent.getId() || groupAgents.containsKey(id);
	}
	
	
	/**
	 * Gets the current LAS2peer context.
	 * 
	 * @throws IllegalStateException called not in a LAS2peer execution thread
	 * 
	 * @return the current context
	 */
	public static Context getCurrent () {
		Thread t = Thread.currentThread();
		
		if (! ( t instanceof L2pThread ))
			throw new IllegalStateException ( "Not executed in a L2pThread environment!");
		
		return ((L2pThread) t).getContext();
	}
	
	
	/**
	 * Logs a message to the l2p system using the observers.
	 * 
	 * Since this method will/should only be used in an L2pThread, the message will come from a
	 * service or a helper, so a SERVICE_MESSAGE is assumed.
	 * 
	 * @param from the calling class
	 * @param message
	 */
	public static void logMessage ( Object from, String message ) {
		try {
			getCurrent().getLocalNode().observerNotice(Event.SERVICE_MESSAGE, getCurrent().getLocalNode().getNodeId(),
					getCurrent().getMainAgent(), from.getClass().getSimpleName() + ": " + message);
		} catch ( IllegalStateException e ) {
			System.err.println ( "Log message not in a l2p context: " + message );
		}
	}
	
	
	/**
	 * Writes a log message.
	 * The given index (1-10) can be used to differentiate between different log messages.
	 * 
	 * @param from the calling class
	 * @param index an index between 1 and 10
	 * @param message
	 */
	protected void logMessage (Object from, int index, String message ) {
		Event event = Event.SERVICE_MESSAGE; //Default
		switch(index){
        case 1:
            event = Event.SERVICE_CUSTOM_MESSAGE_1;
        case 2:
            event = Event.SERVICE_CUSTOM_MESSAGE_2;
        case 3:
            event = Event.SERVICE_CUSTOM_MESSAGE_3;
        case 4:
            event = Event.SERVICE_CUSTOM_MESSAGE_4;
        case 5:
            event = Event.SERVICE_CUSTOM_MESSAGE_5;
        case 6:
            event = Event.SERVICE_CUSTOM_MESSAGE_6;
        case 7:
            event = Event.SERVICE_CUSTOM_MESSAGE_7;
        case 8:
            event = Event.SERVICE_CUSTOM_MESSAGE_8;
        case 9:
            event = Event.SERVICE_CUSTOM_MESSAGE_9;
        case 10:
            event = Event.SERVICE_CUSTOM_MESSAGE_10;
		}
		getCurrent().getLocalNode().observerNotice(event, getCurrent().getLocalNode().getNodeId(),
				getCurrent().getMainAgent(), from.getClass().getSimpleName() + ": " + message);
	}
	
	
	/**
	 * Logs an error message to the l2p system using the observers.
	 * 
	 * Since this method will/should only be used in an L2pThread, the message will come from a
	 * service or a helper, so a SERVICE_MESSAGE is assumed.
	 * 
	 * @param from the calling class
	 * @param message
	 */
	public static void logError ( Object from, String message ) {
		try {
			getCurrent().getLocalNode().observerNotice(Event.SERVICE_ERROR, getCurrent().getLocalNode().getNodeId(),
					getCurrent().getMainAgent(), from.getClass().getSimpleName() + ": " + message);
		} catch ( IllegalStateException e ) {
			System.err.println ( "Logmessage not in a l2p context: " + message );
		}
	}
	
	
	/**
	 * Writes an error message.
	 * The given index (1-10) can be used to differentiate between different log messages.
	 * 
	 * @param from the calling class
	 * @param index an index between 1 and 10
	 * @param message
	 */
	protected void logError (Object from, int index, String message) {
		Event event = Event.SERVICE_ERROR; //Default
		switch(index){
        case 1:
            event = Event.SERVICE_CUSTOM_ERROR_1;
        case 2:
            event = Event.SERVICE_CUSTOM_ERROR_2;
        case 3:
            event = Event.SERVICE_CUSTOM_ERROR_3;
        case 4:
            event = Event.SERVICE_CUSTOM_ERROR_4;
        case 5:
            event = Event.SERVICE_CUSTOM_ERROR_5;
        case 6:
            event = Event.SERVICE_CUSTOM_ERROR_6;
        case 7:
            event = Event.SERVICE_CUSTOM_ERROR_7;
        case 8:
            event = Event.SERVICE_CUSTOM_ERROR_8;
        case 9:
            event = Event.SERVICE_CUSTOM_ERROR_9;
        case 10:
            event = Event.SERVICE_CUSTOM_ERROR_10;
		}
		getCurrent().getLocalNode().observerNotice(event, getCurrent().getLocalNode().getNodeId(),
				getCurrent().getMainAgent(), from.getClass().getSimpleName() + ": " + message);
	}
	
	
	/**
	 * Opens the given envelope using the agents of this context.
	 * 
	 * @param envelope
	 * @throws DecodingFailedException
	 * @throws L2pSecurityException 
	 */
	public void openEnvelope(Envelope envelope) throws DecodingFailedException, L2pSecurityException {
		if ( agent.isLocked())
			throw new AgentLockedException ();
		
		try {
			envelope.open ( agent );
		} catch ( L2pSecurityException e ) {
			for ( long groupId : envelope.getReaderGroups() ) {
				try {
					GroupAgent group = groupAgents.get( groupId );
					
					if ( group == null ) {
						group = (GroupAgent) getLocalNode().getAgent(groupId);
						if ( group.isMember(getMainAgent() )) {
							group.unlockPrivateKey(getMainAgent());
							groupAgents.put( groupId, group);
						}
					}
					
					if ( group != null ) {
						envelope.open ( group );
						return;
					}
				} catch (Exception e1) {
					System.out.println( "strange, no exception should occur here! - " + e1);
					e1.printStackTrace();
				}
			}
			throw e;
		}
	}
	
	
	/**
	 * Tries to unlock the private key of the main agent.
	 * 
	 * @param passphrase
	 * 
	 * @throws L2pSecurityException
	 */
	public void unlockMainAgent(String passphrase) throws L2pSecurityException {
		if(  agent instanceof PassphraseAgent)
			((PassphraseAgent) agent).unlockPrivateKey(passphrase);
		else
			throw new L2pSecurityException ( "this is not passphrase protected agent!");
	}
	
	
}