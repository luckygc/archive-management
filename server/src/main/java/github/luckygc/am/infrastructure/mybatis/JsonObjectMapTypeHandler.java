package github.luckygc.am.infrastructure.mybatis;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

public class JsonObjectMapTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Override
    public void setNonNullParameter(
            PreparedStatement statement,
            int index,
            Map<String, Object> parameter,
            JdbcType jdbcType)
            throws SQLException {
        statement.setObject(index, write(parameter), Types.OTHER);
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet resultSet, String columnName)
            throws SQLException {
        return read(resultSet.getString(columnName));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet resultSet, int columnIndex)
            throws SQLException {
        return read(resultSet.getString(columnIndex));
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement statement, int columnIndex)
            throws SQLException {
        return read(statement.getString(columnIndex));
    }

    private Map<String, Object> read(String json) throws SQLException {
        if (json == null) return Map.of();
        try {
            return Collections.unmodifiableMap(
                    new LinkedHashMap<>(JSON_MAPPER.readValue(json, MAP_TYPE)));
        } catch (Exception exception) {
            throw new SQLException("无法读取 PostgreSQL JSONB 对象", exception);
        }
    }

    private String write(Map<String, Object> value) throws SQLException {
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (Exception exception) {
            throw new SQLException("无法写入 PostgreSQL JSONB 对象", exception);
        }
    }
}
