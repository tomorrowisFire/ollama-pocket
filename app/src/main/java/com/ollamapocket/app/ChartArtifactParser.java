package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Closed chart interchange format. Untrusted input is data only, never executable markup. */
final class ChartArtifactParser {
    private ChartArtifactParser(){}
    static ChartArtifact parse(String raw)throws Exception{
        ToolJsonPolicy.requireObject(raw);
        JSONObject obj=new JSONObject(raw);
        if(obj.length()!=3||!(obj.opt("kind") instanceof String)||!(obj.opt("title") instanceof String))
            throw new IllegalArgumentException("Chart fields");
        String kind=obj.getString("kind");
        ChartArtifact.Kind type="bar".equals(kind)?ChartArtifact.Kind.BAR:"line".equals(kind)?ChartArtifact.Kind.LINE:null;
        JSONArray rows=obj.optJSONArray("points");
        if(type==null||rows==null||rows.length()<1||rows.length()>ChartArtifact.MAX_POINTS)
            throw new IllegalArgumentException("Chart shape");
        List<ChartArtifact.Point> points=new ArrayList<>();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);
            if(row==null||row.length()!=2||!(row.opt("label") instanceof String)||!(row.opt("value") instanceof Number))
                throw new IllegalArgumentException("Chart point");
            points.add(new ChartArtifact.Point(row.getString("label"),((Number)row.get("value")).doubleValue()));
        }
        return new ChartArtifact(type,obj.getString("title"),points);
    }
    static ChartArtifact fromFence(String language,String body){
        if(!"chart".equals(language)&&!"onhand-chart".equals(language))return null;
        try{return parse(body);}catch(Exception invalid){return null;}
    }
}
