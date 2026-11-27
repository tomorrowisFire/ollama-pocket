package com.ollamapocket.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** Cheap screening of an Ollama blob, not a full GGUF integrity or architecture check. */
final class NativeWeightPreflight {
    private static final long MIN_BYTES=1048576L;
    private NativeWeightPreflight(){}

    static File resolve(File modelRoot,String digest,long declaredSize){
        if(modelRoot==null||digest==null||!digest.matches("sha256:[0-9a-fA-F]{64}"))return null;
        try{
            File root=modelRoot.getCanonicalFile();
            File blobs=new File(root,"blobs");
            if(!blobs.equals(blobs.getCanonicalFile())||!blobs.isDirectory())return null;
            File file=new File(blobs,"sha256-"+digest.substring(7));
            if(!file.equals(file.getCanonicalFile())||!file.isFile())return null;
            long length=file.length();
            if(length<=MIN_BYTES||(declaredSize>0&&declaredSize!=length))return null;
            try(FileInputStream in=new FileInputStream(file)){
                if(in.read()!='G'||in.read()!='G'||in.read()!='U'||in.read()!='F')return null;
            }
            if(file.length()!=length||!file.equals(file.getCanonicalFile())||!file.isFile())return null;
            return file;
        }catch(IOException|SecurityException e){return null;}
    }
}
