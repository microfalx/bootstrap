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
@Name("Sessions")
@ReadOnly
public class Session extends IdentityAware<String> {

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
    @Description("The user name used to create the session")
    private String userName;

    @Position(11)
    @Description("The hostname of the client for the session")
    private String hostname;

    @Position(20)
    @Description("The state of the database session")
    private net.microfalx.database.Session.State state;

    @Position(30)
    @Description("The time since the session change its state")
    @OrderBy(OrderBy.Direction.DESC)
    @Filterable
    private Duration elapsed;

    @Position(40)
    @Description("The statement executed by the session")
    @Filterable
    @Name
    @Component(Component.Type.TEXT_AREA)
    private String statement;

    @Position(41)
    @Description("An additional information about session (state/operations/etc.)")
    @Filterable
    private String info;

    @Position(100)
    @Description("The timestamp when the database session was created")
    @Visible
    private ZonedDateTime createdAt;

    public static Session from(net.microfalx.database.Session session) {
        if (session == null) return null;
        Session model = new Session();
        model.setId(session.getId());
        model.setNode(Node.from(session.getNode()));
        model.setDatabase(Database.from(session.getNode().getDatabase()));
        model.setDatabaseType(session.getNode().getDatabase().getType());
        model.setUserName(session.getUserName());
        model.setHostname(session.getClientHostname());
        model.setElapsed(session.getElapsed());
        model.setState(session.getState());
        if (session.getStatement() != null) {
            model.setStatement(StringUtils.abbreviate(session.getStatement().getContent(), 90));
        }
        model.setInfo(session.getInfo());
        model.setCreatedAt(session.getCreatedAt());
        return model;
    }
}
