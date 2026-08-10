package github.luckygc.am.test;

import java.time.LocalDateTime;

import org.springframework.jdbc.core.JdbcTemplate;

import github.luckygc.am.module.archive.metadata.ArchiveFondsStatus;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsDto;

public final class ArchiveTestFixtures {

    private ArchiveTestFixtures() {}

    public static ArchiveFondsDto activeFondsDto(
            Long id, String fondsCode, String fondsName, LocalDateTime now) {
        return new ArchiveFondsDto(
                id,
                fondsCode,
                fondsCode,
                fondsName,
                ArchiveFondsStatus.ACTIVE,
                null,
                now,
                null,
                null,
                null,
                null,
                null,
                0,
                now,
                now);
    }

    public static void insertActiveFonds(
            JdbcTemplate jdbcTemplate, String fondsCode, String fondsName) {
        jdbcTemplate.update(
                "insert into am_archive_fonds (fonds_code, fonds_name) values (?, ?) "
                        + "on conflict (fonds_code) do nothing",
                fondsCode,
                fondsName);
    }
}
