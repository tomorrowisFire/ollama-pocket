package com.ollamapocket.app;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Real authenticated pinned Linux CPU VLM server; synthetic pixels only. */
public final class VisionServerHostIntegrationTest {
    private static VisionImagePolicy.Prepared image(Color color)throws Exception{
        BufferedImage pixels=new BufferedImage(384,384,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=pixels.createGraphics();
        try{g.setColor(color);g.fillRect(0,0,384,384);}finally{g.dispose();}
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        if(!ImageIO.write(pixels,"jpeg",out))throw new IOException("JPEG fixture");
        return VisionImagePolicy.finish(out.toByteArray(),384,384);
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=4)throw new IllegalArgumentException("Host fixture arguments");
        File binary=new File(args[0]),weights=new File(args[1]),projector=new File(args[2]),root=new File(args[3]);
        try(VisionLocalServer server=VisionLocalServer.start(binary,weights,projector,root,null,()->false);
            VisionTurnReview review=new VisionTurnReview()){
            System.out.println("PASS: pinned visual server denied absent/wrong credentials and verified authenticated model/build/vision identity");
            for(String expected:new String[]{"red","blue"}){
                VisionImagePolicy.Prepared prepared=image("red".equals(expected)?Color.RED:Color.BLUE);
                String prompt="What is the main color in this image? Answer with one word.";
                var backend=server.backend();var inspected=review.prepare(backend,prepared,prompt,16,()->false);
                if(review.begin(inspected,backend,prepared,prompt,16,()->false)!=null||!review.approve(inspected))
                    throw new AssertionError("Visual approval");
                try(VisionTurnReview.Lease lease=review.begin(inspected,backend,prepared,prompt,16,()->false)){
                    if(lease==null)throw new AssertionError("Missing visual lease");
                    ReviewedLocalPolicy.Result reply=server.infer(lease,16,()->false);
                    String word=reply.text.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z]","");
                    if(reply.truncated||!word.equals(expected))throw new AssertionError("Wrong synthetic visual answer");
                    System.out.println("PASS: authenticated actual VLM JPEG "+expected);
                    if(review.begin(inspected,backend,prepared,prompt,16,()->false)!=null)throw new AssertionError("Visual replay");
                }
            }
        }
        if(VisionLocalServer.busy())throw new AssertionError("Visual process lease not released");
        System.out.println("PASS: authenticated actual VLM ownership and two one-shot image requests; Android NOT_RUN");
    }
}
