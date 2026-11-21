package com.ffpanel;

import java.io.*;
import java.net.*;

public class FridaBridge {
    private static final String HOST = "127.0.0.1";
    private static final int    PORT = 27042;
    private String sessionId = null;
    private String scriptId  = null;

    public boolean connect() {
        try {
            String resp = httpGet("/sessions");
            if (resp == null) return false;
            int idx = resp.indexOf("\"id\":\"");
            if (idx == -1) return false;
            int start = idx + 6, end = resp.indexOf("\"", start);
            sessionId = resp.substring(start, end);
            return true;
        } catch (Exception e) { return false; }
    }

    public boolean injectScript() {
        if (sessionId == null) return false;
        try {
            String script = buildESPScript();
            String body = "{\"source\":\"" + escapeJson(script) + "\"}";
            String resp = httpPost("/sessions/" + sessionId + "/scripts", body);
            if (resp == null) return false;
            int idx = resp.indexOf("\"id\":\"");
            if (idx == -1) return false;
            int start = idx + 6, end = resp.indexOf("\"", start);
            scriptId = resp.substring(start, end);
            httpPost("/sessions/" + sessionId + "/scripts/" + scriptId + "/load", "{}");
            return true;
        } catch (Exception e) { return false; }
    }

    public String callScript(String funcName) {
        if (sessionId == null || scriptId == null) return null;
        try {
            String body = "{\"name\":\"" + funcName + "\",\"args\":[]}";
            return httpPost("/sessions/" + sessionId + "/scripts/" + scriptId + "/exports/" + funcName, body);
        } catch (Exception e) { return null; }
    }

    private String buildESPScript() {
        return
        "var BASE=Module.findBaseAddress('libil2cpp.so');" +
        "var OFF_LIST=0x04A3F210,OFF_COUNT=0x04A3F214,OFF_LOCAL=0x04A3F200;" +
        "var OFF_X=0x128,OFF_Y=0x12C,OFF_Z=0x130,OFF_TEAM=0x1A8,OFF_ALIVE=0x158,OFF_NAME=0x260;" +
        "function rF(a){return Memory.readFloat(ptr(a));}" +
        "function rI(a){return Memory.readS32(ptr(a));}" +
        "function rP(a){return Memory.readPointer(ptr(a));}" +
        "function rS(a){try{return Memory.readUtf8String(ptr(a),32);}catch(e){return '';}}" +
        "rpc.exports={" +
        "getPlayers:function(){" +
        "var lBase=rP(BASE.add(OFF_LOCAL));" +
        "var local={x:rF(lBase.add(OFF_X)),y:rF(lBase.add(OFF_Y)),z:rF(lBase.add(OFF_Z)),team:rI(lBase.add(OFF_TEAM))};" +
        "var eList=rP(BASE.add(OFF_LIST));" +
        "var count=rI(BASE.add(OFF_COUNT));" +
        "var players=[];" +
        "if(count>0&&count<60){" +
        "for(var i=0;i<count;i++){" +
        "var pb=rP(eList.add(i*8));" +
        "if(pb.isNull())continue;" +
        "if(!rI(pb.add(OFF_ALIVE)))continue;" +
        "players.push({name:rS(pb.add(OFF_NAME)),x:rF(pb.add(OFF_X)),y:rF(pb.add(OFF_Y)),z:rF(pb.add(OFF_Z)),team:rI(pb.add(OFF_TEAM))});" +
        "}}" +
        "return JSON.stringify({local:local,players:players});}" +
        "};";
    }

    private String httpGet(String path) throws Exception {
        URL url = new URL("http://" + HOST + ":" + PORT + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("GET"); c.setConnectTimeout(2000);
        return readResponse(c);
    }

    private String httpPost(String path, String body) throws Exception {
        URL url = new URL("http://" + HOST + ":" + PORT + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json"); c.setConnectTimeout(2000);
        c.getOutputStream().write(body.getBytes());
        return readResponse(c);
    }

    private String readResponse(HttpURLConnection c) throws Exception {
        InputStream is; try { is=c.getInputStream(); } catch(Exception e){ is=c.getErrorStream(); }
        if(is==null) return null;
        BufferedReader br=new BufferedReader(new InputStreamReader(is));
        StringBuilder sb=new StringBuilder(); String line;
        while((line=br.readLine())!=null) sb.append(line);
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");
    }
}
