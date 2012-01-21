package org.jenkinsci.plugins.scriptler.share;

public class DefaultScriptInfoList {
    public int version;
    public ScriptInfo[] list = new ScriptInfo[0];

    public boolean isEmpty() {
        return list == null ? true : list.length > 0;
    }

    public ScriptInfo[] getList() {
        return list;
    }

}
