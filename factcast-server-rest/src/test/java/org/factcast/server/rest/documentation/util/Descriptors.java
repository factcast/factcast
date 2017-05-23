package org.factcast.server.rest.documentation.util;

import java.util.List;

import org.springframework.restdocs.payload.FieldDescriptor;

import com.google.common.collect.Lists;

public class Descriptors {
    public static List<FieldDescriptor> getFactFieldDescriptors(String prefix,
            ConstrainedFields fields) {
        return Lists.newArrayList(fields.withPath(prefix + "header").description(
                "The header of new fact, it could have many custom attributes. The following are known."), //
                fields.withPath(prefix + "header.id").description(
                        "UUID, is given by clients when commiting the fact"), //
                fields.withPath(prefix + "header.ns").description("namespace"), //
                fields.withPath(prefix + "header.type").description("type"), //
                fields.withPath(prefix + "header.aggIds").description("IDs of aggregates involved"), //
                fields.withPath(prefix + "header.meta").description("Key-value map for meta data"),
                fields.withPath(prefix + "payload").description("The payload of the new fact"));

    }

}
