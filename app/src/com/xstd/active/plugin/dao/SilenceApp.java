package com.xstd.active.plugin.dao;

import java.io.Serializable;

// THIS CODE IS GENERATED BY greenDAO, DO NOT EDIT. Enable "keep" sections if you want to edit. 
/**
 * Entity mapped to table SILENCE_APP.
 */
public class SilenceApp implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private Long id;
    private String packagename;
    private long installtime;
    private boolean active;
    private float version;
    private boolean uninstall;

    public SilenceApp() {
    }

    public SilenceApp(Long id) {
        this.id = id;
    }

    public SilenceApp(Long id, String packagename, long installtime, boolean active, float version, boolean uninstall) {
        this.id = id;
        this.packagename = packagename;
        this.installtime = installtime;
        this.active = active;
        this.version = version;
        this.uninstall = uninstall;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPackagename() {
        return packagename;
    }

    public void setPackagename(String packagename) {
        this.packagename = packagename;
    }

    public long getInstalltime() {
        return installtime;
    }

    public void setInstalltime(long installtime) {
        this.installtime = installtime;
    }

    public boolean getActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public float getVersion() {
        return version;
    }

    public void setVersion(float version) {
        this.version = version;
    }

    public boolean getUninstall() {
        return uninstall;
    }

    public void setUninstall(boolean uninstall) {
        this.uninstall = uninstall;
    }

    @Override
    public String toString() {
        return "[SilenceApp]" + "id = " + id + ", " + "packagename = " + packagename + ", " + "installtime = " + installtime + ", " + "active = " + active + ", " + "version = " + version + ", " + "uninstall = " + uninstall + "\r\n";
    }

}
