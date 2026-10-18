package net.microfalx.bootstrap.jdbc.entity.surrogate;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.microfalx.bootstrap.dataset.annotation.Component;
import net.microfalx.bootstrap.dataset.annotation.Filterable;
import net.microfalx.lang.Nameable;
import net.microfalx.lang.annotation.*;

import java.io.Serial;
import java.io.Serializable;

/**
 * A base class for all entities which can be named and have an optional description.
 */
@MappedSuperclass
@ToString(callSuper = true)
@Getter
@Setter
public abstract class NamedIdentityAware<T extends Number & Serializable> extends IdentityAware<T> implements Nameable {

    @Serial
    private static final long serialVersionUID = -720697127573911840L;

    @Column(name = "name", nullable = false)
    @NotBlank
    @Name
    @Position(5)
    @Description("A name for a {name}")
    @Width("200px")
    private String name;

    @Column(name = "description")
    @Position(1000)
    @Component(Component.Type.TEXT_AREA)
    @Description("A description for a {name}")
    @Width("300px")
    @Filterable()
    @Visible(modes = {Visible.Mode.VIEW, Visible.Mode.EDIT, Visible.Mode.ADD})
    private String description;


}
