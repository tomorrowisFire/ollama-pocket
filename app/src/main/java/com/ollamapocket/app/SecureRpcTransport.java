package com.ollamapocket.app;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Authenticated encrypted LAN transport for a loopback-only ggml-rpc-server. No raw RPC on Wi-Fi. */
public final class SecureRpcTransport {
    private SecureRpcTransport() { }
    private static final byte[] MAGIC = { 'A', 'I', 'P', 'R', '1' };
    private static final int MAX_FRAME = 32768;
    private static final SecureRandom RNG = new SecureRandom();

    public static byte[] secret() { byte[] b = new byte[32]; RNG.nextBytes(b); return b; }
    private static byte[] mac(byte[] key, byte[]... parts) throws GeneralSecurityException {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(key, "HmacSHA256"));
        for (byte[] p : parts) m.update(p);
        return m.doFinal();
    }
    private static byte[] utf8(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    public static String hmacHex(byte[] secret, String text) throws GeneralSecurityException {
        byte[] mac = mac(secret, utf8(text));
        char[] hex = "0123456789abcdef".toCharArray();
        char[] result = new char[mac.length * 2];
        for (int i = 0; i < mac.length; i++) { result[i * 2] = hex[(mac[i] >> 4) & 15]; result[i * 2 + 1] = hex[mac[i] & 15]; }
        return new String(result);
    }
    public static boolean constantEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(utf8(a), utf8(b));
    }

    public static Channel connect(Socket socket, byte[] secret) throws Exception {
        socket.setSoTimeout(5000);
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        DataInputStream in = new DataInputStream(socket.getInputStream());
        byte[] client = new byte[16], server = new byte[16]; RNG.nextBytes(client);
        out.write(MAGIC); out.write(client); out.flush();
        in.readFully(server);
        byte[] proof = new byte[32]; in.readFully(proof);
        byte[] expected = mac(secret, utf8("server"), client, server);
        if (!MessageDigest.isEqual(proof, expected)) throw new IOException("Host/guest authentication failed");
        out.write(mac(secret, utf8("client"), client, server)); out.flush();
        socket.setSoTimeout(0);
        return new Channel(socket, mac(secret, utf8("c2s"), client, server),
                mac(secret, utf8("s2c"), client, server));
    }

    public static Channel accept(Socket socket, byte[] secret) throws Exception {
        socket.setSoTimeout(5000);
        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        byte[] magic = new byte[MAGIC.length], client = new byte[16], server = new byte[16];
        in.readFully(magic);
        if (!MessageDigest.isEqual(MAGIC, magic)) throw new IOException("Invalid RPC relay protocol");
        in.readFully(client); RNG.nextBytes(server);
        out.write(server); out.write(mac(secret, utf8("server"), client, server)); out.flush();
        byte[] proof = new byte[32]; in.readFully(proof);
        if (!MessageDigest.isEqual(proof, mac(secret, utf8("client"), client, server)))
            throw new IOException("Client authentication failed");
        socket.setSoTimeout(0);
        return new Channel(socket, mac(secret, utf8("s2c"), client, server),
                mac(secret, utf8("c2s"), client, server));
    }

    public static final class Channel implements AutoCloseable {
        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;
        private final byte[] sendKey, receiveKey;
        private long sent, received;
        private boolean sendEnded,receiveEnded;
        private volatile boolean closed;
        private final Object sendLock=new Object();
        private final Object recvLock=new Object();
        private Channel(Socket socket, byte[] sendKey, byte[] receiveKey) throws IOException {
            this.socket = socket;
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            this.sendKey = sendKey; this.receiveKey = receiveKey;
        }
        private static byte[] nonce(byte[] key, long index) {
            byte[] n = new byte[12];
            System.arraycopy(key, 0, n, 0, 4);
            ByteBuffer.wrap(n, 4, 8).putLong(index);
            return n;
        }
        /** Caller owns source stream. Sending zero bytes signals EOF. */
        public void send(byte[] data, int off, int length) throws IOException {
            synchronized(sendLock) {
            if(closed||sendEnded||sent==Long.MAX_VALUE)throw new IOException("RPC send channel closed");
            if(data==null||off<0||length<0||length>MAX_FRAME||off>data.length-length)throw new IOException("Invalid plaintext size");
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sendKey, "AES"),
                    new GCMParameterSpec(128, nonce(sendKey, sent++)));
                byte[] encoded = cipher.doFinal(data, off, length);
                out.writeInt(encoded.length); out.write(encoded); out.flush();
                if(length==0)sendEnded=true;
            } catch (GeneralSecurityException e) { throw new IOException(e); }
            }
        }
        /** Returns -1 on authenticated EOF, otherwise decrypted bytes. */
        public int receive(byte[] buffer) throws IOException {
            synchronized(recvLock) {
            if(receiveEnded)return -1;
            if(closed||received==Long.MAX_VALUE)throw new IOException("RPC receive channel closed");
            int size;
            try { size = in.readInt(); } catch (EOFException e) { throw new IOException("RPC ended without authenticated EOF",e); }
            if (size < 16 || size > MAX_FRAME + 16) throw new IOException("Invalid encrypted frame length");
            byte[] bytes = new byte[size]; in.readFully(bytes);
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(receiveKey, "AES"),
                    new GCMParameterSpec(128, nonce(receiveKey, received++)));
                byte[] clear = cipher.doFinal(bytes);
                try{
                    if(clear.length==0){receiveEnded=true;return -1;}
                    if(buffer==null||clear.length>buffer.length)throw new IOException("Output buffer too small");
                    System.arraycopy(clear,0,buffer,0,clear.length);
                    return clear.length;
                }finally{Arrays.fill(clear,(byte)0);}
            } catch (GeneralSecurityException e) { throw new IOException("RPC relay integrity check failed", e); }
            }
        }
        @Override public void close() {
            closed=true;try{socket.close();}catch(IOException ignored){}
            synchronized(sendLock){Arrays.fill(sendKey,(byte)0);}
            synchronized(recvLock){Arrays.fill(receiveKey,(byte)0);}
        }
        public void shutdownOutput() throws IOException { send(new byte[0], 0, 0); }
    }
}
