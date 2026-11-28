package com.ollamapocket.app;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Separate bounded local project repository. No chat, model, network or permission access. */
final class ProjectLibrary {
    static final int MAX_PROJECTS=12,MAX_ARTIFACTS=20,MAX_VERSIONS=6;
    static final int MAX_TITLE=80,MAX_INSTRUCTIONS=2000,MAX_TEXT=32000,MAX_BYTES=2*1024*1024;
    static final int MAX_LINKS=12;
    private static final int SCHEMA=1,LINK_SCHEMA=2;
    private ProjectLibrary(){}
    static final class Version {
        final int ordinal;final long saved;final String text;
        private Version(int ordinal,long saved,String text){this.ordinal=ordinal;this.saved=saved;this.text=text;}
    }
    static final class Artifact {
        final String id,title;final List<Version> versions;
        private Artifact(String id,String title,List<Version> versions){
            this.id=id;this.title=title;this.versions=immutable(versions);
        }
        Version current(){return versions.get(versions.size()-1);}
        Version version(int ordinal){
            if(ordinal<1||ordinal>versions.size())throw invalid("Unknown version");
            return versions.get(ordinal-1);
        }
    }
    /** Stable local identity only; no copied chat title, draft or message. */
    static final class Link {
        final String model,session;
        Link(String model,String session){
            text(model,256);
            if(!model.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}")||session==null||!session.matches("[0-9]{1,19}"))
                throw invalid("Invalid chat identity");
            this.model=model;this.session=session;
        }
        @Override public boolean equals(Object o){return o instanceof Link&&model.equals(((Link)o).model)&&session.equals(((Link)o).session);}
        @Override public int hashCode(){return 31*model.hashCode()+session.hashCode();}
    }
    static final class Project {
        final String id,title,instructions;final List<Artifact> artifacts;final List<Link> links;
        private Project(String id,String title,String instructions,List<Artifact> artifacts,List<Link> links){
            this.id=id;this.title=title;this.instructions=instructions;this.artifacts=immutable(artifacts);this.links=immutable(links);
        }
        Artifact artifact(String id){
            for(Artifact a:artifacts)if(a.id.equals(id))return a;
            throw invalid("Unknown artifact");
        }
    }
    static final class State {
        final long revision;final List<Project> projects;
        private State(long revision,List<Project> projects){this.revision=revision;this.projects=immutable(projects);}
        Project project(String id){
            for(Project p:projects)if(p.id.equals(id))return p;
            throw invalid("Unknown project");
        }
    }
    private static <T> List<T> immutable(List<T> list){return Collections.unmodifiableList(new ArrayList<>(list));}
    static State empty(){return new State(0,Collections.emptyList());}
    private static IllegalArgumentException invalid(String reason){return new IllegalArgumentException(reason);}
    private static String text(String s,int max){
        if(s==null||s.length()>max)throw invalid("Text limit");
        for(int i=0;i<s.length();i++){
            char ch=s.charAt(i);
            if(Character.isHighSurrogate(ch)){
                if(++i>=s.length()||!Character.isLowSurrogate(s.charAt(i)))throw invalid("Invalid Unicode");
            }else if(Character.isLowSurrogate(ch))throw invalid("Invalid Unicode");
        }
        return s;
    }
    static String title(String raw){
        String s=text(raw,MAX_TITLE).trim();
        boolean visible=false;
        for(int i=0;i<s.length();){
            int cp=s.codePointAt(i);i+=Character.charCount(cp);
            if(Character.isISOControl(cp)||cp==0x2028||cp==0x2029||
                (Character.getType(cp)==Character.FORMAT&&cp!=0x200C&&cp!=0x200D))throw invalid("Invalid title");
            if(!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp)&&cp!=0x200C&&cp!=0x200D)visible=true;
        }
        if(!visible)throw invalid("Missing title");
        return s;
    }
    private static void id(String id){if(id==null||!id.matches("[A-Za-z0-9_-]{1,64}"))throw invalid("Invalid ID");}
    private static void newId(State state,String id){
        id(id);
        for(Project p:state.projects){
            if(p.id.equals(id))throw invalid("Duplicate ID");
            for(Artifact a:p.artifacts)if(a.id.equals(id))throw invalid("Duplicate ID");
        }
    }
    private static void time(long now){if(now<=0)throw invalid("Invalid saved time");}
    private static State next(State s,List<Project> projects){
        if(s.revision==Long.MAX_VALUE)throw invalid("Revision limit");
        return new State(s.revision+1,projects);
    }
    private static State replace(State s,Project old,Project replacement){
        List<Project> projects=new ArrayList<>(s.projects);int i=projects.indexOf(old);
        if(i<0)throw invalid("Project scope");
        if(replacement==null)projects.remove(i);else projects.set(i,replacement);
        return next(s,projects);
    }
    private static State replaceArtifact(State s,Project p,Artifact old,Artifact replacement){
        List<Artifact> artifacts=new ArrayList<>(p.artifacts);int i=artifacts.indexOf(old);
        if(i<0)throw invalid("Artifact scope");
        if(replacement==null)artifacts.remove(i);else artifacts.set(i,replacement);
        return replace(s,p,new Project(p.id,p.title,p.instructions,artifacts,p.links));
    }
    static State createProject(State s,String id,String title,String instructions){
        newId(s,id);if(s.projects.size()>=MAX_PROJECTS)throw invalid("Project limit");
        List<Project> projects=new ArrayList<>(s.projects);
        projects.add(new Project(id,title(title),text(instructions,MAX_INSTRUCTIONS),Collections.emptyList(),Collections.emptyList()));
        return next(s,projects);
    }
    /** Create a project and its initial artifact in exactly one repository revision. */
    static State createProjectWithArtifact(State s,String pid,String projectTitle,String aid,String artifactTitle,String value,long now){
        newId(s,pid);newId(s,aid);time(now);
        if(pid.equals(aid)||s.projects.size()>=MAX_PROJECTS)throw invalid("Project or ID limit");
        Artifact initial=new Artifact(aid,title(artifactTitle),Collections.singletonList(new Version(1,now,text(value,MAX_TEXT))));
        List<Project> projects=new ArrayList<>(s.projects);
        projects.add(new Project(pid,title(projectTitle),"",Collections.singletonList(initial),Collections.emptyList()));
        return next(s,projects);
    }
    static State editProject(State s,String id,String title,String instructions){
        Project p=s.project(id);String name=title(title);text(instructions,MAX_INSTRUCTIONS);
        if(p.title.equals(name)&&p.instructions.equals(instructions))return s;
        return replace(s,p,new Project(p.id,name,instructions,p.artifacts,p.links));
    }
    static State addLink(State s,String pid,Link link){
        Project p=s.project(pid);if(link==null)throw invalid("Missing link");
        if(p.links.contains(link))return s;
        if(p.links.size()>=MAX_LINKS)throw invalid("Chat link limit");
        List<Link> links=new ArrayList<>(p.links);links.add(link);
        return replace(s,p,new Project(p.id,p.title,p.instructions,p.artifacts,links));
    }
    static State removeLink(State s,String pid,Link link){
        Project p=s.project(pid);List<Link> links=new ArrayList<>(p.links);
        if(!links.remove(link))throw invalid("Unknown link");
        return replace(s,p,new Project(p.id,p.title,p.instructions,p.artifacts,links));
    }
    private static int schema(State s){
        for(Project p:s.projects)if(!p.links.isEmpty())return LINK_SCHEMA;
        return SCHEMA;
    }
    static State deleteProject(State s,String id){return replace(s,s.project(id),null);}
    static State createArtifact(State s,String projectId,String id,String title,String value,long now){
        Project p=s.project(projectId);newId(s,id);time(now);
        if(p.artifacts.size()>=MAX_ARTIFACTS)throw invalid("Artifact limit");
        List<Artifact> artifacts=new ArrayList<>(p.artifacts);
        artifacts.add(new Artifact(id,title(title),Collections.singletonList(new Version(1,now,text(value,MAX_TEXT)))));
        return replace(s,p,new Project(p.id,p.title,p.instructions,artifacts,p.links));
    }
    static State renameArtifact(State s,String projectId,String id,String title){
        Project p=s.project(projectId);Artifact a=p.artifact(id);String name=title(title);
        if(a.title.equals(name))return s;
        return replaceArtifact(s,p,a,new Artifact(a.id,name,a.versions));
    }
    static State deleteArtifact(State s,String projectId,String id){
        Project p=s.project(projectId);return replaceArtifact(s,p,p.artifact(id),null);
    }
    static State editArtifact(State s,String projectId,String id,String value,long now){
        Project p=s.project(projectId);Artifact a=p.artifact(id);text(value,MAX_TEXT);
        if(a.current().text.equals(value))return s;
        time(now);
        if(now<a.current().saved||a.versions.size()>=MAX_VERSIONS)throw invalid("Version limit or clock");
        List<Version> versions=new ArrayList<>(a.versions);
        versions.add(new Version(versions.size()+1,now,value));
        return replaceArtifact(s,p,a,new Artifact(a.id,a.title,versions));
    }
    static State restoreArtifact(State s,String projectId,String id,int ordinal,long now){
        Artifact a=s.project(projectId).artifact(id);
        return editArtifact(s,projectId,id,a.version(ordinal).text,now);
    }

    private static void validate(State s){
        if(s==null||s.revision<0||s.projects.size()>MAX_PROJECTS||(s.revision==0&&!s.projects.isEmpty()))
            throw invalid("Invalid project store");
        Set<String> ids=new HashSet<>();
        for(Project p:s.projects){
            id(p.id);
            if(!ids.add(p.id)||!p.title.equals(title(p.title))||p.artifacts.size()>MAX_ARTIFACTS)throw invalid("Invalid project");
            text(p.instructions,MAX_INSTRUCTIONS);
            if(p.links.size()>MAX_LINKS||p.links.contains(null)||new HashSet<>(p.links).size()!=p.links.size())throw invalid("Invalid chat links");
            for(Artifact a:p.artifacts){
                id(a.id);
                if(!ids.add(a.id)||!a.title.equals(title(a.title))||a.versions.isEmpty()||a.versions.size()>MAX_VERSIONS)
                    throw invalid("Invalid artifact");
                long previous=0;int ordinal=0;
                for(Version v:a.versions){
                    if(v.ordinal!=++ordinal||v.saved<=0||v.saved<previous)throw invalid("Invalid version history");
                    text(v.text,MAX_TEXT);previous=v.saved;
                }
            }
        }
    }
    // Fixed key order and JSON escaping are platform-independent; decoder accepts only this exact canonical format.
    private static void quote(StringBuilder out,String value){
        out.append('"');
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(c=='"'||c=='\\')out.append('\\').append(c);
            else if(c=='\n')out.append("\\n");
            else if(c=='\r')out.append("\\r");
            else if(c=='\t')out.append("\\t");
            else if(c=='\b')out.append("\\b");
            else if(c=='\f')out.append("\\f");
            else if(c<32){
                out.append("\\u00");
                out.append("0123456789abcdef".charAt(c>>>4)).append("0123456789abcdef".charAt(c&15));
            }else out.append(c);
            if(out.length()>MAX_BYTES)throw invalid("Store size limit");
        }
        out.append('"');
    }
    private static void field(StringBuilder b,String name,String value){quote(b,name);b.append(':');quote(b,value);}
    private static String payload(State state){
        validate(state);
        StringBuilder b=new StringBuilder("{\"revision\":").append(state.revision).append(",\"projects\":[");
        boolean first=true;
        for(Project p:state.projects){
            if(!first)b.append(',');first=false;
            b.append('{');field(b,"id",p.id);b.append(',');field(b,"title",p.title);b.append(',');field(b,"instructions",p.instructions);
            b.append(",\"artifacts\":[");boolean fa=true;
            for(Artifact a:p.artifacts){
                if(!fa)b.append(',');fa=false;
                b.append('{');field(b,"id",a.id);b.append(',');field(b,"title",a.title);b.append(",\"versions\":[");
                boolean fv=true;
                for(Version v:a.versions){
                    if(!fv)b.append(',');fv=false;
                    b.append("{\"ordinal\":").append(v.ordinal).append(",\"saved\":").append(v.saved).append(',');
                    field(b,"text",v.text);b.append('}');
                }
                b.append("]}");
            }
            b.append(']');
            if(schema(state)==LINK_SCHEMA){
                b.append(",\"links\":[");boolean fl=true;
                for(Link link:p.links){
                    if(!fl)b.append(',');fl=false;
                    b.append('{');field(b,"model",link.model);b.append(',');field(b,"session",link.session);b.append('}');
                }
                b.append(']');
            }
            b.append('}');
        }
        return b.append("]}").toString();
    }
    private static String digest(String text)throws Exception{
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder b=new StringBuilder(64);
        for(byte value:hash)b.append(Character.forDigit((value&255)>>>4,16)).append(Character.forDigit(value&15,16));
        return b.toString();
    }
    static byte[] encode(State state)throws Exception{
        String payload=payload(state);
        StringBuilder b=new StringBuilder("{\"schema\":").append(schema(state)).append(',');
        field(b,"payload",payload);b.append(',');field(b,"sha256",digest(payload));b.append('}');
        byte[] bytes=b.toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_BYTES)throw invalid("Store size limit");
        return bytes;
    }
    // Bound depth before invoking either Android or JVM JSON parsers; quoted braces are not structural.
    private static void structure(String raw){
        int depth=0,nodes=0;boolean quoted=false,escape=false;
        for(int i=0;i<raw.length();i++){
            char c=raw.charAt(i);
            if(quoted){
                if(escape){escape=false;continue;}
                if(c=='\\'){escape=true;continue;}
                if(c=='"')quoted=false;
            }else if(c=='"')quoted=true;
            else if(c=='{'||c=='['){
                if(++depth>12||++nodes>6000)throw invalid("JSON structure limit");
            }else if(c=='}'||c==']'){if(--depth<0)throw invalid("JSON structure");}
            else if(c!=','&&c!=':'&&c!='-'&&(c<'0'||c>'9'))
                throw invalid("Noncanonical JSON token"); // excludes comments, single quotes and unquoted keys before parsing
        }
        if(quoted||escape||depth!=0)throw invalid("JSON structure");
    }
    private static JSONObject object(String raw)throws Exception{
        structure(raw);
        JSONTokener tok=new JSONTokener(raw);Object value=tok.nextValue();
        if(!(value instanceof JSONObject)||tok.nextClean()!=0)throw invalid("Invalid JSON object");
        return (JSONObject)value;
    }
    private static void keys(JSONObject o,String... keys){
        if(o.length()!=keys.length)throw invalid("Unexpected fields");
        for(String key:keys)if(!o.has(key))throw invalid("Missing field");
    }
    private static String string(JSONObject o,String key)throws Exception{
        Object value=o.get(key);
        if(!(value instanceof String))throw invalid("Expected string");
        return (String)value;
    }
    private static long number(JSONObject o,String key)throws Exception{
        Object value=o.get(key);
        if(!(value instanceof Integer)&&!(value instanceof Long))throw invalid("Expected integer");
        return ((Number)value).longValue();
    }
    private static JSONArray array(JSONObject o,String key,int max)throws Exception{
        Object value=o.get(key);
        if(!(value instanceof JSONArray)||((JSONArray)value).length()>max)throw invalid("Array limit or type");
        return (JSONArray)value;
    }
    private static JSONObject row(JSONArray a,int i)throws Exception{
        Object value=a.get(i);if(!(value instanceof JSONObject))throw invalid("Expected object");
        return (JSONObject)value;
    }
    static State decode(byte[] bytes)throws Exception{
        if(bytes==null||bytes.length==0||bytes.length>MAX_BYTES)throw invalid("Store size limit");
        String raw=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        JSONObject envelope=object(raw);keys(envelope,"schema","payload","sha256");
        long schema=number(envelope,"schema");if(schema!=SCHEMA&&schema!=LINK_SCHEMA)throw invalid("Unknown schema");
        String payload=string(envelope,"payload"),hash=string(envelope,"sha256");
        text(payload,MAX_BYTES);
        if(!hash.matches("[0-9a-f]{64}")||!digest(payload).equals(hash))throw invalid("Store digest mismatch");
        JSONObject root=object(payload);keys(root,"revision","projects");
        long revision=number(root,"revision");if(revision<1)throw invalid("Invalid persisted revision");
        JSONArray pp=array(root,"projects",MAX_PROJECTS);
        List<Project> projects=new ArrayList<>();
        for(int i=0;i<pp.length();i++){
            JSONObject p=row(pp,i);if(schema==LINK_SCHEMA)keys(p,"id","title","instructions","artifacts","links");else keys(p,"id","title","instructions","artifacts");
            JSONArray aa=array(p,"artifacts",MAX_ARTIFACTS);List<Artifact> artifacts=new ArrayList<>();
            for(int j=0;j<aa.length();j++){
                JSONObject a=row(aa,j);keys(a,"id","title","versions");
                JSONArray vv=array(a,"versions",MAX_VERSIONS);List<Version> versions=new ArrayList<>();
                for(int k=0;k<vv.length();k++){
                    JSONObject v=row(vv,k);keys(v,"ordinal","saved","text");
                    long ordinal=number(v,"ordinal");if(ordinal!=k+1)throw invalid("Version ordinal");
                    versions.add(new Version((int)ordinal,number(v,"saved"),string(v,"text")));
                }
                artifacts.add(new Artifact(string(a,"id"),string(a,"title"),versions));
            }
            List<Link> links=new ArrayList<>();
            if(schema==LINK_SCHEMA){
                JSONArray ll=array(p,"links",MAX_LINKS);
                for(int j=0;j<ll.length();j++){
                    JSONObject link=row(ll,j);keys(link,"model","session");links.add(new Link(string(link,"model"),string(link,"session")));
                }
            }
            projects.add(new Project(string(p,"id"),string(p,"title"),string(p,"instructions"),artifacts,links));
        }
        State state=new State(revision,projects);validate(state);
        if(!Arrays.equals(bytes,encode(state)))throw invalid("Noncanonical project store");
        return state;
    }
    static final ProjectTransactions.Codec<State> CODEC=new ProjectTransactions.Codec<State>(){
        public State decode(byte[] bytes)throws Exception{return ProjectLibrary.decode(bytes);}
        public byte[] encode(State value)throws Exception{return ProjectLibrary.encode(value);}
        public long revision(State value){return value.revision;}
    };
}
