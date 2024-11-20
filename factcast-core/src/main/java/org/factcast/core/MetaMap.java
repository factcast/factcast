package org.factcast.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.errorprone.annotations.Var;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import org.checkerframework.checker.units.qual.K;

import javax.sound.midi.Soundbank;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@JsonSerialize(using = MetaMap.Serializer.class)
@JsonDeserialize(using = MetaMap.Deserializer.class)
public class MetaMap implements Map<String, String> {
    @Delegate(excludes = ExcludeDelegationFromMultiMap.class)
    private final Multimap<String, String> backing = ArrayListMultimap.create();

    @Override
    public String get(Object key) {
        return firstElement(backing.get((String) key));
    }

    @Override
    public String put(String key, String value) {
        backing.put(key, value);
        return null;
    }

    @Override
    public String remove(Object key) {
        return firstElement(backing.removeAll(key));
    }

    private static String firstElement(Collection<String> col) {
        return col.stream().findFirst().orElse(null);
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> m) {
        if (m != null)
            m.forEach(backing::put);
    }

    @Override
    public Set<Entry<String, String>> entrySet() {
        return new HashSet<>(backing.entries());
    }


    private interface ExcludeDelegationFromMultiMap {
        String get(String key);

        String put(String k, String v);

        boolean putAll(Multimap<String, String> multimap);
    }

    static class Serializer extends JsonSerializer<MetaMap> {

        @Override
        @SneakyThrows
        public void serialize(MetaMap metaMap, JsonGenerator jgen, SerializerProvider serializerProvider) throws IOException {
            if (metaMap != null) {
                jgen.writeStartObject();
                for (String k : metaMap.keySet()) {
                    Collection<String> values = metaMap.getAll(k);
                    if (values == null || values.isEmpty())
                        throw new IllegalStateException("key without value encountered");

                    if (values.size() == 1) {
                        jgen.writeStringField(k, values.iterator().next());
                    } else {
                        String[] v = values.toArray(new String[0]);
                        System.out.println(v[0]);
                        jgen.writeFieldName(k);
                        jgen.writeArray(v, 0, v.length);
                    }
                }
                jgen.writeEndObject();
            }
        }
    }

    public Collection<String> getAll(String k) {
        return backing.get(k);
    }

    static class Deserializer extends JsonDeserializer<MetaMap> {
        @Override
        public MetaMap deserialize(JsonParser jp, DeserializationContext deserializationContext) throws IOException, JacksonException {
            MetaMap ret = new MetaMap();

            JsonNode node = jp.getCodec().readTree(jp);
            Iterator<Entry<String, JsonNode>> fields = node.fields();
            fields.forEachRemaining(e->{
                String k=e.getKey();
                JsonNode n = e.getValue();
                if(n.isNull())
                    ret.put(k,null);else
                if (n.isTextual())
                    ret.put(k,n.textValue());
                else{
                    if (!n.isArray())
                        throw new IllegalStateException("expected array but got " + n);
                    ((ArrayNode)n).iterator().forEachRemaining(v->ret.put(k,v.textValue()));
                }
            });
            return ret;
        }
    }


    @SneakyThrows
    public static void main(String[] args) {
        ObjectMapper om = new ObjectMapper();
        String json = om.writeValueAsString(new Foo());
        System.out.println(json);
        Foo deser = om.readerFor(Foo.class).readValue(json);
        System.out.println(om.writeValueAsString(deser));
    }

    static class Foo {
        @JsonProperty
        String someString = "oink";
        @JsonProperty
        MetaMap meta;

        Foo() {
            meta = new MetaMap();
            meta.put("foo", "bar");
            meta.put("foo", "baz");
            meta.put("single", "value");
            meta.put("someNull",null); meta.put("otherNull",null); meta.put("otherNull",null);
        }
    }
}
