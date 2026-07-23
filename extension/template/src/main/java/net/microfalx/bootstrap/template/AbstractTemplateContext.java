package net.microfalx.bootstrap.template;

import net.microfalx.bootstrap.model.Attribute;
import net.microfalx.bootstrap.model.Attributes;
import net.microfalx.bootstrap.model.Field;
import net.microfalx.bootstrap.model.Metadata;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ExceptionUtils.rethrowExceptionAndReturn;

/**
 * Base class for all template context.
 *
 * @param <M>  the model
 * @param <F>  the field
 * @param <ID> the identifier type
 */
public abstract class AbstractTemplateContext<M, F extends Field<M>, ID> implements TemplateContext, Cloneable {

    private final Metadata<M, F, ID> metadata;
    private final M model;
    private Attributes<?> attributes;

    private Map<String, Object> variables = new HashMap<>();
    private Map<String, Object> wrapper = new WrapperMap();

    public AbstractTemplateContext(Metadata<M, F, ID> metadata, M model, Attributes<?> attributes) {
        this.metadata = metadata;
        this.model = model;
        this.attributes = attributes;
        if (model != null) {
            set("model", model);
        }
    }

    public Metadata<M, F, ID> getMetadata() {
        return metadata;
    }

    public Object getModel() {
        return model;
    }

    public Attributes<?> getAttributes() {
        return attributes;
    }

    @Override
    public Iterable<String> getNames() {
        return variables.keySet();
    }

    @Override
    public boolean has(String name) {
        requireNonNull(name);
        if (metadata != null) {
            F field = metadata.find(name);
            if (field != null) return true;
        }
        if (attributes != null) {
            Attribute attribute = attributes.get(name);
            if (attribute != null) return true;
        }
        return variables.containsKey(name);
    }

    @Override
    public Object get(String name) {
        requireNonNull(name);
        if (metadata != null) {
            F field = metadata.find(name);
            if (field != null) return field.get(model);
        }
        if (attributes != null) {
            Attribute attribute = attributes.get(name);
            if (attribute != null) return attribute.getValue();
        }
        return variables.get(name);
    }

    @Override
    public Object set(String name, Object value) {
        requireNonNull(name);
        return variables.put(name, value);
    }

    @Override
    public Map<String, Object> toMap() {
        return wrapper;
    }

    @Override
    public TemplateContext withAttributes(Attributes<?> attributes) {
        AbstractTemplateContext<M, F, ID> copy = copy();
        copy.attributes = attributes;
        return copy;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", AbstractTemplateContext.class.getSimpleName() + "[", "]")
                .add("metadata=" + metadata)
                .add("model=" + model)
                .add("attributes=" + attributes)
                .add("variables=" + variables)
                .toString();
    }

    Map<String, Object> getVariables() {
        return variables;
    }

    @SuppressWarnings("unchecked")
    private AbstractTemplateContext<M, F, ID> copy() {
        try {
            AbstractTemplateContext<M, F, ID> clone = (AbstractTemplateContext<M, F, ID>) clone();
            clone.variables = new HashMap<>(variables);
            clone.wrapper = clone.new WrapperMap();
            return clone;
        } catch (CloneNotSupportedException e) {
            return rethrowExceptionAndReturn(e);
        }
    }

    private class WrapperMap extends AbstractMap<String, Object> {

        @Override
        public Set<Entry<String, Object>> entrySet() {
            throw new UnsupportedOperationException("The map cannot be iterated. Use TemplateContext.getNames and TemplateContext.get");
        }

        @Override
        public boolean containsKey(Object key) {
            return AbstractTemplateContext.this.has(key.toString());
        }

        @Override
        public Object get(Object key) {
            return AbstractTemplateContext.this.get(key.toString());
        }

        @Override
        public Object getOrDefault(Object key, Object defaultValue) {
            Object value = AbstractTemplateContext.this.get(key.toString());
            return value != null ? value : defaultValue;
        }

        @Override
        public void forEach(BiConsumer<? super String, ? super Object> action) {
            AbstractTemplateContext.this.getNames().forEach(s -> action.accept(s, AbstractTemplateContext.this.get(s)));
        }

        @Override
        public Object merge(String key, Object value, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
            return AbstractTemplateContext.this.variables.merge(key, value, remappingFunction);
        }

        @Override
        public Object putIfAbsent(String key, Object value) {
            return AbstractTemplateContext.this.variables.putIfAbsent(key, value);
        }

        @Override
        public boolean remove(Object key, Object value) {
            return AbstractTemplateContext.this.variables.remove(key, value);
        }

        @Override
        public boolean replace(String key, Object oldValue, Object newValue) {
            return AbstractTemplateContext.this.variables.replace(key, oldValue, newValue);
        }

        @Override
        public Object replace(String key, Object value) {
            return AbstractTemplateContext.this.variables.replace(key, value);
        }

        @Override
        public Object computeIfAbsent(String key, Function<? super String, ?> mappingFunction) {
            return AbstractTemplateContext.this.variables.computeIfAbsent(key, mappingFunction);
        }

        @Override
        public Object computeIfPresent(String key, BiFunction<? super String, ? super Object, ?> remappingFunction) {
            return AbstractTemplateContext.this.variables.computeIfPresent(key, remappingFunction);
        }

        @Override
        public Object compute(String key, BiFunction<? super String, ? super Object, ?> remappingFunction) {
            return AbstractTemplateContext.this.variables.compute(key, remappingFunction);
        }
    }
}
