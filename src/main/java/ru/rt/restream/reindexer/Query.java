/**
 * Copyright 2020 Restream
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.rt.restream.reindexer;

import ru.rt.restream.reindexer.binding.Binding;
import ru.rt.restream.reindexer.binding.Consts;
import ru.rt.restream.reindexer.binding.QueryResult;
import ru.rt.restream.reindexer.binding.RequestContext;
import ru.rt.restream.reindexer.binding.TransactionContext;
import ru.rt.restream.reindexer.binding.cproto.ByteBuffer;
import ru.rt.restream.reindexer.binding.cproto.CprotoIterator;
import ru.rt.restream.reindexer.binding.cproto.cjson.PayloadType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static ru.rt.restream.reindexer.binding.Consts.INNER_JOIN;
import static ru.rt.restream.reindexer.binding.Consts.LEFT_JOIN;
import static ru.rt.restream.reindexer.binding.Consts.OR_INNER_JOIN;
import static ru.rt.restream.reindexer.binding.Consts.QUERY_DROP_FIELD;
import static ru.rt.restream.reindexer.binding.Consts.QUERY_END;
import static ru.rt.restream.reindexer.binding.Consts.QUERY_JOIN_CONDITION;
import static ru.rt.restream.reindexer.binding.Consts.QUERY_JOIN_ON;
import static ru.rt.restream.reindexer.binding.Consts.QUERY_UPDATE_FIELD;
import static ru.rt.restream.reindexer.binding.Consts.QUERY_UPDATE_FIELD_V2;
import static ru.rt.restream.reindexer.binding.Consts.VALUE_BOOL;
import static ru.rt.restream.reindexer.binding.Consts.VALUE_NULL;

public class Query<T> {

    private static final int DEFAULT_FETCH_COUNT = 100;

    private static final int OP_OR = 1;
    private static final int OP_AND = 2;
    private static final int OP_NOT = 3;

    public enum Condition {
        ANY(0),
        EQ(1),
        LT(2),
        LE(3),
        GT(4),
        GE(5),
        RANGE(6),
        SET(7),
        ALLSET(8),
        EMPTY(9);
        private final int code;

        Condition(int code) {
            this.code = code;
        }
    }

    private final Binding binding;

    private final ByteBuffer buffer = new ByteBuffer();

    private int nextOperation = OP_AND;

    private final ReindexerNamespace<T> namespace;

    private final TransactionContext transactionContext;

    private int fetchCount = DEFAULT_FETCH_COUNT;

    private final List<Query<?>> joinQueries = new ArrayList<>();

    private final List<String> joinToFields = new ArrayList<>();

    private int joinType;

    private Query<?> root;

    public Query(Binding binding, ReindexerNamespace<T> namespace, TransactionContext transactionContext) {
        this.binding = binding;
        this.namespace = namespace;
        this.transactionContext = transactionContext;
        buffer.putVString(namespace.getName());
    }

    public<J> Query<T> join(Query<J> joinQuery, String field) {
        if (nextOperation == OP_OR) {
            nextOperation = OP_AND;
            return join(joinQuery, field, OR_INNER_JOIN);
        }

        return join(joinQuery, field, INNER_JOIN);
    }

    private<J> Query<T> join(Query<J> joinQuery, String field, int joinType) {
        if (joinQuery.root != null) {
            throw new IllegalStateException("query.Join call on already joined query. You shoud create new Query");
        }

        if (joinType != LEFT_JOIN) {
            buffer.putVarUInt32(QUERY_JOIN_CONDITION);
            buffer.putVarUInt32(joinType);
            buffer.putVarUInt32(joinQueries.size()); // index of join query
        }

        joinQuery.joinType = joinType;
        joinQuery.root = this;
        joinQueries.add(joinQuery);
        joinToFields.add(field);
        //q.joinHandlers = append(q.joinHandlers, nil)
        return this;
    }

    public Query<T> on(String index, Condition condition, String joinIndex) {
        Query<?> joinQuery = joinQueries.get(joinQueries.size() - 1);
        joinQuery.buffer.putVarUInt32(QUERY_JOIN_ON);
        joinQuery.buffer.putVarUInt32(joinQuery.nextOperation);
        joinQuery.buffer.putVarUInt32(condition.code);
        joinQuery.buffer.putVString(index);
        joinQuery.buffer.putVString(joinIndex);
        joinQuery.nextOperation = OP_AND;
        return this;
    }

    /**
     * Queries are possible only on the indexed fields, marked with reindex annotation.
     *
     * @param indexName index name
     * @param condition condition value {@link Condition}
     * @param values    values to match
     * @return the {@link Query} for further customizations
     */
    public Query<T> where(String indexName, Condition condition, Object... values) {
        buffer.putVarUInt32(Consts.QUERY_CONDITION)
                .putVString(indexName)
                .putVarUInt32(nextOperation)
                .putVarUInt32(condition.code);

        this.nextOperation = OP_AND;

        if (values != null && values.length > 0) {
            buffer.putVarUInt32(values.length);
            for (Object key : values) {
                putValue(key);
            }
        }

        return this;
    }

    /**
     * Next condition will added with OR
     *
     * @return the {@link Query} for further customizations
     */
    public Query<T> or() {
        this.nextOperation = OP_OR;
        return this;
    }

    /**
     * Next condition will added with NOT
     *
     * @return the {@link Query} for further customizations
     */
    public Query<T> not() {
        this.nextOperation = OP_NOT;
        return this;
    }

    /**
     * Add where condition to DB query with interface args for composite indexes.
     *
     * @param indexName composite index name
     * @param condition condition value {@link Condition}
     * @param values    values of composite index to match
     * @return the {@link Query} for further customizations
     */
    public Query<T> whereComposite(String indexName, Condition condition, Object... values) {
        where(indexName, condition, new Object[]{values});
        return this;
    }

    public Query<T> limit(int limit) {
        if (limit > 0) {
            buffer.putVarUInt32(Consts.QUERY_LIMIT)
                    .putVarUInt32(limit);
        }
        return this;
    }

    public Query<T> offset(int offset) {
        if (offset > 0) {
            buffer.putVarUInt32(Consts.QUERY_OFFSET)
                    .putVarUInt32(offset);
        }
        return this;
    }

    /**
     * Apply sort order to returned from query items. If values argument specified, then items equal to values, if found
     * will be placed in the top positions. For composite indexes values must be []interface{}, with value of each
     * subindex
     *
     * @param index  the index name
     * @param desc   true if sorting in descending order
     * @param values values to match
     * @return the {@link Query} for further customizations
     */
    public Query<T> sort(String index, boolean desc, Object... values) {

        buffer.putVarUInt32(Consts.QUERY_SORT_INDEX)
                .putVString(index);
        if (desc) {
            buffer.putVarUInt32(1);
        } else {
            buffer.putVarUInt32(0);
        }

        buffer.putVarUInt32(values.length);
        for (Object value : values) {
            putValue(value);
        }

        return this;
    }

    /**
     * FetchCount sets the number of items that will be fetched by one operation
     * When fetchCount {@literal <=} 0 query will fetch all results in one operation
     *
     * @param fetchCount items count to fetch
     * @return the {@link Query} for further customizations
     */
    public Query<T> fetchCount(int fetchCount) {
        this.fetchCount = fetchCount;
        return this;
    }

    private void putValue(Object value) {
        if (value == null) {
            buffer.putVarUInt32(VALUE_NULL);
        } else if (value instanceof Boolean) {
            buffer.putVarUInt32(VALUE_BOOL);
            if ((Boolean) value) {
                buffer.putVarUInt32(1);
            } else {
                buffer.putVarUInt32(0);
            }
        } else if (value instanceof Integer) {
            buffer.putVarUInt32(Consts.VALUE_INT)
                    .putVarInt64((Integer) value);
        } else if (value instanceof String) {
            buffer.putVarUInt32(Consts.VALUE_STRING)
                    .putVString((String) value);
        } else if (value instanceof Object[]) {
            buffer.putVarUInt32(Consts.VALUE_TUPLE);
            final Object[] objects = (Object[]) value;
            buffer.putVarUInt32(objects.length);
            for (Object object : objects) {
                putValue(object);
            }
        }
    }

    /**
     * Will execute query, and return stream of items.
     * The returned stream must be closed using the {@link Stream#close()} method or
     * by using a Java 7 try-with-resources block.
     *
     * @return stream of items
     */
    public Stream<T> stream() {
        CloseableIterator<T> iterator = execute();
        Spliterator<T> spliterator = Spliterators.spliterator(iterator, iterator.size(), Spliterator.NONNULL);
        return StreamSupport.stream(spliterator, false).onClose(iterator::close);
    }

    /**
     * Will execute query, and return list of items.
     *
     * @return list of items
     */
    public List<T> toList() {
        try (CloseableIterator<T> iterator = execute()) {
            List<T> result = new ArrayList<>();
            while (iterator.hasNext()) {
                result.add(iterator.next());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Will execute query, and return one item.
     *
     * @return one item
     * @throws IllegalStateException if there are more than one or zero items
     */
    public T getOne() {
        T item = getOneInternal();
        if (item == null) {
            throw new IllegalStateException("Exactly one item expected, but there is zero");
        }
        return item;
    }

    /**
     * Will execute query, and return Optional item.
     *
     * @return Optional item
     * @throws IllegalStateException if there are more than one item
     */
    public Optional<T> findOne() {
        T item = getOneInternal();
        return Optional.ofNullable(item);
    }

    private T getOneInternal() {
        try (CloseableIterator<T> iterator = execute()) {
            T item = null;
            if (iterator.hasNext()) {
                item = iterator.next();
            }
            if (iterator.hasNext()) {
                throw new IllegalStateException("Exactly one item expected, but there are more");
            }
            return item;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Will execute query, and return slice of items.
     *
     * @return an iterator over a query result
     */
    public CloseableIterator<T> execute() {
        buffer.putVarUInt32(QUERY_END);

        for (Query<?> joinQuery : joinQueries) {
            buffer.putVarUInt32(joinQuery.joinType);
            buffer.writeBytes(joinQuery.buffer.bytes());
            buffer.putVarUInt32(QUERY_END);
        }

        PayloadType payloadType = namespace.getPayloadType();
        long[] ptVersions =  new long[]{payloadType == null ? 0 : payloadType.getStateToken()};
        RequestContext requestContext = transactionContext != null
                ? transactionContext.selectQuery(buffer.bytes(), fetchCount, ptVersions)
                : binding.selectQuery(buffer.bytes(), fetchCount, ptVersions);

        QueryResult queryResult = requestContext.getQueryResult();
        queryResult.getPayloadTypes().stream()
                .filter(pt -> payloadType == null || payloadType.getVersion() < pt.getVersion())
                .max(Comparator.comparing(PayloadType::getVersion))
                .ifPresent(namespace::updatePayloadType);

        return new CprotoIterator<>(namespace, requestContext, fetchCount);
    }

    /**
     * Will execute query, and delete items, matches query.
     */
    public void delete() {
        if (transactionContext != null) {
            transactionContext.deleteQuery(buffer.bytes());
        } else {
            binding.deleteQuery(buffer.bytes());
        }
    }

    /**
     * Adds update field request for update query
     *
     * @param fieldName field name
     * @param value     updated value
     * @return the {@link Query} for further customizations
     */
    public Query<T> set(String fieldName, Object value) {
        int cmd = QUERY_UPDATE_FIELD;
        if (value instanceof Collection<?>) { //Not tested
            Collection<?> values = (Collection<?>) value;
            if (values.size() <= 1) {
                cmd = QUERY_UPDATE_FIELD_V2;
                buffer.putVarUInt32(0); //isArray
            }
            buffer.putVarUInt32(cmd);
            buffer.putVString(fieldName);
            buffer.putVarUInt32(values.size());
            for (Object v : values) {
                putValue(v);
            }
        } else if (value != null && value.getClass().isArray()) { //not tested
            Object[] values = (Object[]) value;
            if (values.length <= 1) {
                cmd = QUERY_UPDATE_FIELD_V2;
                buffer.putVarUInt32(0); //isArray
            }
            buffer.putVarUInt32(cmd);
            buffer.putVString(fieldName);
            buffer.putVarUInt32(values.length);
            for (Object v : values) {
                putValue(v);
            }
        } else {
            buffer.putVarUInt32(cmd);
            buffer.putVString(fieldName);
            buffer.putVarUInt32(1); //size
            buffer.putVarUInt32(0); //function/value flag
            putValue(value);
        }

        return this;
    }

    /**
     * Drop removes field from item within Update statement.
     *
     * @param field field to drop
     * @return the {@link Query} for further customizations
     */
    public Query<T> drop(String field) {
        buffer.putVarUInt32(QUERY_DROP_FIELD);
        buffer.putVString(field);
        return this;
    }

    /**
     * Will execute query, and update fields in items, which matches query.
     */
    public void update() {
        if (transactionContext != null) {
            transactionContext.updateQuery(buffer.bytes());
        } else {
            binding.updateQuery(buffer.bytes());
        }
    }
}
