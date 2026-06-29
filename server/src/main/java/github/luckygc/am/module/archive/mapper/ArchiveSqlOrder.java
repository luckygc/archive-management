package github.luckygc.am.module.archive.mapper;

public record ArchiveSqlOrder(String expression, Direction direction) {

    public String sql() {
        return expression + " " + direction.name().toLowerCase();
    }

    public enum Direction {
        ASC,
        DESC
    }
}
