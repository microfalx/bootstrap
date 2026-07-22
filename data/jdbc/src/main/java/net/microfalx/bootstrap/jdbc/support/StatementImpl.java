package net.microfalx.bootstrap.jdbc.support;

import net.microfalx.lang.Hashing;
import net.microfalx.lang.StringUtils;
import net.microfalx.metrics.statistics.SimpleStatisticalSummary;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.SetStatement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.view.AlterView;
import net.sf.jsqlparser.statement.create.view.CreateView;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.merge.Merge;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.upsert.Upsert;
import org.apache.commons.math3.stat.descriptive.StatisticalSummary;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

import static net.microfalx.bootstrap.jdbc.support.DatabaseUtils.cleanupStatement;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;
import static net.microfalx.lang.ExceptionUtils.rethrowExceptionAndReturn;
import static net.microfalx.lang.StringUtils.defaultIfEmpty;
import static net.microfalx.lang.StringUtils.split;

public class StatementImpl implements Statement, Cloneable {

    transient Node node;
    String nodeId;
    private final String id;
    private final String content;
    private String userName;
    private Type type = Type.OTHER;
    private boolean parsed;
    private ZonedDateTime executionTime = ZonedDateTime.now();
    private Statistics statistics = new StatisticsImpl();

    StatementImpl(Node node, String content, String userName) {
        requireNonNull(node);
        requireNotEmpty(content);
        this.node = node;
        this.nodeId = node.getId();
        this.userName = defaultIfEmpty(userName, "anonymous");
        this.content = cleanupStatement(content);
        this.id = calculateId();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Node getNode() {
        return node;
    }

    @Override
    public String getUserName() {
        return userName;
    }

    @Override
    public Type getType() {
        parse();
        return type;
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public ZonedDateTime getExecutionTime() {
        return executionTime;
    }

    @Override
    public Statistics getStatistics() {
        return statistics;
    }

    @Override
    public Statement withUserName(String userName) {
        requireNonNull(userName);
        StatementImpl copy = copy();
        copy.userName = userName;
        return copy;
    }

    @Override
    public Statement withExecutionTime(ZonedDateTime executionTime) {
        requireNonNull(executionTime);
        StatementImpl copy = copy();
        copy.executionTime = executionTime;
        return copy;
    }

    @Override
    public Statement withStatistics(Statistics statistics) {
        requireNonNull(statistics);
        StatementImpl copy = copy();
        copy.statistics = new StatisticsImpl(statistics);
        return copy;
    }

    @Override
    public Statement withStatistics(StatisticalSummary statisticalSummary) {
        requireNonNull(statisticalSummary);
        StatementImpl copy = copy();
        copy.statistics = new StatisticsImpl(statisticalSummary);
        return copy;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", StatementImpl.class.getSimpleName() + "[", "]")
                .add("id='" + id + "'")
                .add("content='" + content + "'")
                .add("type=" + type)
                .add("parsed=" + parsed)
                .add("executionTime=" + executionTime)
                .add("statistics=" + statistics)
                .toString();
    }

    private String calculateId() {
        return Hashing.hash(content);
    }

    private void parse() {
        if (parsed) return;
        try {
            net.sf.jsqlparser.statement.Statement statement = CCJSqlParserUtil.parse(getContent());
            type = extractType(statement);
        } catch (JSQLParserException e) {
            type = extractNonStandardTypes();
        } finally {
            parsed = true;
        }
    }

    private Type extractType(net.sf.jsqlparser.statement.Statement statement) {
        Type type;
        if (statement instanceof Select) {
            type = Type.SELECT;
        } else if (statement instanceof Insert) {
            type = Type.INSERT;
        } else if (statement instanceof Update) {
            type = Type.UPDATE;
        } else if (statement instanceof Delete) {
            type = Type.DELETE;
        } else if (statement instanceof Merge || statement instanceof Upsert) {
            type = Type.MERGE;
        } else if (statement instanceof CreateTable || statement instanceof CreateView || statement instanceof CreateIndex) {
            type = Type.CREATE;
        } else if (statement instanceof Alter || statement instanceof AlterView) {
            type = Type.ALTER;
        } else if (statement instanceof Drop) {
            type = Type.DROP;
        } else if (statement instanceof Truncate) {
            type = Type.TRUNCATE;
        } else if (statement instanceof SetStatement) {
            type = Type.SET;
        } else {
            type = extractNonStandardTypes();
        }
        Type extensionType = extractExtensionTypes(type);
        if (extensionType != Type.OTHER) return extensionType;
        return type;
    }

    private Type extractExtensionTypes(Type type) {
        String contentNormalized = normalizeContent();
        String[] parts = StringUtils.split(contentNormalized, " ()", true);
        if (parts.length > 0) {
            for (int index = 0; index < 5; index++) {
                Type extensionType = EXTENSIONS_SELECT.get(parts[0]);
                if (extensionType != null) return extensionType;
            }
        }
        return Type.OTHER;
    }

    private Type extractNonStandardTypes() {
        String contentNormalized = normalizeContent();
        String[] parts = split(contentNormalized, " ()", true);
        if (parts.length > 0) {
            Type type = TYPES.get(parts[0]);
            if (type != null) return type;
        }
        return Type.OTHER;
    }

    private String normalizeContent() {
        String contentLowerCase = getContent().toLowerCase();
        return contentLowerCase.substring(0, Math.min(200, contentLowerCase.length() - 1));
    }

    private StatementImpl copy() {
        try {
            return (StatementImpl) clone();
        } catch (CloneNotSupportedException e) {
            return rethrowExceptionAndReturn(e);
        }
    }

    public static class StatisticsImpl extends SimpleStatisticalSummary implements Statistics {

        public StatisticsImpl() {
        }

        public StatisticsImpl(StatisticalSummary summary) {
            super(summary);
        }
    }

    private static final Map<String, Type> TYPES = new HashMap<>();
    private static final Map<String, Type> EXTENSIONS_SELECT = new HashMap<>();

    static {
        for (Type type : Type.values()) {
            TYPES.put(type.name().toLowerCase(), type);
        }
        TYPES.put("copy", Type.LOAD);
        TYPES.put("optimize", Type.OPTIMIZE);

        EXTENSIONS_SELECT.put("drop_partition", Type.DROP);
        EXTENSIONS_SELECT.put("do_tm_task", Type.OPTIMIZE);
    }


}
