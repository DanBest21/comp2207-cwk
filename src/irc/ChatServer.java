package comp2207.irc;

import java.net.ServerSocket;
import java.net.Socket;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

/**
 * The ChatServer is able to handle up to _MAXCLIENTS clients
 * simultaneously.
 * 
 * Supported vocabulary:
 * JOIN name - Request to join the group
 * YELL msg - Sends the msg to clients
 * TELL name msg - Sends the msg only the the specified client
 * EXIT - The client gets released by the server
 * @author Tim Norman, University of Southampton
 * @version 2.0
 */

public class ChatServer
{
    /** Max # of clients. */
    final int _MAXCLIENTS = 50;
    /** # of clients currently registered. */
    private int _numOfClients = 0;
    
    /** Maps name to socket. Key is clientName, value is clientOut. */
    private Map<String,PrintWriter> _map = Collections.synchronizedMap(new HashMap<String,PrintWriter>(_MAXCLIENTS));
    
    /**
     * For each client we create a thread that handles
     * all i/o with that client.
     */
    private class ServerThread extends Thread
    {
	private Socket _clientSocket;
	private String _clientName;
	private BufferedReader _clientIn;
	private PrintWriter _clientOut;
	
	ServerThread(Socket client) throws IOException
	{
	    _clientSocket = client;
	    // Open I/O steams
	    _clientIn = new BufferedReader( new InputStreamReader( client.getInputStream() ) );
	    _clientOut = new PrintWriter( new OutputStreamWriter( client.getOutputStream() ) );
	    // Welcome message.
	    _clientOut.println( "Welcome to ChatServer\n ");
	    _clientOut.flush();
	}
	
	public void run()
	{
	    try {
		Token token = null;
		ReqTokenizer reqTokenizer = new ReqTokenizer();
		// First, the client must register.
		token = reqTokenizer.getToken(_clientIn.readLine());
		if (!(token instanceof JoinToken)) {
		    _clientSocket.close();
		    return;
		}
		// Check the client's registration request.
		if (!(register(_clientName = ((JoinToken)token)._name, _clientOut))) {
		    _clientSocket.close();
		    return;
		}
		// If this succeeds, process requests until client exits.
		token = reqTokenizer.getToken( _clientIn.readLine() );
		while (!(token instanceof ExitToken)) {
		    if (token instanceof YellToken)
			yell(_clientName, ((YellToken)token)._msg);
		    if (token instanceof TellToken)
			tell(_clientName, ((TellToken)token)._rcpt, ((TellToken)token)._msg);
		    // Ignore JoinToken
		    token = reqTokenizer.getToken(_clientIn.readLine());
		}
		_clientSocket.close();
		unregister(_clientName);
	    }
	    catch (IOException e) {
		System.err.println("Caught I/O Exception.");
		unregister(_clientName);
	    }
	}
    }


    /**
     * Attempts to register the client under the specified name.
     * Return true if successful.
     */
    boolean register(String name, PrintWriter out)
    {
	if (_numOfClients >= _MAXCLIENTS)
	    return false;
	if (_map.containsKey(name)) {
	    System.err.println("ChatServer: Name already joined.");
	    return false;
	}
	try {
	    _map.put(name, out);
	}
	catch (NullPointerException e) {
	    return false;
	}
	_numOfClients++;
	return true;
    }

    /**
     * Unregisters the client with the specified name.
     */
    void unregister(String name)
    {
	_map.remove(name);
	_numOfClients--;
	yell("ChatServer", name+" has exited.");
    }

    /**
     * Send a message to all registered clients.
     */
    synchronized void yell(String sender, String msg)
    {
	String txt = sender + ": " + msg;
	Iterator iter = _map.values().iterator();
	while (iter.hasNext()) {
	    PrintWriter pw = (PrintWriter)iter.next();
	    pw.println(txt);
	    pw.flush();
	}
    }

    /**
     * Send a message to the specified recipient.
     */
    synchronized void tell(String sender, String rcpt, String msg)
    {
	String txt = sender + ": " + msg;
	PrintWriter pw = _map.get(rcpt);
	if (pw == null)
	    return; // No client with the specified name
	pw.println(txt);
	pw.flush();
    }

    /**
     * Wait for a connection request.
     */
    void startListening(int port) throws IOException
    {
	ServerSocket listener = new ServerSocket(port);
	while (true) {
	    Socket client = listener.accept();
	    new ServerThread(client).start();
	}
    }

    public static void main(String[] args) throws IOException
    {
	if (args.length != 1) {
	    System.out.println("Usage: java ChatServer <port>");
	    return;
	}
	new ChatServer().startListening(Integer.parseInt(args[0]));
    }
} // End of class ChatServer







