package org.factcast.server.rest.documentation;

import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.snippet.Attributes.key;

import org.springframework.restdocs.constraints.ConstraintDescriptions;
import org.springframework.restdocs.constraints.ResourceBundleConstraintDescriptionResolver;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.util.StringUtils;

class ConstrainedFields {

    private final ConstraintDescriptions constraintDescriptions;

    ConstrainedFields(Class<?> input) {
        this.constraintDescriptions = new ConstraintDescriptions(input,
                new ValidatorConstraintResolver(),
                new ResourceBundleConstraintDescriptionResolver());
    }

    FieldDescriptor withPath(String path) {
        return fieldWithPath(path).attributes(key("constraints").value(StringUtils
                .collectionToDelimitedString(this.constraintDescriptions.descriptionsForProperty(
                        path), ". ")));
    }
}