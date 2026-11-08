package net.microfalx.bootstrap.model;

import net.microfalx.lang.StringUtils;
import net.microfalx.lang.annotation.Description;
import net.microfalx.lang.annotation.I18n;
import net.microfalx.lang.annotation.Label;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;
import static net.microfalx.lang.StringUtils.*;
import static org.apache.commons.lang3.ClassUtils.isAssignable;

/**
 * Base class for all fields.
 *
 * @param <M> the model type
 */
public abstract class AbstractField<M> implements Field<M> {

    private final AbstractMetadata<M, ? extends AbstractField<M>, ?> metadata;
    private final String id;
    private final String name;
    private final String property;
    private boolean isId;
    private boolean isNaturalId;
    private boolean isName;
    private int index = -1;
    private int position;
    private Class<?> dataClass = Object.class;
    private Class<?> genericDataClass = Object.class;
    private DataType dataType = DataType.MODEL;
    private boolean readOnly;
    private boolean required;
    private boolean _transient;
    private String label;
    private boolean labelCalculated;
    private String labelIcon;
    private boolean labelIconCalculated;
    private String group;
    private boolean groupCalculated;

    public AbstractField(AbstractMetadata<M, ? extends AbstractField<M>, ?> metadata, String name, String property) {
        requireNotEmpty(metadata);
        requireNotEmpty(name);
        this.id = toIdentifier(name);
        this.metadata = metadata;
        this.name = name;
        this.property = property;
    }

    @Override
    public final String getId() {
        return id;
    }

