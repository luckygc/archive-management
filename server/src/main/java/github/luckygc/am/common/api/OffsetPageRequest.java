package github.luckygc.am.common.api;

public record OffsetPageRequest(int pageSize, int pageNo) {

    public long offset() {
        return (long) (pageNo - 1) * pageSize;
    }
}
