package ru.rt.restream.reindexer.binding.cproto.cjson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PayloadType {

    private final long namespaceId;

    private final String namespaceName;

    private final long version;

    private final long stateToken;

    private final long pStringHdrOffset;

    private final List<String> tags;

    private final List<PayloadField> fields;

    private final Map<String, Integer> names = new HashMap<>();

    public PayloadType(long namespaceId, String namespaceName, long version, long stateToken, long pStringHdrOffset,
                       List<String> tags, List<PayloadField> fields) {
        this.namespaceId = namespaceId;
        this.namespaceName = namespaceName;
        this.version = version;
        this.stateToken = stateToken;
        this.pStringHdrOffset = pStringHdrOffset;
        this.tags = tags;
        this.fields = fields;
        for (int i = 0; i < tags.size(); i++) {
            names.put(tags.get(i), i);
        }
    }

    public int nameToTag (String name) {
        Integer tag = names.get(name);
        if (tag == null) {
            return 0;
        } else {
            return tag + 1;
        }
    }

    public String tagToName (int tag) {
        tag = tag & ((1 << 12) - 1);

        if (tag == 0) {
            return "";
        }

        if (tag - 1 >= tags.size()) {
            throw new IllegalArgumentException(String.format("Unknown tag %d\n", tag));
        }

        return tags.get(tag - 1);
    }

    public long getNamespaceId() {
        return namespaceId;
    }

    public String getNamespaceName() {
        return namespaceName;
    }

    public long getVersion() {
        return version;
    }

    public long getStateToken() {
        return stateToken;
    }

    public long getpStringHdrOffset() {
        return pStringHdrOffset;
    }

    public List<String> getTags() {
        return tags;
    }

    public List<PayloadField> getFields() {
        return fields;
    }
}

