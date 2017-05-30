package org.factcast.server.rest.documentation.util;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.validation.metadata.BeanDescriptor;
import javax.validation.metadata.ConstraintDescriptor;
import javax.validation.metadata.PropertyDescriptor;

import org.reflections.ReflectionUtils;
import org.springframework.restdocs.constraints.Constraint;
import org.springframework.restdocs.constraints.ConstraintResolver;

import com.google.common.base.Predicate;

public class ValidatorConstraintResolver implements ConstraintResolver {

    private final Validator validator;

    /**
     * Creates a new {@code ValidatorConstraintResolver} that will use a
     * {@link Validator} in its default configuration to resolve constraints.
     *
     * @see Validation#buildDefaultValidatorFactory()
     * @see ValidatorFactory#getValidator()
     */
    public ValidatorConstraintResolver() {
        this(Validation.buildDefaultValidatorFactory().getValidator());
    }

    /**
     * Creates a new {@code ValidatorConstraintResolver} that will use the given
     * {@code Validator} to resolve constraints.
     *
     * @param validator
     *            the validator
     */
    public ValidatorConstraintResolver(Validator validator) {
        this.validator = validator;
    }

    @Override
    public List<Constraint> resolveForProperty(String property, Class<?> clazz) {
        List<Constraint> constraints = new ArrayList<>();

        String[] properties = property.split("\\.");
        Class<?> clazzforLoop = clazz;
        for (int i = 0; i < properties.length; i++) {
            String propertyForLoop = properties[i];
            propertyForLoop = propertyForLoop.replace("[]", "");

            BeanDescriptor beanDescriptor = this.validator.getConstraintsForClass(clazzforLoop);
            PropertyDescriptor propertyDescriptor = beanDescriptor.getConstraintsForProperty(
                    propertyForLoop);
            if (propertyDescriptor != null) {
                if (isLastElement(properties, i)) {
                    collectConstraints(constraints, propertyDescriptor);
                }
                clazzforLoop = getFollowUpClass(propertyDescriptor, clazzforLoop);
            } else {
                break;
            }
        }
        return constraints;
    }

    private boolean isLastElement(String[] properties, int i) {
        return i == properties.length - 1;
    }

    private void collectConstraints(List<Constraint> constraints,
            PropertyDescriptor propertyDescriptor) {
        for (ConstraintDescriptor<?> constraintDescriptor : propertyDescriptor
                .getConstraintDescriptors()) {
            constraints.add(new Constraint(constraintDescriptor.getAnnotation()
                    .annotationType()
                    .getName(), constraintDescriptor.getAttributes()));
        }
    }

    private Class<?> getFollowUpClass(PropertyDescriptor propertyDescriptor, Class<?> clazzBefore) {
        Class<?> clazz = propertyDescriptor.getElementClass();
        if (Collection.class.isAssignableFrom(clazz)) {
            final Predicate<? super Field> predicate = f -> f.getName().equals(propertyDescriptor
                    .getPropertyName());
            @SuppressWarnings("unchecked")
            Set<Field> field = ReflectionUtils.getAllFields(clazzBefore, predicate);
            Type typeArgument = ((ParameterizedType) field.iterator().next().getGenericType())
                    .getActualTypeArguments()[0];

            return (Class<?>) typeArgument;
        } else {
            return clazz;
        }
    }
}
