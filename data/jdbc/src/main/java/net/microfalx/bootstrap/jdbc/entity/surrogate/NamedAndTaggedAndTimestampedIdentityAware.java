package net.microfalx.bootstrap.jdbc.entity.surrogate;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.microfalx.bootstrap.dataset.annotation.Filterable;
import net.microfalx.bootstrap.jdbc.jpa.TagsConverter;
import net.microfalx.lang.annotation.Description;
import net.microfalx.lang.annotation.Position;
import net.microfalx.lang.annotation.Width;

import java.io.Serializable;
import java.util.Set;

/**
 * A base class for all entities which can be named and tagged and have timestamps.
 */
@MappedSuperclass
@ToString(callSuper = true)
@Getter
@Setter
public abstract class NamedAndTaggedAndTimestampedIdentityAware<T extends Number & Serializable> extends NamedAndTimestampedIdentityAware<T> {

    @Column(name = "tags")
    @Position(400)
    //@Component(Component.Type.TAG)
    @Description("A collection of tags associated with a {name}")
    @Width("150px")
    @Convert(converter = TagsConverter.class)
    @Filterable()
    private Set<String> tags;
}
