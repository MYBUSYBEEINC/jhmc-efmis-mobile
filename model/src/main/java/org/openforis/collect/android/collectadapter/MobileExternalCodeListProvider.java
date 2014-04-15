package org.openforis.collect.android.collectadapter;

import org.openforis.collect.android.util.persistence.ConnectionCallback;
import org.openforis.collect.android.util.persistence.Database;
import org.openforis.collect.persistence.DatabaseExternalCodeListProvider;
import org.openforis.idm.metamodel.CodeList;
import org.openforis.idm.metamodel.ExternalCodeListItem;

import java.sql.*;
import java.util.*;

/**
 * @author Daniel Wiell
 */
public class MobileExternalCodeListProvider extends DatabaseExternalCodeListProvider {
    private final Database database;

    public MobileExternalCodeListProvider(Database database) {
        this.database = database;
    }

    public List<ExternalCodeListItem> getRootItems(final CodeList codeList) {
        return database.execute(new ConnectionCallback<List<ExternalCodeListItem>>() {
            public List<ExternalCodeListItem> execute(Connection connection) throws SQLException {
                String query = rootItemsQuery(codeList);
                PreparedStatement ps = connection.prepareStatement(query);
                ResultSet rs = ps.executeQuery();
                List<ExternalCodeListItem> items = new ArrayList<ExternalCodeListItem>();
                while (rs.next())
                    items.add(parseRow(toRow(rs), codeList, 1));
                rs.close();
                ps.close();
                return items;
            }
        });
    }

    private String rootItemsQuery(CodeList codeList) {
        String constraint = "1 = 1";
        int childLevel = 2; // Level is 1 based
        if (hasLevel(codeList, childLevel))
            constraint = levelName(codeList, childLevel) + " IS NULL";
        return "SELECT *\n" +
                "FROM " + codeList.getLookupTable() + "\n" +
                "WHERE " + constraint;
    }

    public List<ExternalCodeListItem> getChildItems(final ExternalCodeListItem parentItem) {
        final CodeList codeList = parentItem.getCodeList();
        if (codeList.getHierarchy().size() <= parentItem.getLevel())
            return Collections.emptyList();
        return database.execute(new ConnectionCallback<List<ExternalCodeListItem>>() {
            public List<ExternalCodeListItem> execute(Connection connection) throws SQLException {
                String query = childItemsQuery(parentItem);
                PreparedStatement ps = connection.prepareStatement(query);
                ps.setString(1, parentItem.getCode());
                ResultSet rs = ps.executeQuery();
                List<ExternalCodeListItem> items = new ArrayList<ExternalCodeListItem>();
                while (rs.next())
                    items.add(parseRow(toRow(rs), codeList, parentItem.getLevel() + 1));
                rs.close();
                ps.close();
                return items;
            }
        });
    }

    private String childItemsQuery(ExternalCodeListItem parentItem) {
        CodeList codeList = parentItem.getCodeList();
        String parentName = levelName(codeList, parentItem.getLevel());
        String childName = levelName(codeList, parentItem.getLevel() + 1);
        int grandChildLevel = parentItem.getLevel() + 2;
        String grandChildName = levelName(codeList, grandChildLevel);
        String constraint = parentName + " = ?\n" +
                "AND " + childName + " IS NOT NULL\n";
        if (hasLevel(codeList, grandChildLevel))
            constraint += "AND " + grandChildName + " IS NULL";
        return "SELECT *\n" +
                "FROM " + codeList.getLookupTable() + "\n" +
                "WHERE " + constraint;
    }

    private String levelName(CodeList codeList, int level) {
        if (level > codeList.getHierarchy().size())
            return "NULL";
        return codeList.getHierarchy().get(level - 1).getName();
    }

    private boolean hasLevel(CodeList codeList, int level) {
        return codeList.getHierarchy().size() >= level;
    }

    public String getCode(CodeList codeList, String s, Object... objects) {
        throw new UnsupportedOperationException("Not implemented - deprecated");
    }

    private Map<String, String> toRow(ResultSet rs) throws SQLException {
        HashMap<String, String> row = new HashMap<String, String>();
        ResultSetMetaData metaData = rs.getMetaData();
        for (int i = 1; i < metaData.getColumnCount(); i++)
            row.put(metaData.getColumnName(i).toLowerCase(), rs.getString(i));
        return row;
    }
}