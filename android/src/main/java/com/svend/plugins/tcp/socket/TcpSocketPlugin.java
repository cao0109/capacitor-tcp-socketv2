package com.svend.plugins.tcp.socket;

import android.Manifest;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

@CapacitorPlugin(name = "TcpSocket", permissions = {@Permission(alias = "network", strings = {Manifest.permission.ACCESS_NETWORK_STATE})})
public class TcpSocketPlugin extends Plugin {

    private Socket socket;
    private DataOutputStream mBufferOut;
    private List<Socket> clients = new ArrayList<>();

    @PluginMethod
    public void connect(PluginCall call) {
        String ipAddress = call.getString("ipAddress");

        if (ipAddress == null || ipAddress.isEmpty()) {
            call.reject("Must provide ip address to connect");
            return;
        }
        Integer port = call.getInt("port", 9100);

        try {
            if (socket != null && socket.isConnected()) {
                socket.close();
            }
            socket = new Socket(ipAddress, port);
            clients.add(socket);
        } catch (IOException e) {
            Log.d("Connection failed", e.getMessage());
            call.reject(e.getMessage());
            return;
        }

        JSObject ret = new JSObject();
        ret.put("client", clients.size() - 1);
        call.resolve(ret);
    }

    @PluginMethod
    public void send(final PluginCall call) {
        final Integer client = call.getInt("client", -1);
        final String msg = call.getString("data", "");
        final String dataType = call.getString("encoding", "utf8"); // 默认使用 utf8

        if (client == -1) {
            call.reject("No client specified");
            return;
        }

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    final Socket socket = clients.get(client);
                    mBufferOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                    byte[] byteArray;

                    switch (dataType) {
                        case "utf8":
                            byteArray = msg.getBytes();
                            break;
                        case "hex":
                            byteArray = hexStringToByteArray(msg);
                            break;
                        default:
                            call.reject("Unsupported data type");
                            return;
                    }

                    if (mBufferOut != null) {
                        mBufferOut.write(byteArray);
                        mBufferOut.flush();
                    }

                    call.resolve();

                } catch (IOException e) {
                    call.reject(e.getMessage());
                }
            }
        };

        Socket socket = clients.get(client);
        if (socket == null || !socket.isConnected()) {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) {
                call.reject("Generic error");
            }
            call.reject("Socket not connected");
            return;
        }
        Thread thread = new Thread(runnable);
        thread.start();
    }

    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        if (len % 2 != 0) {
            s = "0" + s; // 如果长度是奇数，前面补0
            len++;
        }
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    @PluginMethod
    public void read(final PluginCall call) {
        final Integer client = call.getInt("client", -1);
        final Integer length = call.getInt("expectLen", 1024);

        if (client == -1 || length == -1) {
            call.reject("Client or length not specified");
            return;
        }

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    final Socket socket = clients.get(client);
                    DataInputStream mBufferIn = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                    byte[] bytes = new byte[length];
                    int read = mBufferIn.read(bytes, 0, length);

                    JSObject ret = new JSObject();
                    ret.put("result", new String(bytes, 0, read));
                    call.resolve(ret);
                } catch (IOException e) {
                    call.reject(e.getMessage());
                }
            }
        };

        Socket socket = clients.get(client);
        if (!socket.isConnected()) {
            try {
                socket.close();
            } catch (IOException e) {
                call.reject("Generic error");
            }
            call.reject("Socket not connected");
            return;
        }
        Thread thread = new Thread(runnable);
        thread.start();
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        final Integer client = call.getInt("client", -1);
        if (client == -1) {
            call.reject("No client specified");
            return;
        }
        if (clients.isEmpty()) {
            call.reject("Socket not connected");
            return;
        }
        final Socket socket = clients.get(client);
        try {
            if (!socket.isConnected()) {
                socket.close();
                call.reject("Socket not connected");
            }
            socket.close();
        } catch (IOException e) {
            call.reject(e.getMessage());
        }

        JSObject ret = new JSObject();
        ret.put("client", client);
        call.resolve(ret);
    }
}
