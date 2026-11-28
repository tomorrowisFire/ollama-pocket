package com.ollamapocket.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** Cheap screening of an Ollama blob, not a full GGUF integrity or architecture check. */
final class NativeWeightPreflight {
    private static final long MIN_BYTES=1048576L;
    private NativeWeightPreflight(){}

    /** Cheap header and byte-count screening for a private copied GGUF, not cryptographic verification. */
    static boolean validGguf(File file,long declaredSize){
        if(file==null)return false;
        try{
            File absolute=file.getAbsoluteFile(),parent=absolute.getParentFile();
            if(parent==null)return false;
            File candidate=new File(parent.getCanonicalFile(),absolute.getName());
            if(!candidate.equals(absolute.getCanonicalFile())||!candidate.isFile())return false;
            long length=candidate.length();
            if(length<=MIN_BYTES||(declaredSize>0&&declaredSize!=length))return false;
            try(FileInputStream in=new FileInputStream(candidate)){
                if(in.read()!='G'||in.read()!='G'||in.read()!='U'||in.read()!='F')return false;
            }
            return candidate.length()==length&&candidate.equals(absolute.getCanonicalFile())&&candidate.isFile();
        }catch(IOException|SecurityException e){return false;}
    }

    static File resolve(File modelRoot,String digest,long declaredSize){
        if(modelRoot==null||digest==null||!digest.matches("sha256:[0-9a-fA-F]{64}"))return null;
        try{
            File root=modelRoot.getCanonicalFile();
            File blobs=new File(root,"blobs");
            if(!blobs.equals(blobs.getCanonicalFile())||!blobs.isDirectory())return null;
            File file=new File(blobs,"sha256-"+digest.substring(7));
            if(!file.equals(file.getCanonicalFile())||!file.isFile())return null;
            return validGguf(file,declaredSize)?file:null;
        }catch(IOException|SecurityException e){return null;}
    }
}
