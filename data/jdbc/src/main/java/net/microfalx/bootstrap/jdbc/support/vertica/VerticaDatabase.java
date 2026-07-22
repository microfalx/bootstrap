package net.microfalx.bootstrap.jdbc.support.vertica;

import net.microfalx.bootstrap.core.utils.HostnameUtils;
import net.microfalx.bootstrap.jdbc.support.*;
import net.microfalx.metrics.statistics.SimpleStatisticalSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static net.microfalx.lang.EnumUtils.fromName;
import static net.microfalx.lang.StringUtils.isNotEmpty;
import static net.microfalx.lang.TimeUtils.*;

public class VerticaDatabase extends AbstractDatabase {

    private static final Logger LOGGER = LoggerFactory.getLogger(VerticaDatabase.class);

    private volatile Collection<Node> cachedNodes = Collections.emptyList();

    public VerticaDatabase(DataSource dataSource) {
        super(dataSource);
    }

    public VerticaDatabase(DatabaseService databaseService, String id, String name, DataSource dataSource) {
        super(databaseService, id, name, dataSource);
    }

    @Override
    public Type getType() {
        return Type.VERTICA;
    }

    @Override
    public Schema getSchema() {
        return new VerticaSchema(this);
    }

    @Override
    protected String doGetSchemaName() {
        return getClient().sql(GET_SCHEMA_SQL).query(String.class).single();
    }

    @Override
    protected Collection<Node> extractNodes() throws SQLException {
        JdbcTemplate template = new JdbcTemplate(getDataSource().unwrap());
        cachedNodes = template.query(GET_NODES_SQL, rs -> {
            Collection<Node> nodes = new ArrayList<>();
            while (rs.next()) {
                String id = rs.getString("node_id");
                String name = rs.getString("node_name");
                String displayName = name;
                String state = rs.getString("node_state");
                String host = rs.getString("export_address");
                if (isLocalHost(host)) host = getDataSource().getHostname();
                if (HostnameUtils.isHostname(host)) {
                    displayName = HostnameUtils.getServerNameFromHost(host);
                } else {
                    displayName = HostnameUtils.getServerNameFromHost(name);
                }
                Node.State stateEnum = fromName(Node.State.class, state, Node.State.UNKNOWN);
                int port = getPortFromHostAndPort(host);
                host = getHostFromHostAndPort(host);
                VerticaNode node = createNode(id, name, host, port);
                node.setDisplayName(displayName);
                node.setState(stateEnum);
                nodes.add(node);
            }
            return nodes;
        });
        return cachedNodes;
    }

    @Override
    protected Collection<Session> extractSessions() throws SQLException {
        JdbcTemplate template = new JdbcTemplate(getDataSource().unwrap());
        return template.query(GET_SESSIONS_SQL, rs -> {
            Collection<Session> sessions = new ArrayList<>();
            while (rs.next()) {
                String sessionId = rs.getString("session_id");
                String nodeName = rs.getString("node_name");
                Node node = getNodeByName(nodeName);
                if (node == null) {
                    LOGGER.error("Cannot find a node with name " + nodeName);
                    continue;
                }
                VerticaSession session = new VerticaSession(node, sessionId);
                session.setUserName(rs.getString("user_name"));
                session.setSchema(session.getUserName());
                session.setClientHostname(getHostFromHostAndPort(rs.getString("client_hostname")));
                session.setState(getState(rs));
                Timestamp statementStart = rs.getTimestamp("statement_start");
                session.setStartedAt(statementStart != null ? toZonedDateTime(statementStart).withZoneSameInstant(getZoneId()) : null);
                session.setStatement(createStatement(this, rs.getString("current_statement"), session.getUserName()));
                session.setCreatedAt(toZonedDateTime(rs.getTimestamp("login_timestamp")));
                sessions.add(session);
            }
            return sessions;
        });
    }

