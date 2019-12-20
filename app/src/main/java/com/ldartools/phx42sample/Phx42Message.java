package com.ldartools.phx42sample;

import java.util.Dictionary;
import java.util.HashMap;

public class Phx42Message {
    private final String hostToUnit = "ZUzu";
    private final String unitToHost = "YTyt";
    private final String endOfMessage = "\r\n";

    private boolean isFromPhx42 = false;
    private String type = null;
    private String extra = null;
    private HashMap<String, String> parameters = new HashMap<String, String>();

    private Phx42Message(){}

    public String getType(){
        return type;
    }

    public void addParameter(String name, String value){
        parameters.put(name, value);
    }

    public boolean hasParameter(String name){
        return parameters.containsKey(name);
    }

    public String getParameterValue(String name){
        return parameters.get(name);
    }

    public String getExtra(){
        return extra;
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();

        if (isFromPhx42){
            sb.append(unitToHost);
        }else{
            sb.append(hostToUnit);
        }

        sb.append(" " + type);

        if (!parameters.isEmpty()){
            sb.append(" ");

            for (String name : parameters.keySet()) {
                sb.append(name + "=" + parameters.get(name));
            }
        }

        if (extra != null){
            sb.append(" " + extra);
        }

        sb.append(endOfMessage);

        return sb.toString();
    }

    public static Phx42Message parseMessageFromPhx42(String message) throws Exception{
        if (message == null){
            throw new Exception("Invalid message! Message is null");
        }

        Phx42Message m = new Phx42Message();

        String[] parts = message.split(" ");

        if (parts.length < 2){
            throw new Exception("Invalid message! Format should be 'YTyt <type> <optional parameters (NAME=VALUE) comma delimited> <optional extra string>'");
        }

        if (parts[0].equalsIgnoreCase("YTyt")){
            m.isFromPhx42 = true;
        }

        m.type = parts[1];

        if (parts.length == 2) return m;

        if (parts[2].contains("=")) { // has parameters
            String[] params = parts[2].split(",");

            for(String param : params){
                String[] pieces = param.split("=");

                if (pieces.length != 2){
                    throw new Exception("Invalid parameter! Parameter format should be 'NAME1=VALUE1,NAME2=VALUE2,...'");
                }

                m.addParameter(pieces[0], pieces[1]);
            }
        } else {
            m.extra = parts[2];
            return m;
        }

        if (parts.length == 4){
            m.extra = parts[3];
        }

        return m;
    }

    public static Phx42Message createMessageToSendToPhx42(String type, HashMap<String, String> parameters, String extra){
        Phx42Message m = new Phx42Message();

        m.isFromPhx42 = false;
        m.type = type;

        if (parameters != null){
            m.parameters = parameters;
        }

        m.extra = extra;

        return m;
    }
}
