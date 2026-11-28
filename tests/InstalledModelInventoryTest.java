package com.ollamapocket.app;

import java.io.IOException;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class InstalledModelInventoryTest {
    private static int n;
    private static void ok(boolean pass){n++;if(!pass)throw new AssertionError("inventory "+n);}
    private static JSONObject row(String name,Object size,Object digest){
        return new JSONObject().put("name",name).put("size",size).put("digest",digest);
    }
    private static JSONObject list(Object... rows){
        JSONArray array=new JSONArray();for(Object row:rows)array.put(row);
        return new JSONObject().put("models",array);
    }
    private static void rejects(JSONObject raw)throws Exception{
        boolean rejected=false;try{InstalledModelInventory.names(raw);}catch(IOException expected){rejected=true;}ok(rejected);
    }
    public static void main(String[] args)throws Exception{
        String digest="a".repeat(64);
        JSONObject good=list(row("model:latest",123,digest),row("model:4b",456,"sha256:"+digest));
        ok(InstalledModelInventory.names(good).equals(List.of("model:latest","model:4b")));
        ok("model:latest".equals(InstalledModelInventory.confirmedTag(good,"model")));
        ok("model:4b".equals(InstalledModelInventory.confirmedTag(good,"model:4b")));
        ok(InstalledModelInventory.confirmedTag(good,"model:8b")==null);
        ok(InstalledModelInventory.names(list()).isEmpty());
        ok(InstalledModelInventory.confirmedTag(list(),"model")==null);
        rejects(null);rejects(new JSONObject());rejects(new JSONObject().put("models",1));
        rejects(list(row("model",1,digest),row("model",2,digest)));
        rejects(list(new JSONObject().put("name",true)));
        rejects(list(row("bad name",1,digest)));rejects(list("not a row"));
        for(Object invalid:new Object[]{0,-1,1.5,"1",JSONObject.NULL,new java.math.BigDecimal("9223372036854775808")})
            ok(InstalledModelInventory.confirmedTag(list(row("model:latest",invalid,digest)),"model")==null);
        for(Object invalid:new Object[]{"", "bad", 1, JSONObject.NULL})
            ok(InstalledModelInventory.confirmedTag(list(row("model:latest",1,invalid)),"model")==null);
        ok(InstalledModelInventory.names(list(row("old",0,JSONObject.NULL))).equals(List.of("old")));
        System.out.println("PASS: "+n+" installed inventory assertions");
    }
}
