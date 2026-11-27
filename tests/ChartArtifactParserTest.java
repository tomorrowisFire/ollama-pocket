package com.ollamapocket.app;

public final class ChartArtifactParserTest {
    private static int checks;
    private static void ok(boolean pass){checks++;if(!pass)throw new AssertionError("check "+checks);}
    private static final String RAW="{\"kind\":\"bar\",\"title\":\"<script>한국어</script>\",\"points\":[{\"label\":\"=SUM(1,2)\",\"value\":-2},{\"label\":\"日本語\",\"value\":3.5}]}";
    public static void main(String[] args)throws Exception{
        ChartArtifact a=ChartArtifactParser.parse(RAW);
        ok(a.kind==ChartArtifact.Kind.BAR&&a.points.size()==2&&a.points.get(0).value==-2);
        ok(!a.html().contains("<script>")&&a.html().contains("&lt;script&gt;")&&a.csv().contains("\"'=SUM(1,2)\""));
        ok(ChartArtifactParser.fromFence("chart",RAW)!=null&&ChartArtifactParser.fromFence("onhand-chart",RAW)!=null);
        ok(ChartArtifactParser.fromFence("html",RAW)==null);
        ok(ChartArtifactParser.parse(RAW.replace("\"bar\"","\"line\"")).kind==ChartArtifact.Kind.LINE);
        for(String bad:new String[]{"",null,"{}",RAW+"{}",RAW.replace("\"bar\"","\"pie\""),
                RAW.replace("\"bar\"","\"bar\",\"\\u006bind\":\"line\""),RAW.replace("\"bar\"","\"bar\",\"url\":\"https://example.invalid\""),
                RAW.replace("\"value\":-2","\"value\":\"-2\""),RAW.replace("\"value\":-2","\"value\":1e256"),
                RAW.replace("\"value\":-2","\"value\":1000000001"),RAW.replace("\"value\":-2","\"value\":-2,\"html\":\"<script>\""),
                RAW.replace("\"value\":-2","\"value\":null"),RAW.replace("\"日本語\"","\"\\uD800\""),
                "{\"kind\":\"bar\",\"title\":\"x\",\"points\":[]}",
                "{\"kind\":\"bar\",\"title\":\"x\",\"points\":["+("{\"label\":\"a\",\"value\":1},").repeat(64)+"{\"label\":\"b\",\"value\":1}]}"}){
            ok(ChartArtifactParser.fromFence("chart",bad)==null);
        }
        System.out.println("PASS: "+checks+" typed chart fence and fallback checks");
    }
}
