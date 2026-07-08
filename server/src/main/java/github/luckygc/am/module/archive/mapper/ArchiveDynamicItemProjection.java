package github.luckygc.am.module.archive.mapper;

import java.util.List;

public record ArchiveDynamicItemProjection(List<String> fields) {

    public ArchiveDynamicItemProjection {
        fields = List.copyOf(fields);
    }
}
