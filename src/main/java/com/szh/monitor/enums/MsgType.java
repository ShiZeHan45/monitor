package com.szh.monitor.enums;

public enum MsgType {
    NORMAL("正常","✅"),
    ERROR("异常","⚠️");

    private String label;
    private String icon;

    MsgType(String label,String icon) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }
}
