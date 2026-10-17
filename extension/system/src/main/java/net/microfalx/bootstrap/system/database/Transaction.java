package net.microfalx.bootstrap.system.database;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.microfalx.bootstrap.dataset.annotation.Component;
import net.microfalx.bootstrap.dataset.annotation.Filterable;
import net.microfalx.bootstrap.dataset.annotation.OrderBy;
import net.microfalx.bootstrap.dataset.model.IdentityAware;
import net.microfalx.lang.annotation.*;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.time.ZonedDateTime;

@Getter
@Setter
@ToString
@Name("Transactions")
@ReadOnly
public class Transaction extends IdentityAware<String> {

    @Position(1)
    @Label(value = "Name", group = "Database")
    @Description("The name of the schema (database)")
    private Database database;

    @Position(2)
    @Label(value = "Type", group = "Database")
    @Description("The type of database")
    private net.microfalx.database.Database.Type databaseType;

    @Position(3)
    @Label(value = "Node", group = "Database")
    @Description("The database node which runs this session")
    private Node node;

    @Position(10)
    @Description("The state of the database transaction")
    private net.microfalx.database.Transaction.State state;

    @Position(12)
    @Description("The time since the transaction was started")
    @OrderBy(OrderBy.Direction.DESC)
    @Filterable
    private Duration elapsed;

    @Position(20)
    @Description("The statement executed by the transaction")
    @Filterable
    @Name
    @Component(Component.Type.TEXT_AREA)
    private String statement;

    @Position(25)
    @Description("The state/status about what the transaction is doing")
    private String operation;

    @Position(30)
    @Description("The transaction weight, based on the number of locked rows and the number of altered rows")
    private Integer weight;

    @Position(31)
    @Description("The number of tables currently being used for processing the current SQL statement")
    @Label(value = "In Use", group = "Tables")
    private int tablesInUseCount;

    @Position(32)
    @Description("The number of tables that have row locks held by the current SQL statement")
    @Label(value = "Locked", group = "Tables")
    private int tablesLockedCount;

    @Position(33)
    @Label(value = "Modified", group = "Rows")
    @Description("The number of rows added or changed in the current transaction")
    private int modifiedRowCount;

    @Position(34)
    @Label(value = "Locked", group = "Rows")
    @Description("The number of rows the current transaction has locked.<br>" +
            "This is an approximation, and may include rows not visible to the current transaction that are delete-marked but physically present.")
    private int lockedRowCount;

    @Position(40)
    @Description("The isolation level of the current transaction")
    @Label("Isolation Level")
    private net.microfalx.database.Transaction.IsolationLevel isolationLevel;

    @Position(100)
    @Description("The timestamp when the database transaction was started")
    @Visible
    private ZonedDateTime startedAt;

    public static Transaction from(net.microfalx.database.Transaction transaction) {
        if (transaction == null) return null;
        Transaction model = new Transaction();
        model.setId(transaction.getId());
        model.setNode(Node.from(transaction.getNode()));
        model.setDatabase(Database.from(transaction.getNode().getDatabase()));
        model.setDatabaseType(transaction.getNode().getDatabase().getType());
        model.setStartedAt(transaction.getStartedAt());
        model.setElapsed(transaction.getStartedAt() != null ? Duration.between(transaction.getStartedAt(), ZonedDateTime.now()) : Duration.ZERO);
        model.setState(transaction.getState());
        model.setOperation(transaction.getOperation());
        if (transaction.getStatement() != null) {
            model.setStatement(StringUtils.abbreviate(transaction.getStatement().getContent(), 90));
        }
        model.setTablesInUseCount(transaction.getTablesInUseCount());
        model.setTablesLockedCount(transaction.getTablesLockedCount());
        model.setModifiedRowCount(transaction.getModifiedRowCount());
        model.setLockedRowCount(transaction.getLockedRowCount());
        model.setIsolationLevel(transaction.getIsolationLevel());
        model.setWeight(transaction.getWeight());
        return model;
    }
}
