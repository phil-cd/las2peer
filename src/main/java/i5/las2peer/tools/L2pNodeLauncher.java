package i5.las2peer.tools;

import i5.las2peer.api.Connector;
import i5.las2peer.api.ConnectorException;
import i5.las2peer.classLoaders.L2pClassLoader;
import i5.las2peer.classLoaders.libraries.FileSystemRepository;
import i5.las2peer.communication.ListMethodsContent;
import i5.las2peer.communication.Message;
import i5.las2peer.communication.MessageException;
import i5.las2peer.execution.L2pServiceException;
import i5.las2peer.execution.NoSuchServiceException;
import i5.las2peer.execution.ServiceInvocationException;
import i5.las2peer.execution.UnlockNeededException;
import i5.las2peer.httpConnector.HttpConnector;
import i5.las2peer.p2p.AgentAlreadyRegisteredException;
import i5.las2peer.p2p.AgentNotKnownException;
import i5.las2peer.p2p.ArtifactNotFoundException;
import i5.las2peer.p2p.NodeException;
import i5.las2peer.p2p.NodeInformation;
import i5.las2peer.p2p.NodeNotFoundException;
import i5.las2peer.p2p.PastryNodeImpl;
import i5.las2peer.p2p.PastryNodeImpl.STORAGE_MODE;
import i5.las2peer.p2p.StorageException;
import i5.las2peer.p2p.TimeoutException;
import i5.las2peer.p2p.pastry.PastryStorageException;
import i5.las2peer.persistency.EncodingFailedException;
import i5.las2peer.persistency.Envelope;
import i5.las2peer.persistency.EnvelopeException;
import i5.las2peer.persistency.MalformedXMLException;
import i5.las2peer.security.Agent;
import i5.las2peer.security.AgentException;
import i5.las2peer.security.GroupAgent;
import i5.las2peer.security.L2pSecurityException;
import i5.las2peer.security.Mediator;
import i5.las2peer.security.PassphraseAgent;
import i5.las2peer.security.ServiceAgent;
import i5.las2peer.security.UserAgent;
import i5.las2peer.security.UserAgentList;
import i5.las2peer.testing.MockAgentFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Random;
import java.util.TreeSet;
import java.util.Vector;

import rice.p2p.commonapi.NodeHandle;



/**
 * This class implements a simple node launcher with additional 
 * methods for testing purposes.
 * 
 * The first command line parameter is the port to open for access from the p2p network.
 * The second parameter can either be "NEW" for starting a completely new p2p network or
 * a comma separated list of bootstrap nodes in form if [address/name]:port.
 * 
 * All methods to be executed can be stated via additional command line parameters to the
 * {@link #main} method. 
 * 
 * All static and parameterless methods of this class can be used this way.
 * 
 * @author Holger Jan&szlig;en
 * @author Peter de Lange
 *
 */
public class L2pNodeLauncher {
	
	
	private PastryNodeImpl node;
	
	private int nodeNumber = -1;
	
	private boolean bFinished = false;

	private CommandPrompt commandPrompt;
	
	private static Connector connector = null;
	/**
	 * is this launcher finished? 
	 */
	public boolean isFinished () { return bFinished; }
	

	/**
	 * 
	 * @return the node of this launcher
	 */
	public PastryNodeImpl getNode () { return node; }
	
	
	/**
	 * send simple test messages to the network
	 * 
	 * @throws InterruptedException
	 * @throws EncodingFailedException
	 * @throws L2pSecurityException
	 * @throws SerializationException
	 * @throws MalformedXMLException
	 * @throws IOException
	 * @throws AgentNotKnownException 
	 * @throws MessageException 
	 */
	public void sendTestMessages () throws InterruptedException, EncodingFailedException, L2pSecurityException, SerializationException, MalformedXMLException, IOException, AgentNotKnownException, MessageException {
		printMessage ( "Sending Test Messages...");
		UserAgent adam = MockAgentFactory.getAdam();
		adam.unlockPrivateKey("adamspass");
		UserAgent eve = MockAgentFactory.getEve();
		eve.unlockPrivateKey("evespass");
		
		node.sendTestMessages(adam, eve);
	}
	
	
	/**
	 * store a number of envelopes of the given size
	 * 
	 * @param count
	 * @param size
	 * 
	 * @throws NumberFormatException
	 * @throws EncodingFailedException
	 * @throws SerializationException
	 * @throws StorageException
	 * @throws MalformedXMLException
	 * @throws IOException
	 * @throws L2pSecurityException
	 */
	public void storeRandoms ( String count, String size ) throws NumberFormatException, EncodingFailedException, SerializationException, StorageException, MalformedXMLException, IOException, L2pSecurityException {
		storeRandoms ( Integer.valueOf( count), Integer.valueOf( size ));
	}
	
	
	/**
	 * store a number of envelopes of the given size
	 * 
	 * @param count
	 * @param size
	 * @throws EncodingFailedException
	 * @throws SerializationException
	 * @throws StorageException
	 * @throws MalformedXMLException
	 * @throws IOException
	 * @throws L2pSecurityException 
	 */
	public void storeRandoms ( int count, int size ) throws EncodingFailedException, SerializationException, StorageException, MalformedXMLException, IOException, L2pSecurityException {
		UserAgent eve = MockAgentFactory.getEve();
		
		Vector<Envelope> stored = new Vector<Envelope> ();
		
		for ( int i=0; i<count; i++) {
			Integer[] testContent = new Integer[ size ];
			testContent[0] = i;
			testContent[1] = nodeNumber;
			
			Random r = new Random();
			for ( int j=2; j<size; j++) testContent[j] = r.nextInt();
			
			Envelope env = Envelope.createClassIdEnvelope(testContent, "NodeTest-" + nodeNumber + "/" + i,  eve );
			node.storeArtifact(env);
			
			stored.add ( env);
		}		
		
		StringBuffer m = new StringBuffer ( " -> stored: " );
		for ( Envelope e: stored )
			m.append ( e.getId()).append (",");
		
		printMessage ( m.toString());
	}

	/**
	 * Get the envelope with the given id.
	 * The id is empty, the main user-list is returned.
	 * 
	 * @param id
	 * 
	 * @return the XML-representation of an envelope as a String
	 * @throws StorageException
	 * @throws ArtifactNotFoundException
	 * @throws NumberFormatException
	 */
	public String getEnvelope ( String id ) throws NumberFormatException, ArtifactNotFoundException, StorageException {
		if ( id == null || id.equals (""))
			id = ""+Envelope.getClassEnvelopeId(UserAgentList.class, "mainlist");
		
		return node.fetchArtifact(Long.valueOf(id)).toXmlString();
	}
	
	
	
