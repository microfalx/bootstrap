package net.microfalx.bootstrap.configuration;

import net.microfalx.bootstrap.configuration.annotation.ConfigurationMapping;
import net.microfalx.configuration.Subset;
import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.ReflectionUtils;
import net.microfalx.lang.SecretUtils;
import net.microfalx.lang.StringUtils;
import net.microfalx.lang.annotation.DefaultValue;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Character.toLowerCase;
import static net.microfalx.lang.ExceptionUtils.getRootCauseDescription;

public class ConfigurationMappingFactoryBean<T> implements FactoryBean<T>, InvocationHandler {

    private final Class<T> type;
    @Autowired private ConfigurationService configurationService;

    private Subset subset;
    private String prefix;
    private final Map<String, Object> defaultValues = new HashMap<>();

    public ConfigurationMappingFactoryBean(Class<T> type) {
        this.type = type;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T getObject() {
        ConfigurationMapping annotation = type.getAnnotation(ConfigurationMapping.class);
        prefix = annotation.prefix();
        subset = configurationService.getConfiguration().at(prefix);
        initDefaultValues();
        Class<?>[] types = {type};
        if (ClassUtils.isSubClassOf(type, ConfigurationListenerAware.class)) {
            types = ArrayUtils.add(types, ConfigurationListenerAware.class);
        }
        return (T) Proxy.newProxyInstance(type.getClassLoader(), types, this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        if (methodName.equals("toString")) {
            return "Configuration{prefix=" + prefix + ",entries=" + describeEntries() + "}";
        } else if (methodName.equals("addListener")) {
            ConfigurationListener listener = (ConfigurationListener) args[0];
            // TODO forward me
            //configurationService.addListener(new PrefixForwardConfigurationListener(prefix, listener));
            return null;
        } else {
            String propertyName = resolvePropertyName(method);
            return subset.get(propertyName, method.getReturnType(), defaultValues.get(methodName));
        }
    }

    @Override
    public Class<?> getObjectType() {
        return type;
    }

    private String resolvePropertyName(Method method) {
        String name = method.getName();
        if (name.startsWith("get")) {
            name = toLowerCase(name.charAt(3)) + name.substring(4);
        } else if (name.startsWith("is")) {
            name = toLowerCase(name.charAt(2)) + name.substring(3);
        }
        return name;
    }

    private void initDefaultValues() {
        List<Method> methods = ReflectionUtils.getMethods(type);
        for (Method method : methods) {
            String name = method.getName();
            DefaultValue defaultValueAnnot = method.getAnnotation(DefaultValue.class);
            if (defaultValueAnnot != null) {
                defaultValues.put(name, defaultValueAnnot.value());
            } else if (ClassUtils.isSubClassOf(method.getReturnType(), Number.class)) {
                defaultValues.put(name, "0");
            } else if (ClassUtils.isSubClassOf(method.getReturnType(), Duration.class)) {
                defaultValues.put(name, Duration.ofSeconds(60));
            }
        }
    }

    private String describeEntries() {
        StringBuilder builder = new StringBuilder();
        try {
            for (String key : subset.getKeys()) {
                String value = subset.get(key);
                if (SecretUtils.isSecret(key)) {
                    value = SecretUtils.maskSecret(value);
                }
                StringUtils.append(builder, key + "=" + value, ';');
            }
        } catch (Exception e) {
            return "#ERROR: " + getRootCauseDescription(e);
        }
        return builder.toString();
    }

}
