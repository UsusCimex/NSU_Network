package ru.nsu;

public class DnsRequest {
    public String getDomain() {
        return domain;
    }

    public ConnectionInfo getConnectionInfo() {
        return connectionInfo;
    }

    public long getSendTime() {
        return sendTime;
    }

    public void setSendTime(long sendTime) {
        this.sendTime = sendTime;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    private final String domain;
    private final ConnectionInfo connectionInfo;
    private long sendTime;
    private int retryCount;

    public DnsRequest(String domain, ConnectionInfo connectionInfo, long sendTime, int retryCount) {
        this.domain = domain;
        this.connectionInfo = connectionInfo;
        this.sendTime = sendTime;
        this.retryCount = retryCount;
    }
}