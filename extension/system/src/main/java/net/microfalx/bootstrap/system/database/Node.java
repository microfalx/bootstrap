package net.microfalx.bootstrap.system.database;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.microfalx.bootstrap.dataset.Alert;
import net.microfalx.bootstrap.dataset.annotation.Formattable;
import net.microfalx.bootstrap.dataset.model.IdentityAware;
import net.microfalx.bootstrap.model.Field;
import net.microfalx.lang.annotation.*;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@Name("Nodes")
@ReadOnly
public class Node extends IdentityAware<String> {

    @NotBlank
    @Position(5)
    private String name;

    @Position(10)
    @Label(value = "Name", group = "Database")
    @Description("The database which owns this node")
    private Database database;

    @Position(11)
    @Label(value = "Type", group = "Database")
    @Description("The type of database")
    private net.microfalx.database.Database.Type databaseType;

    @Position(6)
    @Description("The hostname of the database node")
    private String hostname;

    @Position(7)
    @Formattable(prettyPrint = false)
    @Description("The port of the database service")
    private int port;

    @Position(12)
    @Description("The state of the database node")
    @Formattable(alert = AlertProvider.class)
    private net.microfalx.database.Node.State state;

    @Position(100)
    @Description("The timestamp when the database was started")
    private LocalDateTime startedAt;

    @Position(200)
    @Description("The description of the validation failure")
    @Visible(false)
    private String validationError;

    /**
     * Creates a model from a {@link net.microfalx.database.Node}.
     *
     * @param node the node
     * @return a non-null instance
     */
    public static Node from(net.microfalx.database.Node node) {
        if (node == null) return null;
        Node model = new Node();
        model.setId(node.getId());
        model.setDatabase(Database.from(node.getDatabase()));
        model.setDatabaseType(node.getDatabase().getType());
        model.setName(node.getName());
        model.setHostname(node.getDataSource().getHostname());
        model.setState(node.getState());
        model.setValidationError(node.getValidationError());
        model.setPort(node.getDataSource().getPort());
        model.setStartedAt(node.getStartedAt());
        return model;
    }

    public static class AlertProvider implements Formattable.AlertProvider<Node, Field<Node>, net.microfalx.database.Node.State> {

        @Override
        public Alert provide(net.microfalx.database.Node.State value, Field<Node> field, Node model) {
            Alert.Type type = switch (value) {
                case UP -> Alert.Type.SUCCESS;
                case DOWN -> Alert.Type.DANGER;
                case STANDBY, RECOVERING -> Alert.Type.SECONDARY;
                case UNKNOWN -> Alert.Type.LIGHT;
            };
            return Alert.builder().type(type).message(model.getValidationError()).build();
        }
    }

}
