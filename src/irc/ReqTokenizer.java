package comp2207.irc;

import java.util.StringTokenizer;

/**
 * A scanner and parser for requests.
 */

class ReqTokenizer {
    ReqTokenizer() { ; }
    
    /**
     * Parses requests.
     */
    Token getToken(String req) {
	StringTokenizer sTokenizer = new StringTokenizer(req);
	if (!(sTokenizer.hasMoreTokens()))
	    return null;
	String firstToken = sTokenizer.nextToken();
	if (firstToken.equals("JOIN")) {
	    if (sTokenizer.hasMoreTokens())
		return new JoinToken(req, sTokenizer.nextToken());
	    else
		return null;
	}
	if (firstToken.equals("YELL")) {
	    String msg = "";
	    while (sTokenizer.hasMoreTokens())
		msg += " " + sTokenizer.nextToken();
	    return new YellToken(req, msg);
	}
	if (firstToken.equals("TELL")) {
	    String name = sTokenizer.nextToken();
	    String msg = "";
	    while (sTokenizer.hasMoreTokens())
		msg += " " + sTokenizer.nextToken();
	    return new TellToken(req, name, msg);
	}
	if (firstToken.equals("EXIT"))
	    return new ExitToken(req);
	return null; // Ignore request..
    }
}

/** 
 * The Token Prototype.
 */
abstract class Token {
    String _req;
}

/**
 * Syntax: JOIN &lt;name&gt;
 */
class JoinToken extends Token {
    String _name;
    JoinToken(String req, String name) {
	this._req = req;
	this._name = name;
    }
}

/**
 * Syntax: YELL &lt;msg&gt;
 */
class YellToken extends Token {
    String _msg;
    
    YellToken(String req, String msg) {
	this._req = req;
	this._msg = msg;
    }
}

/**
 * Syntax: TELL &lt;rcpt&gt; &lt;msg&gt;
 */
class TellToken extends Token {
    String _rcpt, _msg;
    
    TellToken(String req, String rcpt, String msg) {
	this._req = req;
	this._rcpt = rcpt;
	this._msg = msg;
    }
}

/**
 * Syntax: EXIT
 */
class ExitToken extends Token {
    ExitToken(String req) { this._req = req; }
}