	/**
	 * Fetch a random envelope previously stored with {@link #storeRandoms(String, String)}.
	 * 
	 * Prints the time needed for the document retrieval.
	 * 
	 * @param maxNode
	 * @param maxPerNode
	 * @throws ArtifactNotFoundException
	 * @throws StorageException
	 * @throws L2pSecurityException
	 * @throws MalformedXMLException
	 * @throws IOException
	 * @throws EnvelopeException
	 */
	public void fetchRandom ( int maxNode, int maxPerNode ) throws ArtifactNotFoundException, StorageException, L2pSecurityException, MalformedXMLException, IOException, EnvelopeException {
		Random r = new Random ();
		int generatedBy = r.nextInt ( maxNode) +1;
		int artNr = r.nextInt(maxPerNode);
		
		long before = new Date().getTime();
		long id = Envelope.getClassEnvelopeId(Integer[].class, "NodeTest-" + generatedBy + "/" + artNr);
		
		
		Integer[] content = new Integer[0];
		long result;
		try {
			Envelope e = node.fetchArtifact(id);
			long after = new Date().getTime();
			
			UserAgent eve = MockAgentFactory.getEve();
			eve.unlockPrivateKey("evespass");
			
			e.open ( eve );
			
			content = e.getContent(Integer[].class);
			
			printMessage("time: \t" + (after-before) + "\tcheck: " + (content[0] == artNr) + "/" +(content[1] == generatedBy));
			result = after-before;
		} catch ( Exception e ) {
			printMessage ( "artefact not found! : " + id  + " (" + generatedBy + "/" + artNr + ")");
			result = -100;
		}
		
		
		if ( ps != null )
			ps.println ("" + maxNode + "\t" + maxPerNode + "\t" + content.length + "\t" + result);
	}
	

	private PrintStream ps = null;
	
	/**
	 * 
	 * Fetch a number of random envelopes from the back-end for performance analysis.
	 * 
	 * @param cnt
	 * @param maxNode
	 * @param maxPerNode
	 * @param outfile
	 * @throws ArtifactNotFoundException
	 * @throws StorageException
	 * @throws L2pSecurityException
	 * @throws MalformedXMLException
	 * @throws IOException
	 * @throws EnvelopeException
	 */
	public void fetchRandoms( int cnt, int maxNode, int maxPerNode, String outfile ) throws ArtifactNotFoundException, StorageException, L2pSecurityException, MalformedXMLException, IOException, EnvelopeException {
		FileOutputStream fos = new FileOutputStream(outfile, true);
		ps = new PrintStream ( fos );
		for ( int i=0; i<cnt; i++)
			fetchRandom ( maxNode, maxPerNode );
		fos.close();
	}
	
	/**
	 * Fetch a number of random envelopes from the back-end for performance analysis.
	 * @param cnt
	 * @param maxNode
	 * @param maxPerNode
	 * @param outfile
	 * @throws NumberFormatException
	 * @throws ArtifactNotFoundException
	 * @throws StorageException
	 * @throws L2pSecurityException
	 * @throws MalformedXMLException
	 * @throws IOException
	 * @throws EnvelopeException
	 */
	public void fetchRandoms ( String cnt, String maxNode, String maxPerNode, String outfile ) throws NumberFormatException, ArtifactNotFoundException, StorageException, L2pSecurityException, MalformedXMLException, IOException, EnvelopeException {
		fetchRandoms ( Integer.valueOf( cnt), Integer.valueOf(maxNode), Integer.valueOf(maxPerNode), outfile);
	}
	
	
	/**
	 * Uploads the mock agents Adam, Eve and Abel to the LAS2peer network.
	 * Also stores Group1, Group2, Group3 and GroupA to the LAS2peer network.
	 * 
	 * @throws AgentAlreadyRegisteredException
	 * @throws L2pSecurityException
	 * @throws PastryStorageException
	 * @throws MalformedXMLException
	 * @throws IOException
	 * @throws AgentException 
	 */
	public void uploadAgents () throws L2pSecurityException, MalformedXMLException, IOException, AgentException {
		printMessage( "Storing Mock agents");
		
		int success = 0;
		int known = 0;
		UserAgent eve = MockAgentFactory.getEve();
		eve.unlockPrivateKey("evespass");
		try {
			node.storeAgent(eve);
			success ++;
		} catch (AgentAlreadyRegisteredException e) {
			printWarning ( "Exception for Agent Eve. Message: " + e.getMessage() );
			known ++;
		}
		
		UserAgent adam = MockAgentFactory.getAdam();
		adam.unlockPrivateKey("adamspass");
		try {
			node.storeAgent(adam);
			success++;
		} catch (AgentAlreadyRegisteredException e) {
			printWarning ( "Exception for Agent Adam. Message: " + e.getMessage() );
			known ++;
		}

		UserAgent abel = MockAgentFactory.getAbel();
		abel.unlockPrivateKey("abelspass");
		try {
			node.storeAgent(abel);
			success++;
		} catch (AgentAlreadyRegisteredException e) {
			printWarning ( "Exception for Agent Abel. Message: " + e.getMessage() );
			known ++;
		}

		try {
			GroupAgent g = MockAgentFactory.getGroup1();
			g.unlockPrivateKey(adam);
			node.storeAgent( g );
			success++;
		} catch (Exception e) {
			printWarning ( "Exception for Agent Group1. Message: " + e.getMessage() );
			known ++;
		}
		
		try {
			GroupAgent g = MockAgentFactory.getGroup2();
			g.unlockPrivateKey(adam);
			node.storeAgent( g );
			success++;
		} catch (Exception e) {
			printWarning ( "Exception for Agent Group2. Message: " + e.getMessage() );
			known ++;		
		}
		
		try {
			GroupAgent g = MockAgentFactory.getGroup3();
			g.unlockPrivateKey(adam);
			node.storeAgent( g );
			success++;
		} catch (Exception e) {
			printWarning ( "Exception for Agent Group3. Message: " + e.getMessage() );
			known ++;		
		}
		
		try {
			GroupAgent g = MockAgentFactory.getGroupA();
			g.unlockPrivateKey(adam);
			node.storeAgent( g );
			success++;
		} catch (Exception e) {
			printWarning ( "Exception for Agent GroupA. Message: " + e.getMessage() );
			known ++;		
		}
		uploadLoginList();
		printMessage ( "--> successfully stored " + success + " agents! - (" + known + " are already known or had problems registering!)" );
	}

	
	
