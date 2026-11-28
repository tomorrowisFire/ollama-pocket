package com.ollamapocket.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Bounded declarative graph and structural checkpoint replay; never executes a task. */
final class WorkflowBranchPlan {
    static final int MAX_NODES = 10;
    static final String COMPLETE = "!complete", BLOCK = "!blocked";
    enum Kind { TASK, CONDITION }
    enum Outcome { VERIFIED, CONDITION_TRUE, CONDITION_FALSE, UNVERIFIED, INVALID, TRUNCATED, UNCERTAIN, CANCELLED }
    enum Status { READY, COMPLETED, BLOCKED, CANCELLED }

    static final class Node {
        final String id, definitionSha256, onTrue, onFalse;
        final Kind kind;
        private Node(String id, String definitionSha256, Kind kind, String onTrue, String onFalse) {
            if (!validId(id) || !validDigest(definitionSha256) || !validTarget(onTrue) ||
                    kind == null || (kind == Kind.CONDITION && (!validTarget(onFalse) || onTrue.equals(onFalse))) ||
                    (kind == Kind.TASK && onFalse != null)) throw new IllegalArgumentException("Invalid workflow node");
            this.id=id; this.definitionSha256=definitionSha256; this.kind=kind;
            this.onTrue=onTrue; this.onFalse=onFalse;
        }
        static Node task(String id, String definitionSha256, String onVerified) {
            return new Node(id,definitionSha256,Kind.TASK,onVerified,null);
        }
        static Node condition(String id, String definitionSha256, String onTrue, String onFalse) {
            return new Node(id,definitionSha256,Kind.CONDITION,onTrue,onFalse);
        }
    }

    /** Evidence digest must refer to a result the caller actually verified; no proof is inferred. */
    static final class Visit {
        final String node, evidenceSha256;
        final Outcome outcome;
        Visit(String node, Outcome outcome, String evidenceSha256) {
            if (!validId(node) || outcome==null || (positive(outcome) && !validDigest(evidenceSha256)) ||
                    (!positive(outcome) && evidenceSha256!=null && !validDigest(evidenceSha256)))
                throw new IllegalArgumentException("Invalid workflow visit");
            this.node=node; this.outcome=outcome; this.evidenceSha256=evidenceSha256;
        }
    }

    static final class Snapshot {
        final String planSha256, nextNode;
        final Status status;
        final List<Visit> visits;
        private Snapshot(String planSha256, String nextNode, Status status, List<Visit> visits) {
            this.planSha256=planSha256; this.nextNode=nextNode; this.status=status;
            this.visits=Collections.unmodifiableList(new ArrayList<>(visits));
        }
    }

    private final List<Node> nodes;
    private final Map<String,Node> byId;
    final String sha256;

    WorkflowBranchPlan(List<Node> source) {
        if(source==null || source.isEmpty() || source.size()>MAX_NODES) throw new IllegalArgumentException("Workflow graph size");
        List<Node> copy=new ArrayList<>(source);
        Map<String,Integer> order=new LinkedHashMap<>(); Map<String,Node> map=new LinkedHashMap<>();
        for(int i=0;i<copy.size();i++) {
            Node n=copy.get(i);
            if(n==null || order.putIfAbsent(n.id,i)!=null) throw new IllegalArgumentException("Duplicate or missing node");
            map.put(n.id,n);
        }
        Set<String> reachable=new HashSet<>();reachable.add(copy.get(0).id);
        for(int i=0;i<copy.size();i++) {
            Node n=copy.get(i);
            validateEdge(n.onTrue,i,order);
            if(n.kind==Kind.CONDITION) validateEdge(n.onFalse,i,order);
            if(!reachable.contains(n.id)) throw new IllegalArgumentException("Unreachable workflow node");
            if(!terminal(n.onTrue))reachable.add(n.onTrue);
            if(n.kind==Kind.CONDITION&&!terminal(n.onFalse))reachable.add(n.onFalse);
        }
        nodes=Collections.unmodifiableList(copy);byId=Collections.unmodifiableMap(map);
        sha256=identity(copy);
    }

