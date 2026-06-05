package com.ls.agent.core.support.persistence;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(String[].class)
@MappedJdbcTypes(JdbcType.ARRAY)
public class StringArrayTypeHandler extends BaseTypeHandler<String[]> {

    private static final String POSTGRES_TEXT_ARRAY_TYPE = "text";

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String[] parameter, JdbcType jdbcType)
            throws SQLException {
        Array array = ps.getConnection().createArrayOf(POSTGRES_TEXT_ARRAY_TYPE, parameter);
        ps.setArray(i, array);
    }

    @Override
    public String[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toStringArray(rs.getArray(columnName));
    }

    @Override
    public String[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toStringArray(rs.getArray(columnIndex));
    }

    @Override
    public String[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toStringArray(cs.getArray(columnIndex));
    }

    private String[] toStringArray(Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        Object value = array.getArray();
        if (value instanceof String[] strings) {
            return strings;
        }
        if (value instanceof Object[] objects) {
            String[] result = new String[objects.length];
            for (int i = 0; i < objects.length; i++) {
                result[i] = objects[i] == null ? null : objects[i].toString();
            }
            return result;
        }
        return new String[0];
    }
}