    @Override
    public final Metadata<M, ? extends Field<M>, ?> getMetadata() {
        return metadata;
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final String getLabel() {
        if (labelCalculated) return label;
        label = getI18n().getText(getI18nPrefix() + ".label", false);
        if (isEmpty(label)) {
            Label labelAnnot = findAnnotation(Label.class);
            if (getLabelIcon() != null) {
                label = EMPTY_STRING;
                labelCalculated = true;
            } else {
                label = labelAnnot != null ? labelAnnot.value() : EMPTY_STRING;
            }
        }
        if (isEmpty(label) && !labelCalculated) {
            label = beautifyCamelCase(getName());
        }
        labelCalculated = true;
        return label;
    }

    public final String getGroup() {
        if (groupCalculated) return group;
        group = getI18n().getText(getI18nPrefix() + ".group", false);
        if (isEmpty(group)) {
            Label labelAnnot = findAnnotation(Label.class);
            if (labelAnnot != null) {
                group = isNotEmpty(labelAnnot.group()) ? labelAnnot.group() : EMPTY_STRING;
            }
        }
        groupCalculated = true;
        return group;
    }

    @Override
    public String getLabelIcon() {
        if (labelIconCalculated) return labelIcon;
        Label labelAnnot = findAnnotation(Label.class);
        labelIcon = labelAnnot != null && isNotEmpty(labelAnnot.icon()) ? labelAnnot.icon() : null;
        labelIconCalculated = true;
        return labelIcon;
    }

    @Override
    public String getDescription() {
        String description = getI18n().getText(getI18nPrefix() + ".description", false);
        if (isEmpty(description)) {
            Description descriptionAnnot = findAnnotation(Description.class);
            if (descriptionAnnot != null) {
                description = descriptionAnnot.value();
            }
        }
        if (isNotEmpty(description)) {
            description = org.apache.commons.lang3.StringUtils.replaceOnce(description, "{name}", metadata.getName());
        }
        return StringUtils.isEmpty(description) ? null : description;
    }

    @Override
    public final String getProperty() {
        return property;
    }

    @Override
    public final boolean isId() {
        return isId;
    }

    protected final void setId(boolean isId) {
        this.isId = isId;
    }

    @Override
    public final boolean isNaturalId() {
        return isNaturalId;
    }

    protected final void setNaturalId(boolean naturalId) {
        isNaturalId = naturalId;
    }

    @Override
    public final boolean isName() {
        return isName;
    }

    protected final void setIsName(boolean isName) {
        this.isName = isName;
    }

    @Override
    public final int getIndex() {
        return index;
    }

    protected final void setIndex(int index) {
        this.index = index;
    }

    @Override
    public final int getPosition() {
        return position;
    }

    protected void setPosition(int position) {
        this.position = position;
    }

    @Override
    public final boolean isRequired() {
        return required;
    }

    protected final void setRequired(boolean required) {
        this.required = required;
    }

    public final boolean isReadOnly() {
        return readOnly;
    }

    protected final void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public final boolean isTransient() {
        return _transient;
    }

    protected final void setTransient(boolean _transient) {
        this._transient = _transient;
    }

    @Override
    public final Class<?> getDataClass() {
        return dataClass;
    }

    final void setDataClass(Class<?> dataClass) {
        requireNonNull(dataClass);
        this.dataClass = dataClass;
        updateDataType(dataClass);
    }

    @Override
    public Class<?> getGenericDataClass() {
        if (!(getDataType() == DataType.COLLECTION || getDataType() == DataType.MAP)) {
            throw new ModelException("A generic type is available only for collections and maps, field " + getName());
        }
        return genericDataClass;
    }

    void setGenericDataClass(Class<?> genericDataClass) {
        requireNonNull(genericDataClass);
        this.genericDataClass = genericDataClass;
    }

    @Override
    public final DataType getDataType() {
        return dataType;
    }

    @Override
    public <A extends Annotation> A findAnnotation(Class<A> annotationClass) {
        return null;
    }

    @Override
    public final <A extends Annotation> boolean hasAnnotation(Class<A> annotationClass) {
        return findAnnotation(annotationClass) != null;
    }

    /**
     * Converts an object to a target type.
     *
     * @param value  the value to convert
     * @param target the target class
     * @param <T>    the type of the target value
     * @return the converted value
     */
    protected final <T> T from(Object value, Class<T> target) {
        return Types.from(value, target);
    }

    /**
     * Returns the I18n reference.
     *
     * @return a non-null instance
     */
    protected final net.microfalx.bootstrap.core.i18n.I18n getI18n() {
        return metadata.getI18n();
    }

    private void updateDataType(Class<?> dataClass) {
        DataType dataType = dataTypes.get(dataClass);
        if (dataType != null) {
            this.dataType = dataType;
        } else if (isAssignable(dataClass, Enum.class)) {
            this.dataType = DataType.ENUM;
        } else if (isAssignable(dataClass, Collection.class)) {
            this.dataType = DataType.COLLECTION;
        } else if (isAssignable(dataClass, Map.class)) {
            this.dataType = DataType.MAP;
        } else {
            this.dataType = DataType.MODEL;
        }
    }

    private String getI18nPrefix() {
        I18n i18nAnnot = findAnnotation(I18n.class);
        String suffix = toIdentifier(i18nAnnot != null ? i18nAnnot.value() : getName());
        return metadata.getI18nPrefix() + suffix;
    }

    @Override
    public String toString() {
        return "AbstractField{" +
                "metadata=" + metadata.getName() +
                ", id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", property='" + property + '\'' +
                ", dataClass=" + dataClass +
                ", dataType=" + dataType +
                '}';
    }

    private static final Map<Class<?>, DataType> dataTypes = new HashMap<>();

    static {

        // numbers
        dataTypes.put(byte.class, DataType.INTEGER);
        dataTypes.put(Byte.class, DataType.INTEGER);
        dataTypes.put(short.class, DataType.INTEGER);
        dataTypes.put(Short.class, DataType.INTEGER);
        dataTypes.put(int.class, DataType.INTEGER);
        dataTypes.put(Integer.class, DataType.INTEGER);
        dataTypes.put(long.class, DataType.INTEGER);
        dataTypes.put(Long.class, DataType.INTEGER);
        dataTypes.put(BigInteger.class, DataType.INTEGER);
        dataTypes.put(float.class, DataType.NUMBER);
        dataTypes.put(Float.class, DataType.NUMBER);
        dataTypes.put(double.class, DataType.NUMBER);
        dataTypes.put(Double.class, DataType.NUMBER);
        dataTypes.put(BigDecimal.class, DataType.NUMBER);

        // date/time
        dataTypes.put(Date.class, DataType.DATE_TIME);
        dataTypes.put(java.sql.Date.class, DataType.DATE);
        dataTypes.put(java.sql.Time.class, DataType.TIME);
        dataTypes.put(LocalDate.class, DataType.DATE);
        dataTypes.put(LocalTime.class, DataType.TIME);
        dataTypes.put(LocalDateTime.class, DataType.DATE_TIME);
        dataTypes.put(ZonedDateTime.class, DataType.DATE_TIME);
        dataTypes.put(OffsetDateTime.class, DataType.DATE_TIME);
        dataTypes.put(Duration.class, DataType.DURATION);

        // various
        dataTypes.put(boolean.class, DataType.BOOLEAN);
        dataTypes.put(Boolean.class, DataType.BOOLEAN);
        dataTypes.put(String.class, DataType.STRING);

    }
}
