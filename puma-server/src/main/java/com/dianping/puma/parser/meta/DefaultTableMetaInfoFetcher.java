/**
 * Project: puma-server
 * <p/>
 * File Created at 2012-8-3
 * $Id$
 * <p/>
 * Copyright 2010 dianping.com.
 * All rights reserved.
 * <p/>
 * This software is the confidential and proprietary information of
 * Dianping Company. ("Confidential Information").  You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with dianping.com.
 */
package com.dianping.puma.parser.meta;

import com.dianping.cat.Cat;
import com.dianping.cat.message.Transaction;
import com.dianping.puma.biz.entity.SrcDbEntity;
import com.dianping.puma.core.meta.TableMetaInfo;
import com.dianping.puma.core.model.Table;
import com.dianping.puma.core.model.TableSet;
import com.dianping.puma.eventbus.DefaultEventBus;
import com.dianping.puma.taskexecutor.change.TargetChangedEvent;
import com.google.common.eventbus.Subscribe;
import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Leo Liang
 */
public class DefaultTableMetaInfoFetcher implements TableMetaInfoFetcher {

    private final Logger logger = LoggerFactory.getLogger(DefaultTableMetaInfoFetcher.class);

    private String name;

    private AtomicReference<Map<String, TableMetaInfo>> tableMetaInfoCache = new AtomicReference<Map<String, TableMetaInfo>>();

    private SrcDbEntity srcDbEntity;

    private MysqlDataSource metaDs;

    private TableSet acceptedTables;

    public DefaultTableMetaInfoFetcher() {
        DefaultEventBus.INSTANCE.register(this);
    }

    @Subscribe
    public void listenTargetChangedEvent(TargetChangedEvent event) {
        if (event.getTaskName().equals(name)) {
            acceptedTables = event.getTableSet();
            try {
                refreshTableMetas();
                logger.info("refresh table metas.");
                logger.info("table set = {}", acceptedTables.toString());
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public TableMetaInfo getTableMetaInfo(String database, String table) {
        return tableMetaInfoCache.get().get(database + "." + table);
    }

    @Override
    public void refreshTableMeta(final String databaseName, final String tableName) throws SQLException {
        Table table = new Table(databaseName, tableName);
        if (acceptedTables.contains(table)) {
            TableMetaInfo tableMetaInfo = _refreshTableMeta(databaseName, tableName);
            tableMetaInfoCache.get().put(databaseName + "." + tableName, tableMetaInfo);
        }
    }

    @Override
    public void refreshTableMetas() throws SQLException {
        Map<String, TableMetaInfo> tableMetaInfoMap = new HashMap<String, TableMetaInfo>();

        for (Table table : acceptedTables.listSchemaTables()) {
            String databaseName = table.getSchemaName();
            String tableName = table.getTableName();

            TableMetaInfo tableMetaInfo = _refreshTableMeta(databaseName, tableName);
            tableMetaInfoMap.put(databaseName + "." + tableName, tableMetaInfo);
        }

        tableMetaInfoCache.set(tableMetaInfoMap);
    }

    protected TableMetaInfo _refreshTableMeta(final String database, final String table) throws SQLException {
        initDsIfNeeded();

        QueryRunner runner = new QueryRunner(metaDs);

        Transaction t = Cat.newTransaction("SQL.meta", database + "." + table);
        try {
            TableMetaInfo tableMetaInfo = runner.query(genTableMetaSql(database, table),
                    new ResultSetHandler<TableMetaInfo>() {
                        @Override
                        public TableMetaInfo handle(ResultSet rs) throws SQLException {
                            TableMetaInfo result = new TableMetaInfo();
                            result.setDatabase(database);
                            result.setTable(table);
                            result.setColumns(new HashMap<Integer, String>());
                            result.setKeys(new ArrayList<String>());
                            result.setTypes(new HashMap<String, String>());
                            result.setSignedInfos(new HashMap<Integer, Boolean>());

                            while (rs.next()) {
                                int i = rs.getRow();
                                String column = rs.getString("Field");

                                result.getColumns().put(i, column);

                                if (rs.getString("Type").contains("unsigned")) {
                                    result.getSignedInfos().put(i, false);
                                } else {
                                    result.getSignedInfos().put(i, true);
                                }

                                if (rs.getString("Key").equalsIgnoreCase("pri")) {
                                    result.getKeys().add(column);
                                }
                            }

                            return result;
                        }
                    });

            t.setStatus("0");
            return tableMetaInfo;
        } catch (SQLException e) {
            t.setStatus("1");
            throw e;
        } finally {
            t.complete();
        }
    }

    private String genTableMetaSql(String database, String table) {
        return "desc " + database + "." + table;
    }

    protected void initDsIfNeeded() {
        if (metaDs == null) {
            metaDs = new MysqlDataSource();
            metaDs.setUrl("jdbc:mysql://" + srcDbEntity.getHost() + ":" + srcDbEntity.getPort()
                    + "?connectTimeout=5000&socketTimeout=10000");
            metaDs.setUser(srcDbEntity.getUsername());
            metaDs.setPassword(srcDbEntity.getPassword());
        }
    }

    public void setSrcDbEntity(SrcDbEntity srcDbEntity) {
        this.srcDbEntity = srcDbEntity;
        this.metaDs = null;
    }

    public void setAcceptedTables(TableSet acceptedTables) {
        this.acceptedTables = acceptedTables;
    }

    public void setName(String name) {
        this.name = name;
    }
}