package no.ntnu.datakomm.chat;

import java.io.*;
import java.net.*;
import java.util.LinkedList;
import java.util.List;

public class TCPClient
{
    private PrintWriter toServer;
    private BufferedReader fromServer;
    private Socket connection;

    // Hint: if you want to store a message for the last error, store it here
    private String lastError = null;
    // Store the list of users her
    private String[] userList = null;

    private final List<ChatListener> listeners = new LinkedList<>();

    /**
     * Connect to a chat server.
     *
     * @param host host name or IP address of the chat server
     * @param port TCP port of the chat server
     * @return True on success, false otherwise
     */
    public boolean connect(String host, int port)
    {
        // Step 1:

        boolean attempt = false;

        try
        {
            connection = new Socket(host, port);
            attempt = true;

            OutputStream outputStream = connection.getOutputStream();
            toServer = new PrintWriter(outputStream, true);
            InputStream inputStream = connection.getInputStream();
            fromServer = new BufferedReader(new InputStreamReader(inputStream));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return attempt;
    }

    /**
     * Close the socket. This method must be synchronized, because several
     * threads may try to call it. For example: When "Disconnect" button is
     * pressed in the GUI thread, the connection will get closed. Meanwhile, the
     * background thread trying to read server's response will get error in the
     * input stream and may try to call this method when the socket is already
     * in the process of being closed. with "synchronized" keyword we make sure
     * that no two threads call this method in parallel.
     */
    public synchronized void disconnect()
    {
        // Step 4: implement this method
        // Hint: remember to check if connection is active

        if (isConnectionActive())
        {
            try
            {
             connection.close();
             connection = null;
             onDisconnect();
            }
            catch (IOException e)
            {
                lastError = "Could not close the socket. " + e.getMessage();
            }
        }
    }

    /**
     * @return true if the connection is active (opened), false if not.
     */
    public boolean isConnectionActive()
    {
        return connection != null;
    }

    /**
     * Send a command to server.
     *
     * @param cmd A command. It should include the command word and optional attributes, according to the protocol.
     * @return true on success, false otherwise
     */
    private boolean sendCommand(String cmd)
    {
        // Step 2
        // Hint: Remember to check if connection is active
        boolean attempt = false;

        // Check if the connection is active
        if (cmd != null)
        {
            String editedCutCmdCommand = null;
            String[] parts = cmd.split(" ");
            String cmdCommand = parts[0];

            if (cmdCommand.startsWith("/"))
            {
                String cutCmdCommand = cmdCommand.replace("/", "");
                if (cutCmdCommand.endsWith("\n")) {
                    editedCutCmdCommand = cutCmdCommand.replace("\n", "");
                }
                else
                    {
                    editedCutCmdCommand = cutCmdCommand;
                    }

                if (editedCutCmdCommand.equals("privmsg") ||
                        editedCutCmdCommand.equals("help") ||
                        editedCutCmdCommand.equals("login") ||
                        editedCutCmdCommand.equals("users")) {
                    String editedCmd = cmd.substring(1, cmd.length());

                    // Print out the console for debugging purposes
                    System.out.println("Sending command: " + editedCmd);
                    attempt = true;
                    toServer.println(editedCmd);
                }
            }
            else
                {
                System.out.println("Sending message: " + cmd);
                attempt = true;
                toServer.println("msg " + cmd);
                }
        }
        return attempt;
    }


    /**
     * Send a public message to all the recipients.
     *
     * @param message Message to send
     * @return true if message sent, false on error
     */
    public boolean sendPublicMessage(String message)
    {
        // Step 2:
        // Hint: Reuse sendCommand() method
        // Hint: update lastError if you want to store the reason for the error.
        boolean attempt = false;

        if (message != null)
        {
            sendCommand(message);
            attempt = true;
        }
        else
        {
            System.out.println("Not a valid message");
        }
        return attempt;
    }

    /**
     * Send a login request to the chat server.
     *
     * @param username Username to use
     */
    public void tryLogin(String username)
    {
        // Step 3:
        // Hint: Reuse sendCommand() method

        sendCommand("/login " + username);
    }

    /**
     * Send a request for latest user list to the server. To get the new users,
     * clear your current user list and use events in the listener.
     */
    public void refreshUserList()
    {
        // Step 5: implement this method
        // Hint: Use Wireshark and the provided chat client reference app to find out what commands the
        // client and server exchange for user listing.

        sendCommand( "/users");
    }

    /**
     * Send a private message to a single recipient.
     *
     * @param recipient username of the chat user who should receive the message
     * @param message   Message to send
     * @return true if message sent, false on error
     */
    public boolean sendPrivateMessage(String recipient, String message)
    {
        // Step 6: Implement this method
        // Hint: Reuse sendCommand() method
        // Hint: update lastError if you want to store the reason for the error.

       sendCommand( "/privmsg " + recipient + " " + message);

        return false;
    }


    /**
     * Send a request for the list of commands that server supports.
     */
    public void askSupportedCommands()
    {
        // Step 8: Implement this method
        // Hint: Reuse sendCommand() method

        sendCommand( "/help");
    }


    /**
     * Wait for chat server's response
     *
     * @return one line of text (one command) received from the server
     */
    private String waitServerResponse()
    {
        // Step 3:
        // Step 4: If you get I/O Exception or null from the stream, it means that something has gone wrong
        // with the stream and hence the socket. Probably a good idea to close the socket in that case.

        String response = null;

        if (isConnectionActive())
        {

            try
            {
                response = fromServer.readLine();
            }
            catch (IOException e)
            {
                this.disconnect();
                lastError = e.getMessage();
            }
        }
        return response;
    }

    /**
     * Get the last error message
     *
     * @return Error message or "" if there has been no error
     */
    public String getLastError()
    {
        if (lastError != null)
        {
            return lastError;
        }
        else
            {
            return "";
            }
    }

    /**
     * Start listening for incoming commands from the server in a new CPU thread.
     */
    public void startListenThread() {
        // Call parseIncomingCommands() in the new thread.
        Thread t = new Thread(() -> {
            parseIncomingCommands();
        });
        t.start();
    }

    /**
     * Read incoming messages one by one, generate events for the listeners. A loop that runs until
     * the connection is closed.
     */
    private void parseIncomingCommands()
    {
        while (isConnectionActive())
        {
            // Step 3: Implement this method
            // Hint: Reuse waitServerResponse() method

            String response = waitServerResponse();

            // Hint: Have a switch-case (or other way) to check what type of response is received from the server
            // and act on it.
            // Hint: In Step 3 you need to handle only login-related responses.
            // Hint: In Step 3 reuse onLoginResult() method
            // Step 5: update this method, handle user-list response from the server
            // Hint: In Step 5 reuse onUserList() method
            // Step 7: add support for incoming chat messages from other users (types: msg, privmsg)
            // Step 7: add support for incoming message errors (type: msgerr)
            // Step 7: add support for incoming command errors (type: cmderr)
            // Hint for Step 7: call corresponding onXXX() methods which will notify all the listeners
            // Step 8: add support for incoming supported command list (type: supported)

            if (response != null)
            {
                String[] parts = response.split("");
                String firstPart = parts[0];

                try
                {
                    switch (firstPart)
                    {
                        case "loginok":
                            onLoginResult(true, response);
                            break;

                        case "loginerr":
                            onLoginResult(false, response);
                            break;

                        case "users":
                            String cutString = response.replaceFirst("users", "");
                            String[] userList = cutString.split(" ");
                            onUsersList(userList);
                            break;

                        case "msg":
                            String cutCmdMsgString = response.replaceFirst("msg", "");
                            String[] msgParts = cutCmdMsgString.split(" ");
                            String sender = msgParts[0];
                            String cutCmdAndSenderString = cutCmdMsgString.replace(sender + " ", "");
                            onMsgReceived(false, sender, cutCmdAndSenderString);
                            break;

                        case "privmsg":
                            String cutCmdPrivMsgString = response.replaceFirst("privmsg", "");
                            String[] privMsgParts = cutCmdPrivMsgString.split(" ");
                            String privSender = privMsgParts[0];
                            String cutCmdAndPrivSenderString = cutCmdPrivMsgString.replace(privSender + " ", "");
                            onMsgReceived(false, privSender, cutCmdAndPrivSenderString);
                            break;

                        case "msgeer":
                            onMsgError(response);
                            break;

                        case "cmdeer":
                            onCmdError(response);

                        case "supported":
                            String cutCmdSupportedString = response.replaceFirst("supported", "");
                            String[] supportedParts = cutCmdSupportedString.split( " ");
                            onSupported(supportedParts);
                            break;
                    }

                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Register a new listener for events (login result, incoming message, etc)
     *
     * @param listener
     */
    public void addListener(ChatListener listener)
    {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Unregister an event listener
     *
     * @param listener
     */
    public void removeListener(ChatListener listener)
    {
        listeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    // The following methods are all event-notificators - notify all the listeners about a specific event.
    // By "event" here we mean "information received from the chat server".
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Notify listeners that login operation is complete (either with success or
     * failure)
     *
     * @param success When true, login successful. When false, it failed
     * @param errMsg  Error message if any
     */
    private void onLoginResult(boolean success, String errMsg)
    {
        for (ChatListener l : listeners) {
            l.onLoginResult(success, errMsg);
        }
    }

    /**
     * Notify listeners that socket was closed by the remote end (server or
     * Internet error)
     */
    private void onDisconnect()
    {
        // Step 4: Implement this method
        // Hint: all the onXXX() methods will be similar to onLoginResult()
        for (ChatListener l : listeners)
        {
            l.onDisconnect();
        }
    }

    /**
     * Notify listeners that server sent us a list of currently connected users
     *
     * @param users List with usernames
     */
    private void onUsersList(String[] users)
    {
        // Step 5: Implement this method

        for (ChatListener l : listeners)
        {
            l.onUserList(users);
        }
    }

    /**
     * Notify listeners that a message is received from the server
     *
     * @param priv   When true, this is a private message
     * @param sender Username of the sender
     * @param text   Message text
     */
    private void onMsgReceived(boolean priv, String sender, String text)
    {
        // Step 7: Implement this method

        for (ChatListener l : listeners)
        {
            l.onMessageReceived(new TextMessage(sender, priv, text));
        }
    }

    /**
     * Notify listeners that our message was not delivered
     *
     * @param errMsg Error description returned by the server
     */
    private void onMsgError(String errMsg)
    {
        // Step 7: Implement this method

        for (ChatListener l : listeners)
        {
            l.onMessageError(errMsg);
        }
    }

    /**
     * Notify listeners that command was not understood by the server.
     *
     * @param errMsg Error message
     */
    private void onCmdError(String errMsg)
    {
        // Step 7: Implement this method

        for (ChatListener l : listeners)
        {
            l.onCommandError(errMsg);
        }
    }

    /**
     * Notify listeners that a help response (supported commands) was received
     * from the server
     *
     * @param commands Commands supported by the server
     */
    private void onSupported(String[] commands)
    {
        // Step 8: Implement this method

        for(ChatListener l : listeners)
        {
            l.onSupportedCommands(commands);
        }
    }
}