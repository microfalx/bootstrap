package net.microfalx.bootstrap.jdbc.entity.surrogate;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.microfalx.bootstrap.jdbc.jpa.JpaUtils;
import net.microfalx.lang.Ownable;
import net.microfalx.lang.annotation.*;
import org.springframework.data.annotation.LastModifiedBy;

import java.io.Serializable;

/**
 * A base class for all entities which can be named, owned and have timestamps.
 */
@MappedSuperclass
@ToString(callSuper = true)
@Getter
@Setter
public abstract class NamedAndOwnedAndTimestampedIdentityAware<T extends Number & Serializable> extends NamedAndTimestampedIdentityAware<T> implements Ownable<String> {

    @Column(name = "created_by", nullable = false, updatable = false)
    @Position(502)
    @Visible(false)
    @Description("The user who created the {name}")
    @CreatedBy
    @org.springframework.data.annotation.CreatedBy
    private String createdBy;

    @Column(name = "modified_by")
    @Position(503)
    @Visible(false)
    @Description("The user who modified the {name} last time")
    @LastModifiedBy
    @ModifiedBy
    private String modifiedBy;

    @Override
    protected void updateOther() {
        if (createdBy == null) createdBy = JpaUtils.getCurrentUserName();
        modifiedBy = JpaUtils.getCurrentUserName();
    }
}
