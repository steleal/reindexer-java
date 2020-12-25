/**
 * Copyright 2020 Restream
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.rt.restream.reindexer.binding.cproto;

import ru.rt.restream.reindexer.binding.cproto.cjson.CJsonItemWriter;
import ru.rt.restream.reindexer.binding.cproto.cjson.CtagMatcher;
import ru.rt.restream.reindexer.binding.cproto.cjson.PayloadType;

/**
 * Encodes item in cjson format and converts it to array of bytes.
 */
public class CjsonItemSerializer<T> implements ItemSerializer<T> {

    private final PayloadType payloadType;

    public CjsonItemSerializer(PayloadType payloadType) {
        this.payloadType = payloadType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] serialize(T item) {
        CtagMatcher ctagMatcher = new CtagMatcher();
        if (payloadType != null) {
            ctagMatcher.read(payloadType);
        }
        ByteBuffer byteBuffer = new ByteBuffer();
        CJsonItemWriter<T> itemWriter = new CJsonItemWriter<>(ctagMatcher);
        itemWriter.writeItem(byteBuffer, item);
        return byteBuffer.bytes();
    }

}