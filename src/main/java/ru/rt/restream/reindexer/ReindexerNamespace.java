package ru.rt.restream.reindexer;

import java.util.List;

public class ReindexerNamespace<T> {

    private final String name;

    private final Class<T> itemClass;

    private final boolean enableStorage;

    private final boolean createStorageIfMissing;

    private final boolean dropStorageOnFileFormatError;

    private final boolean dropOnIndexConflict;

    private final boolean disableObjCache;

    private final long objCacheItemsCount;

    private final List<ReindexerIndex> indexes;

    public static<T> Builder<T> builder() {
        return new Builder<>();
    }

    private ReindexerNamespace(Builder<T> builder) {
        this.itemClass = builder.itemClass;
        this.name = builder.name;
        this.enableStorage = builder.enableStorage;
        this.createStorageIfMissing = builder.createStorageIfMissing;
        this.dropOnIndexConflict = builder.dropOnIndexConflict;
        this.dropStorageOnFileFormatError = builder.dropStorageOnFileFormatError;
        this.disableObjCache = builder.disableObjCache;
        this.objCacheItemsCount = builder.objCacheItemsCount;
        this.indexes = builder.indexes;
    }

    public String getName() {
        return name;
    }

    public Class<T> getItemClass() {
        return itemClass;
    }

    public boolean isEnableStorage() {
        return enableStorage;
    }

    public boolean isCreateStorageIfMissing() {
        return createStorageIfMissing;
    }

    public boolean isDropStorageOnFileFormatError() {
        return dropStorageOnFileFormatError;
    }

    public boolean isDropOnIndexConflict() {
        return dropOnIndexConflict;
    }


    public boolean isDisableObjCache() {
        return disableObjCache;
    }


    public long getObjCacheItemsCount() {
        return objCacheItemsCount;
    }


    public List<ReindexerIndex> getIndexes() {
        return indexes;
    }


    public static final class Builder<T> {
        private String name;
        private Class<T> itemClass;
        private boolean enableStorage;
        private boolean createStorageIfMissing;
        private boolean dropStorageOnFileFormatError;
        private boolean dropOnIndexConflict;
        private boolean disableObjCache;
        private long objCacheItemsCount;
        private List<ReindexerIndex> indexes;

        private Builder() {
        }

        public Builder<T> name(String name) {
            this.name = name;
            return this;
        }

        public Builder<T> itemClass(Class<T> itemClass) {
            this.itemClass = itemClass;
            return this;
        }

        public Builder<T> enableStorage(boolean enableStorage) {
            this.enableStorage = enableStorage;
            return this;
        }

        public Builder<T> createStorageIfMissing(boolean createStorageIfMissing) {
            this.createStorageIfMissing = createStorageIfMissing;
            return this;
        }

        public Builder<T> dropStorageOnFileFormatError(boolean dropStorageOnFileFormatError) {
            this.dropStorageOnFileFormatError = dropStorageOnFileFormatError;
            return this;
        }

        public Builder<T> dropOnIndexConflict(boolean dropOnIndexConflict) {
            this.dropOnIndexConflict = dropOnIndexConflict;
            return this;
        }

        public Builder<T> disableObjCache(boolean disableObjCache) {
            this.disableObjCache = disableObjCache;
            return this;
        }

        public Builder<T> objCacheItemsCount(long objCacheItemsCount) {
            this.objCacheItemsCount = objCacheItemsCount;
            return this;
        }

        public Builder<T> indexes(List<ReindexerIndex> indexes) {
            this.indexes = indexes;
            return this;
        }

        public ReindexerNamespace<T> build() {
            return new ReindexerNamespace<>(this);
        }
    }
}