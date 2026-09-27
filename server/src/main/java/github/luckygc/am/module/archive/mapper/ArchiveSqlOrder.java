package github.luckygc.am.module.archive.mapper;

public record ArchiveSqlOrder(String expression, Direction direction) {

    public String sql() {
        return expression + " " + direction.name().toLowerCase();
    }

    public String directionName() {
        return direction.name();
    }

    public enum Direction {
        ASC,
        DESC
    }
}
