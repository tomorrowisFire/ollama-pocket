package com.ollamapocket.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Fixed, data-only voice downloads. Runtime code is bundled with the application. */
final class VoiceModelCatalog {
    enum Format { RAW, TAR_BZ2, FILES }
    // Strict relative data paths, at most three components; no hidden files or traversal.
    private static boolean validEntryName(String name){
        if(name==null||name.length()>100)return false;
        String[] parts=name.split("/",-1);
        if(parts.length>3)return false;
        for(String part:parts)if(!part.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,100}"))return false;
        return true;
    }
    static final class Entry {
        final String name, sha256;
        final long bytes;
        Entry(String name,long bytes,String sha256){
            if(!validEntryName(name)||bytes<1||
                sha256==null||!sha256.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Model entry");
            this.name=name;this.bytes=bytes;this.sha256=sha256;
        }
    }
    // Digest of the immutable ordered download manifest, not an archive checksum.
    private static String fileManifest(String base,Entry... files){
        StringBuilder canonical=new StringBuilder("onhand-voice-files-v1\n").append(base).append('\n');
        for(Entry e:files)canonical.append(e.name).append('\t').append(e.bytes).append('\t').append(e.sha256).append('\n');
        try{
            byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(
                canonical.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            StringBuilder out=new StringBuilder(64);
            for(byte value:hash)out.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
            return out.toString();
        }catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    static Model fileSet(String id,String name,String base,String license,String licenseUrl,String languages,Entry... files){
        if(files==null||files.length<1||files.length>16)throw new IllegalArgumentException("File set count");
        long total=0;
        for(Entry entry:files){
            if(entry==null||entry.bytes>512L*1024*1024)throw new IllegalArgumentException("File set entry");
            total=Math.addExact(total,entry.bytes);
        }
        return new Model(id,name,base,total,fileManifest(base,files),Format.FILES,null,license,licenseUrl,languages,files);
    }
    static final class Model {
        final String id,name,url,sha256,license,licenseUrl,languages,archivePrefix;
        final long archiveBytes,expandedBytes;
        final Format format;
        final Map<String,Entry> entries;
        final java.util.Set<String> directories;
        Model(String id,String name,String url,long archiveBytes,String sha256,Format format,String archivePrefix,
              String license,String licenseUrl,String languages,Entry... files){
            if(id==null||!id.matches("[a-z0-9][a-z0-9-]{0,80}")||name==null||url==null||!url.startsWith("https://")||
                archiveBytes<1||archiveBytes>512L*1024*1024||sha256==null||!sha256.matches("[a-f0-9]{64}")||
                format==null||files.length<1||files.length>16)throw new IllegalArgumentException("Model descriptor");
            if(format==Format.TAR_BZ2&&(archivePrefix==null||!archivePrefix.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,100}")))
                throw new IllegalArgumentException("Archive prefix");
            if(format==Format.RAW&&(files.length!=1||files[0].name.contains("/")))throw new IllegalArgumentException("Raw model");
            if(format==Format.FILES){
                if(archivePrefix!=null||!url.matches("https://huggingface\\.co/[A-Za-z0-9][A-Za-z0-9._-]{0,100}/[A-Za-z0-9][A-Za-z0-9._-]{0,100}/resolve/[a-f0-9]{40}/"))
                    throw new IllegalArgumentException("Unpinned file set URL");
                for(Entry entry:files)if(entry==null||entry.name.contains("/"))throw new IllegalArgumentException("File set must be flat");
            }
            Map<String,Entry> map=new LinkedHashMap<>();long total=0;
            for(Entry entry:files){
                if(map.put(entry.name,entry)!=null)throw new IllegalArgumentException("Duplicate model file");
                total=Math.addExact(total,entry.bytes);
            }
            java.util.Set<String> dirs=new java.util.LinkedHashSet<>();
            for(String entryName:map.keySet()){
                if(format==Format.TAR_BZ2&&archivePrefix.length()+1+entryName.length()>100)
                    throw new IllegalArgumentException("Tar path too long");
                for(int slash=entryName.lastIndexOf('/');slash>=0;slash=entryName.lastIndexOf('/',slash-1)){
                    String parent=entryName.substring(0,slash);
                    if(map.containsKey(parent))throw new IllegalArgumentException("Model file/directory collision");
                    dirs.add(parent);
                }
            }
            this.directories=Collections.unmodifiableSet(dirs);
            if(total>768L*1024*1024)throw new IllegalArgumentException("Expanded model limit");
            if(format==Format.RAW&&(total!=archiveBytes||!files[0].sha256.equals(sha256)))throw new IllegalArgumentException("Raw model digest");
            if(format==Format.FILES&&(total!=archiveBytes||!fileManifest(url,files).equals(sha256)))throw new IllegalArgumentException("File set manifest");
            this.id=id;this.name=name;this.url=url;this.archiveBytes=archiveBytes;this.sha256=sha256;
            this.format=format;this.archivePrefix=archivePrefix;this.license=license;this.licenseUrl=licenseUrl;
            this.languages=languages;this.entries=Collections.unmodifiableMap(map);this.expandedBytes=total;
        }
        String fileUrl(Entry entry){
            if(format!=Format.FILES||entry==null||entries.get(entry.name)!=entry)throw new IllegalArgumentException("Unlisted model file");
            return url+entry.name;
        }
        long requiredSpace(){
            return Math.addExact(format==Format.FILES?expandedBytes:Math.addExact(archiveBytes,expandedBytes),64L*1024*1024);
        }
    }
    static final Model WHISPER=new Model("whisper-tiny-fb5ed936","Whisper tiny (multilingual)",
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/fb5ed9369c26eaf08392504d8446da431e0fffb9/ggml-tiny.bin",
        77691713L,"be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21",Format.RAW,null,
        "MIT","https://github.com/openai/whisper/blob/main/LICENSE","ko / en / zh / ja",
        new Entry("ggml-tiny.bin",77691713L,"be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21"));
    static final Model TTS=new Model("supertonic3-int8-2026-05-11","Supertonic 3 int8",
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2",
        128774318L,"82fa96f91c4ef8abaae3a14a3f4153facf88bed821d1f7331cec2700f432c427",Format.TAR_BZ2,
        "sherpa-onnx-supertonic-3-tts-int8-2026-05-11","OpenRAIL-M (Supertone Inc., 2026)",
        "https://huggingface.co/Supertone/supertonic-3/blob/724fb5abbf5502583fb520898d45929e62f02c0b/LICENSE","ko / en / ja",
        new Entry("duration_predictor.int8.onnx",3700147L,"c3eb91414d5ff8a7a239b7fe9e34e7e2bf8a8140d8375ffb14718b1c639325db"),
        new Entry("text_encoder.int8.onnx",36416150L,"c7befd5ea8c3119769e8a6c1486c4edc6a3bc8365c67621c881bbb774b9902ff"),
        new Entry("vector_estimator.int8.onnx",78400833L,"20cd86fa5c6effedfda0e7cffe5b0569ca401c440a0c3a1d72bf39286c0db3fd"),
        new Entry("vocoder.int8.onnx",25991073L,"e923d60f53f95eb1ce235f1dc33ec56d9c057823c96fa6f8acf98f32b0da6152"),
        new Entry("tts.json",8253L,"42078d3aef1cd43ab43021f3c54f47d2d75ceb4e75f627f118890128b06a0d09"),
        new Entry("unicode_indexer.bin",262144L,"8402ca48e5189a8950138580b0fff64db6f072f24ac07cd54ba8b2fbb9883b30"),
        new Entry("voice.bin",517168L,"67d5209b0ee8ce6c74105ffbe12fe6a7628aea3b4ba2fcb308a4a67938a93ce8"),
        new Entry("LICENSE",1070L,"0dfe0d0ba84416fe3879d9a34f4909d8d0137c78d1e95834177b0414ac096fa2"),
        new Entry("README.md",19518L,"a96c347945f7c8bc1673bea3525b1ac8d36fdde556e1e0a6a186052429caf863"));
    // Exact host- and install-qualified Chinese model, optional user-enabled playback.
    static final Model MELO=fileSet("melo-zh-en-a0d5c6a2","MeloTTS Chinese FP32",
        "https://huggingface.co/csukuangfj/vits-melo-tts-zh_en/resolve/a0d5c6a264c0ef92d70d8661d8cc502d79627cd6/",
        "MIT (MyShell.ai)","https://huggingface.co/csukuangfj/vits-melo-tts-zh_en/blob/a0d5c6a264c0ef92d70d8661d8cc502d79627cd6/LICENSE","zh-Hans",
        new Entry("LICENSE",1053L,"88a50e5a02bbc2a5c2f084dc19da751aa97b1690f5fda76cd8005c8634d1ca70"),
        new Entry("tokens.txt",655L,"d18664a7e12bd7ea1022ddaf951e534e136815016c5a809d6b64156bffb4369d"),
        new Entry("lexicon.txt",6837671L,"7236884b02435ac5d10cf69b4be40a61b45aa676b5300f0e412f185748fee528"),
        new Entry("date.fst",59154L,"eb8aa079ae3cb81d8f4404992f39d61a0cb990947512b5b8d1e54d1f6980e718"),
        new Entry("number.fst",64482L,"743f402181fcfebf76cc2f0546b71fa26476e626fbe4e460fb7b4c3a7a8bd5bd"),
        new Entry("phone.fst",88630L,"1ac2b6fa56b1442320c4de7db08353bab8963a2b57f365eebcdd3a2d3562f8d7"),
        new Entry("model.onnx",170429550L,"bf30582eb1b012250a35b1a4a80e7dfbcf8485e7bb9de0d95efbbeef0e4ad86d"));
    static boolean matchesMeloLicense(byte[] data){
        Entry entry=MELO.entries.get("LICENSE");
        if(data==null||data.length!=entry.bytes)return false;
        try{
            byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex=new StringBuilder(64);
            for(byte value:hash)hex.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
            return entry.sha256.equals(hex.toString());
        }catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    static final java.util.List<Model> ALL=Collections.unmodifiableList(Arrays.asList(WHISPER,TTS,MELO));
    private VoiceModelCatalog(){}
}
