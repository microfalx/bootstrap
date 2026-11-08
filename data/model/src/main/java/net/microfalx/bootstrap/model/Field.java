package net.microfalx.bootstrap.model;

import net.microfalx.lang.Descriptable;
import net.microfalx.lang.Identifiable;
import net.microfalx.lang.Nameable;

import java.lang.annotation.Annotation;

/**
 * A field in a (data) model.
 */
public interface Field<M> extends Identifiable<String>, Nameable, Descriptable {

    /**
     * Converts an object to a target type.
     *
     * @param value  the value to convert
     * @param target the target class
     * @param <T>    the type of the target value
     * @return the converted value
     */
    static <T> T from(Object value, Class<T> target) {
        return Types.from(value, target);
    }

    /**
     * Returns the metadata which owns this field.
     *
     * @return a non-null instance
     */
    Metadata<M, ? extends Field<M>, ?> getMetadata();

    /**
     * Returns the name of the field.
     * <p>
     * The field name can be different from property name for some models (table column vs bean property for JPA entities).
     *
     * @return a non-null instance
     */
    String getName();

    /**
     * Returns the label associated with the field.
     *
     * @return a non-null instance
     * @see net.microfalx.lang.annotation.I18n
     * @see net.microfalx.lang.annotation.Label
     */
    String getLabel();

    /**
     * Returns the (label) group associated with the field.
     *
     * @return the group, null if there is no group
     * @see net.microfalx.lang.annotation.I18n
     * @see net.microfalx.lang.annotation.Label
     */
    String getGroup();

    /**
     * Returns the icon used along the label (in front of the label)
     *
     * @return the icon, null if not defined
     */
    String getLabelIcon();

    /**
     * Returns the description associated with a field.
     * <p>
     * The description is usually displayed as a tooltip in UIs.
     *
     * @return the description, null if not defined
     */
    String getDescription();

    /**
     * Returns the property name (class property).
     *
     * @return the property name, null if the model is not a POJO
     */
    String getProperty();

    /**
     * Returns the index of the field within the model.
     *
     * @return a positive integer
     */
    int getIndex();

    /**
     * Returns the position of the field for UI representation
     *
     * @return a positive integer
     * @see net.microfalx.lang.annotation.Position
     */
    int getPosition();

    /**
     * Returns whether the field is part of the model identifier.
     *
     * @return {@code true} if part of the model identifier, {@code false} otherwise
     */
    boolean isId();

    /**
     * Returns whether the field holds the natural identifier of the model.
     *
     * @return {@code true} if the natural identifier, {@code false} otherwise
     */
    boolean isNaturalId();

    /**
     * Returns whether the field is part of the model name.
     *
     * @return {@code true} if part of the model name, {@code false} otherwise
     */
    boolean isName();

    /**
     * Returns whether the field is required (to have a value different than null).
     *
     * @return {@code true} if the field is required, {@code false} otherwise
     */
    boolean isRequired();

    /**
     * Returns whether the field is read-only.
     *
     * @return {@code true} if read-only, {@code false} otherwise
     */
    boolean isReadOnly();

    /**
     * Returns whether the field is transient (not persisted).
     *
     * @return {@code true} if transient, {@code false} otherwise
     */
    boolean isTransient();

    /**
     * Returns the class name supporting the field.
     *
     * @return a non-null instance
     */
    Class<?> getDataClass();

    /**
     * Returns the data type of the collection or map supporting this field.
     * <p>
     * This method will fail if the data type is not {@link DataType#COLLECTION} or a {@link DataType#MAP}.
     * <p>
     * For maps, the type is representing the type of the value.
     *
     * @return a non-null instance
     */
    Class<?> getGenericDataClass();

    /**
     * Returns an enum for the data type.
     *
     * @return a non-null instance
     */
    DataType getDataType();

    /**
     * Returns an annotation by its type.
     *
     * @param annotationClass the annotation type
     * @param <A>             the annotation type
     * @return the annotation, null if not registered
     */
    <A extends Annotation> A findAnnotation(Class<A> annotationClass);

    /**
     * Returns whether an annotation with a given type exists.
     *
     * @param annotationClass the annotation type
     * @param <A>             the annotation type
     * @return the annotation, null if not registered
     */
    <A extends Annotation> boolean hasAnnotation(Class<A> annotationClass);

    /**
     * Returns the value from the model for a given field.
     *
     * @param model the model
     * @return the value
     */
    Object get(M model);

    /**
     * Returns the value of the field, converted to a given type.
     *
     * @param model the model
     * @param type  the target type
     * @param <V>   the type of the value
     * @return the converted value
     */
    <V> V get(M model, Class<V> type);

    /**
     * Changes the value in the model for a given field.
     *
     * @param model the model
     * @param value the value
     */
    void set(M model, Object value);

    /**
     * Returns the display value for a field value.
     *
     * @param model the model
     * @return the display value
     */
    String getDisplay(M model);

    /**
     * Holds the data type of the field
     */
    enum DataType {

        /**
         * A boolean (yes/no)
         */
        BOOLEAN(false, false),

        /**
         * An integer (byte/short/int/long,big integer)
         */
        INTEGER(true, false),

        /**
         * A floating point (float/double,big number)
         */
        NUMBER(true, false),

        /**
         * Characters
         */
        STRING(false, false),

        /**
         * A date.
         */
        DATE(false, true),

        /**
         * A time.
         */
        TIME(false, true),

        /**
         * A date/time.
         */
        DATE_TIME(false, true),

        /**
         * A date/time.
         */
        DURATION(false, false),

        /**
         * An enum
         */
        ENUM(false, false),

        /**
         * A collection type
         */
        COLLECTION(false, false),

        /**
         * A map type
         */
        MAP(false, false),

        /**
         * Another model
         */
        MODEL(false, false),

        /**
         * An unmapped object
         */
        OBJECT(false, false);

        private final boolean numeric;
        private final boolean temporal;

        DataType(boolean numeric, boolean temporal) {
            this.numeric = numeric;
            this.temporal = temporal;
        }

        public boolean isText() {
            return this == STRING;
        }

        public boolean isBoolean() {
            return this == BOOLEAN;
        }

        public boolean isNumeric() {
            return numeric;
        }

        public boolean isTemporal() {
            return temporal;
        }

        public boolean isEnum() {
            return this == ENUM;
        }

        public boolean isStructure() {
            return this == COLLECTION || this == MAP;
        }

        public boolean isSimple() {
            return isText() || isBoolean() || isNumeric() || isTemporal() || isEnum();
        }
    }
}
