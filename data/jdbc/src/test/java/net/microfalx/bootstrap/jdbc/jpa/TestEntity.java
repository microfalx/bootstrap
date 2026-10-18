package net.microfalx.bootstrap.jdbc.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.microfalx.bootstrap.jdbc.entity.surrogate.NamedAndTimestampedIdentityAware;
import org.hibernate.annotations.NaturalId;

import static net.microfalx.lang.ExceptionUtils.rethrowExceptionAndReturn;

@Entity
@Table(name = "test_entity")
@Getter
@Setter
@ToString
public class TestEntity extends NamedAndTimestampedIdentityAware<Integer> implements Cloneable {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NaturalId
    @Column(name = "naturalId", nullable = false)
    private String naturalId;

    public TestEntity copy() {
        try {
            return (TestEntity) clone();
        } catch (CloneNotSupportedException e) {
            return rethrowExceptionAndReturn(e);
        }
    }
}
