package com.ollamapocket.app;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.util.function.BooleanSupplier;

/** Independent VLM pair lifecycle, reusing the verified flat-file model store. No engine/UI activation. */
final class VisionModelStore {
    static final VoiceModelCatalog.Model PAIR=VoiceModelCatalog.fileSet(
            VisionModelProfile.ID,"SmolVLM 256M Q8_0",VisionModelProfile.SOURCE,
            VisionModelProfile.LICENSE,
            "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/blob/"+VisionModelProfile.REVISION+"/README.md",
            "vision",entry(VisionModelProfile.WEIGHTS),entry(VisionModelProfile.PROJECTOR));
    private final VoiceModelStore store;
    private static VoiceModelCatalog.Entry entry(VisionModelProfile.Asset a){
        return new VoiceModelCatalog.Entry(a.name,a.bytes,a.sha256);
    }

    /** Caller must supply its app-private no-backup directory, never a user-selected path. */
    VisionModelStore(File noBackupRoot)throws IOException{this(noBackupRoot,null);}
    VisionModelStore(File noBackupRoot,VoiceModelStore.Source testSource)throws IOException{
        if(noBackupRoot==null||!noBackupRoot.isDirectory()||
                !noBackupRoot.getAbsoluteFile().equals(noBackupRoot.getCanonicalFile()))
            throw new IOException("Invalid private vision root");
        File root=new File(noBackupRoot,"vision-models");
        if(Files.isSymbolicLink(root.toPath())||!root.equals(root.getCanonicalFile()))
            throw new IOException("Linked vision root");
        store=new VoiceModelStore(root,testSource);
    }
    long requiredSpace(){return PAIR.requiredSpace();}
    boolean present(){return store.present(PAIR);}
    /** Metadata only; do not advertise usable VLM inference until acquire and engine verification succeed. */
    boolean ready(){return store.ready(PAIR);}
    void install(boolean confirmed,BooleanSupplier cancelled,VoiceModelStore.Progress progress)throws IOException{
        if(!confirmed)throw new IllegalArgumentException("Explicit model download consent required");
        store.install(PAIR,cancelled,progress);
    }
    void cancelInstall(){store.cancelPendingInstall();}
    void remove()throws IOException{store.remove(PAIR);}
    Lease acquire(BooleanSupplier cancelled)throws IOException{
        VoiceModelStore.Lease backing=store.acquire(PAIR,cancelled);
        try{
            checkGguf(backing.file(VisionModelProfile.WEIGHTS.name));
            checkGguf(backing.file(VisionModelProfile.PROJECTOR.name));
            VisionImagePolicy.check(cancelled);
            return new Lease(backing);
        }catch(IOException|RuntimeException|Error e){backing.close();throw e;}
    }
    private static void checkGguf(File file)throws IOException{
        try(InputStream in=Files.newInputStream(file.toPath(),StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            if(in.read()!='G'||in.read()!='G'||in.read()!='U'||in.read()!='F')
                throw new IOException("Invalid vision GGUF header");
        }
    }
    static final class Lease implements AutoCloseable {
        private final VoiceModelStore.Lease backing;
        private Lease(VoiceModelStore.Lease backing){this.backing=backing;}
        File weights()throws IOException{return backing.file(VisionModelProfile.WEIGHTS.name);}
        File projector()throws IOException{return backing.file(VisionModelProfile.PROJECTOR.name);}
        @Override public void close(){backing.close();}
    }
}
