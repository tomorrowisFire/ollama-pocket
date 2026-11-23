package com.ollamapocket.app;

import android.app.ActivityManager;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Temporary same-Wi-Fi session coordinator. RPC always runs on loopback and is
 * accessible to approved peers only through the encrypted SecureRpcTransport.
 * This is an experimental LAN-only feature, not an internet-facing service.
 */
public final class CoopSession {
    public static final int CONTROL_PORT = 47620;
    private static final int MAX_NODES = 3;
    private static final long SESSION_MS = 45L * 60 * 1000;
    private static final long NODE_TIMEOUT_MS = 30_000;
    private static final String NSD_TYPE = "_aipocket-coop._tcp.";
    private static final String TAG = "AiPocketCoop";
    private static final String PROTOCOL = "coop-1";
    private static volatile CoopSession INSTANCE;

    public static CoopSession get(Context context) {
        if (INSTANCE == null) synchronized (CoopSession.class) {
            if (INSTANCE == null) INSTANCE = new CoopSession(context.getApplicationContext());
        }
        return INSTANCE;
    }

    private final Context app;
    private final ExecutorService threads = Executors.newCachedThreadPool();
    private final NsdManager nsd;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Node> nodes = new ConcurrentHashMap<>();
    private final Map<String, String> discovered = new ConcurrentHashMap<>();
    private final Map<String, Long> seenNonces = new ConcurrentHashMap<>();
    private final AtomicBoolean resolving = new AtomicBoolean();
    private volatile String role = "off";
    private volatile String sessionId = "";
    private volatile String hostIp = "";
    private volatile String guestName = "";
    private volatile String guestId = "";
    private volatile byte[] secret;
    private volatile long startedAt;
    private volatile String lastError = "";
    private volatile String workerDigest = "";
    private volatile ServerSocket controlListener, guestListener;
    private volatile Process worker;
    private volatile NsdManager.RegistrationListener registration;
    private volatile NsdManager.DiscoveryListener discovery;
    private volatile PowerManager.WakeLock wakeLock;
    private volatile WifiManager.WifiLock wifiLock;
    private volatile WifiManager.MulticastLock multicastLock;
    private final Semaphore gatewayCapacity = new Semaphore(3);

    public static final class Node {
        public final String id, name, ip;
        public final int port;
        public final long safeRamMiB;
        public volatile long lastSeen;
        public volatile String state = "connected";
        volatile RpcRelay relay;
        Node(String id, String name, String ip, int port, long ramMiB) {
            this.id=id; this.name=name; this.ip=ip; this.port=port;
            this.safeRamMiB=ramMiB; this.lastSeen=SystemClock.elapsedRealtime();
        }
    }

    private CoopSession(Context context) {
        app=context;
        nsd=(NsdManager)app.getSystemService(Context.NSD_SERVICE);
    }

