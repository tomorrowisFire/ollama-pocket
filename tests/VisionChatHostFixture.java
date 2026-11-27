package com.ollamapocket.app;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Host-only, synthetic pixels. Uses exactly the app's bounded JPEG request builder. */
public final class VisionChatHostFixture {
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("fixture directory");
        Path directory=Path.of(args[0]);
        for(String color:new String[]{"red","blue"}){
            BufferedImage image=new BufferedImage(384,384,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=image.createGraphics();
            try{g.setColor("red".equals(color)?Color.RED:Color.BLUE);g.fillRect(0,0,384,384);}
            finally{g.dispose();}
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            if(!ImageIO.write(image,"jpeg",out))throw new IllegalStateException("JPEG encoder unavailable");
            VisionImagePolicy.Prepared prepared=VisionImagePolicy.finish(out.toByteArray(),384,384);
            byte[] request=VisionChatRequest.build(prepared,"What is the main color in this image? Answer with one word.",16,()->false);
            Files.write(directory.resolve(color+".json"),request);
        }
    }
}
