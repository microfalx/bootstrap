package net.microfalx.bootstrap.jdbc.entity.surrogate;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.microfalx.bootstrap.dataset.annotation.Formattable;
import net.microfalx.bootstrap.dataset.annotation.OrderBy;
import net.microfalx.bootstrap.dataset.model.Formatters;
import net.microfalx.lang.Timestampable;
import net.microfalx.lang.annotation.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * A base class for all entities which can be named and have timestamps.
 */
@MappedSuperclass
@ToString(callSuper = true)
@Getter
@Setter
public abstract class NamedAndTimestampedIdentityAware<T extends Number & Serializable> extends NamedIdentityAware<T> implements Timestampable<LocalDateTime> {

    @Column(name = "created_at", nullable = false, updatable = false)
    @NotNull
    @Position(500)
    @Visible(value = false)
    @Description("The timestamp when the {name} was created")
    @Formattable(tooltip = Formatters.CreatedAtTooltip.class, elapsed = true)
    @OrderBy(OrderBy.Direction.DESC)
    @CreatedDate
    @CreatedAt
    private LocalDateTime createdAt;

    @Column(name = "modified_at")
    @Position(501)
    @Visible(modes = {Visible.Mode.BROWSE})
    @Description("The timestamp when the {name} was last time modified")
    @Formattable(tooltip = Formatters.ModifiedAtTooltip.class, elapsed = true)
    @LastModifiedDate
    @ModifiedAt
    private LocalDateTime modifiedAt;

    @PrePersist
    public final void beforePersist() {
        updateTimestamps();
        updateOther();
    }

    @PreUpdate
    public final void beforeUpdate() {
        updateTimestamps();
        updateOther();
    }

    protected void updateTimestamps() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        modifiedAt = LocalDateTime.now();
    }

    protected void updateOther() {

    }
}