    List<Node> nodes() { return nodes; }
    /** Longest possible route, including its terminal-producing node; no execution or prediction. */
    int maxPathNodes(){return maxPathNodesFrom(nodes.get(0).id);}
    int maxPathNodesFrom(String id){
        if(!byId.containsKey(id))throw new IllegalArgumentException("Unknown workflow cursor");
        Map<String,Integer> lengths=new HashMap<>();
        for(int i=nodes.size()-1;i>=0;i--){
            Node node=nodes.get(i);
            int yes=terminal(node.onTrue)?0:lengths.get(node.onTrue);
            int no=node.kind==Kind.TASK||terminal(node.onFalse)?0:lengths.get(node.onFalse);
            lengths.put(node.id,1+Math.max(yes,no));
        }
        return lengths.get(id);
    }
    Snapshot start() { return new Snapshot(sha256,nodes.get(0).id,Status.READY,Collections.emptyList()); }

    /** Caller must verify the actual outcome/evidence and persist with expected-checkpoint ownership. */
    Snapshot advance(Snapshot prior, Visit visit) {
        if(prior==null || !sha256.equals(prior.planSha256) || prior.status!=Status.READY ||
                visit==null || !visit.node.equals(prior.nextNode) || prior.visits.size()>=MAX_NODES)
            throw new IllegalArgumentException("Workflow checkpoint mismatch");
        Node node=byId.get(visit.node);
        if(node==null)throw new IllegalArgumentException("Unknown workflow node");
        String target=null;Status status;
        switch(visit.outcome) {
            case VERIFIED:
                if(node.kind!=Kind.TASK)throw new IllegalArgumentException("Condition needs a boolean observation");
                target=node.onTrue;break;
            case CONDITION_TRUE:case CONDITION_FALSE:
                if(node.kind!=Kind.CONDITION)throw new IllegalArgumentException("Task needs verified output");
                target=visit.outcome==Outcome.CONDITION_TRUE?node.onTrue:node.onFalse;break;
            case CANCELLED:break;
            case UNVERIFIED:case INVALID:case TRUNCATED:case UNCERTAIN:break;
            default:throw new IllegalArgumentException("Unknown outcome");
        }
        if(visit.outcome==Outcome.CANCELLED)status=Status.CANCELLED;
        else if(COMPLETE.equals(target))status=Status.COMPLETED;
        else if(target==null||BLOCK.equals(target))status=Status.BLOCKED;
        else status=Status.READY;
        List<Visit> visits=new ArrayList<>(prior.visits);visits.add(visit);
        return new Snapshot(sha256,status==Status.READY?target:null,status,visits);
    }

    /** Structural replay only. The caller must independently check stored result digests and durable ownership. */
    Snapshot restore(String planSha256, List<Visit> visits, Status expectedStatus, String expectedNext) {
        if(!sha256.equals(planSha256)||visits==null||visits.size()>MAX_NODES||expectedStatus==null)
            throw new IllegalArgumentException("Workflow restore identity");
        Snapshot restored=start();
        for(Visit v:new ArrayList<>(visits))restored=advance(restored,v);
        if(restored.status!=expectedStatus||!Objects.equals(restored.nextNode,expectedNext))
            throw new IllegalArgumentException("Workflow restore cursor mismatch");
        return restored;
    }

    private static boolean positive(Outcome outcome) {
        return outcome==Outcome.VERIFIED||outcome==Outcome.CONDITION_TRUE||outcome==Outcome.CONDITION_FALSE;
    }
    private static boolean validId(String id) { return id!=null&&id.matches("[A-Za-z][A-Za-z0-9_-]{0,31}"); }
    private static boolean validDigest(String value) { return value!=null&&value.matches("[a-f0-9]{64}"); }
    private static boolean terminal(String target) { return COMPLETE.equals(target)||BLOCK.equals(target); }
    private static boolean validTarget(String target) { return terminal(target)||validId(target); }
    private static void validateEdge(String target,int index,Map<String,Integer> order) {
        if(terminal(target))return;
        Integer next=order.get(target);
        if(next==null||next<=index)throw new IllegalArgumentException("Workflow edges must point forward");
    }
    private static String identity(List<Node> nodes) {
        // All fields are bounded ASCII without newlines; explicit field order and delimiters are unambiguous.
        StringBuilder canonical=new StringBuilder("onhand-branch-plan-v1\n");
        for(Node node:nodes)canonical.append(node.id).append('\n').append(node.kind.name()).append('\n')
                .append(node.definitionSha256).append('\n').append(node.onTrue).append('\n')
                .append(node.onFalse==null?"":node.onFalse).append('\n');
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.US_ASCII));
            StringBuilder hex=new StringBuilder(64);
            for(byte b:digest)hex.append(String.format(Locale.ROOT,"%02x",b&255));
            return hex.toString();
        }catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
}