    public static boolean packaged(Context app) {
        String dir=app.getApplicationInfo().nativeLibraryDir;
        return new File(dir,"libggml-rpc-worker.so").isFile()
                && new File(dir,"libllama-distributed-server.so").isFile();
    }
    public String role() { return role; }
    public String error() { return lastError; }
    public void setError(String value) { lastError=value==null?"unknown":value; }
    public String hostIp() { return hostIp; }
    public String sessionId() { return sessionId; }
    public String joinCode() {
        return secret == null ? "" : Base64.encodeToString(secret, Base64.URL_SAFE|Base64.NO_PADDING|Base64.NO_WRAP);
    }
    public List<Node> nodes() { return new ArrayList<>(nodes.values()); }
    public Map<String,String> discovered() { return new LinkedHashMap<>(discovered); }
    public long secondsRemaining() {
        return "off".equals(role)?0:Math.max(0,(SESSION_MS-(SystemClock.elapsedRealtime()-startedAt))/1000);
    }
    public String status() {
        String v = "호스트".equals(role) ? "host" : role;
        StringBuilder s=new StringBuilder("협력 모드: "+v+" · "+secondsRemaining()/60+"분 남음");
        if ("host".equals(role)) s.append("\n연결 노드: ").append(readyNodes().size()).append(" / ").append(MAX_NODES);
        if ("guest".equals(role)) s.append("\n연결 호스트: ").append(hostIp)
               .append("\nRPC worker: ").append(worker!=null && worker.isAlive()?"running":"stopped");
        if (!lastError.isEmpty()) s.append("\n상태: ").append(lastError);
        return s.toString();
    }
    public List<Node> readyNodes() {
        long now=SystemClock.elapsedRealtime();
        List<Node> out=new ArrayList<>();
        for (Node n:nodes.values()) if(now-n.lastSeen<NODE_TIMEOUT_MS && n.relay!=null) out.add(n);
        return out;
    }
    public String rpcEndpoints() {
        List<String> ports=new ArrayList<>();
        for(Node n:readyNodes()) ports.add("127.0.0.1:"+n.relay.localPort());
        return String.join(",",ports);
    }
    public long aggregatedSafeMiB() {
        long local=safeRamMiB();
        for(Node n:readyNodes()) local+=n.safeRamMiB;
        return local;
    }
    private long safeRamMiB() {
        ActivityManager mgr=(ActivityManager)app.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();
        if(mgr!=null) mgr.getMemoryInfo(m);
        return Math.max(0,((m.availMem>>20)-1024)*65/100);
    }
    private String digest() throws Exception {
        if(!workerDigest.isEmpty())return workerDigest;
        File file=new File(app.getApplicationInfo().nativeLibraryDir,"libggml-rpc-worker.so");
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1)md.update(buffer,0,n);
        }
        byte[] d=md.digest(); StringBuilder b=new StringBuilder();
        for(byte v:d)b.append(String.format(Locale.US,"%02x",v&255));
        workerDigest=b.toString();return workerDigest;
    }
    public static String localIPv4() {
        try {
            Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
            List<String> found=new ArrayList<>();
            while(interfaces.hasMoreElements()) {
                NetworkInterface ni=interfaces.nextElement();
                if(!ni.isUp() || ni.isLoopback() || ni.isVirtual())continue;
                Enumeration<InetAddress> addresses=ni.getInetAddresses();
                while(addresses.hasMoreElements()) {
                    InetAddress a=addresses.nextElement();
                    if(a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        if(ni.getName().startsWith("wlan"))return a.getHostAddress();
                        found.add(a.getHostAddress());
                    }
                }
            }
            if(!found.isEmpty())return found.get(0);
        } catch(Exception ignored) { }
        return "";
    }
    private void holdResources() {
        try {
            PowerManager pm=(PowerManager)app.getSystemService(Context.POWER_SERVICE);
            wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"AiPocket:CoopCompute");
            wakeLock.setReferenceCounted(false);wakeLock.acquire(SESSION_MS);
            WifiManager wm=(WifiManager)app.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifiLock=wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"AiPocket:CoopWifi");
            wifiLock.setReferenceCounted(false);wifiLock.acquire();
        }catch(Exception e){ Log.w(TAG,"Unable to acquire runtime lock",e); }
    }
    private void releaseResources() {
        try {if(wakeLock!=null && wakeLock.isHeld())wakeLock.release();}catch(Exception ignored){}
        try {if(wifiLock!=null && wifiLock.isHeld())wifiLock.release();}catch(Exception ignored){}
    }

    public synchronized void startHost() throws Exception {
        stop();
        if(!packaged(app))throw new IOException("분산 RPC 엔진이 없는 APK입니다. 협력용 APK를 설치하세요.");
        hostIp=localIPv4();
        if(hostIp.isEmpty())throw new IOException("Wi-Fi의 사설 IPv4 주소를 확인할 수 없습니다.");
        workerDigest=digest();
        secret=SecureRpcTransport.secret();sessionId=randomId(10);
        startedAt=SystemClock.elapsedRealtime();role="host";lastError="";
        holdResources();
        controlListener=new ServerSocket(CONTROL_PORT,8,InetAddress.getByName(hostIp));
        final ServerSocket s=controlListener;
        threads.execute(()-> {
            while(!s.isClosed() && "host".equals(role)) {
                try { Socket peer=s.accept();threads.execute(()->handleControl(peer)); }
                catch(IOException e){if(!s.isClosed())lastError=e.getMessage();}
            }
        });
        registerHost();
        threads.execute(()->{
            while("host".equals(role)){
                try{Thread.sleep(5000);}catch(InterruptedException e){return;}
                if(SystemClock.elapsedRealtime()-startedAt>SESSION_MS){stop();return;}
                long now=SystemClock.elapsedRealtime();
                for(Node n:nodes.values())if(now-n.lastSeen>NODE_TIMEOUT_MS){
                    nodes.remove(n.id);
                    if(n.relay!=null)n.relay.close();
                }
            }
        });
    }
    private String randomId(int bytes) {
        byte[] b=new byte[bytes];random.nextBytes(b);
        return Base64.encodeToString(b,Base64.URL_SAFE|Base64.NO_PADDING|Base64.NO_WRAP);
    }
    private void registerHost(){
        if(nsd==null)return;
        try{
            NsdServiceInfo info=new NsdServiceInfo();info.setServiceName("AI-in-Pocket-"+sessionId);
            info.setServiceType(NSD_TYPE);info.setPort(CONTROL_PORT);info.setAttribute("version",PROTOCOL);
            NsdManager.RegistrationListener l=new NsdManager.RegistrationListener(){
                @Override public void onServiceRegistered(NsdServiceInfo x){}
                @Override public void onRegistrationFailed(NsdServiceInfo x,int code){lastError="NSD 등록 실패 "+code;}
                @Override public void onServiceUnregistered(NsdServiceInfo x){}
                @Override public void onUnregistrationFailed(NsdServiceInfo x,int code){}
            };
            registration=l;nsd.registerService(info,NsdManager.PROTOCOL_DNS_SD,l);
        }catch(Exception e){lastError="자동 검색 등록 실패 · IP 수동 입력 가능";}
    }
    public synchronized void startDiscovery() {
        if(nsd==null || discovery!=null)return;
        discovered.clear();
        try{
            WifiManager wm=(WifiManager)app.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            multicastLock=wm.createMulticastLock("AiPocket:CoopNSD");
            multicastLock.setReferenceCounted(false);multicastLock.acquire();
            NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener(){
                @Override public void onDiscoveryStarted(String type){}
                @Override public void onStartDiscoveryFailed(String type,int code){lastError="검색 시작 실패 "+code;stopDiscovery();}
                @Override public void onStopDiscoveryFailed(String type,int code){}
                @Override public void onDiscoveryStopped(String type){}
                @Override public void onServiceFound(NsdServiceInfo info){
                    if(!info.getServiceName().startsWith("AI-in-Pocket-"))return;
                    if(!resolving.compareAndSet(false,true))return;
                    nsd.resolveService(info,new NsdManager.ResolveListener(){
                        @Override public void onResolveFailed(NsdServiceInfo service,int code){resolving.set(false);}
                        @Override public void onServiceResolved(NsdServiceInfo service){
                            resolving.set(false);
                            if(service.getHost()!=null && service.getPort()==CONTROL_PORT)
                                discovered.put(service.getServiceName(),service.getHost().getHostAddress());
                        }
                    });
                }
                @Override public void onServiceLost(NsdServiceInfo info){discovered.remove(info.getServiceName());}
            };
            discovery=listener;nsd.discoverServices(NSD_TYPE,NsdManager.PROTOCOL_DNS_SD,listener);
        }catch(Exception e){lastError="NSD 검색 불가 · IP 직접 입력 가능";stopDiscovery();}
    }
    public synchronized void stopDiscovery() {
        if(discovery!=null){try{nsd.stopServiceDiscovery(discovery);}catch(Exception ignored){}discovery=null;}
        if(multicastLock!=null){try{if(multicastLock.isHeld())multicastLock.release();}catch(Exception ignored){}multicastLock=null;}
    }

    private static String boundedLine(BufferedReader reader) throws IOException {
        StringBuilder b=new StringBuilder();int c;
        while((c=reader.read())!=-1 && c!='\n'){
            if(b.length()>2048)throw new IOException("Control packet too large");
            b.append((char)c);
        }
        if(c==-1 && b.length()==0)throw new IOException("Connection closed");
        return b.toString();
    }
    private void handleControl(Socket socket) {
        try(Socket s=socket){
            s.setSoTimeout(5000);
            BufferedReader reader=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));
            OutputStreamWriter writer=new OutputStreamWriter(s.getOutputStream(),StandardCharsets.UTF_8);
            JSONObject req=new JSONObject(boundedLine(reader));
            String name=req.optString("name","");
            String id=req.optString("id","");
            String nonce=req.optString("nonce","");
            String auth=req.optString("mac","");
            String type=req.optString("type","");
            String build=req.optString("build","");
            int port=req.optInt("port",0);
            String command=type+"|"+sessionId+"|"+nonce+"|"+id+"|"+port+"|"+build;
            JSONObject response=new JSONObject();
            if(!"host".equals(role) || !id.matches("[A-Za-z0-9_-]{8,40}") ||
                !nonce.matches("[A-Za-z0-9_-]{16,32}") || !name.matches("[\\p{L}\\p{N} _.-]{1,45}") ||
                !("join".equals(type)||"ping".equals(type)) || port<1024 || port>65535 ||
                !workerDigest.equals(build) || secret==null ||
                !SecureRpcTransport.constantEquals(auth,SecureRpcTransport.hmacHex(secret,command))) {
                response.put("ok",false).put("error","연결 요청 거부: 코드 또는 RPC 엔진 버전 불일치");
            } else if(seenNonces.putIfAbsent(nonce,SystemClock.elapsedRealtime())!=null){
                response.put("ok",false).put("error","반복 요청 거부");
            } else if(!"ping".equals(type) && nodes.size()>=MAX_NODES && !nodes.containsKey(id)) {
                response.put("ok",false).put("error","호스트 노드 연결 한도 초과");
            } else {
                if("join".equals(type)) {
                    String peerIp=s.getInetAddress().getHostAddress();
                    Node old=nodes.get(id);if(old!=null && old.relay!=null)old.relay.close();
                    Node node=new Node(id,name,peerIp,port,Math.max(0,Math.min(16384,req.optLong("safe_ram_mib",0))));
                    RpcRelay relay=new RpcRelay(peerIp,port,secret);
                    relay.start();node.relay=relay;nodes.put(id,node);
                }else{
                    Node n=nodes.get(id);
                    if(n!=null && n.ip.equals(s.getInetAddress().getHostAddress()) && n.port==port)
                        n.lastSeen=SystemClock.elapsedRealtime();
                    else throw new IOException("Unregistered guest heartbeat");
                }
                response.put("ok",true).put("session",sessionId)
                    .put("auth",SecureRpcTransport.hmacHex(secret,"ACK|"+nonce+"|"+sessionId));
            }
            writer.write(response.toString()+"\n");writer.flush();
        }catch(Exception e){ Log.w(TAG,"Control channel rejected",e); }
        if(seenNonces.size()>300) {
            long now=SystemClock.elapsedRealtime();
            for(Map.Entry<String,Long> x:seenNonces.entrySet())
                if(now-x.getValue()>60_000)seenNonces.remove(x.getKey());
        }
    }

    public synchronized void startGuest(String host, String id, String code, String deviceName) throws Exception {
        stop();
        if(!packaged(app))throw new IOException("RPC 바이너리가 APK에 없습니다");
        if(!host.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"))throw new IOException("호스트 IPv4 주소를 확인하세요");
        InetAddress hostAddress=InetAddress.getByName(host);
        if(!(hostAddress instanceof Inet4Address) || !hostAddress.isSiteLocalAddress())
            throw new IOException("사설 Wi-Fi 네트워크의 호스트만 연결할 수 있습니다");
        byte[] decoded;
        try { decoded=Base64.decode(code.replace(" ",""),Base64.URL_SAFE|Base64.NO_WRAP); }
        catch(Exception e){throw new IOException("호스트의 연결 코드를 확인하세요");}
        if(decoded.length!=32)throw new IOException("32바이트 세션 키가 필요합니다. 호스트 코드를 그대로 붙여넣으세요");
        this.secret=decoded;this.hostIp=host;this.sessionId=(id==null ? "" : id.trim());
        if(this.sessionId.isEmpty())this.sessionId=resolveSessionId(host);
        if(!this.sessionId.matches("[A-Za-z0-9_-]{10,32}"))throw new IOException("호스트 세션 ID를 확인하세요");
        this.guestName=deviceName.replaceAll("[^\\p{L}\\p{N} _.-]","").trim();
        if(this.guestName.isEmpty())this.guestName="Android Node";
        this.guestId=randomId(14);this.startedAt=SystemClock.elapsedRealtime();this.workerDigest=digest();
        this.lastError="";this.role="guest";
        try {
            startWorker();
            String localAddress=localIPv4();
            if(localAddress.isEmpty())throw new IOException("동일 Wi-Fi의 기기 IPv4 주소가 필요합니다");
            guestListener=new ServerSocket(0,4,InetAddress.getByName(localAddress));
            int guestPort=guestListener.getLocalPort();
            final ServerSocket l=guestListener;
            threads.execute(()->runGateway(l,hostAddress));
            JSONObject response=exchange("join",guestPort);
            sessionId=response.getString("session");
            holdResources();
            threads.execute(()->{
                while("guest".equals(role)) {
                    try {Thread.sleep(10000);}catch(InterruptedException e){return;}
                    if(SystemClock.elapsedRealtime()-startedAt>SESSION_MS){stop();return;}
                    if(Build.VERSION.SDK_INT>=29){
                        PowerManager pm=(PowerManager)app.getSystemService(Context.POWER_SERVICE);
                        if(pm!=null && pm.getCurrentThermalStatus()>=PowerManager.THERMAL_STATUS_SEVERE){
                            setError("기기 과열로 협력 노드를 종료합니다");stop();return;
                        }
                    }
                    try{exchange("ping",guestPort);}catch(Exception e){lastError="호스트 연결 확인 실패: "+e.getMessage();}
                }
            });
        }catch(Exception e){stop();throw e;}
    }
    private JSONObject exchange(String type,int port) throws Exception {
        String nonce=randomId(16);
        // The host's public session identifier is advertised via mDNS service name.
        // For initial join it is sent as a non-secret explicit field.
        String id=sessionId;
        if(id.isEmpty() && "join".equals(type)) id=resolveSessionId(hostIp);
        if(id.isEmpty())throw new IOException("호스트 세션을 발견하지 못했습니다. 검색 후 선택하세요");
        JSONObject request=new JSONObject();
        request.put("type",type).put("id",guestId).put("name",guestName)
            .put("nonce",nonce).put("port",port).put("build",workerDigest)
            .put("safe_ram_mib",safeRamMiB())
            .put("mac",SecureRpcTransport.hmacHex(secret,type+"|"+id+"|"+nonce+"|"+guestId+"|"+port+"|"+workerDigest));
        try(Socket s=new Socket()){
            s.connect(new java.net.InetSocketAddress(hostIp,CONTROL_PORT),4000);
            s.setSoTimeout(4000);
            OutputStreamWriter writer=new OutputStreamWriter(s.getOutputStream(),StandardCharsets.UTF_8);
            writer.write(request.toString()+"\n");writer.flush();
            JSONObject result=new JSONObject(boundedLine(new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8))));
            if(!result.optBoolean("ok"))throw new IOException(result.optString("error","호스트가 연결을 거부함"));
            if(!id.equals(result.optString("session")) || !SecureRpcTransport.constantEquals(
                result.optString("auth"),SecureRpcTransport.hmacHex(secret,"ACK|"+nonce+"|"+id)))
                throw new IOException("호스트 인증에 실패했습니다");
            return result;
        }
    }
    private String resolveSessionId(String host){
        for(Map.Entry<String,String> entry:discovered.entrySet())if(host.equals(entry.getValue())){
            String name=entry.getKey();return name.substring("AI-in-Pocket-".length());
        }
        return "";
    }
    private void startWorker() throws Exception {
        File binary=new File(app.getApplicationInfo().nativeLibraryDir,"libggml-rpc-worker.so");
        if(!binary.isFile())throw new IOException("보조 RPC 엔진 없음");
        File cache=new File(app.getCacheDir(),"rpc-cache");if(!cache.isDirectory()&&!cache.mkdirs())throw new IOException("캐시 폴더 생성 실패");
        ProcessBuilder pb=new ProcessBuilder(binary.getAbsolutePath(),"-H","127.0.0.1","-p","50052","-c",
            "-t",String.valueOf(Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors()/2))));
        pb.directory(app.getFilesDir());pb.redirectErrorStream(true);
        pb.environment().put("LD_LIBRARY_PATH",app.getApplicationInfo().nativeLibraryDir);
        pb.environment().put("LLAMA_CACHE",cache.getAbsolutePath());
        pb.environment().put("HOME",app.getFilesDir().getAbsolutePath());
        worker=pb.start();
        Process current=worker;
        threads.execute(()->{
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(current.getInputStream(),StandardCharsets.UTF_8))){
                String line;while((line=reader.readLine())!=null)Log.i(TAG,"RPC: "+line);
            }catch(Exception e){Log.w(TAG,"Worker log stopped",e);}
        });
        long deadline=SystemClock.elapsedRealtime()+15000;
        while(SystemClock.elapsedRealtime()<deadline && current.isAlive()){
            try(Socket check=new Socket()){
                check.connect(new java.net.InetSocketAddress("127.0.0.1",50052),400);
                return;
            }catch(Exception e){Thread.sleep(300);}
        }
        throw new IOException("RPC worker가 시작되지 않았습니다. 기기 호환성 또는 로그 확인 필요");
    }
    private void runGateway(ServerSocket listener,InetAddress approvedHost){
        while(!listener.isClosed() && "guest".equals(role)){
            try{
                Socket socket=listener.accept();
                if(!socket.getInetAddress().equals(approvedHost) || !gatewayCapacity.tryAcquire()){
                    socket.close();continue;
                }
                threads.execute(()->{
                    try(Socket remote=socket;
                        SecureRpcTransport.Channel channel=SecureRpcTransport.accept(remote,secret);
                        Socket local=new Socket("127.0.0.1",50052)){
                        bridge(local,channel);
                    }catch(Exception e){Log.w(TAG,"Secure RPC channel closed",e);}
                    finally{gatewayCapacity.release();}
                });
            }catch(IOException e){if(!listener.isClosed())lastError=e.getMessage();}
        }
    }
    /** Single-use bidirectional encrypted tunnel; raw TCP never leaves loopback. */
    static void bridge(Socket local,SecureRpcTransport.Channel channel) throws Exception{
        Thread upstream=new Thread(()->{
            byte[] buffer=new byte[32768];
            try {InputStream in=local.getInputStream();int n;
                while((n=in.read(buffer))!=-1)channel.send(buffer,0,n);
                channel.shutdownOutput();
            }catch(Exception ignored){}},"rpc-encrypted-up");
        upstream.setDaemon(true);upstream.start();
        try {
            byte[] buffer=new byte[32768];OutputStream out=local.getOutputStream();int n;
            while((n=channel.receive(buffer))!=-1){out.write(buffer,0,n);out.flush();}
            local.shutdownOutput();
        }finally{try{local.close();}catch(Exception ignored){}channel.close();}
    }

    /** LAN connector created only for an authenticated/active remote worker. */
    private final class RpcRelay implements AutoCloseable {
        private final String remoteIp;
        private final int remotePort;
        private final byte[] key;
        private ServerSocket localListener;
        RpcRelay(String ip,int port,byte[] secret){remoteIp=ip;remotePort=port;key=secret.clone();}
        void start() throws Exception {
            localListener=new ServerSocket(0,4,InetAddress.getByName("127.0.0.1"));
            final ServerSocket l=localListener;
            threads.execute(()->{
                while(!l.isClosed() && "host".equals(role)){
                    try{
                        Socket local=l.accept();
                        threads.execute(()->{
                            try(Socket origin=local;Socket remote=new Socket()){
                                remote.connect(new java.net.InetSocketAddress(remoteIp,remotePort),4000);
                                SecureRpcTransport.Channel c=SecureRpcTransport.connect(remote,key);
                                try(c){bridge(origin,c);}
                            }catch(Exception e){Log.w(TAG,"Node RPC relay failed",e);}
                        });
                    }catch(IOException e){if(!l.isClosed())Log.w(TAG,"Local relay failed",e);}
                }
            });
        }
        int localPort(){return localListener==null?0:localListener.getLocalPort();}
        @Override public void close(){try{if(localListener!=null)localListener.close();}catch(Exception ignored){}}
    }

    public synchronized void stop(){
        role="off";sessionId="";lastError="";secret=null;
        if(controlListener!=null){try{controlListener.close();}catch(Exception ignored){}controlListener=null;}
        if(guestListener!=null){try{guestListener.close();}catch(Exception ignored){}guestListener=null;}
        if(registration!=null){try{nsd.unregisterService(registration);}catch(Exception ignored){}registration=null;}
        stopDiscovery();
        if(worker!=null){worker.destroy();worker=null;}
        for(Node n:nodes.values())if(n.relay!=null)n.relay.close();
        nodes.clear();seenNonces.clear();hostIp="";
        releaseResources();
    }
}
