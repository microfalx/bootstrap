package net.microfalx.bootstrap.jdbc.entity.surrogate;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.microfalx.lang.Identifiable;
import net.microfalx.lang.annotation.Position;
import net.microfalx.lang.annotation.Visible;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * A base class for all entities which can be identified (which is all) and the primary key is a surrogate key.
 */
@MappedSuperclass
@ToString
@Getter
@Setter
public abstract class IdentityAware<T extends Number & Serializable> implements Identifiable<T>, Serializable {

    @Serial
    private static final long serialVersionUID = 1023653519669708398L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @Position(1)
    @Visible(false)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T id;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdentityAware<?> instance)) return false;
        return Objects.equals(id, instance.id);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id);
    }
}
