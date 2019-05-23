package com.assortedsolutions.streaming.rtsp;

import android.util.Log;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

class RequestListener extends Thread implements Runnable
{
    private static final String TAG = "RequestListener";
    private final ServerSocket serverSocket;
    private final String username;
    private final String password;

    RequestListener(int port, String username, String password) throws IOException
    {
        this.username = username;
        this.password = password;

        try
        {
            serverSocket = new ServerSocket(port);
            start();
        }
        catch (BindException e)
        {
            Log.e(TAG,"Port already in use", e);
            throw e;
        }
    }

    public void run()
    {
        Log.i(TAG,"RTSP server listening on port " + serverSocket.getLocalPort());

        while (!Thread.interrupted())
        {
            try
            {
                // accept() waits until connection is made and then returns a socket
                Socket socket = serverSocket.accept();
                new ClientConnection(socket, username, password).start();
            }
            catch (SocketException e)
            {
                break;
            }
            catch (IOException e)
            {
                Log.e(TAG, e.getMessage(), e);
            }
        }

        Log.v(TAG,"RTSP server stopped");
    }

    void kill()
    {
        try
        {
            serverSocket.close();
        }
        catch (IOException e)
        {
            Log.e(TAG, "Closing the server threw", e);
        }

        try
        {
            this.join();
        }
        catch (InterruptedException e)
        {
            Log.e(TAG, "Waiting for the thread to die threw", e);
        }
    }
}
