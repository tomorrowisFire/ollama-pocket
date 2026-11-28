package com.ollamapocket.app;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** A bounded local inventory. A transfer terminal signal is not proof of registration. */
final class InstalledModelInventory {
    private InstalledModelInventory(){}
    static List<String> names(JSONObject response)throws IOException{
        JSONArray models=response==null?null:response.optJSONArray("models");
        if(models==null||!ModelTileCatalog.validSnapshotCount(models.length()))throw new IOException("Invalid model snapshot");
        List<String> result=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        for(int i=0;i<models.length();i++){
            JSONObject row=models.optJSONObject(i);
            Object raw=row==null?null:row.opt("name");
            if(!(raw instanceof String)||!ModelTileCatalog.validInstalledTag((String)raw)||!seen.add((String)raw))
                throw new IOException("Invalid model identity");
            result.add((String)raw);
        }
        return Collections.unmodifiableList(result);
    }
    /** New installs need the exact tag, positive reported size and valid manifest digest.
     * The digest is reported by the daemon; this is not a hash of all downloaded weights. */
    static String confirmedTag(JSONObject response,String requested)throws IOException{
        List<String> names=names(response);
        String selected=ModelTileCatalog.resolveInstalledTag(requested,names);
        if(selected==null)return null;
        JSONObject row=response.optJSONArray("models").optJSONObject(names.indexOf(selected));
        Object size=row.opt("size"),digest=row.opt("digest");
        if(!(size instanceof Number)||!(digest instanceof String)||!((String)digest).matches("(?i)(?:sha256:)?[0-9a-f]{64}"))return null;
        try{return new java.math.BigDecimal(size.toString()).longValueExact()>0?selected:null;}
        catch(ArithmeticException|NumberFormatException e){return null;}
    }
}
