package com.ollamapocket.app;

import android.content.Context;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;

/** Fixed private no-backup file; separate from all existing app stores. */
final class ProjectStore {
    private static final Object LOCK=new Object();
    private static final String NAME="onhand_projects_v1.json";
    private final ProjectTransactions<ProjectLibrary.State> transactions;
    ProjectStore(Context context){
        File base=new File(context.getApplicationContext().getNoBackupFilesDir(),NAME);
        transactions=new ProjectTransactions<>(new AtomicBackend(base),ProjectLibrary.CODEC,LOCK,
            ProjectLibrary.empty(),ProjectLibrary.MAX_BYTES);
    }
    ProjectTransactions.Snapshot<ProjectLibrary.State> read()throws Exception{return transactions.read();}
    ProjectTransactions.Snapshot<ProjectLibrary.State> commit(ProjectTransactions.Snapshot<ProjectLibrary.State> reviewed,
            ProjectTransactions.Mutation<ProjectLibrary.State> mutation)throws Exception{
        return transactions.commit(reviewed,mutation);
    }
    private static final class AtomicBackend implements ProjectTransactions.Backend {
        final File base;final AtomicFile atomic;
        AtomicBackend(File base){this.base=base;this.atomic=new AtomicFile(base);}
        private boolean absent(File file){
            // notExists is false when existence cannot be determined, unlike !exists.
            return Files.notExists(file.toPath(),LinkOption.NOFOLLOW_LINKS);
        }
        public byte[] read()throws IOException{
            try(FileInputStream input=atomic.openRead()){
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                byte[] buffer=new byte[8192];int count;
                while((count=input.read(buffer))!=-1){
                    if((long)out.size()+count>ProjectLibrary.MAX_BYTES)throw new IOException("Project store size");
                    out.write(buffer,0,count);
                }
                return out.toByteArray();
            }catch(FileNotFoundException e){
                if(absent(base)&&absent(new File(base.getPath()+".bak"))&&absent(new File(base.getPath()+".new")))return null;
                throw e;
            }
        }
        public void write(byte[] bytes)throws IOException{
            if(bytes==null||bytes.length==0||bytes.length>ProjectLibrary.MAX_BYTES)throw new IOException("Project store size");
            FileOutputStream output=null;
            try{
                output=atomic.startWrite();output.write(bytes);atomic.finishWrite(output);output=null;
            }catch(IOException|RuntimeException e){
                if(output!=null)atomic.failWrite(output);
                throw new IOException("Project write uncertain",e);
            }
            // AtomicFile may log a failure rather than throw; transaction verifies exact readback.
        }
    }
}