    @Override
    protected Collection<Transaction> extractTransactions() throws SQLException {
        JdbcTemplate template = new JdbcTemplate(getDataSource().unwrap());
        return template.query(GET_TRANSACTIONS_SQL, rs -> {
            Collection<Transaction> transactions = new ArrayList<>();
            while (rs.next()) {
                String transactionId = rs.getString("transaction_id");
                String nodeName = rs.getString("node_name");
                Node node = getNodeByName(nodeName);
                if (node == null) {
                    LOGGER.error("Cannot find a node with name " + nodeName);
                    continue;
                }
                VerticaTransaction transaction = new VerticaTransaction(node, transactionId);
                //transaction.setState(getState(rs));
                String userName = rs.getString("user_name");
                String currentStatement = rs.getString("description");
                transaction.setStatement(createStatement(this, currentStatement, userName));
                transaction.setIsolationLevel(fromName(Transaction.IsolationLevel.class, rs.getString("isolation"), Transaction.IsolationLevel.READ_COMMITTED));
                transaction.setReadOnly(rs.getInt("is_read_only") != 0);
                transaction.setStartedAt(toZonedDateTime(rs.getTimestamp("start_timestamp")));
                transactions.add(transaction);
            }
            return transactions;
        });
    }

    @Override
    protected Collection<Statement> extractStatements(LocalDateTime start, LocalDateTime end) {
        JdbcTemplate template = new JdbcTemplate(getDataSource().unwrap());
        return template.query(GET_STATEMENTS_SQL, rs -> {
            Collection<Statement> statements = new ArrayList<>();
            while (rs.next()) {
                String request = rs.getString("request");
                String userName = rs.getString("user_name");
                Statement statement = Statement.create(this, request, userName)
                        .withExecutionTime(toZonedDateTimeSameInstant(rs.getTimestamp("max_start_timestamp"), getZoneId()));
                SimpleStatisticalSummary statisticalSummary = new SimpleStatisticalSummary();
                statisticalSummary.setN(rs.getInt("request_cnt"))
                        .setSum(rs.getLong("total_request_duration_ms"))
                        .setMin(rs.getFloat("min_request_duration_ms"))
                        .setMax(rs.getFloat("max_request_duration_ms"));
                statements.add(statement.withStatistics(statisticalSummary));
            }
            return statements;
        }, toTimestamp(start), toTimestamp(end));
    }

    private VerticaNode createNode(String id, String name, DataSource dataSource) {
        return new VerticaNode(this, id, name, dataSource);
    }

    private VerticaNode createNode(String id, String name, String hostName, int port) {
        DataSource dataSource = createDataSource(hostName, port);
        return createNode(id, name, dataSource);
    }

    private Session.State getState(ResultSet rs) throws SQLException {
        String currentStatement = rs.getString("current_statement");
        String transactionId = rs.getString("transaction_id");
        boolean hasValidTransactionId = isNotEmpty(currentStatement) && isNotEmpty(transactionId) && !"-1".equals(transactionId) && !"0".equals(transactionId);
        return hasValidTransactionId ? Session.State.ACTIVE : Session.State.INACTIVE;
    }

    private Node getNodeByName(String nodeName) {
        Collection<Node> nodes = cachedNodes.isEmpty() ? getNodes() : cachedNodes;
        return nodes.stream().filter(node -> node.getName().equals(nodeName)).findFirst().orElse(null);
    }

    private static final String GET_SCHEMA_SQL = "SELECT CURRENT_SCHEMA()";
    private static final String GET_NODES_SQL = "select * from v_catalog.nodes order by node_name";
    private static final String GET_SESSIONS_SQL = "select * from v_monitor.sessions where CURRENT_SESSION() <> session_id";
    private static final String GET_TRANSACTIONS_SQL = "select t.* from v_monitor.sessions s\n" +
            "  join v_monitor.transactions t on s.transaction_id = t.transaction_id";
    private static final String GET_SESSION_STATES_SQL = "select sum(CASE WHEN transaction_id::integer > 0 THEN 1 ELSE 0 END) as active, \n" +
            "        sum(CASE WHEN transaction_id::integer <= 0 THEN 1 ELSE 0 END) as inactive, \n" +
            "        sum(CASE WHEN lock_mode is not null THEN 1 ELSE 0 END) as blocked from (\n" +
            "        select s.transaction_id, l.lock_mode from v_monitor.sessions s left join v_monitor.locks l on s.transaction_id = l.transaction_id\n" +
            ") as t";
    private static final String GET_RESOURCE_QUEUES = "select * from v_monitor.resource_queues";
    private static final String GET_STATEMENTS_SQL = "select request, user_name, count(*) as request_cnt," +
            "\n  sum(request_duration_ms) as total_request_duration_ms, min(request_duration_ms) as min_request_duration_ms," +
            "\n  max(request_duration_ms) as max_request_duration_ms," +
            "\n  max(start_timestamp) as max_start_timestamp" +
            "\nfrom v_monitor.query_requests" +
            "\nwhere start_timestamp between ? and ?" +
            "\ngroup by request, user_name";
}
