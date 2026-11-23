import com.ollamapocket.app.SecureRpcTransport;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public class SecureRpcTransportTest {
    public static void main(String[] args) throws Exception {
        byte[] key=SecureRpcTransport.secret();
        try(ServerSocket server=new ServerSocket(0,4,InetAddress.getLoopbackAddress())){
            AtomicReference<Throwable> failure=new AtomicReference<>();
            Thread guest=new Thread(()->{
                try(Socket socket=server.accept(); SecureRpcTransport.Channel channel=SecureRpcTransport.accept(socket,key)){
                    byte[] data=new byte[32768];int n=channel.receive(data);
                    if(!"test request".equals(new String(data,0,n,StandardCharsets.UTF_8)))
                        throw new AssertionError("Bad input");
                    byte[] reply="test response".getBytes(StandardCharsets.UTF_8);
                    channel.send(reply,0,reply.length);channel.shutdownOutput();
                }catch(Throwable e){failure.set(e);}
            });
            guest.start();
            try(Socket socket=new Socket(InetAddress.getLoopbackAddress(),server.getLocalPort());
                SecureRpcTransport.Channel channel=SecureRpcTransport.connect(socket,key)){
                byte[] data="test request".getBytes(StandardCharsets.UTF_8);
                channel.send(data,0,data.length);
                byte[] response=new byte[32768];int n=channel.receive(response);
                if(!"test response".equals(new String(response,0,n,StandardCharsets.UTF_8)))
                    throw new AssertionError("Bad encrypted response");
                if(channel.receive(response)!=-1)throw new AssertionError("Missing encrypted EOF");
            }
            guest.join(5000);
            if(guest.isAlive()||failure.get()!=null)throw new AssertionError("Guest failed",failure.get());
        }
        // Wrong key must be rejected by the handshake before the RPC socket is opened.
        try(ServerSocket server=new ServerSocket(0,2,InetAddress.getLoopbackAddress())){
            byte[] other=SecureRpcTransport.secret();
            Thread t=new Thread(()->{
                try(Socket socket=server.accept()){SecureRpcTransport.accept(socket,key);}
                catch(Exception expected){return;}
                throw new AssertionError("Authentication should fail");
            });t.start();
            boolean rejected=false;
            try(Socket socket=new Socket(InetAddress.getLoopbackAddress(),server.getLocalPort());
                SecureRpcTransport.Channel ch=SecureRpcTransport.connect(socket,other)){}
            catch(IOException expected){rejected=true;}
            if(!rejected)throw new AssertionError("Wrong secret accepted");
            t.join(5000);
        }
        System.out.println("SecureRpcTransport: encrypted duplex/EOF and wrong-key rejection passed");
    }
}
