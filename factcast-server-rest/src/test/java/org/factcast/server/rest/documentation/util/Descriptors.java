package org.factcast.server.rest.documentation.util;

import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;

import java.util.List;

import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.request.ParameterDescriptor;

import com.google.common.collect.Lists;

public class Descriptors {
    public static List<FieldDescriptor> getFactFieldDescriptors(String prefix,
            ConstrainedFields fields) {
        return Lists.newArrayList(fields.withPath(prefix + "header").description(
                "The header of the new fact, it could have many custom attributes, but the follwing are needed"), //
                fields.withPath(prefix + "header.id").description("client side UUID"), //
                fields.withPath(prefix + "header.ns").description("namespace"), //
                fields.withPath(prefix + "header.type").description("type"), //
                fields.withPath(prefix + "header.aggIds").description("IDs of aggregates involved"), //
                fields.withPath(prefix + "header.meta").description("Key-value map for meta data"),
                fields.withPath(prefix + "payload").description("The payload of the new fact"));

    }

    public static List<ParameterDescriptor> getSubscriptionRequestParamsdescriptor() {

        return Lists.newArrayList(parameterWithName("from").description(
                "UUID of the last events, the client crawled before").optional(),

                parameterWithName("follow").description("Boolean, if the stream is to infinitiy")
                        .optional(),

                parameterWithName("factSpec").description("multiple specifications, in json"));
    }
}