	/**
	 * Start the service {@link i5.las2peer.testing.TestService}.
	 * 
	 * The TestServices can be found in the JUnit source tree. 
	 */
	public void startTestService () {
		try {
			ServiceAgent agent = MockAgentFactory.getCorrectTestService();
			agent.unlockPrivateKey("testpass");
			
			try {
				node.storeAgent(agent);
			} catch ( Exception e ) {}
			
			node.registerReceiver(agent);
			
			printMessage ( "Started Service: " + agent.getServiceClassName());
		} catch (Exception e) {
			printWarning ("Exception while registering Service-Agent for TestService: " + e );
		}
	}
	
	
	public void checkTestService(int nrNodes, int nrInis) throws L2pSecurityException, MalformedXMLException, IOException, ServiceInvocationException, UnlockNeededException, InterruptedException, TimeoutException, AgentNotKnownException {
		if ( ps == null) 
			ps = new PrintStream ( new FileOutputStream ( "service_test.txt", true));
		
		long before = new Date().getTime();

		String servicename = i5.las2peer.testing.TestService.class.getName();
		node.getServiceAgent( servicename );
				
		UserAgent eve = MockAgentFactory.getEve();
		eve.unlockPrivateKey("evespass");
		
		node.invokeGlobally(eve, servicename, "counter", new Serializable[0]);
		
		long after = new Date().getTime();
		
		ps.println ( "" + nrNodes + "\t" + nrInis + "\t" + (after-before) );
	}
	
	public void checkTestService ( String nrNodes, String nrInis ) throws ServiceInvocationException, UnlockNeededException, NumberFormatException, L2pSecurityException, MalformedXMLException, IOException, InterruptedException, TimeoutException, AgentNotKnownException {
		checkTestService ( Integer.valueOf(nrNodes), Integer.valueOf(nrInis));
	}
	
	
	
	/**
	 * Start the service {@link i5.las2peer.api.TestService2}, which is using TestService 
	 * in it's only method.
	 * 
	 * The TestServices can be found in the JUnit source tree.
	 */
	public void startTestService2 () {
		try {
			ServiceAgent agent = MockAgentFactory.getTestService2();
			agent.unlockPrivateKey("testpass");
			node.registerReceiver(agent);
			
			printMessage ( "Service TestService2 started");
		} catch (Exception e) {
			printWarning ( "Exception while registerung Service-Agent for TestService: " + e ); 
		}
	}
	

	/**
	 * 
	 */
	public void callTestServiceAsEve () {
		try {
			UserAgent eve = MockAgentFactory.getEve();
			eve.unlockPrivateKey("evespass");
			
			Object result;
			result = node.invokeGlobally(eve, "i5.las2peer.api.TestService", "inc", new Serializable [] {new Integer(10)});
			
			printMessage ("TS1: Got " + result + " from Service");
		} catch (Exception e) {
			printWarning ( "Exception while calling TestService.inc: "  + e);
			
			e.printStackTrace();
		}
	}
	
	
	public void callTestServiceAsAdam () {
		
		try {
			UserAgent adam = MockAgentFactory.getAdam();
			adam.unlockPrivateKey("adamspass");
			Mediator forAdam = new Mediator ( adam );
			
			node.registerReceiver(forAdam);
			
			Object result = forAdam.invoke("i5.las2peer.TestService", "inc", new Serializable[] { new Integer ( 2 )}, true );
			
			printMessage ( "adam got " + result );
		} catch (Exception e) {
			printWarning ( "Exception calling TestService as Adam: " + e);
		}
	}
	
	/**
	 * 
	 */
	public void callTestService2AsAdam () {
		try {
			UserAgent adam = MockAgentFactory.getAdam();
			adam.unlockPrivateKey("adamspass");
			
			Object result = node.invokeGlobally(adam, "i5.las2peer.api.TestService2", "usingOther", new Serializable [] {new Integer(10)});
			printMessage( "TS2: Got " + result + " from Service");
		} catch (Exception e) {
			printWarning ( "Exception while calling TestService2.usingOther: "  + e);

			e.printStackTrace();
		}
	}
	
	
	/**
	 * 
	 * Waits five seconds.
	 * 
	 * @throws InterruptedException
	 */
	public void waitALittle () throws InterruptedException {
		printMessage ( "Waiting 5 seconds");
		Thread.sleep ( 5000 );
	}
	
	
	/**
	 * Tries to load the agent Eve via the LAS2peer network.
	 * 
	 * @param passphrase	pass phrase to unlock the private key of eve
	 * 
	 * @return the agent
	 * 
	 * @throws AgentNotKnownException
	 * @throws MalformedXMLException
	 * @throws IOException
	 * @throws L2pSecurityException
	 */
	public UserAgent fetchEve ( String passphrase) throws AgentNotKnownException, MalformedXMLException, IOException, L2pSecurityException {
		UserAgent eve = MockAgentFactory.getEve();
		
		Agent fetched = node.getAgent(eve.getId());
			
		((UserAgent)fetched).unlockPrivateKey(passphrase);
		printMessage(  "Successfully opened key of agent " + fetched.getId() + ": " + fetched);
		
		return (UserAgent) eve;
	}
	
	
	/**
	 * Tries to load the agent Eve via the LAS2peer network.
	 * 
	 * @return eve
	 * @throws IOException 
	 * @throws MalformedXMLException 
	 * @throws AgentNotKnownException 
	 */
	public UserAgent fetchEve () throws AgentNotKnownException, MalformedXMLException, IOException {
		return (UserAgent) node.getAgent( MockAgentFactory.getEve().getId());
	}
	
	
	/**
	 * Registers the agent Eve at this node.
	 */
	public void registerEve ()  {
		UserAgent eve;
		try {
			eve = MockAgentFactory.getEve();
			eve.unlockPrivateKey("evespass");
			node.registerReceiver(eve);
			
			printMessage ( "successfully registered eve");
		} catch (Exception e) {
			printWarning ( "Exception while registering eve! " + e);
		}
	}
	
	
	/**
	 * search for running eve agents (instances) in the LAS2peer network
	 * 
	 * @throws MalformedXMLException
	 * @throws IOException
	 * @throws AgentNotKnownException
	 * 
	 * @return node handles of nodes hosting the given agent
	 */
	public Object[] searchEve () throws MalformedXMLException, IOException, AgentNotKnownException {
		UserAgent eve = MockAgentFactory.getEve();
		
		return node.findRegisteredAgent(eve);
	}
	
	/**
	 * Searches for the given agent in the LAS2peer network.
	 * 
	 * @param id
	 * @return node handles 
	 * @throws AgentNotKnownException 
	 */
	public Object[] findAgent ( String id ) throws AgentNotKnownException {
		long agentId = Long.parseLong(id);
		return node.findRegisteredAgent ( agentId );
	}
	
	
	/**
	 * look for the given service in the p2p net
	 * 
	 * @param serviceClass
	 * @return node handles
	 * @throws AgentNotKnownException
	 */
	public Object[] findService ( String serviceClass ) throws AgentNotKnownException {
		Agent agent = node.getServiceAgent(serviceClass);
		return node.findRegisteredAgent(agent);
	}
	

	/**
	 * close the current node
	 */
	public void shutdown () {
		node.shutDown();
		this.bFinished = true;
	}
	
	
	/**
	 * load passphrases from a simple text file where each line consists of
	 * the filename of the agent's xml file and a passphrase separated by a ; 
	 *  
	 * @param filename
	 * @return	hashtable containing agent file &gt;&gt; passphrase
	 */
	private Hashtable<String, String> loadPassphrases ( String filename ) {
		Hashtable<String, String> result = new Hashtable<String, String>();
		
		File file = new File ( filename) ;
		if ( file.isFile () ){
			String[] content;
			try {
				content = FileContentReader.read(file).split("\n");
				for ( String line : content ) {
					String[] split = line.trim().split(";", 2);
					result.put( split[0], split[1]);
				}
			} catch (IOException e) {
				printWarning ( "Error reading contents of " + filename + ": " + e);
				e.printStackTrace();
				bFinished = true;
			}			
		}
		
		return result;
	}
	
	
	/**
	 * Uploads the contents of the given directory to the global storage of the 
	 * LAS2peer network.
	 * 
	 * Each contained .xml-file is used as an artifact or - in case the 
	 * name of the file starts with <i>agent-</i> - as an agent to upload.
	 *
	 * If agents are to be uploaded, make sure, that the startup directory
	 * contains a <i>passphrases.txt</i> file giving the passphrases for the agents.
	 *   
	 * @param directory
	 */
	public void uploadStartupDirectory ( String directory ) {
		File dir = new File ( directory );
		if ( ! dir.isDirectory())
			throw new IllegalArgumentException ( directory + " is not a directory!");
		
		Hashtable<String, String> htPassphrases = loadPassphrases ( directory + "/passphrases.txt");
		
		for ( File xml : dir.listFiles(new FilenameFilter(){
			@Override
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".xml");
			}
		})) {
			try {
				String content = FileContentReader.read(xml);
				
				if ( xml.getName().toLowerCase().startsWith("agent")) {
					Agent agent = Agent.createFromXml(content);
					
					String passphrase = htPassphrases.get(xml.getName());
					if ( passphrase != null ) {
						if ( agent instanceof UserAgent )
							((UserAgent)agent).unlockPrivateKey(passphrase);
						else if ( agent instanceof ServiceAgent )
							((ServiceAgent)agent).unlockPrivateKey(passphrase);
						else
							throw new IllegalArgumentException ( "Unknown Agent type: " + agent.getClass());
						node.storeAgent(agent);
						printMessage ( "\t- stored agent from " + xml);
					} else {
						printWarning ( "\t- got no passphrase for agent from " + xml.getName());
					}
				} else {
					Envelope e = Envelope.createFromXml(content);
					node.storeArtifact(e);
					printMessage ( "\t- stored artifact from " + xml);
				}
			} catch (MalformedXMLException e) {
				printWarning( "unable to deserialize contents of " + xml.toString() + " into an XML envelope!");
			} catch ( IOException e ) {
				printWarning( "problems reading the contents of " + xml.toString() + ": " + e);
			} catch ( L2pSecurityException e ) {
				printWarning( "error storing agent from " + xml.toString() + ": " + e );
			} catch ( AgentAlreadyRegisteredException e ) {
				printWarning( "agent from " + xml.toString() + " already known at this node!");
			} catch ( AgentException e ) {
				printWarning( "unable to generate agent " + xml.toString() + "!");				
			} catch ( StorageException e ) {
				printWarning( "unable to store contents of " + xml.toString() + "!");				
			}
		}
		uploadLoginList();
	}
	
	
	/**
	 * Upload the contents of <i>startup</i> sub directory to the global storage of the 
	 * LAS2peer network.
	 * 
	 * Each contained .xml-file is used as an artifact or - in case the 
	 * name of the file starts with <i>agent-</i> - as an agent to upload.
	 * 
	 * If agents are to be uploaded, make sure, that the startup directory
	 * contains a <i>passphrases.txt</i> file giving the passphrases for the agents.  
	 */
	public void uploadStartupDirectory () {
		uploadStartupDirectory ("startup");
	}
	
		
	/**
	 * First, get the agent description for the TestService, then try to find 2 running versions.
	 */
	public void searchTestService () {
		try {
			Agent test = node.getAgent( ServiceAgent.serviceClass2Id("i5.las2peer.api.TestService"));
			ColoredOutput.printlnYellow( "got agent " + test.getId() + " (" + test.getClass()+")");
			
			Object[] handles;
			handles = node.findRegisteredAgent( node.getServiceAgent("i5.las2peer.api.TestService"), 1);
			ColoredOutput.printlnYellow( "Found Nodes Hosting TestService:");
			for ( Object o: handles )
				ColoredOutput.printlnYellow ( "\t" + o);
			
			//if ( handles.length > 0)
				//nodeHandleForTestService = handles[0];
		} catch (AgentNotKnownException e) {
			ColoredOutput.printlnRed( "Exception while looking gor TestService: " + e);
		}
	}
	
	
	/**
	 * start the HTTP connector at the given port
	 * 
	 * @param port
	 */
	public void startHttpConnector ( String port ) {
		startHttpConnector( Integer.valueOf ( port ) );
	}
	
	
	/**
	 * start the HTTP connector at the default port (8080)
	 * 
	 */
	public void startHttpConnector () {
		startHttpConnector ( 8080 );
	}
	
	/**
	 * start the HTTP connector at the given port
	 * 
	 * @param port
	 */
	public void startHttpConnector ( final int port ) {
		
		try {
			printMessage( "Starting Http Connector!");
			connector = new HttpConnector ();
			connector.setPort( port );
			connector.start( node );
			
		} catch (FileNotFoundException e) {
			printWarning ( " --> Error finding connector logfile!" + e );
		} catch (ConnectorException e) {
			printWarning ( " --> problems starting the connector: " + e);
		}
		
	}
	
	
	private UserAgent currentUser;
	
	
	/**
	 * Try to register the user of the given id at the node and for later usage in this launcher,
	 * i.e. for service method calls via {@link #invoke}.
	 * 
	 * @param id			id or login of the agent to load
	 * @param passphrase	passphrase to unlock the private key of the agent
	 * 
	 * @return	the registered agent
	 */
	public boolean registerUserAgent ( String id, String passphrase ) {
		try {
			if ( id.matches("-?[0-9].*"))
				currentUser = (UserAgent) node.getAgent(Long.valueOf(id));
			else
				currentUser = (UserAgent) node.getAgent( node.getAgentIdForLogin(id) );
			
			currentUser.unlockPrivateKey(passphrase);
			
			node.registerReceiver(currentUser);
			
			return true;
		} catch ( Exception e ) {
			e.printStackTrace();
			currentUser = null;
			return false;
		}
	}
	
	
	/**
	 * force an upload of the current user list
	 */
	public void uploadLoginList() {
		node.forceUserListUpdate();
	}
	
	
	/**
	 * Register the given agent at the LAS2peer node and for later usage with {@link #invoke}.
	 * 
	 * Make sure, that the private key of the agent is unlocked before registering
	 * 
	 * @param agent
	 * 
	 * @throws L2pSecurityException
	 * @throws AgentAlreadyRegisteredException
	 * @throws AgentException
	 */
	public void registerUserAgent ( UserAgent agent ) throws L2pSecurityException, AgentAlreadyRegisteredException, AgentException {
		registerUserAgent (agent, null);
	}
	
	/**
	 * Register the given agent at the l2p node and for later usage with {@link #invoke}.
	 * 
	 * If the private key of the agent is not unlocked and a pass phrase has been given, an attempt to 
	 * unlock the key is started before registering.
	 * 
	 * @param agent
	 * @param passphrase
	 * 
	 * @throws L2pSecurityException
	 * @throws AgentAlreadyRegisteredException
	 * @throws AgentException
	 */
	public void registerUserAgent ( UserAgent agent, String passphrase ) throws L2pSecurityException, AgentException {
		if ( passphrase != null && agent.isLocked() )
			agent.unlockPrivateKey(passphrase);
		if ( agent.isLocked())
			throw new IllegalStateException ( "You have to unlock the agent first or give a correct passphrase!");
		try{
			node.registerReceiver(agent);

		}
		catch (AgentAlreadyRegisteredException e)
		{
		}
		currentUser = agent;
	}
	
	
	/**
	 * Unregister the current user from the LAS2peer node and from this launcher.
	 * 
	 * @see #registerUserAgent
	 */
	public void unregisterCurrentAgent ()  {
		if ( currentUser == null )
			return;
		
		try {
			node.unregisterAgent(currentUser);
		} catch ( AgentNotKnownException  e ) {}
		
		currentUser = null;
	}
	
	
	/**
	 * Invokes a service method as the current agent.
	 * 
	 * @see #registerUserAgent
	 * 
	 * @param serviceClass
	 * @param parameters
	 * @throws L2pServiceException any exception during service method invocation
	 */
	public Serializable invoke ( String serviceClass, String serviceMethod, Serializable... parameters ) throws L2pServiceException {
		if ( currentUser == null )
			throw new IllegalStateException ( "please register a valid user with registerUserAgent before invoking!");
		
		try {
			try {
				return node.invokeLocally(currentUser.getId(), serviceClass, serviceMethod, parameters);
			} catch ( NoSuchServiceException e ) {
				return node.invokeGlobally(currentUser, serviceClass, serviceMethod, parameters);
			}
		} catch (Exception e) {
			throw new L2pServiceException ( "Exception during service method invocation!", e );
		}
	}
	
	
	/**
	 * Returns a list of available methods for the given service class name.
	 * 
	 * @param serviceName
	 * 
	 * @return list of methods encapsulated in a ListMethodsContent
	 * 
	 * @throws L2pSecurityException
	 * @throws AgentNotKnownException 
	 * @throws InterruptedException 
	 * @throws SerializationException 
	 * @throws EncodingFailedException 
	 * @throws TimeoutException 
	 */
	public ListMethodsContent getServiceMethods ( String serviceName ) throws L2pSecurityException, AgentNotKnownException, InterruptedException, EncodingFailedException, SerializationException, TimeoutException {
		if ( currentUser == null )
			throw new IllegalStateException ( "please log in a valid user with registerUserAgent before!");		
		
		Agent receiver = node.getServiceAgent(serviceName);
		Message request = new Message ( currentUser, receiver, (Serializable) new ListMethodsContent (), 30000 );
		request.setSendingNodeId((NodeHandle) node.getNodeId());
		
		Message response = node.sendMessageAndWaitForAnswer(request);
		response.open( currentUser, node );
		
		return (ListMethodsContent) response.getContent();
	}
	
	
	/**
	 * Generate a new {@link i5.las2peer.security.ServiceAgent} instance for the given
	 * service class and start an instance of this service at the current LAS2peer node.
	 * 
	 * @param serviceClass
	 * 
	 * @return	the passphrase of the generated {@link i5.las2peer.security.ServiceAgent}
	 * @throws L2pServiceException 
	 */
	public String startService ( String serviceClass ) throws L2pServiceException {
		try {
			String passPhrase = SimpleTools.createRandomString(20);
			
			ServiceAgent myAgent = ServiceAgent.generateNewAgent(serviceClass, passPhrase);
			myAgent.unlockPrivateKey(passPhrase);
			
			node.registerReceiver(myAgent);
			
			return passPhrase;
		} catch (Exception e) {
			
			if ( e instanceof L2pServiceException )
				throw (L2pServiceException) e;
			else
				throw new L2pServiceException ( "Error registering the service at the node!", e);
		}
	}
	
	
	/**
	 * start a service defined by an XML file of the corresponding agent
	 * @param file
	 * @param passphrase
	 * @return the service agent
	 * @throws Exception
	 */
	public ServiceAgent startServiceXml ( String file, String passphrase ) throws Exception {
		try {
			ServiceAgent sa = ServiceAgent.createFromXml(FileContentReader.read( file ));
			sa.unlockPrivateKey(passphrase );
			startService ( sa );
			return sa;
		} catch ( Exception e  ) {
			System.out.println ( "starting service failed");
			e.printStackTrace();
			throw e;
		}
	}
	
	
	/**
	 * start a service with a known agent 
	 * 
	 * @param serviceClass
	 * @param agentPass
	 * @throws L2pSecurityException 
	 * @throws AgentException 
	 * @throws AgentAlreadyRegisteredException 
	 */
	public void startService ( String serviceClass, String agentPass ) throws AgentNotKnownException, L2pSecurityException, AgentAlreadyRegisteredException, AgentException {
		ServiceAgent sa = node.getServiceAgent(serviceClass);
		sa.unlockPrivateKey(agentPass);
		startService ( sa );
	}
	

	/**
	 * start the service defined by the given (Service-)Agent
	 * 
	 * @param serviceAgent
	 * 
	 * @throws AgentAlreadyRegisteredException
	 * @throws L2pSecurityException
	 * @throws AgentException
	 */
	public void startService ( Agent serviceAgent ) throws AgentAlreadyRegisteredException, L2pSecurityException, AgentException {
		if ( !( serviceAgent instanceof ServiceAgent ) )
			throw new IllegalArgumentException( "given Agent is not a service agent!" );
		if ( serviceAgent.isLocked())
			throw new IllegalStateException  ( "You have to unlock the agent before starting the corresponding service!");
		
		node.registerReceiver( serviceAgent );
	}
	
	
	/**
	 * load an agent from an XML file and return it for later usage
	 * 
	 * @param filename	name of the file to load
	 * 
	 * @return	the loaded agent 
	 * @throws AgentException 
	 */
	public Agent loadAgentFromXml ( String filename ) throws AgentException {
		try {
			String contents = FileContentReader.read(filename);
			Agent result = Agent.createFromXml(contents);
			return result;
		} catch (Exception e) {
			throw new AgentException ( "problems loading an agent from the given file", e);
		}
	}
	
	
	/**
	 * try to unlock the private key of the given agent with the given pass phrase
	 * @param agent
	 * @param passphrase
	 * @throws L2pSecurityException
	 */
	public void unlockAgent ( PassphraseAgent agent, String passphrase ) throws L2pSecurityException {
		agent.unlockPrivateKey(passphrase);
	}
	
	
	/**
	 * start interactive console mode
	 * based on a {@link i5.las2peer.tools.CommandPrompt}
	 */
	public void interactive() {
		System.out.println ( 
				"Entering interactive mode for node " + this + "\n"
				+"-----------------------------------------------\n"
				+ "Enter 'help' for further information of the console.\n"
				+ "Use all public methods of the L2pNodeLauncher class for interaction with the P2P network.\n\n"
		);
	
		commandPrompt.startPrompt();
		
		System.out.println ( "Exiting interactive mode for node " + this );
	}
	
	
	/**
	 * get the information stored about the local Node
	 * 
	 * @return a node information 
	 * @throws CryptoException 
	 */
	public NodeInformation getLocalNodeInfo () throws CryptoException {
		return node.getNodeInformation();
	}
	
	
	
	/**
	 * try to get node information about all known (neighbor) nodes
	 * 
	 * @return	array with node informations or catched exceptions
	 */
	public Object[] getInfoOfKnownNodes () {
		final Object[] known = node.getOtherKnownNodes();
		final Object[] answer = new Object[ known.length];
		
		//System.out.println( "  -> ask " + known.length + " nodes about their information!");
		
		
		final Thread[] subs = new Thread[known.length];
		for ( int i=0; i<answer.length; i++ ) {
			final int number = i;
			subs[number] = new Thread( new Runnable () {
				@Override
				public void run() {
					System.out.println( "" + number + " started");
					try {
						answer[number] = node.getNodeInformation(known[number]);
												
						// System.out.println( "rec: " + answer[number]);
						
						try {
							((NodeInformation) answer[number]).verifySignature();
							System.out.println( "Answer of " + number + " is authentic!");
						} catch (L2pSecurityException e) {
							System.out.println( "Answer of " + number + " is faulty!!!!");
						}
						
					} catch (NodeNotFoundException e) {
						answer[number] = e;
					}
					
					System.out.println( "" + number + " finished");
				}
			});
			subs[number].start();
		}
		
		for ( int i=0; i<answer.length; i++)
			try {
				System.out.println( "waiting for " + i);
				subs[i].join();
				System.out.println( "waiting for " + i + " done");
			} catch (InterruptedException e) {
			}
		
		return answer;
	}
	
	/**
	 * get information about other nodes (probably neighbors in the ring etc)
	 * 
	 * @return string with node information
	 */
	public String getNetInfo () {
		return SimpleTools.join( node.getOtherKnownNodes(), "\n\t");
	}
	
	
	
	/**
	 * Creates a new node launcher instance.
	 * 
	 * @param port local port number to open
	 * @param bootstrap comma separated list of bootstrap nodes to connect to or "NEW"
	 * @param monitoringObserver determines, if the monitoring-observer will be started at this node
	 */
	private L2pNodeLauncher ( int port, String bootstrap, boolean monitoringObserver ) {
		if ( System.getenv().containsKey("MEM_STORAGE"))
			node = new PastryNodeImpl ( port, bootstrap, STORAGE_MODE.memory, monitoringObserver );		
		else
			node = new PastryNodeImpl ( port, bootstrap, STORAGE_MODE.filesystem, monitoringObserver );		
		
		commandPrompt = new CommandPrompt ( this );
	}
	
	
	/**
	 * Creates a new node launcher instance. 
	 * 
	 * @param port local port number to open
	 * @param bootstrap	comma separated list of bootstrap nodes to connect to or "NEW"
	 * @param nodeNumber (local) number to identify the launcher instance
	 * @param monitoringObserver determines, if the monitoring-observer will be started at this node
	 */
	private L2pNodeLauncher ( int port, String bootstrap, int nodeNumber, boolean monitoringObserver ) {
		this ( port, bootstrap, monitoringObserver );
		
		this.nodeNumber = nodeNumber;
	}
	
	/**
	 * set the directory to write the logfile(s) to
	 * 
	 * @param logDir
	 */
	private void setLogDir ( File logDir ) {
		node.setLogfilePrefix(logDir + "/" + nodeNumber + "__l2p-node__");
	}
	
	/**
	 * actually start the node
	 * @throws NodeException
	 */
	private void start() throws NodeException {
		node.launch();		
		printMessage ( "node started!");
	}
	
		
	/**
	 * get s simple string for identifying the node (launcher) at command line output
	 * 
	 * @return	a node (launcher) id
	 */
	private String nodeString() {
		if ( nodeNumber >= 0)
			return "Node " + nodeNumber + ": ";
		else
			return "";
	}
	
	@Override 
	public String toString () {
		return "Node " + nodeNumber;
	}
	
	
	/**
	 * print a (yellow) message to the console
	 * @param message
	 */
	private void printMessage ( String message ) {
		ColoredOutput.printlnYellow ( nodeString() + message );
	}
	
	
	/**
	 * print a (red) warning message to the console
	 * @param message
	 */
	private void printWarning ( String message ) {
		ColoredOutput.printlnRed( nodeString() + message );
	}
	
	
	private static Integer finished = 0;
	
	/**
	 * increase the counter of finished nodes
	 */
	private static void incFinished () {
		synchronized ( finished ) {
			finished ++;
		}
	}
	
	private static final long waitTime = 5000; // 5 seconds
	

	/**
	 * wait for at least <i>number</i> other nodes to finish their work
	 * 
	 * @param number
	 */
	public void waitFinished ( int number ) {
		do {
			printMessage ( "waiting for " + (number-finished) + " of " + number + " ..." );
			//synchronized ( finished ) {
				try {
					Thread.sleep ( waitTime );
				} catch (InterruptedException e) {
				}
			//}
		} while ( number > finished );
	}
	
	
	/**
	 * wait for at least <i>number</i> other nodes to finish their work
	 * @param number
	 */
	public void waitFinished ( String number ) {
		waitFinished ( Integer.valueOf(number));
	}
	
	
	/**
	 * wait for an enter key
	 * @throws IOException 
	 */
	public void waitEnter ( String message ) throws IOException {
		if ( message != null )
			printMessage ( message );
		printMessage ( "WAITING FOR ENTER...........");
		System.in.read();
	}
	
	/**
	 * wait for an enter key
	 * @throws IOException 
	 */
	public void waitEnter () throws IOException {
		waitEnter ( null );
	}
	
	
	/**
	 * launch single node
	 * 
	 * @param args
	 * @param nodeNumber
	 * @param logDir
	 * @throws NodeException
	 */
	static L2pNodeLauncher launchSingle ( String[] args, int nodeNumber, File logDir, L2pClassLoader cl) throws NodeException {
		int port = Integer.parseInt(args[0].trim());
		String bootstrap = args[1];
		L2pNodeLauncher launcher;
		int startWith = 2; //To be backwards compatible, startObserver and service directory entries are not required
		if (args.length > 3 && args[2].equals("startObserver")){
			launcher = new L2pNodeLauncher (port, bootstrap, nodeNumber, true);
			startWith++;
		}
		else{
			launcher = new L2pNodeLauncher (port, bootstrap, nodeNumber, false);
		}
		try {
			if ( logDir != null )
				launcher.setLogDir ( logDir );
			launcher.start();
			
			CommandPrompt cmd = new CommandPrompt ( launcher ) ;
			
			for ( int i=startWith; i<args.length; i++) {
				System.out.println ( "Handling: '" + args[i]+ "'");
				cmd.handleLine(args[i]);
			}	
			
			incFinished();
			
			if ( launcher.isFinished() )
				launcher.printMessage ( "All commands have been handled and shutdown has been called -> end!");
			else
				launcher.printMessage ( "All commands have been handled -- keeping node open!");
		} catch ( NodeException e ) {
			launcher.bFinished = true;
			e.printStackTrace();
			throw e;
		}
		
		return launcher;
	}
	
	/**
	 * Sets up the classloader.
	 * 
	 * @return a class loader looking into the given directories
	 * 
	 */
	private static L2pClassLoader setupClassLoader(String[] serviceDirectory) {
		return new L2pClassLoader( 
				new FileSystemRepository (serviceDirectory), L2pNodeLauncher.class.getClassLoader() 
				);
	}
	
	
	/**
	 * Tries to kill the complete process.
	 */
	public void killAll () {
		System.out.println("System.exit was called!");
		System.exit(0);
	}
	
	
	/**
	 * Create a new subdirectory of the log directory below the current working directory.
	 * 
	 * Use the current date and a counter as name for the new directory.
	 * 
	 * @return	log directory name
	 */
	private static File getLogSubDir () {
		File log = new File ("log");
		
		if ( ! log.exists () )
			log.mkdirs();
		
		if ( ! log.isDirectory() )
			throw new RuntimeException ( "log is not a directory!");
		
		String dateString = new SimpleDateFormat ( "yyyy-MM-dd_").format ( new Date() );
		int counter = 0;
		
		DecimalFormat format = new DecimalFormat( "000");
		while ( new File ( "log/" + dateString + format.format(counter)).exists() )
			counter++;
		
		File newDir = new File ( "log/" + dateString + format.format(counter) );
		newDir.mkdir();
		
		return newDir;
	}
	
	
	/**
	 * launch a node for each .node file in the given configuration directory
	 * 
	 * @param dirname
	 */
	private static void launchFromConfigDir (String dirname ) {
		File dir = new File ( dirname );
		
		final File logDir = getLogSubDir ();
		ColoredOutput.printlnYellow("Using " + logDir + " as log directory!\n\n" );
		
		if ( ! dir.exists() || ! dir.isDirectory() ) {
			ColoredOutput.printlnYellow("Config dir does not exist or is not a directory!" );
			return;
		}
		
		File[] files = dir.listFiles();
		TreeSet<File> configFiles = new TreeSet<File> ();
		
		for ( File f : files ) {
			if (f.isFile() && ! f.isHidden() && f.getName().endsWith (".node"))
				configFiles.add(f);
		}
		
		// sort files
		File[] sorted = configFiles.toArray(new File[0]);
		java.util.Arrays.sort( sorted );
				
		Vector<LauncherThread> subThreads = new Vector<LauncherThread> ();
		
				
		int nodeNumber = 0;
		for ( File config : sorted ) {
			nodeNumber ++;
			
			LauncherThread thread = new LauncherThread ( config, nodeNumber, logDir );
			thread.start();
			subThreads.add ( thread);
			
			// wait a second between node startup
			try {
				Thread.sleep ( 1000 );
			} catch (InterruptedException e) {
			}
		}
					
		
		boolean finished;
		try {
			do {
				finished = true;
				Thread.sleep ( 1000 );
				for ( LauncherThread sub: subThreads ) {
					finished = finished && sub.isFinished();
					//if ( ! sub.isFinished () )
					//	System.out.println ( "Node " + sub.getName() + " is not finished!");
				}
			} while ( ! finished );
		} catch (InterruptedException e) {
			ColoredOutput.printlnRed("\n\nWaiting interrupted!");
		}
				
		ColoredOutput.printlnYellow("\n\nall nodes seem to have finished - exiting!");
		System.exit(0);
	}
	
	
	/**
	 * Prints a help message for command line usage.
	 * 
	 * @param message a custom message that will be shown before the help message content
	 */
	public static void printHelp ( String message ) {
		if ( message != null && ! message.equals( ""))
			System.out.println (message + "\n\n");
		
		System.out.println( "LAS2peer node launcher");
		System.out.println( "----------------------\n");
		System.out.println( "usage:\n");
		
		System.out.println( "help message:");
		System.out.println ( "\tjava [-cp classpath] i5.las2peer.testing.L2pNodeLauncher ['--help'|'-h']");
		
		System.out.println ("\nStart Single Node:");
		System.out.println ( "\tjava [-cp classpath] i5.las2peer.tools.L2pNodeLauncher {optional: windows_shell} -s [port] ['-'|bootstrap] {optional: startObserver} {method1} {method2} ...");
		
		System.out.println ( "\nWhere" );
		System.out.println ( "\t- {windows_shell} disables the colored output (better readable for windows command line clients)\n");
		System.out.println ( "\t- [port] specifies the port number for the pastry port of the new local node\n");
		System.out.println ( "\t- '-' states, that a complete new p2p network is to start");
		System.out.println ( "\tor");
		System.out.println ( "\t- [bootstrap] gives a comma seperated list of [address:ip] pairs of bootstrap nodes to connect to\n");
		System.out.println ( "\t- {startObserver} starts a monitoring observer at this node\n\n");
		
		System.out.println ("\nStart Multiple Nodes: unchecked functionality at the moment, use at own risk;-)");
		System.out.println ( "\tjava [-cp classpath] i5.las2peer.testing.L2pNodeLauncher -d [config directory]");
		
		System.out.println ( "\tWhere");
		System.out.println ( "\t- [config directory] gives a directory containing .node-files, where each ");
		System.out.println ( "\tfile configures a node in of the simulated network. The files follow a simple syntax: ");
		System.out.println ( "\t\t[port]");
		System.out.println ( "\t\t[bootstrap]");
		System.out.println ( "\t\t[method1]");
		System.out.println ( "\t\t[method2]");
		System.out.println ( "\t\t...");

		System.out.println ( "\n\nCurrently implemented testing methods to execute at the new node are:");
		
		for ( Method m : L2pNodeLauncher.class.getMethods()) {
			if ( Modifier.isPublic ( m.getModifiers()) 
					&& !Modifier.isStatic(m.getModifiers() )
					&& m.getParameterTypes().length == 0
					) {
				System.out.println( "\t\t- " + m.getName());
			}
		}
		
		System.out.println ( "\nYou can use them in any arbitrary order and number.\n");
	}
	
	
	/**
	 * Prints a help message for command line usage.
	 */
	public static void printHelp () { printHelp ( null ); }
	
	
	/**
	 * 
	 * Main method for command line processing.
	 * 
	 * 
	 * The method will start a node and try to invoke all command line parameters as
	 * parameterless methods of this class.
	 * 
	 * Hint: use "windows_shell" as the first command to turn off all colored output
	 * (since this creates cryptic symbols in a Windows environment).
	 * 
	 * Hint: with "log-directory=.." you can set the logfile directory you want to use.
	 * @param argv
	 * 
	 * @throws InterruptedException
	 * @throws MalformedXMLException
	 * @throws IOException
	 * @throws L2pSecurityException
	 * @throws EncodingFailedException
	 * @throws SerializationException
	 * @throws NodeException 
	 */
	public static void main ( String[] argv ) throws InterruptedException, MalformedXMLException, IOException, L2pSecurityException, EncodingFailedException, SerializationException, NodeException  {
		String logfileDirectoryString = "log";
		File logfileDirectory = new File (".");
		if ( argv.length < 2 || argv[0].equals( "--help") || argv[0].equals("-h")) {
			printHelp();
			System.exit(1);
		}
		//Set this parameter to turn of the (bash-) color features for a better readable
		//output on the windows console.
		if(argv[0].equals("windows_shell")){
			ColoredOutput.allOff();
			String[] args = new String [argv.length-1];
			System.arraycopy( argv, 1, args, 0, args.length );
			argv = args;
		}
		//
		if(argv[0].contains("log-directory=")){
			logfileDirectoryString = argv[0].substring(argv[0].indexOf("="));
			String[] args = new String [argv.length-1];
			System.arraycopy( argv, 1, args, 0, args.length );
			argv = args;
		}
		logfileDirectory = new File ("./" + logfileDirectoryString + "/");
		System.out.println("Logfile is set to: " + logfileDirectory.toString());
		if ( argv[0].equals ( "-s")) {
			String[] args = new String [ argv.length-1];
			System.arraycopy( argv, 1, args, 0, args.length );
			String[] serviceDirectory = {"./service/"}; //Temporary solution TODO
			L2pClassLoader classloader = setupClassLoader(serviceDirectory);
			// launch a single node
			L2pNodeLauncher launcher = launchSingle( args, -1, logfileDirectory, classloader);
			
			if ( launcher.isFinished() ){
				System.out.println( "single node has handled all commands and shut down!");
				try {
					if(connector != null)
						connector.stop();
					} catch (ConnectorException e) {
					e.printStackTrace();
				}
			}
			else {
				System.out.println ( "single node has handled all commands -- keeping node open\n");
				System.out.println ( "press Strg-C to exit\n");
				try{
				while (true) {
					Thread.sleep(5000);
				}
				} catch (InterruptedException e) {
					try {
						if(connector != null)
							connector.stop();
					} catch (ConnectorException ce) {
						ce.printStackTrace();
					}
				}
			}
		} else if ( argv[0].equals ( "-d")) {
			// launch from a directory (unchecked functionality at the moment!!)
			System.out.println("Launching multiple nodes, please note that this functionality is not tested at the moment!");
			launchFromConfigDir ( argv[1] );
		} else {
			System.out.println( 
					"Please start either a single node with -s or several "
					+"nodes defined by a configuration directory with -d. Use --help or -h "
					+"for further information.");
		}
	
	}
	
}